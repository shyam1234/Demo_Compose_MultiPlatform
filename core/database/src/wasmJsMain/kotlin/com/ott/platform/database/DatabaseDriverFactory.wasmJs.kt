package com.ott.platform.database

import com.ott.platform.domain.model.AppMenuItem
import com.ott.platform.domain.model.Category
import com.ott.platform.domain.model.Channel
import com.ott.platform.domain.model.ContentId
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val databaseIoDispatcher: CoroutineDispatcher = Dispatchers.Default

internal actual fun createDatabaseModule(): Module = module {
    single<CatalogLocalDataSource> { WebCatalogLocalDataSource() }
    single<WatchlistLocalDataSource> { WebWatchlistLocalDataSource() }
}

private val webJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
private data class StoredMenuBundle(
    val updatedAtMs: Long,
    val items: List<StoredMenuItem>,
)

@Serializable
private data class StoredLayoutRecord(
    val id: String,
    val name: String,
    val themeUrl: String? = null,
    val railIds: List<String> = emptyList(),
    val updatedAtMs: Long = 0L,
)

@Serializable
private data class StoredCategory(
    val id: String,
    val name: String,
    val description: String = "",
    val displayType: String = "Landscape",
    val contentType: String = "generic",
    val hasBackgroundImage: Boolean = false,
    val backgroundImageUrl: String? = null,
    val deviceTypes: List<String> = emptyList(),
)

@Serializable
private data class StoredCategoriesBundle(
    val updatedAtMs: Long,
    val categories: List<StoredCategory>,
)

@Serializable
private data class StoredChannel(
    val id: String,
    val title: String,
    val logoUrl: String? = null,
    val streamUrl: String,
    val categoryId: String,
    val categoryName: String,
    val groupTitle: String = categoryName,
    val isGeoBlocked: Boolean = false,
    val isNot247: Boolean = false,
    val quality: String? = null,
    val tvgId: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val metadataJson: String = "",
)

@Serializable
private data class StoredChannelShelf(
    val updatedAtMs: Long,
    val channels: List<StoredChannel>,
)

private fun Category.toStored() = StoredCategory(
    id = id,
    name = name,
    description = description,
    displayType = displayType,
    contentType = contentType,
    hasBackgroundImage = hasBackgroundImage,
    backgroundImageUrl = backgroundImageUrl,
    deviceTypes = deviceTypes,
)

private fun StoredCategory.toDomain() = Category(
    id = id,
    name = name,
    description = description,
    displayType = displayType,
    contentType = contentType,
    hasBackgroundImage = hasBackgroundImage,
    backgroundImageUrl = backgroundImageUrl,
    deviceTypes = deviceTypes,
)

private fun Channel.toStored() = StoredChannel(
    id = id.value,
    title = title,
    logoUrl = logoUrl,
    streamUrl = streamUrl,
    categoryId = categoryId,
    categoryName = categoryName,
    groupTitle = groupTitle,
    isGeoBlocked = isGeoBlocked,
    isNot247 = isNot247,
    quality = quality,
    tvgId = tvgId,
    userAgent = userAgent,
    referrer = referrer,
    metadataJson = encodeMetadataJson(),
)

private fun StoredChannel.toDomain(): Channel {
    val meta = decodeChannelMetadata(metadataJson)
    return Channel(
        id = ContentId(id),
        title = title,
        logoUrl = logoUrl,
        streamUrl = streamUrl,
        categoryId = categoryId,
        categoryName = categoryName,
        groupTitle = groupTitle,
        isGeoBlocked = isGeoBlocked,
        isNot247 = isNot247,
        quality = quality,
        tvgId = tvgId,
        userAgent = userAgent,
        referrer = referrer,
        images = meta.images.map { it.toDomain() },
        backdropUrl = meta.backdropUrl,
        synopsis = meta.synopsis,
        genres = meta.genres,
        releaseYear = meta.releaseYear,
        runtimeMinutes = meta.runtimeMinutes,
        parentalRating = meta.parentalRating,
        imdbRating = meta.imdbRating,
        assetType = meta.assetType,
        cast = meta.cast,
        director = meta.director,
        tags = meta.tags,
        trailerUrl = meta.trailerUrl,
        language = meta.language,
    )
}

