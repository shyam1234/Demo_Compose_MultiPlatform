# Architecture Decisions (ADRs)

## ADR-001: Compose Multiplatform as primary UI

**Status:** Accepted  

**Context:** Need shared UI across Android, iOS, and macOS Desktop with a production-oriented OTT demo.  

**Decision:** Use **Compose Multiplatform** for all officially supported UI targets.  

**Consequences:**
- One design system and shared screens for phone, tablet, desktop, and (with TV adaptations) Android TV.
- Apple TV cannot share that UI officially.

---

## ADR-002: Native SwiftUI for Apple TV

**Status:** Accepted  

**Context:** CMP does not list tvOS as an official UI platform. KMP tvOS is Beta. Community CMP-tvOS forks exist but are not JetBrains-supported.  

**Decision:** Apple TV UI is **SwiftUI**; shared **Kotlin** handles domain/data/player contracts.  

**Consequences:**
- Some UI duplication for TV catalogs/player chrome.
- Clear support matrix; no false claim of full CMP on tvOS.
- Better alignment with tvOS focus engine and HIG.

---

## ADR-003: Feature api / impl split

**Status:** Accepted  

**Context:** Features must be removable/extractable by different teams.  

**Decision:** Every product feature has `api` + `impl` Gradle modules. App shells depend on `impl`; cross-feature references use `api` only.  

**Consequences:**
- More modules to maintain.
- Strong boundaries and safer extraction.
- DI registration becomes the feature toggle mechanism.

---

## ADR-004: PlayerEngine platform abstraction

**Status:** Accepted  

**Context:** ExoPlayer and AVPlayer APIs differ; DRM/PiP are platform-specific.  

**Decision:** Introduce `core/player-api.PlayerEngine` with platform adapters and a fake for tests.  

**Consequences:**
- Shared player UI possible on CMP targets.
- Native player chrome on tvOS still uses the same contract.
- Slight indirection vs calling ExoPlayer directly.

---

## ADR-005: Koin for multiplatform DI

**Status:** Accepted  

**Context:** Need multiplatform DI with Compose ViewModel integration.  

**Decision:** Use **Koin** (`koin-core`, `koin-compose`, `koin-compose-viewmodel`).  

**Consequences:**
- Runtime DI (less compile-time graph safety than DI frameworks with codegen).
- Simple feature module registration.
- Alternative (e.g. kotlin-inject) possible later behind the same module boundaries.

---

## ADR-006: Mock-first data layer (evolved by ADR-010)

**Status:** Superseded in part by ADR-010  

**Context:** Initial demo needed to run without a real OTT backend or DRM.  

**Decision:** Initially shipped in-memory watchlist and remote-only catalog; kept repository interfaces ready for SQLDelight persistence.  

**Consequences:**
- Fast initial bring-up and unit tests.
- Persistence was cleanly added in `:core:database` without changing `core/domain` or feature UI modules.

---

## ADR-007: Separate Android TV application module

**Status:** Accepted  

**Context:** TV quality guidelines require remote-first UX; phone layouts are insufficient.  

**Decision:** Provide `apps/androidTvApp` with TV-oriented navigation and focus, sharing `apps/shared` / feature impls where sensible.  

**Consequences:**
- Extra app module and manifest/Leanback launcher surface.
- Clearer TV QA path than packing TV into the phone app only.

---

## ADR-008: Prefer official stability over maximum code sharing

**Status:** Accepted  

**Context:** Maximizing shared UI can push experimental targets into the critical path.  

**Decision:** Reuse **business logic aggressively**, **UI selectively**, **playback natively**.  

**Consequences:**
- Architecture stays maintainable for a real OTT product.
- Some platform UI divergence is expected and documented.

---

## ADR-009: Centralized version catalog

**Status:** Accepted  

**Context:** Many modules need aligned Kotlin / CMP / Ktor / Koin versions.  

**Decision:** All dependency versions live in `gradle/libs.versions.toml`.  

**Consequences:**
- One place to bump stack versions.
- Convention plugins optional; catalog is mandatory.

---

## ADR-010: SQLDelight common database for offline catalog & watchlist

**Status:** Accepted (updated)  

