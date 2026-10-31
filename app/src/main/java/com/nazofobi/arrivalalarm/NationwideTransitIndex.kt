package com.nazofobi.arrivalalarm

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.roundToInt

class NationwideTransitIndex(context: Context) : SQLiteOpenHelper(context, "nationwide_transit.db", null, 4) {
    companion object {
        private const val PROVIDER_ID = "gtfs-de-full"
        private const val READY_KEY = "ready"
        private const val FETCHED_KEY = "fetched_at"
        private const val COUNT_KEY = "stop_count"
        private const val VERSION_KEY = "source_version"
        private const val SCHEMA_KEY = "schema_version"
        private const val SOURCE_URL_KEY = "source_url"
        private const val HTTP_ETAG_KEY = "http_etag"
        private const val HTTP_LAST_MODIFIED_KEY = "http_last_modified"
        private const val REQUIRED_STOP_COUNT = 10_000
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        createTransitSchema(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            // Keep the last known-good stop index usable while the richer graph is populated.
            addColumnIfMissing(db, "stops", "location_type", "INTEGER")
            addColumnIfMissing(db, "stops", "platform_code", "TEXT")
            addColumnIfMissing(db, "stops", "wheelchair_boarding", "INTEGER")
            addColumnIfMissing(db, "stops", "stop_code", "TEXT")
            addColumnIfMissing(db, "stops", "zone_id", "TEXT")
            addColumnIfMissing(db, "stops", "stop_timezone", "TEXT")
            addColumnIfMissing(db, "stops", "level_id", "TEXT")
            // A pre-v3 database has no schedule graph, so create the current v4 shape directly.
            createScheduleTables(db)
        } else if (oldVersion < 4) {
            migrateScheduleSchemaV4(db)
        }
        if (oldVersion < 4) putMetadata(db, SCHEMA_KEY, "4")
    }

    fun isReady(): Boolean = metadata(READY_KEY) == "1"
    fun fetchedAt(): Long = metadata(FETCHED_KEY)?.toLongOrNull() ?: 0L
    fun stopCount(): Int = metadata(COUNT_KEY)?.toIntOrNull() ?: 0
    fun sourceVersion(): String = metadata(VERSION_KEY) ?: "unknown"

    /**
     * Historical API name retained for callers. The import is now a full GTFS schedule-graph import.
     * One SQLite transaction makes a failed refresh roll back to the previous known-good graph.
     */
    fun importStops(url: String, onCount: (Int) -> Unit) = importFeed(url, onCount)

    fun importFeed(url: String, onCount: (Int) -> Unit) {
        importFeedWithConnection(url, onCount, URL(url).openConnection())
    }

