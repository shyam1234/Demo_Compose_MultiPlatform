package com.ott.platform.androidtv

import android.app.Application
import com.ott.platform.data.cms.CmsDeviceType
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

class OttTvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val customer = runBlocking { BrandConfigLoader.loadActiveCustomer() }
        val brandCms = BrandConfigLoader.toCmsConfig(customer)
        startKoin {
            allowOverride(true)
            androidLogger(Level.ERROR)
            androidContext(this@OttTvApp)
            modules(
                coreModules() + featureModules() + customerModule(customer) + listOf(
                    androidPlayerModule,
                    module {
                        // TV always targets androidtv unless customer forces an override.
                        val device = brandCms.menuDeviceType.takeIf {
                            it.isNotBlank() && it != CmsDeviceType.ANDROID
                        } ?: CmsDeviceType.ANDROID_TV
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
