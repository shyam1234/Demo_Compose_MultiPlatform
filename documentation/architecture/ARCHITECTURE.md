# Architecture Overview

**OttPlatform** is a modular OTT (streaming) demo built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform (CMP)**. Shared business logic and shared UI live in common modules; media playback and Apple TV UI stay platform-native where required.

## Goals

| Goal | How we achieve it |
|------|-------------------|
| Share business logic | Domain + data + use cases in `commonMain` |
| Share UI where official | CMP on Android, iOS, macOS Desktop |
| Native where needed | Player engines; SwiftUI for Apple TV |
| Feature independence | `features/<name>/api` + `features/<name>/impl` |
| Testability | Fakes, contracts, common unit tests |
| Production shape | Clean Architecture, DI registration, documented boundaries |

## High-level system

```mermaid
flowchart TB
    subgraph Apps["Application shells"]
        AndroidApp["androidApp<br/>Phone / Tablet"]
        AndroidTv["androidTvApp<br/>Android TV"]
        Desktop["desktopApp<br/>macOS Desktop CMP"]
        WebApp["webApp<br/>Web Wasm CMP"]
        Ios["iosApp<br/>iPhone / iPad CMP"]
        AppleTv["appleTvApp<br/>SwiftUI + shared KMP"]
    end

    subgraph SharedUI["Compose Multiplatform UI"]
        Shared["apps/shared"]
        FeaturesUI["Feature impl screens"]
        Design["core/designsystem + core/ui"]
    end

    subgraph DomainCore["Shared Kotlin"]
        Domain["core/domain"]
        Database["core/database<br/>SQLDelight"]
        Data["core/data"]
        PlayerApi["core/player-api"]
        DI["core/di"]
    end

    subgraph Platform["Platform adapters"]
        PAndroid["platform/android<br/>Media3"]
        PIos["platform/ios<br/>AVPlayer"]
        PMac["platform/macos<br/>AVPlayer"]
        PWeb["platform/web<br/>HTML5 video + hls.js"]
        PAtv["platform/androidtv<br/>TV focus helpers"]
        PTvos["platform/tvos<br/>AVPlayer bridge"]
    end

    AndroidApp --> Shared
    AndroidTv --> Shared
    Desktop --> Shared
    WebApp --> Shared
    Ios --> Shared
    AppleTv --> DomainCore
    AppleTv --> PTvos

    Shared --> FeaturesUI
    Shared --> Design
    FeaturesUI --> DomainCore
    DomainCore --> Platform
```

## Clean Architecture layers

Dependency direction is always **inward toward domain**. UI and data never leak into domain.

```mermaid
flowchart LR
    subgraph Presentation["Presentation"]
        Screens["CMP Screens"]
        VMs["ViewModels / State holders"]
        Nav["Navigation"]
    end

    subgraph Domain["Domain"]
        Entities["Entities"]
        UseCases["Use cases"]
        RepoIfaces["Repository interfaces"]
        PlayerContract["PlayerEngine contract"]
    end

    subgraph Data["Data"]
        RepoImpl["Repository impls"]
        LocalDb["SQLDelight offline mirror<br/>(network-first + prune)"]
        Mappers["DTO ↔ Domain mappers"]
        Network["Ktor network"]
    end

    subgraph PlatformLayer["Platform"]
        Exo["Media3 ExoPlayer"]
        AVP["AVPlayer"]
        DbDrivers["SQLite drivers<br/>Android / Native / JDBC"]
    end

    Screens --> VMs
    VMs --> UseCases
    UseCases --> RepoIfaces
    UseCases --> PlayerContract
    RepoImpl -.->|implements| RepoIfaces
    RepoImpl --> Network
    RepoImpl --> LocalDb
    LocalDb --> DbDrivers
    Exo -.->|implements| PlayerContract
    AVP -.->|implements| PlayerContract
```

### Layer rules

| Layer | May depend on | Must not depend on |
|-------|---------------|--------------------|
| Presentation | Domain APIs, design system, feature APIs | Data impls, ExoPlayer, AVPlayer, SQL |
| Domain | Kotlin stdlib, coroutines, datetime | Android, iOS, Compose, Ktor, Koin (optional pure) |
| Data | Domain interfaces, network, serialization | Compose UI, feature screens |
| Platform | Domain/player contracts | Feature UI modules |

## Compose Multiplatform strategy

| Target | UI technology | Notes |
|--------|---------------|-------|
| Android Mobile / Tablet | **CMP** | Adaptive layouts, bottom / nav rail |
| Android TV | **CMP** | Dedicated TV focus / D-pad UX |
| iPhone / iPad | **CMP** | Official stable CMP iOS |
| macOS Desktop | **CMP Desktop (JVM)** | Window + keyboard/mouse |
| Web (`wasmJs`) | **CMP Web (Wasm)** | Responsive (`>= 840dp` top nav, `< 840dp` bottom nav) + `hls.js` |
| Apple TV (tvOS) | **SwiftUI** | CMP does **not** officially support tvOS UI |

