package com.nazofobi.arrivalalarm

data class ScreenAssistLocation(val latitude: Double, val longitude: Double, val freshnessEpochMs: Long, val source: String)
data class ScreenAssistStop(val id: String, val name: String, val latitude: Double, val longitude: Double)
data class ScreenAssistJourneyContext(
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
data class ScreenAssistAppContext(val location: ScreenAssistLocation?, val journey: ScreenAssistJourneyContext?)

interface ScreenAssistContextReader { fun read(): ScreenAssistAppContext }

sealed class ScreenAssistCommand {
    data class SetOrigin(val stopId: String) : ScreenAssistCommand()
    data class SetDestination(val stopId: String) : ScreenAssistCommand()
    data class SetBoardingStop(val stopId: String) : ScreenAssistCommand()
    data class SelectJourney(val journeyId: String) : ScreenAssistCommand()
    data class FillSearch(val query: String) : ScreenAssistCommand()
    data class RequestRoute(val originStopId: String?, val destinationStopId: String) : ScreenAssistCommand()
    data class SetAlarmTarget(val stopId: String) : ScreenAssistCommand()
}

enum class ScreenAssistCommandRisk { REVERSIBLE, CONFIRMATION_REQUIRED }

data class ScreenAssistCommandResult(val applied: Boolean, val requiresConfirmation: Boolean, val message: String)

interface ScreenAssistCommandGateway {
    fun validate(command: ScreenAssistCommand): Boolean
    fun apply(command: ScreenAssistCommand): ScreenAssistCommandResult
}

class ScreenAssistCommandPolicy {
    fun risk(command: ScreenAssistCommand): ScreenAssistCommandRisk = when (command) {
        is ScreenAssistCommand.SelectJourney, is ScreenAssistCommand.SetAlarmTarget -> ScreenAssistCommandRisk.CONFIRMATION_REQUIRED
        else -> ScreenAssistCommandRisk.REVERSIBLE
    }

    fun execute(command: ScreenAssistCommand, confirmed: Boolean, gateway: ScreenAssistCommandGateway): ScreenAssistCommandResult {
        if (!gateway.validate(command)) return ScreenAssistCommandResult(false, false, "validation_failed")
        if (risk(command) == ScreenAssistCommandRisk.CONFIRMATION_REQUIRED && !confirmed) {
            return ScreenAssistCommandResult(false, true, "confirmation_required")
        }
        return gateway.apply(command)
    }
}
