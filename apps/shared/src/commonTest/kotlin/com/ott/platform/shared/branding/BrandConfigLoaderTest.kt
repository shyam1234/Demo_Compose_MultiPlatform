package com.ott.platform.shared.branding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrandConfigLoaderTest {

    @Test
    fun `defaultCustomerId selects opted-in customer`() {
        val json = """
            {
              "defaultCustomerId": "acme",
              "customers": [
                {
                  "id": "teplay",
                  "enabled": true,
                  "packageName": "com.cmsdemo.teplay",
                  "branding": {
                    "brandName": "OTT",
                    "splash": { "type": "image", "source": "compose:splash_ott", "title": "OTT" }
                  }
                },
                {
                  "id": "acme",
                  "enabled": true,
                  "packageName": "com.cmsdemo.acme",
                  "branding": {
                    "brandName": "Acme",
                    "splash": {
                      "type": "gif",
                      "source": "https://example.com/splash.gif",
                      "posterSource": "compose:splash_ott",
                      "title": "Acme Stream",
                      "durationMs": 3500
                    }
                  },
                  "cms": { "deviceTypeOptOut": false, "deviceTypeOverride": "web" },
                  "debug": { "enabled": true }
                }
              ]
            }
        """.trimIndent()

        val active = BrandConfigLoader.resolveActiveCustomer(json)
        assertEquals("acme", active.id)
        assertEquals("Acme", active.brandName)
        assertEquals(SplashMediaType.Gif, active.splash.type)
        assertEquals("https://example.com/splash.gif", active.splash.source)
        assertEquals(3500, active.splash.durationMs)
        assertEquals("com.cmsdemo.acme", active.packageName)
        assertTrue(active.debug.enabled)
        assertEquals("web", active.cmsDeviceTypeOverride)
    }

    @Test
    fun `video splash type is preserved with poster fallback`() {
        val json = """
            {
              "defaultCustomerId": "demo_video_splash",
              "customers": [
                {
                  "id": "demo_video_splash",
                  "enabled": true,
                  "packageName": "com.cmsdemo.videodemo",
                  "branding": {
                    "brandName": "VideoDemo",
                    "splash": {
                      "type": "video",
                      "source": "https://example.com/splash.mp4",
                      "posterSource": "compose:splash_ott",
                      "durationMs": 5000,
                      "title": "VideoDemo"
                    }
                  },
                  "cms": { "deviceTypeOptOut": true },
                  "features": { "search": false }
                }
              ]
            }
        """.trimIndent()
        val active = BrandConfigLoader.resolveActiveCustomer(json)
        assertEquals(SplashMediaType.Video, active.splash.type)
        assertEquals("https://example.com/splash.mp4", active.splash.source)
        assertEquals("compose:splash_ott", active.splash.posterSource)
        assertTrue(active.cmsDeviceTypeOptOut)
        assertFalse(active.features.search)
    }

    @Test
    fun `disabled default falls back to first enabled customer`() {
        val json = """
            {
              "defaultCustomerId": "off",
              "customers": [
                { "id": "off", "enabled": false, "branding": { "brandName": "Off" } },
                { "id": "on", "enabled": true, "branding": { "brandName": "On" } }
              ]
            }
        """.trimIndent()
        val active = BrandConfigLoader.resolveActiveCustomer(json)
        assertEquals("on", active.id)
        assertEquals("On", active.brandName)
    }
}
