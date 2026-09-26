package com.nazofobi.arrivalalarm

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class ConnectorIdempotencyEntry(
    val idempotencyKey: String,
    val commandType: String,
    val stateVersionBefore: Long,
    val receipt: ConnectorCommandReceipt? = null,
)

interface ConnectorIdempotencyStore {
    fun get(idempotencyKey: String): ConnectorIdempotencyEntry?
    fun begin(entry: ConnectorIdempotencyEntry)
    fun complete(receipt: ConnectorCommandReceipt)
}

class InMemoryConnectorIdempotencyStore(
    private val maxCompletedEntries: Int = 128,
) : ConnectorIdempotencyStore {
    private val entries = LinkedHashMap<String, ConnectorIdempotencyEntry>()

    @Synchronized
    override fun get(idempotencyKey: String): ConnectorIdempotencyEntry? =
        entries[idempotencyKey]

    @Synchronized
    override fun begin(entry: ConnectorIdempotencyEntry) {
        check(!entries.containsKey(entry.idempotencyKey)) {
            "Idempotency key already claimed"
        }
        entries[entry.idempotencyKey] = entry.copy(receipt = null)
        trimCompleted()
    }

    @Synchronized
    override fun complete(receipt: ConnectorCommandReceipt) {
        val existing = entries[receipt.idempotencyKey]
            ?: error("Idempotency key must be claimed before completion")
        entries[receipt.idempotencyKey] = existing.copy(receipt = receipt)
        trimCompleted()
    }

    private fun trimCompleted() {
        val limit = maxCompletedEntries.coerceAtLeast(1)
        while (entries.values.count { it.receipt != null } > limit) {
            val oldestCompleted = entries.entries.firstOrNull { it.value.receipt != null } ?: return
            entries.remove(oldestCompleted.key)
        }
    }
}

/**
 * Durable, non-secret command idempotency journal.
 *
 * A claim is synchronously committed before a domain write. If the process dies after
 * the claim but before a receipt is durably completed, the next delivery fails closed
 * instead of risking the same side effect twice.
 */
class SharedPreferencesConnectorIdempotencyStore(
    private val preferences: SharedPreferences,
    private val maxCompletedEntries: Int = 128,
    private val preferenceKey: String = "connector_idempotency_v1",
) : ConnectorIdempotencyStore {
    @Synchronized
    override fun get(idempotencyKey: String): ConnectorIdempotencyEntry? =
        loadEntries().firstOrNull { it.idempotencyKey == idempotencyKey }

    @Synchronized
    override fun begin(entry: ConnectorIdempotencyEntry) {
        val entries = loadEntries().toMutableList()
        check(entries.none { it.idempotencyKey == entry.idempotencyKey }) {
            "Idempotency key already claimed"
        }
        entries += entry.copy(receipt = null)
        persist(trimCompleted(entries))
    }

    @Synchronized
    override fun complete(receipt: ConnectorCommandReceipt) {
        val entries = loadEntries().toMutableList()
        val index = entries.indexOfFirst { it.idempotencyKey == receipt.idempotencyKey }
        check(index >= 0) { "Idempotency key must be claimed before completion" }
        entries[index] = entries[index].copy(receipt = receipt)
        persist(trimCompleted(entries))
    }

    private fun trimCompleted(values: List<ConnectorIdempotencyEntry>): List<ConnectorIdempotencyEntry> {
        val mutable = values.toMutableList()
        val limit = maxCompletedEntries.coerceAtLeast(1)
        while (mutable.count { it.receipt != null } > limit) {
            val index = mutable.indexOfFirst { it.receipt != null }
            if (index < 0) break
            mutable.removeAt(index)
        }
        return mutable
    }

    private fun loadEntries(): List<ConnectorIdempotencyEntry> {
        val raw = preferences.getString(preferenceKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val value = array.optJSONObject(index) ?: continue
                    value.decodeEntryOrNull()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(entries: List<ConnectorIdempotencyEntry>) {
        val array = JSONArray()
        entries.forEach { array.put(it.encode()) }
        check(preferences.edit().putString(preferenceKey, array.toString()).commit()) {
            "Failed to persist connector idempotency journal"
        }
    }

    private fun ConnectorIdempotencyEntry.encode(): JSONObject =
        JSONObject()
            .put("idempotency_key", idempotencyKey)
            .put("command_type", commandType)
            .put("state_version_before", stateVersionBefore)
            .apply { receipt?.let { put("receipt", it.encode()) } }

    private fun ConnectorCommandReceipt.encode(): JSONObject =
        JSONObject()
            .put("idempotency_key", idempotencyKey)
            .put("command_type", commandType)
            .put("status", status.name)
            .put("message", message)
            .put("state_version_before", stateVersionBefore)
            .put("state_version_after", stateVersionAfter)
            .put("duplicate", duplicate)
            .apply { actionId?.let { put("action_id", it) } }

    private fun JSONObject.decodeEntryOrNull(): ConnectorIdempotencyEntry? =
        runCatching {
            ConnectorIdempotencyEntry(
                idempotencyKey = getString("idempotency_key"),
                commandType = getString("command_type"),
                stateVersionBefore = getLong("state_version_before"),
                receipt = optJSONObject("receipt")?.decodeReceipt(),
            )
        }.getOrNull()

    private fun JSONObject.decodeReceipt(): ConnectorCommandReceipt =
        ConnectorCommandReceipt(
            idempotencyKey = getString("idempotency_key"),
            commandType = getString("command_type"),
            status = ConnectorCommandStatus.valueOf(getString("status")),
            message = getString("message"),
            stateVersionBefore = getLong("state_version_before"),
            stateVersionAfter = getLong("state_version_after"),
            actionId = optString("action_id").takeIf { it.isNotBlank() },
            duplicate = optBoolean("duplicate", false),
        )
}
