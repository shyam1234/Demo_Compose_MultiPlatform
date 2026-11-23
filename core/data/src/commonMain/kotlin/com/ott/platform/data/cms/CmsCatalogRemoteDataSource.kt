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
    suspend fun fetchMenu(
        deviceType: String = config.menuDeviceType,
        languageCode: String = config.langCode,
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
        if (!config.matchesDevice(layout.deviceTypes)) {
            error("CMS layout $layoutId is not available for device ${config.contentDeviceType}")
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
            deviceType = config.contentDeviceType,
        )
        if (!config.matchesDevice(rail.deviceTypes)) {
            error("CMS rail $railId is not available for device ${config.contentDeviceType}")
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
