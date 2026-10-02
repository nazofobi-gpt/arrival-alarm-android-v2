package com.nazofobi.arrivalalarm

import com.google.protobuf.InvalidProtocolBufferException
import com.google.transit.realtime.GtfsRealtime
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object GermanyGtfsRealtimeProvenance {
    const val ENDPOINT = "https://realtime.gtfs.de/realtime-free.pb"
    const val PROVIDER = "gtfs.de Germany-wide GTFS-RT"
    const val LICENSE = "CC BY-SA 4.0"
}

enum class GermanyRealtimeUnavailableReason {
    HTTP,
    NETWORK,
    TOO_LARGE,
    PARSE,
}

data class GermanyRealtimeStopUpdate(
    val stopSequence: Int?,
    val stopId: String?,
    val arrivalDelaySeconds: Int?,
    val departureDelaySeconds: Int?,
    val arrivalTimeEpochSeconds: Long?,
    val departureTimeEpochSeconds: Long?,
)

data class GermanyRealtimeTripUpdate(
    val entityId: String,
    val tripId: String,
    val routeId: String?,
    val startDate: String?,
    val scheduleRelationship: String?,
    val cancelled: Boolean,
    val stops: List<GermanyRealtimeStopUpdate>,
)

data class GermanyRealtimeActivePeriod(
    val startEpochSeconds: Long?,
    val endEpochSeconds: Long?,
)

data class GermanyRealtimeAlert(
    val entityId: String,
    val cause: String?,
    val effect: String?,
    val header: String?,
    val description: String?,
    val routeIds: Set<String>,
    val tripIds: Set<String>,
    val stopIds: Set<String>,
    val activePeriods: List<GermanyRealtimeActivePeriod>,
)

data class GermanyRealtimeSnapshot(
    val source: String,
    val license: String,
    val feedTimestampEpochSeconds: Long?,
    val fetchedAtEpochSeconds: Long,
    val tripUpdates: List<GermanyRealtimeTripUpdate>,
    val serviceAlerts: List<GermanyRealtimeAlert>,
)

sealed interface GermanyRealtimeFetchResult {
    data class Available(val snapshot: GermanyRealtimeSnapshot) : GermanyRealtimeFetchResult
    data class Unavailable(
        val reason: GermanyRealtimeUnavailableReason,
        val detail: String? = null,
    ) : GermanyRealtimeFetchResult
}

