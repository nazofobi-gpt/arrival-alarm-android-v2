package com.nazofobi.arrivalalarm

import com.google.protobuf.CodedInputStream
import com.google.protobuf.ExtensionRegistryLite
import com.google.protobuf.CodedInputStream
import com.google.protobuf.ExtensionRegistryLite
import com.google.protobuf.InvalidProtocolBufferException
import com.google.transit.realtime.GtfsRealtime
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object GermanyGtfsRealtimeProvenance {
    const val ENDPOINT = "https://realtime.gtfs.de/realtime-free.pb"
    const val PROVIDER = "gtfs.de Germany-wide GTFS-RT"
    const val LICENSE = "CC BY-SA 4.0"
}

internal const val GERMANY_REALTIME_DEFAULT_MAX_BYTES = 64 * 1024 * 1024
private const val GERMANY_REALTIME_PROTO_RECURSION_LIMIT = 64
private const val PROTOBUF_LENGTH_DELIMITED_WIRE_TYPE = 2

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

enum class GermanyRealtimeAlertSelectorValidity {
    VALID,
    UNMATCHABLE_EMPTY,
    UNMATCHABLE_TRIP_SELECTOR,
}

data class GermanyRealtimeTripSelector(
    val tripId: String?,
    val routeId: String?,
    val directionId: Int?,
    val startTime: String?,
    val startDate: String?,
    val scheduleRelationship: String?,
    val validity: GermanyRealtimeTripSelectorValidity,
) {
    val hasFrequencyInstanceIdentity: Boolean
        get() = tripId != null && startTime != null && startDate != null
}

data class GermanyRealtimeAlertSelector(
    val agencyId: String?,
    val routeId: String?,
    val routeType: Int?,
    val directionId: Int?,
    val stopId: String?,
    val trip: GermanyRealtimeTripSelector?,
    val validity: GermanyRealtimeAlertSelectorValidity,
)

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

