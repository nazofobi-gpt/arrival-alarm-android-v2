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
    UNSUPPORTED_INCREMENTALITY,
}

enum class GermanyRealtimeStopUpdateValidity {
    VALID,
    ASSIGNED_STOP_REQUIRES_SEQUENCE,
    ASSIGNED_STOP_ID_MISMATCH,
}

enum class GermanyRealtimeTripSelectorValidity {
    VALID_ID_BASED,
    VALID_IDLESS_SCHEDULED,
    UNMATCHABLE_INCOMPLETE,
}

data class GermanyRealtimeStopUpdate(
    val stopSequence: Int?,
    val stopId: String?,
    val arrivalDelaySeconds: Int?,
    val departureDelaySeconds: Int?,
    val arrivalTimeEpochSeconds: Long?,
    val departureTimeEpochSeconds: Long?,
    val scheduleRelationship: String? = null,
    val assignedStopId: String? = null,
    val pickupType: String? = null,
    val dropOffType: String? = null,
    val stopHeadsign: String? = null,
    val validity: GermanyRealtimeStopUpdateValidity = GermanyRealtimeStopUpdateValidity.VALID,
)

data class GermanyRealtimeTripUpdate(
    val entityId: String,
    val tripId: String?,
    val routeId: String?,
    val startDate: String?,
    val scheduleRelationship: String?,
    val cancelled: Boolean,
    val stops: List<GermanyRealtimeStopUpdate>,
    val directionId: Int? = null,
    val startTime: String? = null,
    val selectorValidity: GermanyRealtimeTripSelectorValidity =
        GermanyRealtimeTripSelectorValidity.UNMATCHABLE_INCOMPLETE,
    val updateTimestampEpochSeconds: Long? = null,
) {
    val hasFrequencyInstanceIdentity: Boolean
        get() = tripId != null && startTime != null && startDate != null
}

data class GermanyRealtimeActivePeriod(
    val startEpochSeconds: Long?,
    val endEpochSeconds: Long?,
)

enum class GermanyRealtimeAlertSelectorValidity {
    VALID,
    UNMATCHABLE_EMPTY,
    UNMATCHABLE_TRIP_SELECTOR,
}

data class GermanyRealtimeAlertTripSelector(
    val tripId: String?,
    val routeId: String?,
    val directionId: Int?,
    val startTime: String?,
    val startDate: String?,
    val scheduleRelationship: String?,
    val validity: GermanyRealtimeTripSelectorValidity,
)

data class GermanyRealtimeAlertSelector(
    val agencyId: String?,
    val routeId: String?,
    val routeType: Int?,
    val directionId: Int?,
    val stopId: String?,
    val trip: GermanyRealtimeAlertTripSelector?,
    val validity: GermanyRealtimeAlertSelectorValidity,
)

data class GermanyRealtimeAlert(
    val entityId: String,
    val cause: String?,
    val effect: String?,
    val header: String?,
    val description: String?,
    val informedEntities: List<GermanyRealtimeAlertSelector>,
    val activePeriods: List<GermanyRealtimeActivePeriod>,
    val severityLevel: String? = null,
) {
    val routeIds: Set<String>
        get() = informedEntities
            .asSequence()
            .filter { it.validity == GermanyRealtimeAlertSelectorValidity.VALID }
            .mapNotNull { it.routeId }
            .toCollection(linkedSetOf())

    val tripIds: Set<String>
        get() = informedEntities
            .asSequence()
            .filter { it.validity == GermanyRealtimeAlertSelectorValidity.VALID }
            .mapNotNull { it.trip?.tripId }
            .toCollection(linkedSetOf())

    val stopIds: Set<String>
        get() = informedEntities
            .asSequence()
            .filter { it.validity == GermanyRealtimeAlertSelectorValidity.VALID }
            .mapNotNull { it.stopId }
            .toCollection(linkedSetOf())
}