class GermanyGtfsRealtimeClient(
    private val endpoint: String = GermanyGtfsRealtimeProvenance.ENDPOINT,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 8_000,
    private val maxBytes: Int = 32 * 1024 * 1024,
    private val clock: EpochClock = EpochClock { System.currentTimeMillis() / 1_000L },
    private val loader: ((String) -> ByteArray)? = null,
) {
    init {
        require(connectTimeoutMs > 0)
        require(readTimeoutMs > 0)
        require(maxBytes > 0)
    }

    fun fetch(): GermanyRealtimeFetchResult {
        return try {
            val bytes = loader?.invoke(endpoint) ?: loadBounded(endpoint)
            if (bytes.size > maxBytes) {
                GermanyRealtimeFetchResult.Unavailable(
                    GermanyRealtimeUnavailableReason.TOO_LARGE,
                    "GTFS-RT payload exceeded $maxBytes bytes",
                )
            } else {
                parse(bytes, clock.nowEpochSeconds())
            }
        } catch (failure: HttpStatusException) {
            GermanyRealtimeFetchResult.Unavailable(
                GermanyRealtimeUnavailableReason.HTTP,
                "HTTP ${failure.statusCode}",
            )
        } catch (_: PayloadTooLargeException) {
            GermanyRealtimeFetchResult.Unavailable(
                GermanyRealtimeUnavailableReason.TOO_LARGE,
                "GTFS-RT payload exceeded $maxBytes bytes",
            )
        } catch (failure: IOException) {
            GermanyRealtimeFetchResult.Unavailable(
                GermanyRealtimeUnavailableReason.NETWORK,
                failure.message,
            )
        } catch (failure: RuntimeException) {
            GermanyRealtimeFetchResult.Unavailable(
                GermanyRealtimeUnavailableReason.NETWORK,
                failure.message,
            )
        }
    }

    internal fun parse(
        bytes: ByteArray,
        fetchedAtEpochSeconds: Long,
    ): GermanyRealtimeFetchResult {
        val feed = try {
            GtfsRealtime.FeedMessage.parseFrom(bytes)
        } catch (failure: InvalidProtocolBufferException) {
            return GermanyRealtimeFetchResult.Unavailable(
                GermanyRealtimeUnavailableReason.PARSE,
                failure.message,
            )
        }

        val tripUpdates = buildList {
            feed.entityList.forEach { entity ->
                if (!entity.hasTripUpdate()) return@forEach
                val update = entity.tripUpdate
                val trip = update.trip
                val tripId = trip.tripId.takeIf { it.isNotBlank() } ?: return@forEach
                val relationship = if (trip.hasScheduleRelationship()) {
                    trip.scheduleRelationship.name
                } else {
                    null
                }
                add(
                    GermanyRealtimeTripUpdate(
                        entityId = entity.id,
                        tripId = tripId,
                        routeId = trip.routeId.takeIf { it.isNotBlank() },
                        startDate = trip.startDate.takeIf { it.isNotBlank() },
                        scheduleRelationship = relationship,
                        cancelled = relationship == "CANCELED",
                        stops = update.stopTimeUpdateList.map { stop ->
                            GermanyRealtimeStopUpdate(
                                stopSequence = stop.stopSequence.takeIf { stop.hasStopSequence() },
                                stopId = stop.stopId.takeIf { it.isNotBlank() },
                                arrivalDelaySeconds = stop.arrival.delay.takeIf {
                                    stop.hasArrival() && stop.arrival.hasDelay()
                                },
                                departureDelaySeconds = stop.departure.delay.takeIf {
                                    stop.hasDeparture() && stop.departure.hasDelay()
                                },
                                arrivalTimeEpochSeconds = stop.arrival.time.takeIf {
                                    stop.hasArrival() && stop.arrival.hasTime()
                                },
                                departureTimeEpochSeconds = stop.departure.time.takeIf {
                                    stop.hasDeparture() && stop.departure.hasTime()
                                },
                            )
                        },
                    ),
                )
            }
        }

        val alerts = buildList {
            feed.entityList.forEach { entity ->
                if (!entity.hasAlert()) return@forEach
                val alert = entity.alert
                val routeIds = linkedSetOf<String>()
                val tripIds = linkedSetOf<String>()
                val stopIds = linkedSetOf<String>()
                alert.informedEntityList.forEach { selector ->
                    selector.routeId.takeIf { it.isNotBlank() }?.let(routeIds::add)
                    selector.stopId.takeIf { it.isNotBlank() }?.let(stopIds::add)
                    if (selector.hasTrip()) {
                        selector.trip.tripId.takeIf { it.isNotBlank() }?.let(tripIds::add)
                    }
                }
                add(
                    GermanyRealtimeAlert(
                        entityId = entity.id,
                        cause = if (alert.hasCause()) alert.cause.name else null,
                        effect = if (alert.hasEffect()) alert.effect.name else null,
                        header = if (alert.hasHeaderText()) preferredText(alert.headerText) else null,
                        description = if (alert.hasDescriptionText()) {
                            preferredText(alert.descriptionText)
                        } else {
                            null
                        },
                        routeIds = routeIds,
                        tripIds = tripIds,
                        stopIds = stopIds,
                        activePeriods = alert.activePeriodList.map { period ->
                            GermanyRealtimeActivePeriod(
                                startEpochSeconds = period.start.takeIf { period.hasStart() },
                                endEpochSeconds = period.end.takeIf { period.hasEnd() },
                            )
                        },
                    ),
                )
            }
        }

        return GermanyRealtimeFetchResult.Available(
            GermanyRealtimeSnapshot(
                source = GermanyGtfsRealtimeProvenance.PROVIDER,
                license = GermanyGtfsRealtimeProvenance.LICENSE,
                feedTimestampEpochSeconds = feed.header.timestamp.takeIf {
                    feed.header.hasTimestamp()
                },
                fetchedAtEpochSeconds = fetchedAtEpochSeconds,
                tripUpdates = tripUpdates,
                serviceAlerts = alerts,
            ),
        )
    }

    private fun preferredText(value: GtfsRealtime.TranslatedString): String? {
        val translations = value.translationList
        return translations.firstOrNull { it.language.equals("de", ignoreCase = true) }?.text
            ?.takeIf { it.isNotBlank() }
            ?: translations.firstOrNull()?.text?.takeIf { it.isNotBlank() }
    }

    private fun loadBounded(url: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            requestMethod = "GET"
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept", "application/x-protobuf,application/octet-stream")
            setRequestProperty("User-Agent", "ArrivalAlarmAndroid/0.1")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw HttpStatusException(status)
            val contentLength = connection.contentLength
            if (contentLength > maxBytes) throw PayloadTooLargeException()

            return connection.inputStream.use { input ->
                val output = ByteArrayOutputStream(
                    contentLength.takeIf { it in 1..maxBytes } ?: 16 * 1024,
                )
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw PayloadTooLargeException()
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private class HttpStatusException(val statusCode: Int) : IOException()
    private class PayloadTooLargeException : IOException()
}
