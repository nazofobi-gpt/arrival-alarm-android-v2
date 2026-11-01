package com.nazofobi.arrivalalarm

import android.content.Context
import android.util.Log

class ConnectorRouteRegistry {
    private var routes: List<RouteOption> = emptyList()

    @Synchronized
    fun replace(values: List<RouteOption>) {
        routes = values.toList()
    }

    @Synchronized
    fun clear() {
        routes = emptyList()
    }

    @Synchronized
    fun resolve(routeId: String): RouteOption? =
        routes.firstOrNull { route -> route.id == routeId }
}

class ConnectorRuntimeCoordinator(
    private val runtime: ConnectorRuntimeControl,
) {
    private val owners = LinkedHashSet<String>()

    @Synchronized
    fun acquire(owner: String) {
        require(owner.isNotBlank())
        if (!owners.add(owner)) return
        if (owners.size == 1) runtime.start()
    }

    @Synchronized
    fun release(owner: String) {
        if (!owners.remove(owner)) return
        if (owners.isEmpty()) runtime.stop()
    }

    fun syncNow(): ConnectorRuntimeStatus = runtime.syncNow()

    @Synchronized
    fun activeOwnerCount(): Int = owners.size

    @Synchronized
    fun close() {
        owners.clear()
        runtime.stop()
    }
}

private fun hasPersistedSelectedTripSnapshot(context: Context): Boolean = try {
    context.getSharedPreferences("selected_gtfs_trip_state", Context.MODE_PRIVATE)
        .contains(SelectedGtfsTripStore.KEY)
} catch (_: RuntimeException) {
    // An unreadable preference must fail closed rather than allow a connector arm command.
    true
}

class ArrivalAlarmProcessGraph private constructor(context: Context) {
    private val appContext = context.applicationContext

    val controller = JourneyController(
        stateStore = SharedPreferencesJourneyStateStore(appContext),
    )
    val routeRegistry = ConnectorRouteRegistry()
    val connectorPort = JourneyConnectorActionPort(
        controller = controller,
        routeResolver = routeRegistry::resolve,
        onAlarmArmed = { ActiveJourneyService.tryStart(appContext) },
        onAlarmCancelled = { ActiveJourneyService.stop(appContext) },
        selectedTripArmValidationRequired = hasPersistedSelectedTripSnapshot(appContext),
    )
    val connectorSessionStore = AndroidConnectorSessionStore(appContext)
    private val connectorOAuthTransport = HttpConnectorOAuthTransport()
    val connectorOAuth = ConnectorOAuthCoordinator(
        sessionStore = connectorSessionStore,
        transactionStore = AndroidConnectorOAuthTransactionStore(appContext),
        deviceIdentityProvider = AndroidConnectorDeviceIdentityStore(appContext),
        transport = connectorOAuthTransport,
    )
    private val connectorRuntime = ConnectorRuntime(
        processor = ConnectorCommandProcessor(
            port = connectorPort,
            idempotencyStore = SharedPreferencesConnectorIdempotencyStore(
                appContext.getSharedPreferences("connector_idempotency", Context.MODE_PRIVATE)
            ),
        ),
        sessionProvider = RefreshingConnectorSessionProvider(
            store = connectorSessionStore,
            oauth = connectorOAuth,
        ),
    )
    val connectorRuntimeCoordinator = ConnectorRuntimeCoordinator(connectorRuntime)

    companion object {
        fun create(context: Context) = ArrivalAlarmProcessGraph(context)
    }
}

object ArrivalAlarmRuntimeGraph {
    @Volatile
    private var instance: ArrivalAlarmProcessGraph? = null

    @Volatile
    private var refreshSchedulingConfigured = false

    fun get(context: Context): ArrivalAlarmProcessGraph {
        val appContext = context.applicationContext
        if (!refreshSchedulingConfigured) {
            synchronized(this) {
                if (!refreshSchedulingConfigured) {
                    refreshSchedulingConfigured = try {
                        NationwideFeedRefreshScheduler.ensureScheduled(appContext)
                        true
                    } catch (failure: Exception) {
                        Log.w(
                            "ArrivalAlarmRuntimeGraph",
                            "Unable to schedule offline feed refresh",
                            failure,
                        )
                        false
                    }
                }
            }
        }

        return instance ?: synchronized(this) {
            instance ?: ArrivalAlarmProcessGraph.create(appContext).also { instance = it }
        }
    }

    @Synchronized
    fun resetForTests() {
        instance?.connectorRuntimeCoordinator?.close()
        instance = null
        refreshSchedulingConfigured = false
    }
}