@OptIn(ExperimentalTime::class)
private class WebCatalogLocalDataSource : CatalogLocalDataSource {
    private val mutex = Mutex()
    private val memoryShelves = LinkedHashMap<String, StoredChannelShelf>()
    private val memoryLayouts = LinkedHashMap<String, StoredLayoutRecord>()
    private val memoryCategories = LinkedHashMap<String, StoredCategory>()
    private var categoriesUpdatedAtMs: Long? = null

    init {
        loadFromStorage()
    }

    override suspend fun getMenuItems(): List<AppMenuItem> = mutex.withLock {
        readMenuBundle()?.items?.map { it.toDomain() }.orEmpty()
    }

    override suspend fun getMenuUpdatedAtMs(): Long? = mutex.withLock {
        readMenuBundle()?.updatedAtMs
    }

    override suspend fun replaceMenuItems(items: List<AppMenuItem>) {
        mutex.withLock {
            val bundle = StoredMenuBundle(
                updatedAtMs = Clock.System.now().toEpochMilliseconds(),
                items = items.map { it.toStored() },
            )
            runCatching {
                window.localStorage.setItem(KEY_MENU, webJson.encodeToString(bundle))
            }
        }
    }

    override suspend fun getLayout(layoutId: String): CachedLayout? = mutex.withLock {
        memoryLayouts[layoutId]?.let {
            CachedLayout(
                id = it.id,
                name = it.name,
                themeUrl = it.themeUrl,
                railIds = it.railIds,
                updatedAtMs = it.updatedAtMs,
            )
        }
    }

    override suspend fun saveLayout(layout: CachedLayout) {
        mutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            memoryLayouts[layout.id] = StoredLayoutRecord(
                id = layout.id,
                name = layout.name,
                themeUrl = layout.themeUrl,
                railIds = layout.railIds,
                updatedAtMs = now,
            )
            persistLayouts()
        }
    }

    override suspend fun getCategories(): List<Category> = mutex.withLock {
        memoryCategories.values.map { it.toDomain() }
    }

    override suspend fun getCategory(categoryId: String): Category? = mutex.withLock {
        memoryCategories[categoryId]?.toDomain()
    }

    override suspend fun getCategoriesUpdatedAtMs(): Long? = mutex.withLock {
        categoriesUpdatedAtMs
    }

    override suspend fun replaceCategories(categories: List<Category>) {
        mutex.withLock {
            memoryCategories.clear()
            categories.forEach { memoryCategories[it.id] = it.toStored() }
            categoriesUpdatedAtMs = Clock.System.now().toEpochMilliseconds()
            persistCategories()
        }
    }

    override suspend fun upsertCategory(category: Category, sortOrder: Int) {
        mutex.withLock {
            memoryCategories[category.id] = category.toStored()
            if (categoriesUpdatedAtMs == null) {
                categoriesUpdatedAtMs = Clock.System.now().toEpochMilliseconds()
            }
            persistCategories()
        }
    }

    override suspend fun getChannelsByCategory(categoryId: String, limit: Int): List<Channel> =
        mutex.withLock {
            memoryShelves[categoryId]?.channels?.take(limit)?.map { it.toDomain() }.orEmpty()
        }

    override suspend fun getCategoryUpdatedAtMs(categoryId: String): Long? = mutex.withLock {
        memoryShelves[categoryId]?.updatedAtMs
    }

    override suspend fun replaceChannelsForCategory(categoryId: String, channels: List<Channel>) {
        mutex.withLock {
            memoryShelves[categoryId] = StoredChannelShelf(
                updatedAtMs = Clock.System.now().toEpochMilliseconds(),
                channels = channels.map { it.toStored() },
            )
            persistShelves()
        }
    }

    override suspend fun searchChannels(query: String, limit: Int): List<Channel> =
        mutex.withLock {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return@withLock emptyList()
            memoryShelves.values
                .asSequence()
                .flatMap { it.channels.asSequence() }
                .filter {
                    it.title.lowercase().contains(q) ||
                        it.categoryName.lowercase().contains(q) ||
                        it.groupTitle.lowercase().contains(q) ||
                        it.metadataJson.lowercase().contains(q)
                }
                .distinctBy { it.id }
                .take(limit)
                .map { it.toDomain() }
                .toList()
        }

    override suspend fun getChannel(id: ContentId): Channel? = mutex.withLock {
        memoryShelves.values
            .asSequence()
            .flatMap { it.channels.asSequence() }
            .firstOrNull { it.id == id.value }
            ?.toDomain()
    }

    override suspend fun getChannelCount(): Long = mutex.withLock {
        memoryShelves.values.sumOf { it.channels.size }.toLong()
    }

    private fun readMenuBundle(): StoredMenuBundle? = runCatching {
        window.localStorage.getItem(KEY_MENU)?.let {
            webJson.decodeFromString<StoredMenuBundle>(it)
        }
    }.getOrNull()

    private fun loadFromStorage() {
        runCatching {
            window.localStorage.getItem(KEY_CATEGORIES)?.let { raw ->
                val bundle = webJson.decodeFromString<StoredCategoriesBundle>(raw)
                categoriesUpdatedAtMs = bundle.updatedAtMs
                memoryCategories.clear()
                bundle.categories.forEach { memoryCategories[it.id] = it }
            }
        }
        runCatching {
            window.localStorage.getItem(KEY_LAYOUTS)?.let { raw ->
                val decoded = webJson.decodeFromString<Map<String, StoredLayoutRecord>>(raw)
                memoryLayouts.clear()
                memoryLayouts.putAll(decoded)
            }
        }
        runCatching {
            val raw = window.localStorage.getItem(KEY_SHELVES) ?: return
            val decoded = webJson.decodeFromString<Map<String, StoredChannelShelf>>(raw)
            memoryShelves.clear()
            memoryShelves.putAll(decoded)
        }
    }

    private fun persistCategories() {
        runCatching {
            val bundle = StoredCategoriesBundle(
                updatedAtMs = categoriesUpdatedAtMs ?: Clock.System.now().toEpochMilliseconds(),
                categories = memoryCategories.values.toList(),
            )
            window.localStorage.setItem(KEY_CATEGORIES, webJson.encodeToString(bundle))
        }
    }

    private fun persistLayouts() {
        runCatching {
            window.localStorage.setItem(KEY_LAYOUTS, webJson.encodeToString(memoryLayouts.toMap()))
        }
    }

    private fun persistShelves() {
        runCatching {
            window.localStorage.setItem(KEY_SHELVES, webJson.encodeToString(memoryShelves.toMap()))
        }
    }

    private companion object {
        const val KEY_MENU = "ott_web_cms_menu_v3"
        const val KEY_LAYOUTS = "ott_web_cms_layouts_v3"
        const val KEY_CATEGORIES = "ott_web_cms_categories_v3"
        const val KEY_SHELVES = "ott_web_cms_shelves_v3"
    }
}

