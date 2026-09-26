package com.nazofobi.arrivalalarm

import java.net.HttpURLConnection
import java.net.URI

/**
 * Minimal HTTPS transport for the device channel.
 *
 * Credentials are supplied at call time and are never persisted or logged by this class.
 * Production endpoints must be HTTPS.
 */
class HttpConnectorGatewayTransport(
    baseUrl: String,
    private val bearerTokenProvider: () -> String,
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 15_000,
) : ConnectorGatewayTransport {
    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/').also { value ->
        val uri = URI(value)
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Connector gateway must use HTTPS"
        }
        require(!uri.host.isNullOrBlank()) { "Connector gateway host is required" }
    }

    override fun publishSnapshot(payload: String) {
        request("POST", "/device/state", payload)
    }

    override fun fetchPendingCommands(): String =
        request("GET", "/device/commands", null)

    override fun publishReceipt(payload: String) {
        request("POST", "/device/receipt", payload)
    }

    private fun request(method: String, path: String, body: String?): String {
        val token = bearerTokenProvider().trim()
        require(token.isNotEmpty()) { "Connector device credential is unavailable" }

        val connection = URI(normalizedBaseUrl + path).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(body)
                }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                error("connector_http_$status")
            }
            response
        } finally {
            connection.disconnect()
        }
    }
}