data class GermanyRealtimeSnapshot(
    val source: String,
    val license: String,
    val feedTimestampEpochSeconds: Long?,
    val fetchedAtEpochSeconds: Long,
    val tripUpdates: List<GermanyRealtimeTripUpdate>,
    val serviceAlerts: List<GermanyRealtimeAlert>,
    val gtfsRealtimeVersion: String? = null,
    val incrementality: String = "FULL_DATASET",
    val feedVersion: String? = null,
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

        val header = feed.header
        val incrementality = if (header.hasIncrementality()) {
            header.incrementality.name
        } else {
            "FULL_DATASET"
        }
        if (incrementality != "FULL_DATASET") {
            return GermanyRealtimeFetchResult.Unavailable(
                GermanyRealtimeUnavailableReason.UNSUPPORTED_INCREMENTALITY,
                "GTFS-RT incrementality $incrementality is not supported as snapshot state",
            )
        }
        val gtfsRealtimeVersion = header.gtfsRealtimeVersion.takeIf { it.isNotBlank() }
        val feedVersion = header.feedVersion.takeIf {
            header.hasFeedVersion() && it.isNotBlank()
        }

        val tripUpdates = buildList {
            feed.entityList.forEach { entity ->
                if (!entity.hasTripUpdate()) return@forEach
                val update = entity.tripUpdate
                val trip = update.trip
                val tripId = trip.tripId.takeIf { it.isNotBlank() }
                val routeId = trip.routeId.takeIf { it.isNotBlank() }
                val directionId = trip.directionId.takeIf { trip.hasDirectionId() }
                val startTime = trip.startTime.takeIf { it.isNotBlank() }
                val startDate = trip.startDate.takeIf { it.isNotBlank() }
                val relationship = if (trip.hasScheduleRelationship()) {
                    trip.scheduleRelationship.name
                } else {
                    null
                }
                val selectorValidity = tripSelectorValidity(
                    tripId = tripId,
                    routeId = routeId,
                    directionId = directionId,
                    startTime = startTime,
                    startDate = startDate,
                    relationship = relationship,
                )
                add(
                    GermanyRealtimeTripUpdate(
                        entityId = entity.id,
                        tripId = tripId,
                        routeId = routeId,
                        startDate = startDate,
                        scheduleRelationship = relationship,
                        cancelled = relationship == "CANCELED",
                        stops = update.stopTimeUpdateList.map { stop ->
                            val stopSequence = stop.stopSequence.takeIf { stop.hasStopSequence() }
                            val stopId = stop.stopId.takeIf { it.isNotBlank() }
                            val stopRelationship = if (stop.hasScheduleRelationship()) {
                                stop.scheduleRelationship.name
                            } else {
                                null
                            }
                            val properties = stop.stopTimeProperties.takeIf {
                                stop.hasStopTimeProperties()
                            }
                            val assignedStopId = properties?.assignedStopId?.takeIf { it.isNotBlank() }
                            val validity = when {
                                assignedStopId != null && stopSequence == null ->
                                    GermanyRealtimeStopUpdateValidity.ASSIGNED_STOP_REQUIRES_SEQUENCE
                                assignedStopId != null && stopId != null && assignedStopId != stopId ->
                                    GermanyRealtimeStopUpdateValidity.ASSIGNED_STOP_ID_MISMATCH
                                else -> GermanyRealtimeStopUpdateValidity.VALID
                            }
                            val noRealtimeTiming = stopRelationship == "NO_DATA"
                            GermanyRealtimeStopUpdate(
                                stopSequence = stopSequence,
                                stopId = stopId,
                                arrivalDelaySeconds = stop.arrival.delay.takeIf {
                                    !noRealtimeTiming && stop.hasArrival() && stop.arrival.hasDelay()
                                },
                                departureDelaySeconds = stop.departure.delay.takeIf {
                                    !noRealtimeTiming && stop.hasDeparture() && stop.departure.hasDelay()
                                },
                                arrivalTimeEpochSeconds = stop.arrival.time.takeIf {
                                    !noRealtimeTiming && stop.hasArrival() && stop.arrival.hasTime()
                                },
                                departureTimeEpochSeconds = stop.departure.time.takeIf {
                                    !noRealtimeTiming && stop.hasDeparture() && stop.departure.hasTime()
                                },
                                scheduleRelationship = stopRelationship,
                                assignedStopId = assignedStopId,
                                pickupType = properties?.pickupType?.name?.takeIf {
                                    properties.hasPickupType()
                                },
                                dropOffType = properties?.dropOffType?.name?.takeIf {
                                    properties.hasDropOffType()
                                },
                                stopHeadsign = properties?.stopHeadsign?.takeIf {
                                    properties.hasStopHeadsign() && it.isNotBlank()
                                },
                                validity = validity,
                            )
                        },
                        directionId = directionId,
                        startTime = startTime,
                        selectorValidity = selectorValidity,
                        updateTimestampEpochSeconds = update.timestamp.takeIf {
                            update.hasTimestamp()
                        },
                    ),
                )
            }
        }

        val alerts = buildList {
            feed.entityList.forEach { entity ->
                if (!entity.hasAlert()) return@forEach
                val alert = entity.alert
                val informedEntities = alert.informedEntityList.map { selector ->
                    val agencyId = selector.agencyId.takeIf { it.isNotBlank() }
                    val routeId = selector.routeId.takeIf { it.isNotBlank() }
                    val routeType = selector.routeType.takeIf { selector.hasRouteType() }
                    val directionId = selector.directionId.takeIf { selector.hasDirectionId() }
                    val stopId = selector.stopId.takeIf { it.isNotBlank() }
                    val tripSelector = if (selector.hasTrip()) {
                        val trip = selector.trip
                        val tripId = trip.tripId.takeIf { it.isNotBlank() }
                        val tripRouteId = trip.routeId.takeIf { it.isNotBlank() }
                        val tripDirectionId = trip.directionId.takeIf { trip.hasDirectionId() }
                        val startTime = trip.startTime.takeIf { it.isNotBlank() }
                        val startDate = trip.startDate.takeIf { it.isNotBlank() }
                        val relationship = if (trip.hasScheduleRelationship()) {
                            trip.scheduleRelationship.name
                        } else {
                            null
                        }
                        GermanyRealtimeAlertTripSelector(
                            tripId = tripId,
                            routeId = tripRouteId,
                            directionId = tripDirectionId,
                            startTime = startTime,
                            startDate = startDate,
                            scheduleRelationship = relationship,
                            validity = tripSelectorValidity(
                                tripId = tripId,
                                routeId = tripRouteId,
                                directionId = tripDirectionId,
                                startTime = startTime,
                                startDate = startDate,
                                relationship = relationship,
                            ),
                        )
                    } else {
                        null
                    }
                    val hasSelectorDimension =
                        agencyId != null ||
                            routeId != null ||
                            routeType != null ||
                            directionId != null ||
                            stopId != null ||
                            tripSelector != null
                    val validity = when {
                        tripSelector?.validity ==
                            GermanyRealtimeTripSelectorValidity.UNMATCHABLE_INCOMPLETE ->
                            GermanyRealtimeAlertSelectorValidity.UNMATCHABLE_TRIP_SELECTOR
                        !hasSelectorDimension ->
                            GermanyRealtimeAlertSelectorValidity.UNMATCHABLE_EMPTY
                        else -> GermanyRealtimeAlertSelectorValidity.VALID
                    }
                    GermanyRealtimeAlertSelector(
                        agencyId = agencyId,
                        routeId = routeId,
                        routeType = routeType,
                        directionId = directionId,
                        stopId = stopId,
                        trip = tripSelector,
                        validity = validity,
                    )
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
                        informedEntities = informedEntities,
                        activePeriods = alert.activePeriodList.map { period ->
                            GermanyRealtimeActivePeriod(
                                startEpochSeconds = period.start.takeIf { period.hasStart() },
                                endEpochSeconds = period.end.takeIf { period.hasEnd() },
                            )
                        },
                        severityLevel = if (alert.hasSeverityLevel()) {
                            alert.severityLevel.name
                        } else {
                            null
                        },
                    ),
                )
            }
        }

        return GermanyRealtimeFetchResult.Available(
            GermanyRealtimeSnapshot(
                source = GermanyGtfsRealtimeProvenance.PROVIDER,
                license = GermanyGtfsRealtimeProvenance.LICENSE,
                feedTimestampEpochSeconds = header.timestamp.takeIf {
                    header.hasTimestamp()
                },
                fetchedAtEpochSeconds = fetchedAtEpochSeconds,
                tripUpdates = tripUpdates,
                serviceAlerts = alerts,
                gtfsRealtimeVersion = gtfsRealtimeVersion,
                incrementality = incrementality,
                feedVersion = feedVersion,
            ),
        )
    }

    private fun tripSelectorValidity(
        tripId: String?,
        routeId: String?,
        directionId: Int?,
        startTime: String?,
        startDate: String?,
        relationship: String?,
    ): GermanyRealtimeTripSelectorValidity = when {
        tripId != null -> GermanyRealtimeTripSelectorValidity.VALID_ID_BASED
        routeId != null &&
            directionId != null &&
            startTime != null &&
            startDate != null &&
            (relationship == null || relationship == "SCHEDULED") ->
            GermanyRealtimeTripSelectorValidity.VALID_IDLESS_SCHEDULED
        else -> GermanyRealtimeTripSelectorValidity.UNMATCHABLE_INCOMPLETE
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
