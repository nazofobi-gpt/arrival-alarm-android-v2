package com.nazofobi.arrivalalarm

data class ScreenAssistJourneySnapshot(
    val currentLocation: ScreenAssistLocation?,
    val journeyId: String?,
    val line: String?,
    val tripId: String?,
    val boardingStop: ScreenAssistStop?,
    val targetStop: ScreenAssistStop?,
    val etaEpochMs: Long?,
    val realtime: Boolean,
    val serviceAlert: String?,
    val freshnessEpochMs: Long,
    val source: String,
)

interface ScreenAssistJourneyState {
    fun snapshot(): ScreenAssistJourneySnapshot
    fun hasStop(stopId: String): Boolean
    fun setOrigin(stopId: String): Boolean
    fun setDestination(stopId: String): Boolean
    fun setBoardingStop(stopId: String): Boolean
    fun selectJourney(journeyId: String): Boolean
    fun fillSearch(query: String): Boolean
    fun requestRoute(originStopId: String?, destinationStopId: String): Boolean
    fun setAlarmTarget(stopId: String): Boolean
}

class CanonicalScreenAssistJourneyGateway(
    private val state: ScreenAssistJourneyState,
) : ScreenAssistContextReader, ScreenAssistCommandGateway {
    override fun read(): ScreenAssistAppContext {
        val value = state.snapshot()
        return ScreenAssistAppContext(
            location = value.currentLocation,
            journey = ScreenAssistJourneyContext(
                journeyId = value.journeyId,
                line = value.line,
                tripId = value.tripId,
                boardingStop = value.boardingStop,
                targetStop = value.targetStop,
                etaEpochMs = value.etaEpochMs,
                realtime = value.realtime,
                serviceAlert = value.serviceAlert,
                freshnessEpochMs = value.freshnessEpochMs,
                source = value.source,
            ),
        )
    }

    override fun validate(command: ScreenAssistCommand): Boolean = when (command) {
        is ScreenAssistCommand.SetOrigin -> state.hasStop(command.stopId)
        is ScreenAssistCommand.SetDestination -> state.hasStop(command.stopId)
        is ScreenAssistCommand.SetBoardingStop -> state.hasStop(command.stopId)
        is ScreenAssistCommand.SelectJourney -> command.journeyId.isNotBlank()
        is ScreenAssistCommand.FillSearch -> command.query.trim().length >= 2
        is ScreenAssistCommand.RequestRoute ->
            state.hasStop(command.destinationStopId) &&
                (command.originStopId == null || state.hasStop(command.originStopId))
        is ScreenAssistCommand.SetAlarmTarget -> state.hasStop(command.stopId)
    }

    override fun apply(command: ScreenAssistCommand): ScreenAssistCommandResult {
        if (!validate(command)) return ScreenAssistCommandResult(false, false, "validation_failed")
        val applied = when (command) {
            is ScreenAssistCommand.SetOrigin -> state.setOrigin(command.stopId)
            is ScreenAssistCommand.SetDestination -> state.setDestination(command.stopId)
            is ScreenAssistCommand.SetBoardingStop -> state.setBoardingStop(command.stopId)
            is ScreenAssistCommand.SelectJourney -> state.selectJourney(command.journeyId)
            is ScreenAssistCommand.FillSearch -> state.fillSearch(command.query.trim())
            is ScreenAssistCommand.RequestRoute -> state.requestRoute(command.originStopId, command.destinationStopId)
            is ScreenAssistCommand.SetAlarmTarget -> state.setAlarmTarget(command.stopId)
        }
        return ScreenAssistCommandResult(applied, false, if (applied) "applied" else "state_rejected")
    }
}
