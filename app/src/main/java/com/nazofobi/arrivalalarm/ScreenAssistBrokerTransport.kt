package com.nazofobi.arrivalalarm

import java.net.HttpURLConnection
import java.net.URL

class ScreenAssistBrokerTransport(
    private val brokerUrl: String,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 5_000,
    private val connectionFactory: (URL) -> HttpURLConnection = { url -> url.openConnection() as HttpURLConnection },
) {
    init {
        require(brokerUrl.startsWith("https://"))
        require(connectTimeoutMs in 1..30_000)
        require(readTimeoutMs in 1..30_000)
    }

    fun requestTokenResponse(): String {
        val connection = connectionFactory(URL(brokerUrl))
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            check(code in 200..299) { "Broker request failed: HTTP $code" }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                .also { check(it.isNotBlank()) { "Broker returned an empty response" } }
        } finally {
            connection.disconnect()
        }
    }
}
