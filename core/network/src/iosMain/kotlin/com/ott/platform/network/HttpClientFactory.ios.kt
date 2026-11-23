package com.ott.platform.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json

actual fun createOttHttpClient(): HttpClient = HttpClient(Darwin) {
    engine {
        val timeoutSeconds = OTT_API_TIMEOUT_MS / 1000.0
        configureRequest {
            setTimeoutInterval(timeoutSeconds)
        }
        configureSession {
            timeoutIntervalForRequest = timeoutSeconds
            timeoutIntervalForResource = timeoutSeconds
        }
    }
    install(ContentNegotiation) { json(OttJson.instance) }
    install(HttpTimeout) {
        requestTimeoutMillis = OTT_API_TIMEOUT_MS
        connectTimeoutMillis = OTT_API_TIMEOUT_MS
        socketTimeoutMillis = OTT_API_TIMEOUT_MS
    }
    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) = println("Ktor: $message")
        }
        level = LogLevel.INFO
    }
}
