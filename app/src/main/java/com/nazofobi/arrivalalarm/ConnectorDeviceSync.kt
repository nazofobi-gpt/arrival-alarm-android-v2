package com.nazofobi.arrivalalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ConnectorRemoteCommandEnvelope(
    val commandId: String,
    val expectedStateVersion: Long,
    val userConfirmed: Boolean,
    val command: ArrivalAlarmConnectorCommand,
)

data class ConnectorSyncResult(
    val pushedStateVersion: Long,
    val commandsReceived: Int,
    val commandsApplied: Int,
    val receiptsPosted: Int,
)

interface ConnectorRemoteTransport {
    fun pushSnapshot(snapshotJson: String)
    fun fetchCommands(): String
    fun pushReceipt(receiptJson: String)
}

interface ConnectorCredentialProvider {
    fun bearerToken(): String?
}

class SharedPreferencesConnectorReceiptStore(context: Context) : ConnectorReceiptStore {
    private val prefs = context.getSharedPreferences("connector_receipts", Context.MODE_PRIVATE)

    override fun get(idempotencyKey: String): ConnectorCommandReceipt? {
        val raw = prefs.getString(idempotencyKey, null) ?: return null
        return runCatching { decodeReceipt(JSONObject(raw)) }.getOrNull()
    }

    override fun put(receipt: ConnectorCommandReceipt) {
        prefs.edit().putString(receipt.idempotencyKey, ConnectorJsonCodec.encodeReceipt(receipt)).apply()
    }

    private fun decodeReceipt(root: JSONObject) = ConnectorCommandReceipt(
        idempotencyKey = root.getString("idempotency_key"),
        commandType = root.getString("command_type"),
        status = ConnectorCommandStatus.valueOf(root.getString("status")),
        message = root.getString("message"),
        stateVersionBefore = root.getLong("state_version_before"),
        stateVersionAfter = root.getLong("state_version_after"),
        actionId = root.optString("action_id").takeIf { it.isNotBlank() },
        duplicate = root.optBoolean("duplicate", false),
    )
}

class HttpConnectorRemoteTransport(
    baseUrl: String,
    private val credentialProvider: ConnectorCredentialProvider,
    private val connectTimeoutMs: Int = 6_000,
    private val readTimeoutMs: Int = 8_000,
    allowLocalHttpForDevelopment: Boolean = false,
) : ConnectorRemoteTransport {
    private val base = baseUrl.trimEnd('/').also {
        val url = URL(it)
        val localHttp = allowLocalHttpForDevelopment &&
            url.protocol == "http" &&
            (url.host == "127.0.0.1" || url.host == "localhost" || url.host == "10.0.2.2")
        require(url.protocol == "https" || localHttp) {
            "Connector gateway must use HTTPS outside explicit local development"
        }
    }

    override fun pushSnapshot(snapshotJson: String) {
        request("POST", "/device/state", snapshotJson)
    }

    override fun fetchCommands(): String = request("GET", "/device/commands", null)

    override fun pushReceipt(receiptJson: String) {
        request("POST", "/device/receipt", receiptJson)
    }

    private fun request(method: String, path: String, body: String?): String {
        val token = credentialProvider.bearerToken()?.takeIf { it.isNotBlank() }
            ?: error("Connector account/device is not linked")
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer " + token)
            setRequestProperty("User-Agent", "ArrivalAlarmAndroid/0.1")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            if (body != null) {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                error("Connector gateway HTTP " + code + if (response.isBlank()) "" else ": " + response)
            }
            response
        } finally {
            connection.disconnect()
        }
    }
}

class ConnectorDeviceSyncEngine(
    private val processor: ConnectorCommandProcessor,
    private val remote: ConnectorRemoteTransport,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
) {
    fun syncOnce(): ConnectorSyncResult {
        val initial = processor.snapshot()
        remote.pushSnapshot(ConnectorJsonCodec.encodeSnapshot(initial))

        val commands = decodeEnvelopes(remote.fetchCommands())
        var applied = 0
        var receipts = 0

        commands.forEach { envelope ->
            val receipt = processor.execute(
                command = envelope.command,
                userConfirmed = envelope.userConfirmed,
                expectedStateVersion = envelope.expectedStateVersion,
                nowEpochSeconds = nowEpochSeconds(),
            )
            if (receipt.status == ConnectorCommandStatus.APPLIED) applied++
            remote.pushReceipt(ConnectorJsonCodec.encodeReceipt(receipt))
            receipts++
        }

        val finalSnapshot = processor.snapshot()
        if (finalSnapshot.stateVersion != initial.stateVersion) {
            remote.pushSnapshot(ConnectorJsonCodec.encodeSnapshot(finalSnapshot))
        }

        return ConnectorSyncResult(
            pushedStateVersion = finalSnapshot.stateVersion,
            commandsReceived = commands.size,
            commandsApplied = applied,
            receiptsPosted = receipts,
        )
    }

    internal fun decodeEnvelopes(json: String): List<ConnectorRemoteCommandEnvelope> {
        val root = JSONObject(json)
        val values = root.optJSONArray("commands") ?: JSONArray()
        return buildList {
            for (index in 0 until values.length()) {
                val item = values.optJSONObject(index) ?: continue
                if (item.optString("status") != "queued") continue
                val commandObject = item.optJSONObject("command") ?: continue
                val command = ConnectorJsonCodec.decodeCommand(commandObject.toString())
                add(
                    ConnectorRemoteCommandEnvelope(
                        commandId = item.getString("command_id"),
                        expectedStateVersion = item.getLong("expected_state_version"),
                        userConfirmed = item.optBoolean("user_confirmed", false),
                        command = command,
                    )
                )
            }
        }
    }
}
