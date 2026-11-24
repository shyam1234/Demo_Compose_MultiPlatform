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

    /**
     * Content may be tagged for a fallback catalog (e.g. phone loads `androidtv` menu while
     * primary is `androidmobile`). Accept any candidate device type for the current form factor.
     */
    fun matchesDevice(allowedDevices: List<String>): Boolean {
        if (config.deviceTypeOptOut) return true
        val isPhone = CmsDeviceType.sameDevice(
            platformCmsMenuFallbackDeviceType(),
            CmsDeviceType.ANDROID_MOBILE,
        )
        val candidates = CmsDeviceType.menuDeviceTypeCandidates(
            primary = effectiveDeviceType,
            isPhone = isPhone,
        )
        return candidates.any { CmsDeviceType.matches(allowedDevices, it) }
    }

    /** Clear auth + device state so a retry after reconnect does a fresh login/menu resolve. */
    suspend fun prepareForReconnect() {
        authManager.invalidateToken()
        effectiveDeviceType = config.menuDeviceType
    }

    /**
     * When serving a cached menu without re-running [fetchMenu], phones must still accept
     * `androidtv`-tagged rails (mobile CMS menu is often empty / stub-only).
     */
    fun alignEffectiveDeviceTypeForCachedCatalog() {
        val isPhone = CmsDeviceType.sameDevice(
            platformCmsMenuFallbackDeviceType(),
            CmsDeviceType.ANDROID_MOBILE,
        )
        if (isPhone) {
            effectiveDeviceType = CmsDeviceType.ANDROID_TV
        }
    }

    /**
     * Soft device gate for rails/layouts: mismatch is skipped by callers instead of failing
     * the whole home feed after a reconnect.
     */
    fun requireDeviceMatchOrThrow(allowedDevices: List<String>, label: String) {
        if (matchesDevice(allowedDevices)) return
        error("$label is not available for device $effectiveDeviceType")
    }

    /**
     * Loads the CMS menu, trying [deviceType] then platform fallbacks until a non-empty menu
     * is returned. Phones always include [CmsDeviceType.ANDROID_MOBILE], then [CmsDeviceType.ANDROID_TV]
     * if the mobile menu exists but has no L1 items (current iPhone CMS gap).
     */
    suspend fun fetchMenu(
        deviceType: String = config.menuDeviceType,
        languageCode: String = config.langCode,
    ): List<AppMenuItem> {
        val phoneFallback = CmsDeviceType.ANDROID_MOBILE
        val isPhone = CmsDeviceType.sameDevice(
            platformCmsMenuFallbackDeviceType(),
            phoneFallback,
        )
        val candidates = CmsDeviceType.menuDeviceTypeCandidates(
            primary = deviceType,
            isPhone = isPhone,
        )
        var lastError: Throwable? = null
        for ((index, candidate) in candidates.withIndex()) {
            try {
                val items = fetchMenuOnce(deviceType = candidate, languageCode = languageCode)
                if (items.isNotEmpty()) {
                    if (index > 0) {
                        logger.i(TAG, "Menu loaded via fallback deviceType=$candidate")
                    }
                    effectiveDeviceType = candidate
                    return items
                }
                logger.i(TAG, "No usable CMS menu items for deviceType=$candidate")
            } catch (t: Throwable) {
                lastError = t
                logger.w(
                    TAG,
                    "Menu failed for deviceType=$candidate (${t.message})" +
                        if (index < candidates.lastIndex) "; trying next fallback" else "",
                )
            }
        }
        lastError?.let { throw it }
        return emptyList()
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
        requireDeviceMatchOrThrow(layout.deviceTypes, "CMS layout $layoutId")
        return layout
    }

    suspend fun fetchRail(railId: String): CmsRail {
        val url = config.railUrl(railId)
        logger.d(TAG, "Fetching CMS rail: $url")
        val body = authenticatedGetText(url)
        val dto = OttJson.instance.decodeFromString<CmsRailsResponseDto>(body)
        val railDto = dto.results.firstOrNull()
            ?: error("CMS rail $railId returned empty results")
        // Prefer TV image variants when phone catalog fell back to androidtv.
        val mappingDevice = if (
            CmsDeviceType.sameDevice(platformCmsMenuFallbackDeviceType(), CmsDeviceType.ANDROID_MOBILE)
        ) {
            CmsDeviceType.ANDROID_TV
        } else {
            effectiveDeviceType
        }
        val rail = CmsCatalogMapper.mapRail(
            dto = railDto,
            fallbackRailId = railId,
            baseUrl = config.baseUrl,
            deviceType = mappingDevice,
            fallbackStreamUrl = config.defaultPlaybackUrl,
        )
        requireDeviceMatchOrThrow(rail.deviceTypes, "CMS rail $railId")
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
