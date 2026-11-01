package com.nazofobi.arrivalalarm

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream

/** Loads the nationwide DELFI GTFS feed instead of relying on demo fixtures. */
object GtfsDeutschlandLoader {
    const val FEED_URL = "https://download.gtfs.de/germany/free/latest.zip"
    private val requiredCoreFiles = setOf("stops.txt", "routes.txt", "trips.txt", "stop_times.txt")

    fun download(url: String = FEED_URL): CatalogSnapshot {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "ArrivalAlarmAndroid/0.1")
        return try {
            connection.inputStream.use { stream ->
                val bytes = stream.readBytes()
                val revision = connection.getHeaderField("ETag")
                    ?: connection.getHeaderField("Last-Modified")
                    ?: "bytes-${bytes.size}"
                parse(bytes, revision, nowUtcIso8601())
            }
        } finally {
            connection.disconnect()
        }
    }

    fun parse(zipBytes: ByteArray, sourceVersion: String, fetchedAt: String): CatalogSnapshot {
        val files = mutableMapOf<String, List<Map<String, String>>>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.substringAfterLast('/')
                if (name in requiredCoreFiles) {
                    require(name !in files) { "Duplicate GTFS core member: $name" }
                    val entryBytes = ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        entryBytes.write(buffer, 0, count)
                    }
                    // Reject malformed UTF-8 in every required table instead of replacing invalid bytes.
                    files[name] = strictGtfsUtf8Reader(ByteArrayInputStream(entryBytes.toByteArray())).use { parseCsv(it, name) }
                }
                zip.closeEntry()
            }
        }
        require(files.keys.containsAll(requiredCoreFiles)) {
            "Missing GTFS core members: ${(requiredCoreFiles - files.keys).sorted().joinToString(", ")}"
        }
        // A header-only core table is not a valid refreshed transit catalog.
        // Fail the candidate snapshot instead of replacing an offline known-good feed.
        for (table in requiredCoreFiles) {
            require(files.getValue(table).isNotEmpty()) {
                "Empty GTFS core table: $table"
            }
        }
        // GTFS identity keys must be unique. A duplicate would silently alias stops,
        // overwrite route metadata or publish two trips under the same identity.
        // Reject the candidate snapshot so callers can retain the old-known-good feed.
        for ((table, key) in listOf(
            "stops.txt" to "stop_id",
            "routes.txt" to "route_id",
            "trips.txt" to "trip_id",
        )) {
            val seen = HashSet<String>()
            files.getValue(table).forEach { row ->
                val id = row[key]
                require(!id.isNullOrBlank()) { "Missing $key in $table" }
                require(seen.add(id)) { "Duplicate $key in $table: $id" }
            }
        }
        val provider = TransitProvider("delfi","DELFI / GTFS Deutschland","Germany",FEED_URL,"Creative Commons 4.0",sourceVersion,fetchedAt,true,"https://realtime.gtfs.de/realtime-free.pb")
        val stops = files["stops.txt"].orEmpty().mapNotNull { row ->
            val id=row["stop_id"]?:return@mapNotNull null; val name=row["stop_name"]?:return@mapNotNull null
            val lat=row["stop_lat"]?.toDoubleOrNull()?:return@mapNotNull null; val lon=row["stop_lon"]?.toDoubleOrNull()?:return@mapNotNull null
            // A syntactically numeric value (NaN/Infinity) is not usable GPS evidence.
            if (!lat.isFinite() || lat !in -90.0..90.0 ||
                !lon.isFinite() || lon !in -180.0..180.0) return@mapNotNull null
            CatalogStop(id,provider.id,name,lat,lon)
        }
        val validStopIds = stops.mapTo(hashSetOf()) { it.id }
        val routesById=files["routes.txt"].orEmpty().associateBy{it["route_id"].orEmpty()}; val trips=files["trips.txt"].orEmpty()
        val stopTimesByTrip=files["stop_times.txt"].orEmpty().groupBy{it["trip_id"].orEmpty()}; val catalogRoutes=mutableListOf<CatalogRoute>(); val catalogTrips=mutableListOf<CatalogTrip>()
        trips.forEach { trip ->
            val tripId=trip["trip_id"]?:return@forEach; val routeId=trip["route_id"]?:return@forEach; val routeRow=routesById[routeId]?:return@forEach
            // A malformed or repeated stop_sequence cannot define a reliable trip order.
            // GTFS sequences need not be consecutive (or start at one).
            val numberedStops = stopTimesByTrip[tripId].orEmpty().map { row ->
                row["stop_sequence"]?.toIntOrNull() to row
            }
            if (numberedStops.size < 2 ||
                numberedStops.any { (sequence, _) -> sequence == null || sequence < 0 } ||
                numberedStops.map { it.first }.toSet().size != numberedStops.size
            ) return@forEach
            val ordered = numberedStops.sortedBy { it.first }.map { it.second }
            val stopIds=ordered.mapNotNull{it["stop_id"]}
            // Never publish a journey with an invalid or absent stop reference.
            if (stopIds.size != ordered.size || stopIds.any { it.isBlank() || it !in validStopIds }) return@forEach
            val direction=trip["trip_headsign"].orEmpty().ifBlank{routeRow["route_long_name"].orEmpty()}; val shortName=routeRow["route_short_name"].orEmpty().ifBlank{routeRow["route_long_name"].orEmpty()}
            val uniqueRouteId="$routeId:$tripId"; catalogRoutes+=CatalogRoute(uniqueRouteId,provider.id,shortName,direction,stopIds); catalogTrips+=CatalogTrip(tripId,provider.id,uniqueRouteId,ordered.first()["departure_time"].orEmpty())
        }
        // A nonempty set of CSV rows is insufficient: broken stop references or
        // unusable coordinates can still yield zero usable journeys. Keep the
        // previously installed offline catalog instead of publishing that state.
        require(stops.isNotEmpty() && catalogTrips.isNotEmpty()) {
            "No usable stops or journeys in GTFS core tables"
        }
        return CatalogSnapshot(listOf(provider),stops,catalogRoutes,catalogTrips,fetchedAt,stale=false)
    }

    private fun nowUtcIso8601(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    // Parse logical CSV records: a quoted GTFS stop_name can contain newlines.
    // The shared bounded reader also rejects malformed quoting before ingestion.
    private fun parseCsv(reader: BufferedReader, table: String): List<Map<String, String>> {
        val records = gtfsCsvRecords(reader).iterator()
        require(records.hasNext()) { "Missing GTFS CSV header in $table" }
        val header = csvLine(records.next()).map { it.removePrefix("\uFEFF") }
        // Each core table must expose the identity/ordering columns consumed below.
        // Missing or repeated headers must not turn a corrupt import into a fresh, empty catalog.
        val required = when (table) {
            "stops.txt" -> setOf("stop_id", "stop_name", "stop_lat", "stop_lon")
            "routes.txt" -> setOf("route_id")
            "trips.txt" -> setOf("route_id", "trip_id")
            "stop_times.txt" -> setOf("trip_id", "stop_id", "stop_sequence")
            else -> emptySet()
        }
        require(header.containsAll(required)) {
            "Missing GTFS columns in $table: ${(required - header.toSet()).sorted().joinToString(", ")}"
        }
        require(header.none { it.isBlank() }) { "Empty GTFS CSV column name in $table" }
        require(header.size == header.toSet().size) { "Duplicate GTFS CSV header in $table" }
        return buildList {
            while (records.hasNext()) {
                val record = records.next()
                if (record.isBlank()) continue
                val values = csvLine(record)
                // Extra cells can shift GTFS identities or coordinates; never drop them silently.
                require(values.size <= header.size) { "Extra GTFS CSV columns in $table" }
                add(header.mapIndexed { index, key ->
                    key to values.getOrElse(index) { "" }
                }.toMap())
            }
        }
    }

    // Do not reinterpret stray quotes in unquoted GTFS identities or stop names.
    // A quoted field may contain doubled quotes and embedded line breaks, but
    // the closing quote must be followed by a delimiter or the end of a record.
    private fun csvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var state = 0 // 0=start, 1=unquoted, 2=quoted, 3=closed, 4=closed whitespace
        var index = 0
        fun emit() {
            fields += field.toString()
            field.clear()
            state = 0
        }
        while (index < line.length) {
            val c = line[index]
            when (state) {
                0 -> when (c) {
                    '"' -> state = 2
                    ',' -> emit()
                    else -> { field.append(c); state = 1 }
                }
                1 -> when (c) {
                    '"' -> throw IllegalArgumentException("Malformed GTFS CSV quoting")
                    ',' -> emit()
                    else -> field.append(c)
                }
                2 -> when {
                    c == '"' && index + 1 < line.length && line[index + 1] == '"' -> {
                        field.append('"')
                        index++
                    }
                    c == '"' -> state = 3
                    else -> field.append(c)
                }
                3 -> when (c) {
                    ',' -> emit()
                    ' ', '\t' -> state = 4 // accepted by the bounded logical-record reader
                    else -> throw IllegalArgumentException("Malformed GTFS CSV quoting")
                }
                4 -> when (c) {
                    ',' -> emit()
                    ' ', '\t' -> Unit
                    else -> throw IllegalArgumentException("Malformed GTFS CSV quoting")
                }
            }
            index++
        }
        require(state != 2) { "Malformed GTFS CSV quoting" }
        fields += field.toString()
        return fields
    }
}
