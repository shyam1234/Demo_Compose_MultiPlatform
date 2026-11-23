package com.ott.platform.database

import com.ott.platform.domain.model.AppMenuItem
import com.ott.platform.domain.model.Category
import com.ott.platform.domain.model.Channel
import com.ott.platform.domain.model.ContentId
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

data class CachedLayout(
    val id: String,
    val name: String,
    val themeUrl: String? = null,
    val railIds: List<String> = emptyList(),
    val updatedAtMs: Long = 0L,
)

interface CatalogLocalDataSource {
    suspend fun getMenuItems(): List<AppMenuItem>
    suspend fun getMenuUpdatedAtMs(): Long?
    suspend fun replaceMenuItems(items: List<AppMenuItem>)

    suspend fun getLayout(layoutId: String): CachedLayout?
    suspend fun saveLayout(layout: CachedLayout)

    suspend fun getCategories(): List<Category>
    suspend fun getCategory(categoryId: String): Category?
    suspend fun getCategoriesUpdatedAtMs(): Long?
    suspend fun replaceCategories(categories: List<Category>)
    suspend fun upsertCategory(category: Category, sortOrder: Int = 0)

    suspend fun getChannelsByCategory(categoryId: String, limit: Int): List<Channel>
    suspend fun getCategoryUpdatedAtMs(categoryId: String): Long?
    suspend fun replaceChannelsForCategory(categoryId: String, channels: List<Channel>)

    suspend fun searchChannels(query: String, limit: Int): List<Channel>
    suspend fun getChannel(id: ContentId): Channel?
    suspend fun getChannelCount(): Long
}