**Context:** Users need persistent watchlist state and offline browsing of the previously fetched catalog when network connectivity is unavailable across Android, iOS, and Desktop. Stale CMS rows (removed menus / layouts / rails / assets) must not linger after a successful online refresh.  

**Decision:** Introduce `:core:database` using **SQLDelight 2.4.1** (`ott_platform.db`) with shared schema/queries/data sources in `commonMain` and platform SQLite drivers (`AndroidSqliteDriver`, `NativeSqliteDriver`, `JdbcSqliteDriver`). Bind `PersistentWatchlistRepository` and wire `CatalogLocalDataSource` into `CmsCatalogRepository` with a **network-first** policy:
- Online → always fetch Play-CMS; on success **replace** local menu / layout / per-rail channels and **prune** layouts & rails CMS no longer returns (`deleteLayout`, `deleteCategoryAndChannels`).
- Offline / network failure → serve the last synced local snapshot (menu, layout, rails, search).
- Web mirrors the same contracts via `localStorage` (`ott_web_cms_*_v7`).
- Use `kotlin.time.Clock.System` in common code for Kotlin/Native compatibility.

**Consequences:**
- Single shared SQLite schema and reactive `Flow` watchlist across Android, Android TV, iOS, and Desktop.
- Requires `-lsqlite3` linker flag for the static `OttShared` iOS framework.
- Schema migrations (`1.sqm`, `2.sqm`) upgrade existing installations cleanly; sync pruning removes orphan catalog rows without a schema bump.
- Local DB is no longer a TTL “prefer cache” layer — it is strictly an offline mirror of the last successful API response.

---

## ADR-011: Play-CMS dynamic menu, layout, rail, and extensible ImageSlot architecture

**Status:** Accepted  

**Context:** Replaced static `iptv-org` M3U/category feeds with **Play-CMS** (`https://cms.play-cms.com`), where navigation tabs (`menuL1`/`menuL2`), screen layouts (`theme_url`, `sortrail`), rail presentation (`rail_display_type`, `rail_content_type`), and artwork `imagetype` values are server-driven and may introduce new types in the future.

**Decision:**
- Implement `CmsAuthManager` (internal login + `Mutex` single-flight caching) and `CmsCatalogRemoteDataSource` (automatic `401` token refresh + retry once) in `:core:data`, backed by SQLDelight v3 (`cms_menu`, `cms_layout`, `categories`, composite-key `channels(category_id, id)`) and Web `localStorage` (`ott_web_cms_*_v7`).
- Store all asset `MediaImage` entries in `Channel.images` and resolve artwork dynamically through `ImageSlot.forRail(displayType, contentType)` + `ShelfCardStyle.fromCms()`, matching future CMS `imagetype` values case-insensitively with orientation-based fallbacks.
- Drive top/bottom navigation dynamically from `AppMenuViewModel` (`OttRoutes.HOME` + `OttRoutes.LAYOUT`) and render `menuL2` sub-menus inline on `HomeScreen`.

**Consequences:**
- New CMS menu tabs, layouts, rail styles (`Portrait`, `Landscape`, `Square`, `Banner`, `Top10`, `Grid`, `Mosaic`, `Editorial`, `discovery_dock`, thematic `rail_background_image`), and future `imagetype` artwork work without rewriting repository or parser code.
- Thematic rails (`ContentShelfRow.ThematicShelfBody`) size the branded panel from CMS `Rail_Background_Image` aspect (`imagewidth`/`imageheight`, default `1840/346`) and render with `ContentScale.Fit` vertically centered so brand artwork (e.g. football / crest) is not cropped by the card overlay.
- Discovery-dock child tabs are filtered by CMS `devicetype` for the active platform (iPhone → `iosmobile`); tabs missing that allow-list will not appear on that device.
- See [API.md](API.md) for full API contracts and team ownership roles.

---

## Trade-off summary

| Choice | Benefit | Cost |
|--------|---------|------|
| Many feature modules | Extraction / team scale | Build graph complexity |
| CMP everywhere official | Shared UX velocity | Not for tvOS UI |
| SwiftUI on tvOS | Correct TV platform | Duplicate some screens |
| Player abstraction | Swappable engines / testability | Extra interfaces |
| SQLDelight common DB | Shared offline cache + watchlist across targets | `-lsqlite3` iOS linking + migration discipline |