    /** Injected URLConnection is used only for deterministic HTTP/SQLite acceptance tests. */
    internal fun importFeedWithConnection(
        url: String,
        onCount: (Int) -> Unit,
        connection: URLConnection,
    ) {
        val db = writableDatabase
        connection.apply {
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        val http = connection as? HttpURLConnection
        // Revalidate only an already usable snapshot fetched from this exact URL.
        val validators = gtfsConditionalHeaders(
            ready = isReady(),
            sameSource = metadata(SOURCE_URL_KEY) == url,
            etag = metadata(HTTP_ETAG_KEY),
            lastModified = metadata(HTTP_LAST_MODIFIED_KEY),
        )
        http?.apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ArrivalAlarmAndroid/0.1")
            validators.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        if (http != null) {
            val status = try {
                http.responseCode
            } catch (failure: java.io.IOException) {
                // A failed response header read must not leave the feed connection open.
                http.disconnect()
                throw failure
            }
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                http.disconnect()
                // HTTP 304 does not change the usable feed or fetch timestamp.
                if (validators.isNotEmpty() && isReady()) {
                    onCount(stopCount())
                    return
                }
                error("GTFS beklenmeyen HTTP 304")
            }
            if (status != HttpURLConnection.HTTP_OK) {
                http.disconnect()
                error("GTFS indirme HTTP $status")
            }
        }

        // Reading feed headers can also fail after HTTP 200, before the import
        // transaction begins. Disconnect instead of leaking the network socket.
        val (responseEtag, responseLastModified, sourceVersion) = try {
            val etag = http?.getHeaderField("ETag")?.takeIf { it.isNotBlank() }
            val lastModified = http?.getHeaderField("Last-Modified")?.takeIf { it.isNotBlank() }
            val version = etag
                ?: lastModified
                ?: connection.lastModified.takeIf { it > 0L }?.toString()
                ?: connection.url.toString()
            Triple(etag, lastModified, version)
        } catch (failure: java.io.IOException) {
            http?.disconnect()
            throw failure
        }
        val counts = linkedMapOf<String, Int>()
        val seenGtfsTables = hashSetOf<String>()

        db.beginTransaction()
        try {
            clearTransitData(db)
            ZipInputStream(BufferedInputStream(connection.inputStream, 128 * 1024)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val file = entry.name.substringAfterLast('/').lowercase(Locale.ROOT)
                    if (!entry.isDirectory) {
                        rejectDuplicateGtfsZipMember(entry.name, seenGtfsTables)
                        val reader = strictGtfsUtf8Reader(zip)
                        val count = when (file) {
                            "stops.txt" -> importStopsEntry(db, reader, onCount)
                            "agency.txt" -> importAgency(db, reader)
                            "routes.txt" -> importRoutes(db, reader)
                            "trips.txt" -> importTrips(db, reader)
                            "stop_times.txt" -> importStopTimes(db, reader)
                            "calendar.txt" -> importCalendar(db, reader)
                            "calendar_dates.txt" -> importCalendarDates(db, reader)
                            "transfers.txt" -> importTransfers(db, reader)
                            "shapes.txt" -> importShapes(db, reader)
                            "levels.txt" -> importLevels(db, reader)
                            "pathways.txt" -> importPathways(db, reader)
                            "frequencies.txt" -> importFrequencies(db, reader)
                            "attributions.txt" -> importAttributions(db, reader)
                            "feed_info.txt" -> importFeedInfo(db, reader)
                            else -> 0
                        }
                        if (count > 0) counts[file] = count
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            // ZIP entry order is not significant in GTFS. Validate optional linked-transfer
            // stop references only after every stops.txt row has been imported.
            discardInvalidLinkedTransferStopReferences(db)

            val stops = counts["stops.txt"] ?: 0
            val routes = counts["routes.txt"] ?: 0
            val trips = counts["trips.txt"] ?: 0
            val stopTimes = counts["stop_times.txt"] ?: 0
            if (stops < REQUIRED_STOP_COUNT) error("GTFS durak sayısı beklenenden düşük: $stops")
            if (routes == 0 || trips == 0 || stopTimes == 0) {
                error("GTFS schedule graph eksik: routes=$routes trips=$trips stop_times=$stopTimes")
            }
            if ((counts["calendar.txt"] ?: 0) == 0 && (counts["calendar_dates.txt"] ?: 0) == 0) {
                error("GTFS service calendar eksik")
            }

            // Core table counts alone cannot prove a usable journey. A feed with
            // orphaned route/stop references must not replace last-known-good SQLite.
            val hasConnectedTrip = db.rawQuery(
                """
                SELECT 1 FROM trips t
                JOIN routes r ON r.route_id = t.route_id
                JOIN stop_times first_time ON first_time.trip_id = t.trip_id
                JOIN stop_times second_time ON second_time.trip_id = t.trip_id
                    AND second_time.stop_sequence > first_time.stop_sequence
                JOIN stops first_stop ON first_stop.id = first_time.stop_id
                JOIN stops second_stop ON second_stop.id = second_time.stop_id
                LIMIT 1
                """.trimIndent(),
                null,
            ).use { it.moveToFirst() }
            if (!hasConnectedTrip) error("GTFS schedule graph bağlantısız: geçerli iki duraklı sefer yok")
            putMetadata(db, READY_KEY, "1")
            putMetadata(db, FETCHED_KEY, (System.currentTimeMillis() / 1000).toString())
            putMetadata(db, COUNT_KEY, stops.toString())
            putMetadata(db, VERSION_KEY, sourceVersion)
            putMetadata(db, SOURCE_URL_KEY, url)
            putMetadata(db, HTTP_ETAG_KEY, responseEtag.orEmpty())
            putMetadata(db, HTTP_LAST_MODIFIED_KEY, responseLastModified.orEmpty())
            putMetadata(db, SCHEMA_KEY, "4")
            counts.forEach { (file, count) -> putMetadata(db, "count_$file", count.toString()) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            http?.disconnect()
        }
        onCount(counts["stops.txt"] ?: 0)
    }

    fun search(query: String, limit: Int): List<CatalogStop> {
        // SQLite interprets a negative LIMIT as unlimited. Never allow a caller to
        // materialize the full nationwide stop index through an unbounded search.
        if (limit <= 0) return emptyList()
        val boundedLimit = limit.coerceAtMost(100)
        val tokens = query.lowercase(Locale.GERMANY)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val match = tokens.joinToString(" ") { "$it*" }
        val sql = "SELECT s.id,s.name,s.lat,s.lon FROM stop_search f JOIN stops s ON s.id=f.stop_id WHERE stop_search MATCH ? LIMIT ?"
        return readableDatabase.rawQuery(sql, arrayOf(match, boundedLimit.toString())).use { c ->
            buildList {
                while (c.moveToNext()) add(CatalogStop(c.getString(0), PROVIDER_ID, c.getString(1), c.getDouble(2), c.getDouble(3)))
            }
        }
    }

    fun searchRouteAndTripLocations(
        query: String,
        limit: Int,
    ): List<TransitLocationResult> {
        val (lineLimit, tripLimit) = boundedTransitResultSplit(limit)
        val boundedLimit = lineLimit + tripLimit
        val q = query.trim()
        if (q.length < 2 || boundedLimit == 0) return emptyList()
        val prefix = escapeTransitLikePrefix(q)

        val lines = readableDatabase.rawQuery(
            """
            SELECT route_id, COALESCE(short_name, ''), COALESCE(long_name, '')
            FROM routes
            WHERE short_name LIKE ? ESCAPE '!' COLLATE NOCASE
               OR long_name LIKE ? ESCAPE '!' COLLATE NOCASE
            ORDER BY
                CASE WHEN short_name LIKE ? ESCAPE '!' COLLATE NOCASE THEN 0 ELSE 1 END,
                short_name COLLATE NOCASE,
                long_name COLLATE NOCASE,
                route_id
            LIMIT ?
            """.trimIndent(),
            arrayOf(prefix, prefix, prefix, lineLimit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val routeId = cursor.getString(0)
                    val shortName = cursor.getString(1).trim()
                    val longName = cursor.getString(2).trim()
                    val completion = shortName.ifBlank { longName }.ifBlank { routeId }
                    val label = if (
                        shortName.isNotBlank() &&
                        longName.isNotBlank() &&
                        !shortName.equals(longName, ignoreCase = true)
                    ) {
                        "$shortName • $longName"
                    } else {
                        completion
                    }
                    add(
                        TransitLocationResult(
                            key = "line:$routeId",
                            kind = TransitLocationKind.LINE,
                            label = label,
                            latitude = 0.0,
                            longitude = 0.0,
                            routeId = routeId,
                            autocompleteText = completion,
                        )
                    )
                }
            }
        }

        if (tripLimit <= 0) return lines.take(boundedLimit)

        val trips = readableDatabase.rawQuery(
            """
            SELECT t.trip_id, COALESCE(r.short_name, ''), COALESCE(t.headsign, '')
            FROM trips t
            JOIN routes r ON r.route_id = t.route_id
            WHERE t.headsign LIKE ? ESCAPE '!' COLLATE NOCASE
               OR t.trip_id LIKE ? ESCAPE '!' COLLATE NOCASE
            ORDER BY
                t.headsign COLLATE NOCASE,
                r.short_name COLLATE NOCASE,
                t.trip_id
            LIMIT ?
            """.trimIndent(),
            arrayOf(prefix, prefix, tripLimit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val tripId = cursor.getString(0)
                    val shortName = cursor.getString(1).trim()
                    val headsign = cursor.getString(2).trim()
                    val completion = headsign.ifBlank { shortName }.ifBlank { tripId }
                    val label = when {
                        shortName.isNotBlank() && headsign.isNotBlank() -> "$shortName → $headsign"
                        headsign.isNotBlank() -> headsign
                        shortName.isNotBlank() -> shortName
                        else -> tripId
                    }
                    add(
                        TransitLocationResult(
                            key = "trip:$tripId",
                            kind = TransitLocationKind.TRIP,
                            label = label,
                            latitude = 0.0,
                            longitude = 0.0,
                            tripId = tripId,
                            autocompleteText = completion,
                        )
                    )
                }
            }
        }

        return (lines + trips).take(boundedLimit)
    }

    fun nearest(latitude: Double, longitude: Double, limit: Int): List<NearbyStop> =
        nearestInternal(latitude, longitude, limit, canonicalizeStations = false)

    fun nearestCanonicalStations(latitude: Double, longitude: Double, limit: Int): List<NearbyStop> =
        nearestInternal(latitude, longitude, limit, canonicalizeStations = true)

    private fun nearestInternal(
        latitude: Double,
        longitude: Double,
        limit: Int,
        canonicalizeStations: Boolean,
    ): List<NearbyStop> {
        if (limit <= 0) return emptyList()
        // The direct nearby-index APIs are also called outside the route planner.
        // Never send NaN, infinities or impossible coordinates into SQLite, where
        // implicit coercion may return unrelated stations or invalid distances.
        if (!validTransitJourneyCoordinates(latitude, longitude)) return emptyList()
        val radii = listOf(5_000.0, 20_000.0, 80_000.0)
        for (radius in radii) {
            val latDelta = radius / 111_320.0
            val longitudeScale = cos(Math.toRadians(latitude)).absoluteValue.coerceAtLeast(0.2)
            val lonDelta = radius / (111_320.0 * longitudeScale)
            val longitudeWeight = longitudeScale * longitudeScale
            // Applying LIMIT before a spatial ranking loses the nearest stations in dense
            // areas: the lat/lon index alone can return 6000 farther southern stops first.
            // Rank in SQL before capping; the final ordering still uses exact haversine metres.
            val sql = """
                SELECT id,name,lat,lon,parent_station
                FROM stops
                WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?
                ORDER BY ((lat-?)*(lat-?) + (lon-?)*(lon-?)*?), id
                LIMIT 6000
            """.trimIndent()
            val candidates = readableDatabase.rawQuery(
                sql,
                arrayOf(
                    (latitude - latDelta).toString(),
                    (latitude + latDelta).toString(),
                    (longitude - lonDelta).toString(),
                    (longitude + lonDelta).toString(),
                    latitude.toString(), latitude.toString(),
                    longitude.toString(), longitude.toString(),
                    longitudeWeight.toString(),
                ),
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val stop = CatalogStop(c.getString(0), PROVIDER_ID, c.getString(1), c.getDouble(2), c.getDouble(3), if (c.isNull(4)) null else c.getString(4))
                        val distance = haversineMeters(latitude, longitude, stop.latitude, stop.longitude).roundToInt()
                        if (distance <= radius) add(NearbyStop(stop, distance))
                    }
                }
            }
            val resolved = if (canonicalizeStations) {
                NearbyStationCanonicalizer.canonicalize(latitude, longitude, candidates, limit)
            } else {
                candidates.sortedWith(compareBy<NearbyStop>({ it.distanceMeters }, { it.stop.id })).take(limit)
            }
            if (resolved.size >= limit || (resolved.isNotEmpty() && radius == radii.last())) return resolved
        }
        return emptyList()
    }

    private fun createTransitSchema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE stops(id TEXT PRIMARY KEY, name TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, parent_station TEXT, location_type INTEGER, platform_code TEXT, wheelchair_boarding INTEGER, stop_code TEXT, zone_id TEXT, stop_timezone TEXT, level_id TEXT)")
        db.execSQL("CREATE INDEX idx_stops_lat_lon ON stops(lat, lon)")
        db.execSQL("CREATE INDEX idx_stops_parent ON stops(parent_station)")
        db.execSQL("CREATE VIRTUAL TABLE stop_search USING fts4(stop_id, name, tokenize=unicode61)")
        createScheduleTables(db)
    }

    private fun createScheduleTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS agency(agency_id TEXT PRIMARY KEY, name TEXT NOT NULL, url TEXT, timezone TEXT, lang TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS routes(route_id TEXT PRIMARY KEY, agency_id TEXT, short_name TEXT, long_name TEXT, route_type INTEGER, color TEXT, text_color TEXT, sort_order INTEGER)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_routes_agency ON routes(agency_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS trips(trip_id TEXT PRIMARY KEY, route_id TEXT NOT NULL, service_id TEXT NOT NULL, headsign TEXT, direction_id INTEGER, shape_id TEXT, wheelchair_accessible INTEGER, bikes_allowed INTEGER, block_id TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_trips_route_service ON trips(route_id, service_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_trips_block_service ON trips(block_id, service_id, trip_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS stop_times(trip_id TEXT NOT NULL, stop_sequence INTEGER NOT NULL, stop_id TEXT NOT NULL, arrival_time TEXT, departure_time TEXT, pickup_type INTEGER, drop_off_type INTEGER, shape_dist_traveled REAL, timepoint INTEGER, PRIMARY KEY(trip_id, stop_sequence))")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_stop_times_stop ON stop_times(stop_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS calendar(service_id TEXT PRIMARY KEY, monday INTEGER, tuesday INTEGER, wednesday INTEGER, thursday INTEGER, friday INTEGER, saturday INTEGER, sunday INTEGER, start_date TEXT, end_date TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS calendar_dates(service_id TEXT NOT NULL, date TEXT NOT NULL, exception_type INTEGER NOT NULL, PRIMARY KEY(service_id, date))")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_calendar_dates_date ON calendar_dates(date)")
        db.execSQL("CREATE TABLE IF NOT EXISTS transfers(from_stop_id TEXT, to_stop_id TEXT, transfer_type INTEGER, min_transfer_time INTEGER, from_route_id TEXT, to_route_id TEXT, from_trip_id TEXT, to_trip_id TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transfers_from_stop ON transfers(from_stop_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transfers_to_stop ON transfers(to_stop_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transfers_from_trip_type ON transfers(from_trip_id, transfer_type, to_trip_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS shapes(shape_id TEXT NOT NULL, sequence INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, dist_traveled REAL, PRIMARY KEY(shape_id, sequence))")
        db.execSQL("CREATE TABLE IF NOT EXISTS levels(level_id TEXT PRIMARY KEY, level_index REAL, level_name TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS pathways(pathway_id TEXT PRIMARY KEY, from_stop_id TEXT NOT NULL, to_stop_id TEXT NOT NULL, pathway_mode INTEGER, is_bidirectional INTEGER, length REAL, traversal_time INTEGER, stair_count INTEGER, max_slope REAL, min_width REAL, signposted_as TEXT, reversed_signposted_as TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_pathways_from_stop ON pathways(from_stop_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_pathways_to_stop ON pathways(to_stop_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS frequencies(trip_id TEXT NOT NULL, start_time TEXT NOT NULL, end_time TEXT NOT NULL, headway_secs INTEGER NOT NULL, exact_times INTEGER)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_frequencies_trip ON frequencies(trip_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS attributions(attribution_id TEXT, agency_id TEXT, route_id TEXT, trip_id TEXT, organization_name TEXT, is_producer INTEGER, is_operator INTEGER, is_authority INTEGER, url TEXT, email TEXT, phone TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS feed_info(publisher_name TEXT, publisher_url TEXT, lang TEXT, start_date TEXT, end_date TEXT, version TEXT)")
    }

    private fun clearTransitData(db: SQLiteDatabase) {
        listOf("stop_search", "stops", "agency", "routes", "trips", "stop_times", "calendar", "calendar_dates", "transfers", "shapes", "levels", "pathways", "frequencies", "attributions", "feed_info")
            .forEach { db.delete(it, null, null) }
    }

    private fun importStopsEntry(db: SQLiteDatabase, reader: BufferedReader, onCount: (Int) -> Unit): Int {
        val rows = rows(reader)
        val h = rows.first
        val stmt = db.compileStatement("INSERT OR REPLACE INTO stops(id,name,lat,lon,parent_station,location_type,platform_code,wheelchair_boarding,stop_code,zone_id,stop_timezone,level_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")
        val search = db.compileStatement("INSERT INTO stop_search(stop_id,name) VALUES(?,?)")
        var count = 0
        rows.second.forEach { r ->
            val id = r.value(h, "stop_id")
            val name = r.value(h, "stop_name")
            val lat = r.value(h, "stop_lat").toDoubleOrNull()
            val lon = r.value(h, "stop_lon").toDoubleOrNull()
            if (id.isBlank() || name.isBlank() || lat == null || lon == null ||
                !lat.isFinite() || lat !in -90.0..90.0 ||
                !lon.isFinite() || lon !in -180.0..180.0
            ) return@forEach
            stmt.bindValues(
                id, name, lat, lon,
                r.nullValue(h, "parent_station"),
                r.intValue(h, "location_type"),
                r.nullValue(h, "platform_code"),
                r.intValue(h, "wheelchair_boarding"),
                r.nullValue(h, "stop_code"),
                r.nullValue(h, "zone_id"),
                r.nullValue(h, "stop_timezone"),
                r.nullValue(h, "level_id"),
            )
            stmt.executeInsert()
            search.bindValues(id, name)
            search.executeInsert()
            count++
            if (count % 50_000 == 0) onCount(count)
        }
        return count
    }

    private fun importAgency(db: SQLiteDatabase, reader: BufferedReader) = importRows(reader) { h, r ->
        val id = r.value(h, "agency_id").ifBlank { r.value(h, "agency_name") }
        if (id.isBlank()) false else {
            db.execSQL("INSERT OR REPLACE INTO agency(agency_id,name,url,timezone,lang) VALUES(?,?,?,?,?)", arrayOf(id, r.value(h, "agency_name"), r.nullValue(h, "agency_url"), r.nullValue(h, "agency_timezone"), r.nullValue(h, "agency_lang")))
            true
        }
    }

    private fun importRoutes(db: SQLiteDatabase, reader: BufferedReader) = importRows(reader) { h, r ->
        val id = r.value(h, "route_id")
        if (id.isBlank()) false else {
            db.execSQL("INSERT OR REPLACE INTO routes(route_id,agency_id,short_name,long_name,route_type,color,text_color,sort_order) VALUES(?,?,?,?,?,?,?,?)", arrayOf(id, r.nullValue(h, "agency_id"), r.nullValue(h, "route_short_name"), r.nullValue(h, "route_long_name"), r.intValue(h, "route_type"), r.nullValue(h, "route_color"), r.nullValue(h, "route_text_color"), r.intValue(h, "route_sort_order")))
            true
        }
    }

    private fun importTrips(db: SQLiteDatabase, reader: BufferedReader): Int {
        val (h, tripRows) = rows(reader)
        val stmt = db.compileStatement(
            "INSERT OR REPLACE INTO trips(trip_id,route_id,service_id,headsign,direction_id,shape_id,wheelchair_accessible,bikes_allowed,block_id) VALUES(?,?,?,?,?,?,?,?,?)"
        )
        var count = 0
        tripRows.forEach { r ->
            val id = r.value(h, "trip_id")
            val route = r.value(h, "route_id")
            val service = r.value(h, "service_id")
            if (id.isBlank() || route.isBlank() || service.isBlank()) return@forEach
            stmt.bindValues(
                id,
                route,
                service,
                r.nullValue(h, "trip_headsign"),
                r.intValue(h, "direction_id"),
                r.nullValue(h, "shape_id"),
                r.intValue(h, "wheelchair_accessible"),
                r.intValue(h, "bikes_allowed"),
                r.nullValue(h, "block_id"),
            )
            stmt.executeInsert()
            count++
        }
        return count
    }

    private fun importStopTimes(db: SQLiteDatabase, reader: BufferedReader): Int {
        val (h, sequenceRows) = rows(reader)
        val stmt = db.compileStatement("INSERT OR REPLACE INTO stop_times(trip_id,stop_sequence,stop_id,arrival_time,departure_time,pickup_type,drop_off_type,shape_dist_traveled,timepoint) VALUES(?,?,?,?,?,?,?,?,?)")
        var count = 0
        sequenceRows.forEach { r ->
            val trip = r.value(h, "trip_id")
            val stop = r.value(h, "stop_id")
            val sequence = r.intValue(h, "stop_sequence")
            if (trip.isBlank() || stop.isBlank() || sequence == null) return@forEach
            stmt.bindValues(
                trip, sequence, stop,
                r.nullValue(h, "arrival_time"),
                r.nullValue(h, "departure_time"),
                r.intValue(h, "pickup_type"),
                r.intValue(h, "drop_off_type"),
                r.value(h, "shape_dist_traveled").toDoubleOrNull(),
                r.intValue(h, "timepoint"),
            )
            stmt.executeInsert()
            count++
        }
        return count
    }

    private fun importCalendar(db: SQLiteDatabase, reader: BufferedReader) = importRows(reader) { h, r ->
        val id = r.value(h, "service_id")
        if (id.isBlank()) false else {
            db.execSQL("INSERT OR REPLACE INTO calendar VALUES(?,?,?,?,?,?,?,?,?,?)", arrayOf(id, r.intValue(h, "monday"), r.intValue(h, "tuesday"), r.intValue(h, "wednesday"), r.intValue(h, "thursday"), r.intValue(h, "friday"), r.intValue(h, "saturday"), r.intValue(h, "sunday"), r.nullValue(h, "start_date"), r.nullValue(h, "end_date")))
            true
        }
    }

    private fun importCalendarDates(db: SQLiteDatabase, reader: BufferedReader) = importRows(reader) { h, r ->
        val id = r.value(h, "service_id")
        val date = r.value(h, "date")
        val type = r.intValue(h, "exception_type")
        if (id.isBlank() || date.isBlank() || type == null) false else {
            db.execSQL("INSERT OR REPLACE INTO calendar_dates VALUES(?,?,?)", arrayOf(id, date, type))
            true
        }
    }

    private fun importTransfers(db: SQLiteDatabase, reader: BufferedReader) = importRows(reader) { h, r ->
        val from = r.nullValue(h, "from_stop_id")
        val to = r.nullValue(h, "to_stop_id")
        val transferType = r.intValue(h, "transfer_type")
        val effectiveType = transferType ?: 0
        val fromTrip = r.nullValue(h, "from_trip_id")
        val toTrip = r.nullValue(h, "to_trip_id")
        val valid = when (effectiveType) {
            in 0..3 -> from != null && to != null
            4, 5 -> fromTrip != null && toTrip != null
            else -> false
        }
        if (!valid) false else {
            db.execSQL(
                "INSERT INTO transfers(from_stop_id,to_stop_id,transfer_type,min_transfer_time,from_route_id,to_route_id,from_trip_id,to_trip_id) VALUES(?,?,?,?,?,?,?,?)",
                arrayOf(
                    from, to,
                    transferType,
                    r.intValue(h, "min_transfer_time"),
                    r.nullValue(h, "from_route_id"),
                    r.nullValue(h, "to_route_id"),
                    fromTrip,
                    toTrip,
                ),
            )
            true
        }
    }

    private fun discardInvalidLinkedTransferStopReferences(db: SQLiteDatabase) {
        db.execSQL(
            """
            DELETE FROM transfers
            WHERE transfer_type IN (4,5)
              AND (
                (
                  from_stop_id IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM stops s
                    WHERE s.id=transfers.from_stop_id
                      AND COALESCE(s.location_type,0)=0
                  )
                )
                OR
                (
                  to_stop_id IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM stops s
                    WHERE s.id=transfers.to_stop_id
                      AND COALESCE(s.location_type,0)=0
                  )
                )
              )
            """.trimIndent()
        )
    }

    private fun importShapes(db: SQLiteDatabase, reader: BufferedReader): Int {
        val (h, shapeRows) = rows(reader)
        val stmt = db.compileStatement("INSERT OR REPLACE INTO shapes(shape_id,sequence,lat,lon,dist_traveled) VALUES(?,?,?,?,?)")
        var count = 0
        shapeRows.forEach { r ->
            val id = r.value(h, "shape_id")
            val seq = r.intValue(h, "shape_pt_sequence")
            val lat = r.value(h, "shape_pt_lat").toDoubleOrNull()
            val lon = r.value(h, "shape_pt_lon").toDoubleOrNull()
            if (id.isBlank() || seq == null || lat == null || lon == null) return@forEach
            stmt.bindValues(id, seq, lat, lon, r.value(h, "shape_dist_traveled").toDoubleOrNull())
            stmt.executeInsert()
            count++
        }
        return count
    }

    private fun importLevels(db: SQLiteDatabase, reader: BufferedReader) = importRows(reader) { h, r ->
        val id = r.value(h, "level_id")
        if (id.isBlank()) false else {
            db.execSQL(
                "INSERT OR REPLACE INTO levels(level_id,level_index,level_name) VALUES(?,?,?)",
                arrayOf(id, r.value(h, "level_index").toDoubleOrNull(), r.nullValue(h, "level_name")),
            )
            true
        }
    }

    private fun importPathways(db: SQLiteDatabase, reader: BufferedReader) = importRows(reader) { h, r ->
        val id = r.value(h, "pathway_id")
        val from = r.value(h, "from_stop_id")
        val to = r.value(h, "to_stop_id")
        if (id.isBlank() || from.isBlank() || to.isBlank()) false else {
            db.execSQL(
                "INSERT OR REPLACE INTO pathways(pathway_id,from_stop_id,to_stop_id,pathway_mode,is_bidirectional,length,traversal_time,stair_count,max_slope,min_width,signposted_as,reversed_signposted_as) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf(
                    id, from, to,
                    r.intValue(h, "pathway_mode"),
                    r.intValue(h, "is_bidirectional"),
                    r.value(h, "length").toDoubleOrNull(),
                    r.intValue(h, "traversal_time"),
                    r.intValue(h, "stair_count"),
                    r.value(h, "max_slope").toDoubleOrNull(),
                    r.value(h, "min_width").toDoubleOrNull(),
                    r.nullValue(h, "signposted_as"),
                    r.nullValue(h, "reversed_signposted_as"),
                ),
            )
            true
        }
    }

    private fun importFrequencies(db: SQLiteDatabase, reader: BufferedReader) = importRows(reader) { h, r ->
        val trip = r.value(h, "trip_id")
        val start = r.value(h, "start_time")
        val end = r.value(h, "end_time")
        val headway = r.intValue(h, "headway_secs")
        if (trip.isBlank() || start.isBlank() || end.isBlank() || headway == null) false else {
            db.execSQL(
                "INSERT INTO frequencies(trip_id,start_time,end_time,headway_secs,exact_times) VALUES(?,?,?,?,?)",
                arrayOf(trip, start, end, headway, r.intValue(h, "exact_times")),
            )
            true
        }
    }

    private fun importAttributions(db: SQLiteDatabase, reader: BufferedReader) = importRows(reader) { h, r ->
        val org = r.value(h, "organization_name")
        if (org.isBlank()) false else {
            db.execSQL(
                "INSERT INTO attributions(attribution_id,agency_id,route_id,trip_id,organization_name,is_producer,is_operator,is_authority,url,email,phone) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf(
                    r.nullValue(h, "attribution_id"),
                    r.nullValue(h, "agency_id"),
                    r.nullValue(h, "route_id"),
                    r.nullValue(h, "trip_id"),
                    org,
                    r.intValue(h, "is_producer"),
                    r.intValue(h, "is_operator"),
                    r.intValue(h, "is_authority"),
                    r.nullValue(h, "attribution_url"),
                    r.nullValue(h, "attribution_email"),
                    r.nullValue(h, "attribution_phone"),
                ),
            )
            true
        }
    }

    private fun importFeedInfo(db: SQLiteDatabase, reader: BufferedReader) = importRows(reader) { h, r ->
        db.execSQL("INSERT INTO feed_info VALUES(?,?,?,?,?,?)", arrayOf(r.nullValue(h, "feed_publisher_name"), r.nullValue(h, "feed_publisher_url"), r.nullValue(h, "feed_lang"), r.nullValue(h, "feed_start_date"), r.nullValue(h, "feed_end_date"), r.nullValue(h, "feed_version")))
        true
    }

    private fun migrateScheduleSchemaV4(db: SQLiteDatabase) {
        addColumnIfMissing(db, "trips", "block_id", "TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_trips_block_service ON trips(block_id, service_id, trip_id)")

        // SQLite cannot relax NOT NULL in-place. SQLiteOpenHelper runs onUpgrade inside its
        // upgrade transaction, so copy/swap preserves the entire v3 graph or rolls back together.
        db.execSQL(
            "CREATE TABLE transfers_v4(from_stop_id TEXT, to_stop_id TEXT, transfer_type INTEGER, min_transfer_time INTEGER, from_route_id TEXT, to_route_id TEXT, from_trip_id TEXT, to_trip_id TEXT)"
        )
        db.execSQL(
            """
            INSERT INTO transfers_v4(from_stop_id,to_stop_id,transfer_type,min_transfer_time,from_route_id,to_route_id,from_trip_id,to_trip_id)
            SELECT from_stop_id,to_stop_id,transfer_type,min_transfer_time,from_route_id,to_route_id,from_trip_id,to_trip_id
            FROM transfers
            """.trimIndent()
        )
        db.execSQL("DROP TABLE transfers")
        db.execSQL("ALTER TABLE transfers_v4 RENAME TO transfers")
        db.execSQL("CREATE INDEX idx_transfers_from_stop ON transfers(from_stop_id)")
        db.execSQL("CREATE INDEX idx_transfers_to_stop ON transfers(to_stop_id)")
        db.execSQL("CREATE INDEX idx_transfers_from_trip_type ON transfers(from_trip_id, transfer_type, to_trip_id)")
    }

    private fun rows(reader: BufferedReader): Pair<List<String>, Sequence<List<String>>> {
        val records = gtfsCsvRecords(reader).iterator()
        val header = parseGtfsCsvHeader(if (records.hasNext()) records.next() else error("GTFS dosyası boş"))
        return header to generateSequence {
            if (records.hasNext()) parseCsvLine(records.next()) else null
        }
    }

    private inline fun importRows(reader: BufferedReader, crossinline consume: (List<String>, List<String>) -> Boolean): Int {
        val (header, sequence) = rows(reader)
        var count = 0
        sequence.forEach { if (consume(header, it)) count++ }
        return count
    }

    private fun List<String>.value(header: List<String>, name: String): String {
        val i = header.indexOf(name)
        return if (i >= 0 && i < size) this[i].trim() else ""
    }

    private fun List<String>.nullValue(header: List<String>, name: String): String? = value(header, name).ifBlank { null }
    private fun List<String>.intValue(header: List<String>, name: String): Int? = value(header, name).toIntOrNull()

    private fun android.database.sqlite.SQLiteStatement.bindValues(vararg values: Any?) {
        clearBindings()
        values.forEachIndexed { index, value ->
            val i = index + 1
            when (value) {
                null -> bindNull(i)
                is String -> bindString(i, value)
                is Double -> bindDouble(i, value)
                is Float -> bindDouble(i, value.toDouble())
                is Number -> bindLong(i, value.toLong())
                else -> bindString(i, value.toString())
            }
        }
    }

    private fun addColumnIfMissing(db: SQLiteDatabase, table: String, column: String, type: String) {
        val exists = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val nameIndex = c.getColumnIndex("name")
            var found = false
            while (c.moveToNext()) if (c.getString(nameIndex) == column) found = true
            found
        }
        if (!exists) db.execSQL("ALTER TABLE $table ADD COLUMN $column $type")
    }

    private fun metadata(key: String): String? = readableDatabase.rawQuery("SELECT value FROM metadata WHERE key=?", arrayOf(key)).use {
        if (it.moveToFirst()) it.getString(0) else null
    }

    private fun putMetadata(db: SQLiteDatabase, key: String, value: String) {
        db.execSQL("INSERT OR REPLACE INTO metadata(key,value) VALUES(?,?)", arrayOf(key, value))
    }
}