Shared Kotlin (domain, data, player contracts, use cases) is reused on tvOS via a KMP framework. UI for Apple TV is native.

## Unidirectional data flow

```mermaid
sequenceDiagram
    participant User
    participant Screen as Compose Screen
    participant VM as ViewModel
    participant UC as UseCase
    participant Repo as Repository
    participant Source as Mock/Remote source

    User->>Screen: Intent (click, search, play)
    Screen->>VM: onEvent(UiEvent)
    VM->>UC: invoke(...)
    UC->>Repo: query / command
    Repo->>Source: load
    Source-->>Repo: DTO / entity data
    Repo-->>UC: Domain model
    UC-->>VM: Result
    VM-->>Screen: StateFlow UiState
    Screen-->>User: Render
```

- **UiState** is immutable.
- **UiEvent** is the only way screens talk to ViewModels.
- Side effects (navigation, snackbars) are one-shot events, not duplicated in state when avoidable.

## Feature modularity

Each feature is split into:

- **`api`** — public routes, models, ports other modules may use
- **`impl`** — screens, ViewModels, Koin module; depended on only by app composition roots

```mermaid
flowchart TB
    SharedApp["apps/shared<br/>Feature registration"]

    HomeApi["features/home/api"]
    HomeImpl["features/home/impl"]
    SearchApi["features/search/api"]
    SearchImpl["features/search/impl"]
    PlayerApi["features/player/api"]
    PlayerImpl["features/player/impl"]
    Domain["core/domain"]

    SharedApp --> HomeImpl
    SharedApp --> SearchImpl
    SharedApp --> PlayerImpl

    HomeImpl --> HomeApi
    SearchImpl --> SearchApi
    PlayerImpl --> PlayerApi

    HomeImpl --> Domain
    SearchImpl --> Domain
    PlayerImpl --> Domain

    HomeImpl -.->|must NOT depend| SearchImpl
    SearchImpl -.->|must NOT depend| HomeImpl
```

**Validation checks**

1. Remove Search: delete `:features:search:impl` registration → Home and Player still compile.
2. Replace ExoPlayer: swap `platform/android` player binding → player UI unchanged.
3. Test domain without devices: run `commonTest` on JVM.

## Player architecture (summary)

```mermaid
flowchart TB
    PlayerUI["feature/player/impl<br/>CMP Player UI"]
    Controller["PlayerController"]
    Contract["core/player-api<br/>PlayerEngine"]
    Fake["FakePlayerEngine<br/>tests"]
    Media3["platform/android<br/>Media3PlayerEngine"]
    AVPlayer["platform/ios|macos|tvos<br/>AvPlayerEngine"]

    PlayerUI --> Controller
    Controller --> Contract
    Fake -.->|implements| Contract
    Media3 -.->|implements| Contract
    AVPlayer -.->|implements| Contract
```

Common code never imports ExoPlayer or AVPlayer. See [PLAYER_ARCHITECTURE.md](../PLAYER_ARCHITECTURE.md).

## Application startup

```mermaid
sequenceDiagram
    participant App as Platform App entry
    participant Koin as Koin DI
    participant Features as Feature modules
    participant Root as Shared App()
    participant Nav as NavHost

    App->>Koin: startKoin { modules(...) }
    Koin->>Features: register feature modules
    Koin->>Koin: bind PlayerEngine (platform)
    App->>Root: setContent { OttApp() }
    Root->>Nav: start destination
    Nav-->>Root: Home / Onboarding
```

Features are enabled by **explicit Koin registration** in the app shell, not by scattered feature flags.

## Related docs

| Document | Purpose |
|----------|---------|
| [MODULE_STRUCTURE.md](../MODULE_STRUCTURE.md) | Folder / Gradle module map |
| [DEPENDENCY_RULES.md](../DEPENDENCY_RULES.md) | Allowed / forbidden edges |
| [PLATFORM_SUPPORT.md](../PLATFORM_SUPPORT.md) | Support matrix & limitations |
| [PLAYER_ARCHITECTURE.md](../PLAYER_ARCHITECTURE.md) | Playback contracts & adapters |
| [FEATURE_EXTRACTION_GUIDE.md](../FEATURE_EXTRACTION_GUIDE.md) | How to remove or extract a feature |
| [TESTING_GUIDE.md](../TESTING_GUIDE.md) | Unit / UI / platform test strategy |
| [BUILD_AND_RUN.md](../BUILD_AND_RUN.md) | Build & run commands |
| [API.md](../API.md) | Play-CMS API definitions, extensibility & job roles |
| [ARCHITECTURE_DECISIONS.md](../ARCHITECTURE_DECISIONS.md) | ADRs / trade-offs |
| [FEASIBILITY_CHECK.md](../FEASIBILITY_CHECK.md) | CMP production feasibility & benchmarks |
