package com.nazofobi.arrivalalarm

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
                if (name in setOf("stops.txt", "routes.txt", "trips.txt", "stop_times.txt")) {
                    val entryBytes = ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        entryBytes.write(buffer, 0, count)
                    }
                    files[name] = parseCsv(entryBytes.toString(Charsets.UTF_8.name()).lineSequence().toList())
                }
                zip.closeEntry()
            }
        }
        val provider = TransitProvider("delfi","DELFI / GTFS Deutschland","Germany",FEED_URL,"Creative Commons 4.0",sourceVersion,fetchedAt,true,"https://realtime.gtfs.de/realtime-free.pb")
        val stops = files["stops.txt"].orEmpty().mapNotNull { row ->
            val id=row["stop_id"]?:return@mapNotNull null; val name=row["stop_name"]?:return@mapNotNull null
            val lat=row["stop_lat"]?.toDoubleOrNull()?:return@mapNotNull null; val lon=row["stop_lon"]?.toDoubleOrNull()?:return@mapNotNull null
            CatalogStop(id,provider.id,name,lat,lon)
        }
        val routesById=files["routes.txt"].orEmpty().associateBy{it["route_id"].orEmpty()}; val trips=files["trips.txt"].orEmpty()
        val stopTimesByTrip=files["stop_times.txt"].orEmpty().groupBy{it["trip_id"].orEmpty()}; val catalogRoutes=mutableListOf<CatalogRoute>(); val catalogTrips=mutableListOf<CatalogTrip>()
        trips.forEach { trip ->
            val tripId=trip["trip_id"]?:return@forEach; val routeId=trip["route_id"]?:return@forEach; val routeRow=routesById[routeId]?:return@forEach
            val ordered=stopTimesByTrip[tripId].orEmpty().sortedBy{it["stop_sequence"]?.toIntOrNull()?:Int.MAX_VALUE}; if(ordered.size<2)return@forEach
            val stopIds=ordered.mapNotNull{it["stop_id"]}; val direction=trip["trip_headsign"].orEmpty().ifBlank{routeRow["route_long_name"].orEmpty()}; val shortName=routeRow["route_short_name"].orEmpty().ifBlank{routeRow["route_long_name"].orEmpty()}
            val uniqueRouteId="$routeId:$tripId"; catalogRoutes+=CatalogRoute(uniqueRouteId,provider.id,shortName,direction,stopIds); catalogTrips+=CatalogTrip(tripId,provider.id,uniqueRouteId,ordered.first()["departure_time"].orEmpty())
        }
        return CatalogSnapshot(listOf(provider),stops,catalogRoutes,catalogTrips,fetchedAt,stale=false)
    }

    private fun nowUtcIso8601(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    private fun parseCsv(lines:List<String>):List<Map<String,String>> { if(lines.isEmpty())return emptyList(); val header=csvLine(lines.first()).map{it.removePrefix("\uFEFF")}; return lines.drop(1).filter{it.isNotBlank()}.map{line->val values=csvLine(line);header.mapIndexed{index,key->key to values.getOrElse(index){""}}.toMap()} }
    private fun csvLine(line:String):List<String>{val out=mutableListOf<String>();val current=StringBuilder();var quoted=false;var i=0;while(i<line.length){val c=line[i];when{c=='"'&&quoted&&i+1<line.length&&line[i+1]=='"'->{current.append('"');i++};c=='"'->quoted=!quoted;c==','&&!quoted->{out+=current.toString();current.clear()};else->current.append(c)};i++};out+=current.toString();return out}
}