// Do not mix rows from duplicate GTFS tables (including nested/case aliases).
// A thrown exception leaves the existing SQLite import transaction uncommitted.
private val GTFS_IMPORT_TABLES = setOf(
    "stops.txt", "agency.txt", "routes.txt", "trips.txt", "stop_times.txt",
    "calendar.txt", "calendar_dates.txt", "transfers.txt", "shapes.txt",
    "levels.txt", "pathways.txt", "frequencies.txt", "attributions.txt", "feed_info.txt",
)

internal fun rejectDuplicateGtfsZipMember(entryName: String, seen: MutableSet<String>) {
    val table = entryName.substringAfterLast('/').lowercase(Locale.ROOT)
    if (table !in GTFS_IMPORT_TABLES) return
    check(seen.add(table)) { "Duplicate GTFS ZIP table: $table" }
}


// Reject corrupted feed text rather than persisting U+FFFD substitutions.
// Decoder errors abort the existing SQLite transaction, preserving last-known-good data.
internal fun strictGtfsUtf8Reader(input: InputStream): BufferedReader =
    BufferedReader(
        InputStreamReader(
            input,
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT),
        ),
        128 * 1024,
    )


// GTFS tables may begin with a UTF-8 BOM; it is a file marker, not part of column one.
internal fun parseGtfsCsvHeader(line: String): List<String> =
    parseCsvLine(line.removePrefix("\uFEFF"))