@OptIn(ExperimentalTime::class)
class SqlDelightCatalogLocalDataSource(
    private val database: OttDatabase,
    private val ioDispatcher: CoroutineDispatcher = databaseIoDispatcher,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : CatalogLocalDataSource {

    private val queries: OttDatabaseQueries
        get() = database.ottDatabaseQueries

    override suspend fun getMenuItems(): List<AppMenuItem> = withContext(ioDispatcher) {
        val row = queries.selectMenuPayload(MENU_ROW_ID).executeAsOneOrNull() ?: return@withContext emptyList()
        runCatching {
            localDbJson.decodeFromString<List<StoredMenuItem>>(row.payload_json).map { it.toDomain() }
        }.getOrDefault(emptyList())
    }

    override suspend fun getMenuUpdatedAtMs(): Long? = withContext(ioDispatcher) {
        queries.selectMenuPayload(MENU_ROW_ID).executeAsOneOrNull()?.updated_at_ms
    }

    override suspend fun replaceMenuItems(items: List<AppMenuItem>) {
        withContext(ioDispatcher) {
            val json = localDbJson.encodeToString(items.map { it.toStored() })
            queries.upsertMenuPayload(
                id = MENU_ROW_ID,
                payload_json = json,
                updated_at_ms = nowMs(),
            )
        }
    }

    override suspend fun getLayout(layoutId: String): CachedLayout? = withContext(ioDispatcher) {
        val row = queries.selectLayoutById(layoutId).executeAsOneOrNull() ?: return@withContext null
        val railIds = runCatching {
            localDbJson.decodeFromString<List<String>>(row.rail_ids_json)
        }.getOrDefault(emptyList())
        CachedLayout(
            id = row.id,
            name = row.name,
            themeUrl = row.theme_url,
            railIds = railIds,
            updatedAtMs = row.updated_at_ms,
        )
    }

    override suspend fun saveLayout(layout: CachedLayout) {
        withContext(ioDispatcher) {
            queries.upsertLayout(
                id = layout.id,
                name = layout.name,
                theme_url = layout.themeUrl,
                rail_ids_json = localDbJson.encodeToString(layout.railIds),
                updated_at_ms = nowMs(),
            )
        }
    }

    override suspend fun getCategories(): List<Category> = withContext(ioDispatcher) {
        queries.selectAllCategories().executeAsList().map { row -> row.toCategory() }
    }

    override suspend fun getCategory(categoryId: String): Category? = withContext(ioDispatcher) {
        queries.selectCategoryById(categoryId).executeAsOneOrNull()?.toCategory()
    }

    override suspend fun getCategoriesUpdatedAtMs(): Long? = withContext(ioDispatcher) {
        queries.selectCategoriesUpdatedAt().executeAsOneOrNull()?.oldest_updated_at
    }

    override suspend fun replaceCategories(categories: List<Category>) = withContext(ioDispatcher) {
        val timestamp = nowMs()
        database.transaction {
            queries.deleteAllCategories()
            categories.forEachIndexed { index, category ->
                queries.upsertCategory(
                    id = category.id,
                    name = category.name,
                    description = category.persistedDescription(),
                    rail_display_type = category.displayType,
                    rail_content_type = category.contentType,
                    sort_order = index.toLong(),
                    updated_at_ms = timestamp,
                )
            }
        }
    }

    override suspend fun upsertCategory(category: Category, sortOrder: Int) {
        withContext(ioDispatcher) {
            queries.upsertCategory(
                id = category.id,
                name = category.name,
                description = category.persistedDescription(),
                rail_display_type = category.displayType,
                rail_content_type = category.contentType,
                sort_order = sortOrder.toLong(),
                updated_at_ms = nowMs(),
            )
        }
    }

    override suspend fun getChannelsByCategory(categoryId: String, limit: Int): List<Channel> =
        withContext(ioDispatcher) {
            queries.selectChannelsByCategory(
                category_id = categoryId,
                value_ = limit.toLong(),
            ).executeAsList().map { it.toDomain() }
        }

    override suspend fun getCategoryUpdatedAtMs(categoryId: String): Long? =
        withContext(ioDispatcher) {
            queries.selectCategoryUpdatedAt(categoryId).executeAsOneOrNull()?.oldest_updated_at
        }

    override suspend fun replaceChannelsForCategory(categoryId: String, channels: List<Channel>) =
        withContext(ioDispatcher) {
            val timestamp = nowMs()
            database.transaction {
                queries.deleteChannelsByCategory(categoryId)
                channels.forEachIndexed { index, channel ->
                    queries.upsertChannel(
                        id = channel.id.value,
                        title = channel.title,
                        logo_url = channel.logoUrl,
                        stream_url = channel.streamUrl,
                        category_id = channel.categoryId,
                        category_name = channel.categoryName,
                        group_title = channel.groupTitle,
                        is_geo_blocked = if (channel.isGeoBlocked) 1L else 0L,
                        is_not_247 = if (channel.isNot247) 1L else 0L,
                        quality = channel.quality,
                        tvg_id = channel.tvgId,
                        user_agent = channel.userAgent,
                        referrer = channel.referrer,
                        metadata_json = channel.encodeMetadataJson(),
                        shelf_order = index.toLong(),
                        updated_at_ms = timestamp,
                    )
                }
            }
        }

    override suspend fun searchChannels(query: String, limit: Int): List<Channel> =
        withContext(ioDispatcher) {
            queries.searchChannels(
                query = query,
                limit = limit.toLong(),
            ).executeAsList().map { it.toDomain() }
        }

    override suspend fun getChannel(id: ContentId): Channel? = withContext(ioDispatcher) {
        queries.selectChannelById(id = id.value).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getChannelCount(): Long = withContext(ioDispatcher) {
        queries.countChannels().executeAsOne()
    }

    private fun Channels.toDomain(): Channel {
        val meta = decodeChannelMetadata(metadata_json)
        return Channel(
            id = ContentId(id),
            title = title,
            logoUrl = logo_url,
            streamUrl = stream_url,
            categoryId = category_id,
            categoryName = category_name,
            groupTitle = group_title,
            isGeoBlocked = is_geo_blocked != 0L,
            isNot247 = is_not_247 != 0L,
            quality = quality,
            tvgId = tvg_id,
            userAgent = user_agent,
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

    private fun Categories.toCategory(): Category {
        val meta = decodeCategoryRailMeta(description)
        return Category(
            id = id,
            name = name,
            description = meta.note,
            displayType = rail_display_type,
            contentType = rail_content_type,
            hasBackgroundImage = meta.hasBackgroundImage,
            backgroundImageUrl = meta.backgroundImageUrl,
            deviceTypes = meta.deviceTypes,
        )
    }

    private fun Category.persistedDescription(): String = encodeCategoryDescription(
        hasBackgroundImage = hasBackgroundImage,
        backgroundImageUrl = backgroundImageUrl,
        deviceTypes = deviceTypes,
        note = description,
    )

    private companion object {
        const val MENU_ROW_ID = "default_menu"
    }
}
