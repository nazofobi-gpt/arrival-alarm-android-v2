package com.nazofobi.arrivalalarm

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL

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
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
) : ConnectorGatewayTransport {
    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/').also { value ->
        // URI parse errors can echo the supplied URL, including embedded credentials.
        val uri = try {
            URI(value)
        } catch (_: Exception) {
            throw IllegalArgumentException("Connector gateway URL is invalid")
        }
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Connector gateway must use HTTPS"
        }
        require(!uri.host.isNullOrBlank()) { "Connector gateway host is required" }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "Connector gateway URL must not contain credentials, query or fragment"
        }
    }

    init {
        // A zero timeout means an unlimited network wait in HttpURLConnection.
        require(connectTimeoutMillis in 1..60_000) { "Connector connect timeout must be bounded" }
        require(readTimeoutMillis in 1..60_000) { "Connector read timeout must be bounded" }
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

        val connection = try {
            connectionFactory(URI(normalizedBaseUrl + path).toURL())
        } catch (_: SocketTimeoutException) {
            throw SocketTimeoutException("connector_timeout")
        } catch (_: IOException) {
            throw IOException("connector_network_error")
        }
        return try {
            // A gateway redirect must never forward the device bearer credential to
            // another endpoint (or silently switch to a different transport).
            connection.instanceFollowRedirects = false
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
        } catch (_: SocketTimeoutException) {
            // Do not propagate original IOException messages or causes: they can
            // contain private URLs, credentials and precise trip coordinates.
            throw SocketTimeoutException("connector_timeout")
        } catch (_: IOException) {
            throw IOException("connector_network_error")
        } finally {
            connection.disconnect()
        }
    }
}