// Keep both SQLite LIMIT clauses positive and bounded even for Int.MAX_VALUE requests.
internal fun boundedTransitResultSplit(requestedLimit: Int): Pair<Int, Int> {
    val boundedLimit = requestedLimit.coerceIn(0, 100)
    val lineLimit = (boundedLimit + 1) / 2
    return lineLimit to (boundedLimit - lineLimit)
}

    
// Treat user-entered percent, underscore and escape characters as literal
// GTFS names. Only the final % is a deliberate autocomplete wildcard.
internal fun escapeTransitLikePrefix(text: String): String = buildString {
    for (character in text) {
        if (character == '!' || character == '%' || character == '_') append('!')
        append(character)
    }
    append('%')
}

/** Conditional validators are valid only for the ready snapshot from this source. */
internal fun gtfsConditionalHeaders(
    ready: Boolean,
    sameSource: Boolean,
    etag: String?,
    lastModified: String?,
): Map<String, String> {
    if (!ready || !sameSource) return emptyMap()
    return buildMap {
        // Persisted provider validators are untrusted network data. Never echo
        // C0/DEL control characters into a subsequent HTTP request header.
        fun safeValidator(value: String?): String? = value?.takeIf { candidate ->
            // An oversized persisted provider header can make every subsequent
            // conditional refresh fail (HTTP 431) while the cached feed stays stale.
            // Bound untrusted validators before replaying them over the network.
            candidate.isNotBlank() && candidate.length <= 1024 &&
                candidate.none { char -> char.code < 0x20 || char.code == 0x7f }
        }
        safeValidator(etag)?.let { put("If-None-Match", it) }
        safeValidator(lastModified)?.let { put("If-Modified-Since", it) }
    }
}
