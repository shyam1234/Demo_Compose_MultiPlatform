package com.ott.platform.di

import com.ott.platform.data.cms.CmsAuthManager
import com.ott.platform.data.cms.CmsCatalogRemoteDataSource
import com.ott.platform.data.cms.CmsConfig
import com.ott.platform.data.repository.CmsCatalogRepository
import com.ott.platform.data.repository.PersistentWatchlistRepository
import com.ott.platform.database.coreDatabaseModule
import com.ott.platform.domain.playback.PlayerController
import com.ott.platform.domain.playback.PlayerEngine
import com.ott.platform.domain.repository.CatalogRepository
import com.ott.platform.domain.repository.WatchlistRepository
import com.ott.platform.domain.usecase.GetChannelDetailsUseCase
import com.ott.platform.domain.usecase.GetHomeFeedUseCase
import com.ott.platform.domain.usecase.GetLayoutFeedUseCase
import com.ott.platform.domain.usecase.GetMenuUseCase
import com.ott.platform.domain.usecase.SearchChannelsUseCase
import com.ott.platform.domain.usecase.ToggleWatchlistUseCase
import com.ott.platform.logging.OttLogger
import com.ott.platform.logging.StdOutLogger
import com.ott.platform.network.createOttHttpClient
import com.ott.platform.testing.FakePlayerEngine
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val coreLoggingModule = module {
    single<OttLogger> { StdOutLogger }
}

val coreNetworkModule = module {
    single { createOttHttpClient() }
}

val coreDataModule = module {
    // Apps may override with a device-specific CmsConfig (e.g. androidtv vs android).
    single { CmsConfig() }
    single { CmsAuthManager(httpClient = get(), logger = get(), config = get()) }
    single {
        CmsCatalogRemoteDataSource(
            httpClient = get(),
            authManager = get(),
            logger = get(),
            config = get(),
        )
    }
    single {
        CmsCatalogRepository(
            remote = get(),
            logger = get(),
            local = get(),
            config = get(),
        )
    } bind CatalogRepository::class
    singleOf(::PersistentWatchlistRepository) bind WatchlistRepository::class
}

val coreDomainModule = module {
    factoryOf(::GetMenuUseCase)
    factoryOf(::GetHomeFeedUseCase)
    factoryOf(::GetLayoutFeedUseCase)
    factoryOf(::SearchChannelsUseCase)
    factoryOf(::GetChannelDetailsUseCase)
    factoryOf(::ToggleWatchlistUseCase)
}

/**
 * Demo default: fake engine works on all CMP targets.
 * Platform apps override with Media3 / AVPlayer / HTML5 video via platformPlayerModule.
 */
val corePlayerModule = module {
    single<PlayerEngine> { FakePlayerEngine() }
    single { PlayerController(get()) }
}

fun coreModules(): List<Module> = listOf(
    coreLoggingModule,
    coreDatabaseModule,
    coreNetworkModule,
    coreDataModule,
    coreDomainModule,
    corePlayerModule,
)
