package com.ott.platform.domain.usecase

import com.ott.platform.domain.repository.CatalogRepository

class InvalidateCatalogCacheUseCase(
    private val catalogRepository: CatalogRepository,
) {
    suspend operator fun invoke() {
        catalogRepository.invalidateCache()
    }
}
