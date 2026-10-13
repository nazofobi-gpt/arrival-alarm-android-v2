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
        check(existing.receipt == null) { "Idempotency claim already completed" }
        check(existing.commandType == receipt.commandType &&
            existing.stateVersionBefore == receipt.stateVersionBefore
        ) { "Idempotency receipt does not match claim" }
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
        val existing = entries[index]
        // Never rewrite a completed receipt or attach a result to another command.
        check(existing.receipt == null) { "Idempotency claim already completed" }
        check(existing.commandType == receipt.commandType &&
            existing.stateVersionBefore == receipt.stateVersionBefore
        ) { "Idempotency receipt does not match claim" }
        entries[index] = existing.copy(receipt = receipt)
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
        // Never discard an unreadable claim: a replay could duplicate a command
        // whose side effect happened before its receipt was persisted.
        return try {
            val array = JSONArray(raw)
            val seenKeys = mutableSetOf<String>()
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val entry = array.getJSONObject(index).decodeEntry()
                    check(entry.idempotencyKey.isNotBlank() && entry.commandType.isNotBlank())
                    check(entry.stateVersionBefore >= 0L)
                    check(seenKeys.add(entry.idempotencyKey))
                    add(entry)
                }
            }
        } catch (_: Exception) {
            // Do not include journal content (possibly sensitive) in diagnostics.
            throw IllegalStateException("Corrupt connector idempotency journal")
        }
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

    private fun JSONObject.decodeEntry(): ConnectorIdempotencyEntry {
        // A present-but-malformed receipt is not equivalent to a pending claim.
        val storedReceipt = if (has("receipt")) getJSONObject("receipt").decodeReceipt() else null
        val entry = ConnectorIdempotencyEntry(
            idempotencyKey = strictString("idempotency_key"),
            commandType = strictString("command_type"),
            stateVersionBefore = strictLong("state_version_before"),
            receipt = storedReceipt,
        )
        check(storedReceipt == null || (
            storedReceipt.idempotencyKey == entry.idempotencyKey &&
                storedReceipt.commandType == entry.commandType &&
                storedReceipt.stateVersionBefore == entry.stateVersionBefore
            ))
        return entry
    }


    // org.json getters coerce native booleans, numbers and numeric strings.
    // Durable idempotency evidence must be parsed without such coercion.
    private fun JSONObject.strictString(field: String): String =
        get(field) as? String ?: error("Invalid connector journal string field")

    private fun JSONObject.strictLong(field: String): Long {
        val value = get(field)
        check(value is Int || value is Long) { "Invalid connector journal integer field" }
        return (value as Number).toLong()
    }

    private fun JSONObject.strictBoolean(field: String): Boolean =
        get(field) as? Boolean ?: error("Invalid connector journal boolean field")

    private fun JSONObject.decodeReceipt(): ConnectorCommandReceipt =
        ConnectorCommandReceipt(
            idempotencyKey = strictString("idempotency_key"),
            commandType = strictString("command_type"),
            status = ConnectorCommandStatus.valueOf(strictString("status")),
            message = strictString("message"),
            stateVersionBefore = strictLong("state_version_before"),
            stateVersionAfter = strictLong("state_version_after"),
            actionId = if (has("action_id")) strictString("action_id").also { check(it.isNotBlank()) } else null,
            duplicate = if (has("duplicate")) strictBoolean("duplicate") else false,
        )
}
