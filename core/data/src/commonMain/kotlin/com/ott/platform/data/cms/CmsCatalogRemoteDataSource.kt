package com.ott.platform.data.cms

import com.ott.platform.domain.model.AppMenuItem
import com.ott.platform.logging.OttLogger
import com.ott.platform.network.OttJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

class CmsCatalogRemoteDataSource(
    private val httpClient: HttpClient,
    private val authManager: CmsAuthManager,
    private val logger: OttLogger,
    private val config: CmsConfig = CmsConfig(),
) {
    /**
     * Device type used for content filtering / image mapping after the last successful menu load.
     * Updated when menu falls back so rails match the catalog that actually loaded.
     */
    var effectiveDeviceType: String = config.menuDeviceType
        private set

    fun matchesDevice(allowedDevices: List<String>): Boolean =
        config.deviceTypeOptOut || CmsDeviceType.matches(allowedDevices, effectiveDeviceType)

    /**
     * Loads the CMS menu for [deviceType], then retries with the platform fallback
     * ([platformCmsMenuFallbackDeviceType]) when the primary call errors or returns empty.
     */
    suspend fun fetchMenu(
        deviceType: String = config.menuDeviceType,
        languageCode: String = config.langCode,
    ): List<AppMenuItem> {
        val primaryError: Throwable? = try {
            val primary = fetchMenuOnce(deviceType = deviceType, languageCode = languageCode)
            if (primary.isNotEmpty()) {
                effectiveDeviceType = deviceType
                return primary
            }
            null
        } catch (t: Throwable) {
            t
        }

        val fallback = platformCmsMenuFallbackDeviceType()
        if (CmsDeviceType.sameDevice(fallback, deviceType)) {
            primaryError?.let { throw it }
            logger.i(TAG, "No CMS menu for deviceType=$deviceType (fallback equals primary)")
            return emptyList()
        }

        if (primaryError != null) {
            logger.w(
                TAG,
                "Menu failed for deviceType=$deviceType (${primaryError.message}); " +
                    "retrying with fallback=$fallback",
            )
        } else {
            logger.i(
                TAG,
                "Menu empty for deviceType=$deviceType; retrying with fallback=$fallback",
            )
        }

        val fallbackItems = fetchMenuOnce(deviceType = fallback, languageCode = languageCode)
        if (fallbackItems.isNotEmpty()) {
            effectiveDeviceType = fallback
        }
        return fallbackItems
    }

    private suspend fun fetchMenuOnce(
        deviceType: String,
        languageCode: String,
    ): List<AppMenuItem> {
        val url = config.menuUrl(deviceType = deviceType, languageCode = languageCode)
        logger.d(TAG, "Fetching CMS menu: $url")
        val token = authManager.getAccessToken()
        var response = performGet(url, token)
        if (response.status == HttpStatusCode.Unauthorized) {
            logger.w(TAG, "CMS returned 401 for $url; refreshing access token and retrying")
            authManager.invalidateToken()
            val refreshedToken = authManager.getAccessToken(forceRefresh = true)
            response = performGet(url, refreshedToken)
        }
        val body = response.bodyAsText()
        // CMS returns 207 Multi-Status with {"message":"Hamberger Menu not found"} when
        // no menu exists for the requested device type — treat as empty, not fatal.
        if (response.status.value == 207 || body.contains("Hamberger Menu not found")) {
            logger.i(TAG, "No CMS menu for deviceType=$deviceType")
            return emptyList()
        }
        if (!response.status.isSuccess()) {
            error("CMS request failed (${response.status.value}) for $url: ${body.take(200)}")
        }
        val dto = OttJson.instance.decodeFromString<CmsMenuResponseDto>(body)
        return CmsCatalogMapper.mapMenu(
            response = dto,
            baseUrl = config.baseUrl,
            deviceType = deviceType,
        )
    }

    suspend fun fetchLayout(layoutId: String): CmsLayout {
        val url = config.layoutUrl(layoutId)
        logger.d(TAG, "Fetching CMS layout: $url")
        val body = authenticatedGetText(url)
        val dto = OttJson.instance.decodeFromString<CmsLayoutResponseDto>(body)
        val layout = CmsCatalogMapper.mapLayout(dto, fallbackLayoutId = layoutId, baseUrl = config.baseUrl)
        if (!matchesDevice(layout.deviceTypes)) {
            error("CMS layout $layoutId is not available for device $effectiveDeviceType")
        }
        return layout
    }

    suspend fun fetchRail(railId: String): CmsRail {
        val url = config.railUrl(railId)
        logger.d(TAG, "Fetching CMS rail: $url")
        val body = authenticatedGetText(url)
        val dto = OttJson.instance.decodeFromString<CmsRailsResponseDto>(body)
        val railDto = dto.results.firstOrNull()
            ?: error("CMS rail $railId returned empty results")
        val rail = CmsCatalogMapper.mapRail(
            dto = railDto,
            fallbackRailId = railId,
            baseUrl = config.baseUrl,
            deviceType = effectiveDeviceType,
            fallbackStreamUrl = config.defaultPlaybackUrl,
        )
        if (!matchesDevice(rail.deviceTypes)) {
            error("CMS rail $railId is not available for device $effectiveDeviceType")
        }
        return rail
    }

    private suspend fun authenticatedGetText(url: String): String {
        val token = authManager.getAccessToken()
        var response = performGet(url, token)
        if (response.status == HttpStatusCode.Unauthorized) {
            logger.w(TAG, "CMS returned 401 for $url; refreshing access token and retrying")
            authManager.invalidateToken()
            val refreshedToken = authManager.getAccessToken(forceRefresh = true)
            response = performGet(url, refreshedToken)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("CMS request failed (${response.status.value}) for $url: ${body.take(200)}")
        }
        return body
    }

    private suspend fun performGet(url: String, token: String): HttpResponse =
        httpClient.get(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

    private companion object {
        const val TAG = "CmsRemote"
    }
}
