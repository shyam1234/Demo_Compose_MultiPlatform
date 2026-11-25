package com.ott.platform.data.repository

import com.ott.platform.data.cms.CmsCatalogRemoteDataSource
import com.ott.platform.data.cms.CmsLayout
import com.ott.platform.data.cms.CmsRail
import com.ott.platform.database.CachedLayout
import com.ott.platform.database.CatalogLocalDataSource
import com.ott.platform.domain.model.AppMenuItem
import com.ott.platform.domain.model.Category
import com.ott.platform.domain.model.Channel
import com.ott.platform.domain.model.ContentId
import com.ott.platform.domain.model.ContentShelf
import com.ott.platform.domain.model.HomeFeed
import com.ott.platform.domain.model.OttResult
import com.ott.platform.domain.model.RailContentType
import com.ott.platform.domain.model.ShelfCardStyle
import com.ott.platform.domain.repository.CatalogRepository
import com.ott.platform.logging.OttLogger
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalTime::class)
class CmsCatalogRepository(
    private val remote: CmsCatalogRemoteDataSource,
    private val logger: OttLogger,
    private val local: CatalogLocalDataSource? = null,
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : CatalogRepository {

    private val mutex = Mutex()
    private var cachedMenu: List<AppMenuItem>? = null
    private val layoutCache = LinkedHashMap<String, CmsLayout>()
    private val shelfCache = LinkedHashMap<String, ContentShelf>()
    private val channelIndex = LinkedHashMap<String, Channel>()

    override suspend fun invalidateCache() {
        mutex.withLock {
            cachedMenu = null
            layoutCache.clear()
            shelfCache.clear()
            channelIndex.clear()
        }
        remote.prepareForReconnect()
        logger.i(TAG, "Catalog memory cache invalidated for reconnect")
    }

    override suspend fun getMenu(): OttResult<List<AppMenuItem>> = runCatchingResult {
        mutex.withLock {
            cachedMenu?.let { return@withLock it }

            val dbMenu = local?.getMenuItems().orEmpty()
            try {
                // Always refresh from API when online; DB is offline-only.
                val fetched = remote.fetchMenu()
                if (fetched.isNotEmpty()) {
                    runCatching {
                        val pruned = syncMenuToLocal(fetched)
                        pruned.layoutIds.forEach { layoutCache.remove(it) }
                        pruned.railIds.forEach { shelfCache.remove(it) }
                    }.onFailure { logger.w(TAG, "Failed to persist menu: ${it.message}", it) }
                    cachedMenu = fetched
                    fetched
                } else if (dbMenu.isNotEmpty()) {
                    cachedMenu = dbMenu
                    remote.alignEffectiveDeviceTypeForCachedCatalog()
                    dbMenu
                } else {
                    emptyList()
                }
            } catch (t: Throwable) {
                if (dbMenu.isNotEmpty()) {
                    logger.w(TAG, "Offline fallback: serving ${dbMenu.size} cached menu items")
                    cachedMenu = dbMenu
                    remote.alignEffectiveDeviceTypeForCachedCatalog()
                    dbMenu
                } else {
                    throw t
                }
            }
        }
    }

    override suspend fun getCategories(): OttResult<List<Category>> = runCatchingResult {
        // Prefer live feed (network → synced DB) so removed rails disappear when online.
        when (val home = getHomeFeed()) {
            is OttResult.Success -> {
                val fromFeed = home.data.shelves.map { shelf ->
                    Category(
                        id = shelf.id,
                        name = shelf.title,
                        displayType = shelf.rawDisplayType,
                        contentType = shelf.rawContentType,
                    )
                }
                if (fromFeed.isNotEmpty()) fromFeed
                else local?.getCategories().orEmpty()
            }
            is OttResult.Error -> {
                val dbCategories = local?.getCategories().orEmpty()
                if (dbCategories.isNotEmpty()) dbCategories
                else throw home.cause ?: IllegalStateException(home.message)
            }
            OttResult.Loading -> local?.getCategories().orEmpty()
        }
    }

    override suspend fun getHomeFeed(limitPerShelf: Int): OttResult<HomeFeed> = runCatchingResult {
        val menuItems = when (val menuResult = getMenu()) {
            is OttResult.Success -> menuResult.data
            is OttResult.Error -> {
                logger.w(TAG, "Menu fetch failed (${menuResult.message}); no CMS home without menu")
                emptyList()
            }
            OttResult.Loading -> emptyList()
        }
        // Home is CMS-driven only — never invent a layout when the device menu is empty.
        val primaryLayoutId = menuItems.firstOrNull { !it.layoutId.isNullOrBlank() }?.layoutId
            ?: return@runCatchingResult HomeFeed()
        when (val feedResult = getLayoutFeed(primaryLayoutId, limitPerShelf)) {
            is OttResult.Success -> feedResult.data
            is OttResult.Error -> throw feedResult.cause ?: IllegalStateException(feedResult.message)
            OttResult.Loading -> HomeFeed()
        }
    }

    override suspend fun getLayoutFeed(layoutId: String, limitPerShelf: Int): OttResult<HomeFeed> =
        runCatchingResult {
            val layout = loadLayout(layoutId)
            var firstFailure: Throwable? = null
            val shelves = coroutineScope {
                layout.railIds.mapIndexed { index, railId ->
                    async {
                        runCatching {
                            loadRail(railId, sortOrder = index, limit = limitPerShelf)
                        }.onFailure { error ->
                            if (firstFailure == null) firstFailure = error
                            logger.w(TAG, "Skipping rail $railId: ${error.message}")
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
                    .filter { shelf -> remote.matchesDevice(shelf.deviceTypes) }
                    .filter { shelf -> shelf.items.isNotEmpty() || shelf.isDiscoveryDock }
            }

            if (shelves.isEmpty()) {
                firstFailure?.let { throw it }
            }

            val (heroes, contentShelves) = partitionHeroAndContentShelves(shelves)
            HomeFeed(
                heroes = heroes,
                shelves = contentShelves,
                layoutId = layout.id,
                layoutName = layout.name,
                themeUrl = layout.themeUrl,
            )
        }

    override suspend fun getChannelsByCategory(categoryId: String, limit: Int): OttResult<List<Channel>> =
        runCatchingResult {
            loadRail(categoryId, sortOrder = 0, limit = limit).items
        }

    override suspend fun search(query: String, limit: Int): OttResult<List<Channel>> = runCatchingResult {
        ensureSearchIndex()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@runCatchingResult emptyList()

        val fromDb = local?.searchChannels(trimmed, limit).orEmpty()
        if (fromDb.isNotEmpty()) {
            mutex.withLock {
                fromDb.forEach { channelIndex[it.id.value] = it }
            }
            return@runCatchingResult fromDb
        }

        val q = trimmed.lowercase()
        mutex.withLock {
            channelIndex.values
                .asSequence()
                .filter { channel ->
                    channel.title.lowercase().contains(q) ||
                        channel.categoryName.lowercase().contains(q) ||
                        channel.synopsis.lowercase().contains(q) ||
                        channel.genres.any { it.lowercase().contains(q) } ||
                        channel.tags.any { it.lowercase().contains(q) } ||
                        channel.cast.any { it.lowercase().contains(q) }
                }
                .distinctBy { it.id.value }
                .take(limit)
                .toList()
        }
    }

    override suspend fun getChannel(id: ContentId): OttResult<Channel> = runCatchingResult {
        mutex.withLock { channelIndex[id.value] }
            ?: local?.getChannel(id)?.also { cached ->
                mutex.withLock { channelIndex[cached.id.value] = cached }
            }
            ?: run {
                ensureSearchIndex()
                mutex.withLock { channelIndex[id.value] } ?: local?.getChannel(id)
            }
            ?: error("Content not found: ${id.value}")
    }

    private suspend fun ensureSearchIndex() {
        val memoryCount = mutex.withLock { channelIndex.size }
        val dbCount = local?.getChannelCount() ?: 0L
        if (memoryCount >= 10 || dbCount >= 10L) return

        val menuLayoutIds = when (val menuResult = getMenu()) {
            is OttResult.Success -> menuResult.data.flatMap { item ->
                listOfNotNull(item.layoutId) + item.children.mapNotNull { it.layoutId }
            }.distinct()
            else -> emptyList()
        }

        menuLayoutIds.forEach { layoutId ->
            runCatching { getLayoutFeed(layoutId, DEFAULT_RAIL_FETCH_LIMIT) }
                .onFailure { logger.w(TAG, "Search index skip for layout $layoutId: ${it.message}") }
        }
    }

    private suspend fun loadLayout(layoutId: String): CmsLayout {
        mutex.withLock {
            layoutCache[layoutId]?.let { return it }
        }

        val dbLayout = local?.getLayout(layoutId)
        return try {
            // Always refresh from API when online; DB is offline-only.
            val fetched = remote.fetchLayout(layoutId)
            runCatching {
                val pruned = syncLayoutToLocal(fetched)
                mutex.withLock {
                    pruned.railIds.forEach { shelfCache.remove(it) }
                }
            }.onFailure { logger.w(TAG, "Failed to persist layout $layoutId: ${it.message}", it) }
            mutex.withLock { layoutCache[layoutId] = fetched }
            fetched
        } catch (t: Throwable) {
            if (dbLayout != null && dbLayout.railIds.isNotEmpty()) {
                logger.w(TAG, "Offline fallback: serving cached layout $layoutId")
                val layout = CmsLayout(
                    id = dbLayout.id,
                    name = dbLayout.name,
                    themeUrl = dbLayout.themeUrl,
                    railIds = dbLayout.railIds,
                )
                mutex.withLock { layoutCache[layoutId] = layout }
                layout
            } else {
                throw t
            }
        }
    }

    private suspend fun loadRail(railId: String, sortOrder: Int, limit: Int): ContentShelf {
        mutex.withLock {
            shelfCache[railId]?.let { cached ->
                return cached.copy(items = cached.items.take(limit))
            }
        }

        val fetchLimit = limit.coerceAtLeast(DEFAULT_RAIL_FETCH_LIMIT)
        val dbCategory = local?.getCategory(railId)
        val dbItems = local?.getChannelsByCategory(railId, fetchLimit).orEmpty()

        return try {
            // Always refresh from API when online; replace local rail contents so removals stick.
            val fetchedRail: CmsRail = remote.fetchRail(railId)
            val category = Category(
                id = fetchedRail.id,
                name = fetchedRail.title,
                displayType = fetchedRail.rawDisplayType,
                contentType = fetchedRail.rawContentType,
                hasBackgroundImage = fetchedRail.hasBackgroundImage,
                backgroundImageUrl = fetchedRail.backgroundImageUrl,
                backgroundImageAspectRatio = fetchedRail.backgroundImageAspectRatio,
                deviceTypes = fetchedRail.deviceTypes,
            )
            runCatching {
                local?.upsertCategory(category, sortOrder)
                local?.replaceChannelsForCategory(railId, fetchedRail.items)
            }.onFailure { logger.w(TAG, "Failed to persist rail $railId: ${it.message}", it) }

            val shelf = enrichDiscoveryDock(
                fetchedRail.toContentShelf(limit = fetchedRail.items.size),
            )
            cacheShelfInMemory(railId, shelf)
            shelf.copy(items = shelf.items.take(limit))
        } catch (t: Throwable) {
            if (dbItems.isNotEmpty()) {
                logger.w(TAG, "Offline fallback: serving ${dbItems.size} cached items for rail $railId")
                val shelf = buildShelfFromDb(railId, dbCategory, dbItems)
                cacheShelfInMemory(railId, shelf)
                shelf.copy(items = shelf.items.take(limit))
            } else {
                throw t
            }
        }
    }

    /**
     * Persist menu and drop layouts/rails CMS no longer returns.
     * Does not touch in-memory caches (caller may already hold [mutex]).
     */
    private suspend fun syncMenuToLocal(fetched: List<AppMenuItem>): PrunedCatalogIds {
        val store = local ?: return PrunedCatalogIds()
        val previousLayoutIds = collectLayoutIds(store.getMenuItems())
        val nextLayoutIds = collectLayoutIds(fetched)
        store.replaceMenuItems(fetched)

        val removedLayoutIds = LinkedHashSet<String>()
        val removedRailIds = LinkedHashSet<String>()

        suspend fun purgeLayout(layoutId: String) {
            val layout = store.getLayout(layoutId)
            layout?.railIds?.forEach { railId ->
                store.deleteCategoryAndChannels(railId)
                removedRailIds += railId
            }
            store.deleteLayout(layoutId)
            removedLayoutIds += layoutId
        }

        for (layoutId in previousLayoutIds - nextLayoutIds) {
            purgeLayout(layoutId)
        }
        // Drop layout rows that were never tied to the current menu.
        for (layoutId in store.getAllLayoutIds() - nextLayoutIds) {
            purgeLayout(layoutId)
        }

        return PrunedCatalogIds(layoutIds = removedLayoutIds, railIds = removedRailIds)
    }

    /**
     * Persist layout and drop rails removed from this layout since the last sync.
     * Does not touch in-memory caches (caller clears [shelfCache] from the result).
     */
    private suspend fun syncLayoutToLocal(fetched: CmsLayout): PrunedCatalogIds {
        val store = local ?: return PrunedCatalogIds()
        val previous = store.getLayout(fetched.id)
        store.saveLayout(
            CachedLayout(
                id = fetched.id,
                name = fetched.name,
                themeUrl = fetched.themeUrl,
                railIds = fetched.railIds,
                updatedAtMs = nowMs(),
            ),
        )
        val removedRails = previous?.railIds.orEmpty().toSet() - fetched.railIds.toSet()
        val deletedRails = LinkedHashSet<String>()
        for (railId in removedRails) {
            // Keep the rail if another retained layout still references it.
            val stillReferenced = store.getAllLayoutIds().any { layoutId ->
                store.getLayout(layoutId)?.railIds?.contains(railId) == true
            }
            if (!stillReferenced) {
                store.deleteCategoryAndChannels(railId)
                deletedRails += railId
            }
        }
        return PrunedCatalogIds(railIds = deletedRails)
    }

    private fun collectLayoutIds(items: List<AppMenuItem>): Set<String> {
        val ids = LinkedHashSet<String>()
        fun walk(item: AppMenuItem) {
            item.layoutId?.takeIf { it.isNotBlank() }?.let { ids += it }
            item.children.forEach(::walk)
        }
        items.forEach(::walk)
        return ids
    }

    private data class PrunedCatalogIds(
        val layoutIds: Set<String> = emptySet(),
        val railIds: Set<String> = emptySet(),
    )

    /**
     * Discovery-dock nested payloads often omit assets for linked subrails.
     * Fill empty tabs by fetching each linked rail id (uses in-memory cache when available).
     */
    private suspend fun enrichDiscoveryDock(shelf: ContentShelf): ContentShelf {
        if (!shelf.isDiscoveryDock) return shelf
        val tabs = shelf.discoveryDockTabs.mapIndexed { index, tab ->
            if (!remote.matchesDevice(tab.deviceTypes)) return@mapIndexed null
            if (tab.items.isNotEmpty()) {
                cacheShelfInMemory(tab.id, tab)
                return@mapIndexed tab
            }
            runCatching {
                loadRail(tab.id, sortOrder = index, limit = DEFAULT_RAIL_FETCH_LIMIT)
            }.onFailure { error ->
                logger.w(TAG, "Discovery dock tab ${tab.id} failed: ${error.message}")
            }.getOrNull()
                ?.takeIf { remote.matchesDevice(it.deviceTypes) }
        }.filterNotNull()
        return shelf.copy(discoveryDockTabs = tabs)
    }

    private fun buildShelfFromDb(
        railId: String,
        dbCategory: Category?,
        items: List<Channel>,
    ): ContentShelf {
        val title = dbCategory?.name ?: items.firstOrNull()?.categoryName ?: railId
        val rawDisplay = dbCategory?.displayType ?: "Landscape"
        val rawContent = dbCategory?.contentType ?: "generic"
        return ContentShelf(
            id = railId,
            title = title,
            items = items,
            cardStyle = ShelfCardStyle.fromCms(rawDisplay, rawContent),
            contentType = RailContentType.fromCms(rawContent),
            rawDisplayType = rawDisplay,
            rawContentType = rawContent,
            hasBackgroundImage = dbCategory?.hasBackgroundImage == true,
            backgroundImageUrl = dbCategory?.backgroundImageUrl,
            backgroundImageAspectRatio = dbCategory?.backgroundImageAspectRatio,
            deviceTypes = dbCategory?.deviceTypes.orEmpty(),
        )
    }

    private suspend fun cacheShelfInMemory(railId: String, shelf: ContentShelf) {
        mutex.withLock {
            shelfCache[railId] = shelf
            shelf.items.forEach { channelIndex[it.id.value] = it }
            shelf.discoveryDockTabs.forEach { tab ->
                shelfCache[tab.id] = tab
                tab.items.forEach { channelIndex[it.id.value] = it }
            }
        }
    }

    /**
     * Hero banner is CMS-defined only (`rail_content_type=hero/featured` or Hero/Billboard display).
     * Never synthesize a hero from ordinary shelves.
     */
    private fun partitionHeroAndContentShelves(
        shelves: List<ContentShelf>,
    ): Pair<List<Channel>, List<ContentShelf>> {
        val heroShelves = shelves.filter { it.isHeroRail }
        val contentShelves = shelves.filterNot { it.isHeroRail }
        val heroes = heroShelves
            .flatMap { it.items }
            .distinctBy { it.id.value }
            .take(HERO_COUNT)
        return heroes to contentShelves
    }

    private suspend fun <T> runCatchingResult(block: suspend () -> T): OttResult<T> =
        try {
            OttResult.Success(block())
        } catch (t: Throwable) {
            logger.e(TAG, "CMS catalog error: ${t.message}", t)
            OttResult.Error(t.message ?: "Unknown catalog error", t)
        }

    private companion object {
        const val TAG = "CmsCatalogRepository"
        const val HERO_COUNT = 6
        const val DEFAULT_RAIL_FETCH_LIMIT = 40
        /** Retained for call-site compatibility; DB is offline-only (no TTL read-through). */
        const val DEFAULT_CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    }
}
