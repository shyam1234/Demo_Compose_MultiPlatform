package com.ott.platform.shared.branding

import com.ott.platform.data.cms.CmsConfig
import com.ott.platform.data.cms.platformCmsMenuDeviceType
import com.ott.platform.shared.generated.resources.Res
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

private val brandJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

object BrandConfigLoader {

    /**
     * Loads `files/config.json` from Compose resources and resolves the default customer.
     * Any failure returns [ActiveCustomerConfig.demoDefault] so the app keeps working.
     */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadActiveCustomer(
        overrideCustomerId: String? = null,
    ): ActiveCustomerConfig {
        return runCatching {
            val bytes = Res.readBytes("files/config.json")
            val text = bytes.decodeToString()
            resolveActiveCustomer(text, overrideCustomerId)
        }.getOrElse {
            ActiveCustomerConfig.demoDefault()
        }
    }

    fun resolveActiveCustomer(
        jsonText: String,
        overrideCustomerId: String? = null,
    ): ActiveCustomerConfig {
        val file = brandJson.decodeFromString<AppConfigFile>(jsonText)
        val requested = overrideCustomerId?.takeIf { it.isNotBlank() } ?: file.defaultCustomerId
        val customer = file.customers.firstOrNull { it.id == requested && it.enabled }
            ?: file.customers.firstOrNull { it.enabled }
            ?: return ActiveCustomerConfig.demoDefault()
        return customer.toActive()
    }

    fun toCmsConfig(customer: ActiveCustomerConfig): CmsConfig {
        val platformDevice = platformCmsMenuDeviceType()
        val device = customer.cmsDeviceTypeOverride?.takeIf { it.isNotBlank() }
            ?: customer.cmsMenuDeviceType?.takeIf { it.isNotBlank() }
            ?: platformDevice
        return CmsConfig(
            baseUrl = customer.cmsBaseUrl?.takeIf { it.isNotBlank() } ?: CmsConfig.DEFAULT_BASE_URL,
            menuDeviceType = device,
            contentDeviceType = device,
            deviceTypeOptOut = customer.cmsDeviceTypeOptOut,
            fallbackLayoutId = customer.cmsFallbackLayoutId?.takeIf { it.isNotBlank() }
                ?: CmsConfig.DEFAULT_FALLBACK_LAYOUT_ID,
            defaultPlaybackUrl = customer.cmsDefaultPlaybackUrl?.takeIf { it.isNotBlank() }
                ?: CmsConfig.DEFAULT_PLAYBACK_URL,
        )
    }
}
