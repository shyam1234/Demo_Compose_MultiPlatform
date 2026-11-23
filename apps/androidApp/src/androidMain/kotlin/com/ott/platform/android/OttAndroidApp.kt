package com.ott.platform.android

import android.app.Application
import com.ott.platform.data.cms.CmsConfig
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

class OttAndroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val customer = runBlocking { BrandConfigLoader.loadActiveCustomer() }
        val brandCms = BrandConfigLoader.toCmsConfig(customer)
        startKoin {
            allowOverride(true)
            androidLogger(Level.ERROR)
            androidContext(this@OttAndroidApp)
            modules(
                coreModules() + featureModules() + customerModule(customer) + listOf(
                    androidPlayerModule,
                    module {
                        // Phone uses CMS android device type unless customer overrides/opt-outs.
                        single {
                            brandCms.copy(
                                menuDeviceType = brandCms.menuDeviceType.ifBlank { CmsDeviceType.ANDROID },
                                contentDeviceType = brandCms.contentDeviceType.ifBlank { CmsDeviceType.ANDROID },
                            )
                        }
                    },
                ),
            )
        }
    }
}
