package com.ott.platform.shared

import com.ott.platform.data.cms.CmsConfig
import com.ott.platform.di.coreModules
import com.ott.platform.feature.details.impl.detailsModule
import com.ott.platform.feature.home.impl.homeModule
import com.ott.platform.feature.player.impl.playerFeatureModule
import com.ott.platform.feature.search.impl.searchModule
import com.ott.platform.feature.watchlist.impl.watchlistModule
import com.ott.platform.shared.branding.ActiveCustomerConfig
import com.ott.platform.shared.branding.BrandConfigLoader
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

private val sharedAppModule = module {
    viewModelOf(::AppMenuViewModel)
}

/**
 * @param customer active white-label profile from `config/config.json`
 *                 (defaults preserve current OTT / TE Play behavior)
 */
fun initKoin(
    extraModules: List<Module> = emptyList(),
    customer: ActiveCustomerConfig = ActiveCustomerConfig.demoDefault(),
) {
    startKoin {
        allowOverride(true)
        modules(
            coreModules() +
                featureModules() +
                customerModule(customer) +
                extraModules,
        )
    }
}

fun customerModule(customer: ActiveCustomerConfig): Module = module {
    single { customer }
    single<CmsConfig> { BrandConfigLoader.toCmsConfig(customer) }
}

fun featureModules(): List<Module> = listOf(
    sharedAppModule,
    homeModule,
    searchModule,
    detailsModule,
    playerFeatureModule,
    watchlistModule,
)