data class GermanyRealtimeAlert(
    val entityId: String,
    val cause: String?,
    val effect: String?,
    val header: String?,
    val description: String?,
    val selectors: List<GermanyRealtimeAlertSelector>,
    val activePeriods: List<GermanyRealtimeActivePeriod>,
    val severityLevel: String? = null,
) {
    private val validSelectors: List<GermanyRealtimeAlertSelector>
        get() = selectors.filter { it.validity == GermanyRealtimeAlertSelectorValidity.VALID }

    val routeIds: Set<String>
        get() = validSelectors.mapNotNull { it.routeId }.toSet()

    val tripIds: Set<String>
        get() = validSelectors.mapNotNull { it.trip?.tripId }.toSet()

    val stopIds: Set<String>
        get() = validSelectors.mapNotNull { it.stopId }.toSet()
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
    private val maxBytes: Int = GERMANY_REALTIME_DEFAULT_MAX_BYTES,
    private val clock: EpochClock = EpochClock { System.currentTimeMillis() / 1_000L },
    private val loader: ((String) -> ByteArray)? = null,
    private val streamLoader: ((String) -> InputStream)? = null,
) {
    init {
        require(connectTimeoutMs > 0)
        require(readTimeoutMs > 0)
        require(maxBytes > 0)
    }

    fun fetch(): GermanyRealtimeFetchResult {
        return try {
            val fetchedAtEpochSeconds = clock.nowEpochSeconds()
            when {
                streamLoader != null -> streamLoader.invoke(endpoint).use { input ->
                    parse(BoundedInputStream(input, maxBytes), fetchedAtEpochSeconds)
                }
                loader != null -> {
                    val bytes = loader.invoke(endpoint)
                    if (bytes.size > maxBytes) {
                        GermanyRealtimeFetchResult.Unavailable(
                            GermanyRealtimeUnavailableReason.TOO_LARGE,
                            "GTFS-RT payload exceeded $maxBytes bytes",
                        )
                    } else parse(bytes, fetchedAtEpochSeconds)
                }
                else -> loadAndParseBounded(endpoint, fetchedAtEpochSeconds)
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
    ): GermanyRealtimeFetchResult =
        ByteArrayInputStream(bytes).use { input -> parse(input, fetchedAtEpochSeconds) }

    private fun parse(
        input: InputStream,
        fetchedAtEpochSeconds: Long,
    ): GermanyRealtimeFetchResult {
        val codedInput = CodedInputStream.newInstance(input).apply {
            setSizeLimit(maxBytes)
            setRecursionLimit(64)
        }
        val registry = ExtensionRegistryLite.getEmptyRegistry()
        var header: GtfsRealtime.FeedHeader? = null
        val tripUpdates = mutableListOf<GermanyRealtimeTripUpdate>()
        val alerts = mutableListOf<GermanyRealtimeAlert>()

        try {
            while (!codedInput.isAtEnd) {
                when (val tag = codedInput.readTag()) {
                    0 -> break
                    10 -> {
                        val parsedHeader =
                            codedInput.readMessage(GtfsRealtime.FeedHeader.parser(), registry)
                        if (!parsedHeader.isInitialized) {
                            return GermanyRealtimeFetchResult.Unavailable(
                                GermanyRealtimeUnavailableReason.PARSE,
                                "GTFS-RT FeedHeader is missing required fields",
                            )
                        }
                        header = parsedHeader
                    }
                    18 -> {
                        val entity =
                            codedInput.readMessage(GtfsRealtime.FeedEntity.parser(), registry)
                        if (!entity.isInitialized) {
                            return GermanyRealtimeFetchResult.Unavailable(
                                GermanyRealtimeUnavailableReason.PARSE,
                                "GTFS-RT FeedEntity is missing required fields",
                            )
                        }
                        if (entity.hasTripUpdate()) tripUpdates += projectTripUpdate(entity)
                        if (entity.hasAlert()) alerts += projectAlert(entity)
                    }
                    else -> if (!codedInput.skipField(tag)) break
                }
            }
        } catch (failure: InvalidProtocolBufferException) {
            return GermanyRealtimeFetchResult.Unavailable(
                GermanyRealtimeUnavailableReason.PARSE,
                failure.message,
            )
        }

        val resolvedHeader = header ?: return GermanyRealtimeFetchResult.Unavailable(
            GermanyRealtimeUnavailableReason.PARSE,
            "GTFS-RT FeedHeader missing",
        )
        val incrementality = if (resolvedHeader.hasIncrementality()) {
            resolvedHeader.incrementality.name
        } else {
            "FULL_DATASET"
        }
        if (incrementality != "FULL_DATASET") {
            return GermanyRealtimeFetchResult.Unavailable(
                GermanyRealtimeUnavailableReason.UNSUPPORTED_INCREMENTALITY,
                "GTFS-RT incrementality $incrementality is not supported as snapshot state",
            )
        }

        return GermanyRealtimeFetchResult.Available(
            GermanyRealtimeSnapshot(
                source = GermanyGtfsRealtimeProvenance.PROVIDER,
                license = GermanyGtfsRealtimeProvenance.LICENSE,
                feedTimestampEpochSeconds = resolvedHeader.timestamp.takeIf {
                    resolvedHeader.hasTimestamp()
                },
                fetchedAtEpochSeconds = fetchedAtEpochSeconds,
                tripUpdates = tripUpdates,
                serviceAlerts = alerts,
                gtfsRealtimeVersion = resolvedHeader.gtfsRealtimeVersion.takeIf { it.isNotBlank() },
                incrementality = incrementality,
                feedVersion = resolvedHeader.feedVersion.takeIf {
                    resolvedHeader.hasFeedVersion() && it.isNotBlank()
                },
            ),
        )
    }

    private fun projectTripUpdate(
        entity: GtfsRealtime.FeedEntity,
    ): GermanyRealtimeTripUpdate {
        val update = entity.tripUpdate
        val trip = update.trip
        val tripId = trip.tripId.takeIf { it.isNotBlank() }
        val routeId = trip.routeId.takeIf { it.isNotBlank() }
        val directionId = trip.directionId.takeIf { trip.hasDirectionId() }
        val startTime = trip.startTime.takeIf { it.isNotBlank() }
        val startDate = trip.startDate.takeIf { it.isNotBlank() }
        val relationship = if (trip.hasScheduleRelationship()) trip.scheduleRelationship.name else null
        val selectorValidity = when {
            tripId != null -> GermanyRealtimeTripSelectorValidity.VALID_ID_BASED
            routeId != null &&
                directionId != null &&
                startTime != null &&
                startDate != null &&
                (relationship == null || relationship == "SCHEDULED") ->
                GermanyRealtimeTripSelectorValidity.VALID_IDLESS_SCHEDULED
            else -> GermanyRealtimeTripSelectorValidity.UNMATCHABLE_INCOMPLETE
        }
        return GermanyRealtimeTripUpdate(
            entityId = entity.id,
            tripId = tripId,
            routeId = routeId,
            startDate = startDate,
            scheduleRelationship = relationship,
            cancelled = relationship == "CANCELED",
            stops = update.stopTimeUpdateList.map { stop ->
                val stopSequence = stop.stopSequence.takeIf { stop.hasStopSequence() }
                val stopId = stop.stopId.takeIf { it.isNotBlank() }
                val stopRelationship =
                    if (stop.hasScheduleRelationship()) stop.scheduleRelationship.name else null
                val properties = stop.stopTimeProperties.takeIf { stop.hasStopTimeProperties() }
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
                    pickupType = properties?.pickupType?.name?.takeIf { properties.hasPickupType() },
                    dropOffType = properties?.dropOffType?.name?.takeIf { properties.hasDropOffType() },
                    stopHeadsign = properties?.stopHeadsign?.takeIf {
                        properties.hasStopHeadsign() && it.isNotBlank()
                    },
                    validity = validity,
                )
            },
            directionId = directionId,
            startTime = startTime,
            selectorValidity = selectorValidity,
            updateTimestampEpochSeconds = update.timestamp.takeIf { update.hasTimestamp() },
        )
    }

    private fun projectAlert(
        entity: GtfsRealtime.FeedEntity,
    ): GermanyRealtimeAlert {
        val alert = entity.alert
        val selectors = alert.informedEntityList.map { selector ->
            val agencyId = selector.agencyId.takeIf { it.isNotBlank() }
            val routeId = selector.routeId.takeIf { it.isNotBlank() }
            val routeType = selector.routeType.takeIf { selector.hasRouteType() }
            val directionId = selector.directionId.takeIf { selector.hasDirectionId() }
            val stopId = selector.stopId.takeIf { it.isNotBlank() }
            val trip = selector.trip.takeIf { selector.hasTrip() }?.let(::parseTripSelector)
            val validity = when {
                trip?.validity == GermanyRealtimeTripSelectorValidity.UNMATCHABLE_INCOMPLETE ->
                    GermanyRealtimeAlertSelectorValidity.UNMATCHABLE_TRIP_SELECTOR
                agencyId == null && routeId == null && routeType == null &&
                    directionId == null && stopId == null && trip == null ->
                    GermanyRealtimeAlertSelectorValidity.UNMATCHABLE_EMPTY
                else -> GermanyRealtimeAlertSelectorValidity.VALID
            }
            GermanyRealtimeAlertSelector(
                agencyId = agencyId,
                routeId = routeId,
                routeType = routeType,
                directionId = directionId,
                stopId = stopId,
                trip = trip,
                validity = validity,
            )
        }
        return GermanyRealtimeAlert(
            entityId = entity.id,
            cause = if (alert.hasCause()) alert.cause.name else null,
            effect = if (alert.hasEffect()) alert.effect.name else null,
            header = if (alert.hasHeaderText()) preferredText(alert.headerText) else null,
            description = if (alert.hasDescriptionText()) preferredText(alert.descriptionText) else null,
            selectors = selectors,
            activePeriods = alert.activePeriodList.map { period ->
                GermanyRealtimeActivePeriod(
                    startEpochSeconds = period.start.takeIf { period.hasStart() },
                    endEpochSeconds = period.end.takeIf { period.hasEnd() },
                )
            },
            severityLevel = if (alert.hasSeverityLevel()) alert.severityLevel.name else null,
        )
    }

    private fun parseTripSelector(
        trip: GtfsRealtime.TripDescriptor,
    ): GermanyRealtimeTripSelector {
        val tripId = trip.tripId.takeIf { it.isNotBlank() }
        val routeId = trip.routeId.takeIf { it.isNotBlank() }
        val directionId = trip.directionId.takeIf { trip.hasDirectionId() }
        val startTime = trip.startTime.takeIf { it.isNotBlank() }
        val startDate = trip.startDate.takeIf { it.isNotBlank() }
        val scheduleRelationship = if (trip.hasScheduleRelationship()) {
            trip.scheduleRelationship.name
        } else {
            null
        }
        val validity = when {
            tripId != null -> GermanyRealtimeTripSelectorValidity.VALID_ID_BASED
            routeId != null &&
                directionId != null &&
                startTime != null &&
                startDate != null &&
                (scheduleRelationship == null || scheduleRelationship == "SCHEDULED") ->
                GermanyRealtimeTripSelectorValidity.VALID_IDLESS_SCHEDULED
            else -> GermanyRealtimeTripSelectorValidity.UNMATCHABLE_INCOMPLETE
        }
        return GermanyRealtimeTripSelector(
            tripId = tripId,
            routeId = routeId,
            directionId = directionId,
            startTime = startTime,
            startDate = startDate,
            scheduleRelationship = scheduleRelationship,
            validity = validity,
        )
    }

    private fun preferredText(value: GtfsRealtime.TranslatedString): String? {
        val translations = value.translationList
        return translations.firstOrNull { it.language.equals("de", ignoreCase = true) }?.text
            ?.takeIf { it.isNotBlank() }
            ?: translations.firstOrNull()?.text?.takeIf { it.isNotBlank() }
    }

    private fun loadAndParseBounded(
        url: String,
        fetchedAtEpochSeconds: Long,
    ): GermanyRealtimeFetchResult {
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
                parse(BoundedInputStream(input, maxBytes), fetchedAtEpochSeconds)
            }
        } finally {
            connection.disconnect()
        }
    }

    private class BoundedInputStream(
        input: InputStream,
        private val maxBytes: Int,
    ) : FilterInputStream(input) {
        private var total = 0L
        override fun read(): Int {
            val value = super.read()
            if (value >= 0) account(1)
            return value
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(buffer, offset, length)
            if (read > 0) account(read)
            return read
        }
        private fun account(read: Int) {
            total += read
            if (total > maxBytes.toLong()) throw PayloadTooLargeException()
        }
    }

    private class HttpStatusException(val statusCode: Int) : IOException()
    private class PayloadTooLargeException : IOException()
}