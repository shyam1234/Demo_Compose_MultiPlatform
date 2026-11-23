package com.ott.platform.android

import android.app.Application
import com.ott.platform.data.cms.installAndroidCmsDeviceTypeContext
import com.ott.platform.data.cms.platformCmsMenuDeviceType
import com.ott.platform.di.coreModules
import com.ott.platform.platform.android.androidPlayerModule
import com.ott.platform.shared.branding.BrandConfigLoader
import com.ott.platform.shared.customerModule
import com.ott.platform.shared.featureModules
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module

class OttAndroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Must run before BrandConfigLoader.toCmsConfig() so tablets resolve as androidtv.
        installAndroidCmsDeviceTypeContext(this)
        val customer = runBlocking { BrandConfigLoader.loadActiveCustomer() }
        val brandCms = BrandConfigLoader.toCmsConfig(customer)
        val platformDefault = platformCmsMenuDeviceType() // android | androidtv (tablet)
        val customerChoseDevice = !customer.cmsDeviceTypeOverride.isNullOrBlank() ||
            !customer.cmsMenuDeviceType.isNullOrBlank()
        val device = if (customerChoseDevice) {
            brandCms.menuDeviceType.ifBlank { platformDefault }
        } else {
            platformDefault
        }
        startKoin {
            allowOverride(true)
            androidLogger(Level.ERROR)
            androidContext(this@OttAndroidApp)
            modules(
                coreModules() + featureModules() + customerModule(customer) + listOf(
                    androidPlayerModule,
                    module {
                        // Phone → android; tablet → androidtv (unless customer config overrides).
                        single {
                            brandCms.copy(
                                menuDeviceType = device,
                                contentDeviceType = device,
                            )
                        }
                    },
                ),
            )
        }
    }
}
