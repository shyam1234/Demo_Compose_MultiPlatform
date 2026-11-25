# Module Structure

## Repository layout

```
OttPlatform/
├── apps/
│   ├── androidApp/          # Phone + tablet entry (CMP)
│   ├── androidTvApp/        # Android TV entry (CMP + TV UX)
│   ├── desktopApp/          # macOS Desktop entry (CMP Desktop)
│   ├── webApp/              # Web Wasm entry (ComposeViewport + hls.js)
│   ├── iosApp/              # Xcode shell hosting CMP UI
│   ├── appleTvApp/          # SwiftUI shell + shared KMP logic
│   └── shared/              # Shared CMP App(), NavHost, DI wiring
├── core/
│   ├── domain/              # Entities, use cases, repository interfaces
│   ├── database/            # SQLDelight schema, local data sources & platform drivers
│   ├── data/                # Repository impls, mappers, network-first catalog sync & watchlist
│   ├── network/             # Ktor client setup (demo-ready)
│   ├── designsystem/        # Theme, typography, TV/mobile tokens
│   ├── ui/                  # Shared composables (cards, rows, hero)
│   ├── navigation/          # Route contracts / Nav helpers
│   ├── logging/             # Logger abstraction
│   ├── testing/             # Fakes, fixtures, test dispatchers
│   ├── player-api/          # PlayerEngine + playback models
│   └── di/                  # Core Koin modules
├── features/
│   ├── onboarding/{api,impl}
│   ├── authentication/{api,impl}
│   ├── home/{api,impl}
│   ├── search/{api,impl}
│   ├── details/{api,impl}
│   ├── player/{api,impl}
│   ├── watchlist/{api,impl}
│   ├── continuewatching/{api,impl}
│   ├── profile/{api,impl}
│   └── settings/{api,impl}
├── platform/
│   ├── android/             # Media3 PlayerEngine, Android helpers
│   ├── ios/                 # AVPlayer expect/actual / bridge
│   ├── web/                 # WebPlayerEngine (HTML5 video + hls.js)
│   ├── macos/               # Desktop player / desktop helpers
│   ├── androidtv/           # TV focus / remote helpers
│   └── tvos/                # tvOS player bridge for SwiftUI app
├── documentation/
├── build-logic/             # Optional convention plugins
├── gradle/libs.versions.toml
└── settings.gradle.kts
```

## Module responsibilities

### Apps

| Module | Role |
|--------|------|
| `apps/shared` | Composition root: `OttApp()`, navigation graph, feature registration |
| `apps/androidApp` | Android Activity / Application; phone & tablet |
| `apps/androidTvApp` | Leanback/TV Activity; D-pad first |
| `apps/desktopApp` | `main()` Compose Desktop window |
| `apps/webApp` | `ComposeViewport` Wasm browser shell + `hls.js` host |
| `apps/iosApp` | Xcode project embedding shared CMP |
| `apps/appleTvApp` | Native tvOS UI; consumes KMP framework |

### Core

| Module | Responsibility | Compose? |
|--------|----------------|----------|
| `core/domain` | Entities, use cases, repo interfaces | No |
| `core/database` | SQLDelight `OttDatabase`, local data sources, Android/iOS/Desktop drivers | No |
| `core/data` | Repo implementations, network-first catalog sync (prune + offline fallback), persistent watchlist | No |
| `core/network` | Ktor HttpClient factory | No |
| `core/player-api` | `PlayerEngine`, `PlayerState`, track contracts | No |
| `core/designsystem` | Colors, type, spacing, TV tokens | Yes |
| `core/ui` | ContentCard, ShelfRow, HeroBanner, etc. | Yes |
| `core/navigation` | Typed routes / destinations | Minimal |
| `core/logging` | `OttLogger` | No |
| `core/testing` | Fakes & fixtures | No |
| `core/di` | Core Koin modules | No |

### Features

| Feature | api exposes | impl contains |
|---------|-------------|---------------|
| onboarding | routes, entry API | welcome / carousel screens |
| authentication | auth ports / routes | login / register demo |
| home | home route, shelf models | home screen + VM |
| search | search route / query API | search UI + VM |
| details | details route (`contentId`) | details screen + VM |
| player | player route / playback intents | player UI + controller wiring |
| watchlist | watchlist ports | watchlist screen |
| continuewatching | continue-watching ports | row / screen wiring |
| profile | profile route | profile screen |
| settings | settings route | settings screen |

### Platform

| Module | Implements |
|--------|------------|
| `platform/android` | Media3 `PlayerEngine`, Android secure storage hooks |
| `platform/ios` | AVPlayer-backed `PlayerEngine` |
| `platform/web` | HTML5 `<video>` + `hls.js`-backed `WebPlayerEngine` |
| `platform/macos` | Desktop player adapter |
| `platform/androidtv` | Focus traversal helpers, TV metrics |
| `platform/tvos` | Shared logic + AVPlayer for SwiftUI |

## Gradle project path map

```
:apps:androidApp
:apps:androidTvApp
:apps:desktopApp
:apps:webApp
:apps:shared
:core:domain
:core:database
:core:data
:core:network
:core:designsystem
:core:ui
:core:navigation
:core:logging
:core:testing
:core:player-api
:core:di
:features:home:api
:features:home:impl
… (same pattern for each feature)
:platform:android
:platform:ios
:platform:web
:platform:macos
:platform:androidtv
:platform:tvos
```

## What is intentionally not a separate module

| Concern | Why folded |
|---------|------------|
| Per-feature database modules | Premature; keep shared persistence in `:core:database` behind repository interfaces |
| Community CMP-tvOS UI | Unsupported officially; we use SwiftUI instead |
