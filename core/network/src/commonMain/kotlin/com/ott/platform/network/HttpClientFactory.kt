package com.ott.platform.network

import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

object OttJson {
    val instance = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }
}

/** Shared API timeout (connect / request / socket) for every platform. */
const val OTT_API_TIMEOUT_MS: Long = 10_000L

expect fun createOttHttpClient(): HttpClient
