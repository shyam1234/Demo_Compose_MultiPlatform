package com.ott.platform.feature.home.impl

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val homeModule = module {
    viewModel { params ->
        val layoutId = params.values.firstOrNull() as? String
        HomeViewModel(
            initialLayoutId = layoutId,
            getHomeFeed = get(),
            getLayoutFeed = get(),
            invalidateCatalogCache = get(),
        )
    }
}