private class WebWatchlistLocalDataSource : WatchlistLocalDataSource {
    private val mutex = Mutex()
    private val state = MutableStateFlow(loadInitial())

    override fun observeAll(): Flow<List<Channel>> = state.asStateFlow()

    override suspend fun add(channel: Channel) {
        mutex.withLock {
            if (state.value.none { it.id == channel.id }) {
                val updated = state.value + channel
                state.value = updated
                save(updated)
            }
        }
    }

    override suspend fun remove(id: ContentId) {
        mutex.withLock {
            val updated = state.value.filterNot { it.id == id }
            state.value = updated
            save(updated)
        }
    }

    override suspend fun exists(id: ContentId): Boolean =
        state.value.any { it.id == id }

    override suspend fun toggle(channel: Channel): Boolean = mutex.withLock {
        val exists = state.value.any { it.id == channel.id }
        val updated = if (exists) {
            state.value.filterNot { it.id == channel.id }
        } else {
            state.value + channel
        }
        state.value = updated
        save(updated)
        !exists
    }

    private fun loadInitial(): List<Channel> = runCatching {
        val raw = window.localStorage.getItem(KEY_WATCHLIST) ?: return emptyList()
        webJson.decodeFromString<List<StoredChannel>>(raw).map { it.toDomain() }
    }.getOrDefault(emptyList())

    private fun save(channels: List<Channel>) {
        runCatching {
            window.localStorage.setItem(
                KEY_WATCHLIST,
                webJson.encodeToString(channels.map { it.toStored() }),
            )
        }
    }

    private companion object {
        const val KEY_WATCHLIST = "ott_web_watchlist_v2"
    }
}
