# Testing Guide

Testing is part of the architecture, not a follow-up. Shared business logic must be testable on the JVM without launching Android or iOS.

## Test pyramid

```mermaid
flowchart TB
    UI["Platform UI tests<br/>Compose / XCUITest"]
    Comp["CMP / Android Compose component tests"]
    Integ["Repository + DI integration tests"]
    Unit["Domain / VM / Player controller unit tests"]

    UI --> Comp
    Comp --> Integ
    Integ --> Unit
```

## What to test where

| Layer | Tooling | Runs on |
|-------|---------|---------|
| Use cases / domain validation | `kotlin.test` + coroutines-test | `commonTest` (JVM) |
| Repository + mappers | `kotlin.test`, fakes | `commonTest` |
| ViewModels / state transitions | coroutines-test, Turbine optional | `commonTest` |
| Player controller | `FakePlayerEngine` | `commonTest` |
| Design system / cards | CMP UI tests where available | JVM / Android |
| Android screens | Compose UI Test | Android instrumented |
| Android TV focus | Compose + Espresso / UI Automator | Android TV emulator |
| iOS / macOS UI | XCTest / XCUITest | Mac + Xcode |
| tvOS UI | XCUITest | Mac + Xcode + tvOS simulator |

## Mandatory example coverage

The repo should include sample tests for at least:

1. A **domain use case**
2. A **ViewModel** state transition
3. A **repository** (mock catalog)
4. A **reusable Compose component** (e.g. content card)
5. A **player controller** with `FakePlayerEngine`

## Conventions

- Prefer **fakes** over heavy mocking frameworks in common code.
- Use **deterministic dispatchers** (`StandardTestDispatcher`).
- Mark Compose nodes with **test tags** / semantics for stable selectors.
- Do **not** claim a test passed unless it was executed in this environment.
- Document skipped targets (e.g. no Xcode) as **unverified**.

## Commands (typical)

```bash
# Shared unit tests (JVM)
./gradlew :core:domain:cleanAllTests :core:domain:allTests
./gradlew :core:data:allTests
./gradlew :features:home:impl:desktopTest

# Android unit / connected (when emulator available)
./gradlew :apps:androidApp:testDebugUnitTest
./gradlew :apps:androidApp:connectedDebugAndroidTest

# Desktop
./gradlew :apps:desktopApp:test
```

### Executed on this machine (2026-11-20)

```bash
./gradlew testDebugUnitTest desktopTest --console=plain
```

**Total:** 23 suites, **46 tests passed** (0 failures, 0 errors, 0 skipped):

| Suite | Target(s) | Tests | Result |
|-------|-----------|-------|--------|
| `SqlDelightDataSourcesTest` | `desktopTest` | 2 | Passed |
| `CmsCatalogMapperTest` | `desktopTest`, `testDebugUnitTest` | 4 × 2 | Passed |
| `CmsCatalogRepositoryTest` | `desktopTest`, `testDebugUnitTest` | 1 × 2 | Passed |
| `InMemoryWatchlistRepositoryTest` | `desktopTest`, `testDebugUnitTest` | 1 × 2 | Passed |
| `ImageResolverTest` | `desktopTest`, `testDebugUnitTest` | 4 × 2 | Passed |
| `GetMenuAndLayoutFeedUseCaseTest` | `desktopTest`, `testDebugUnitTest` | 2 × 2 | Passed |
| `SearchChannelsUseCaseTest` | `desktopTest`, `testDebugUnitTest` | 2 × 2 | Passed |
| `GetHomeFeedUseCaseTest` | `desktopTest`, `testDebugUnitTest` | 2 × 2 | Passed |
| `PlayerControllerTest` | `desktopTest`, `testDebugUnitTest` | 2 × 2 | Passed |
| `HomeViewModelTest` | `desktopTest`, `testDebugUnitTest` | 1 × 2 | Passed |
| `PlayerViewModelTest` | `desktopTest`, `testDebugUnitTest` | 2 × 2 | Passed |
| `AppMenuViewModelTest` | `desktopTest`, `testDebugUnitTest` | 1 × 2 | Passed |

Also verified on physical devices & Web (`2026-11-20`):
- **iPhone 13 (`com.ott.platform.ios`)**: Built, installed, and launched via Xcode/devicectl; verified SQLDelight v3 (`ott_platform.db`) synced and cached `1` dynamic menu, `1` layout, and all `7` Play-CMS rails (`33` assets across `Landscape`, `Portrait`, `new`, `top_10`, `mosaic`, `grid`, `editorial`).
- **Samsung Galaxy Tab (`SM_P613`, `com.ott.platform.android`)**: Installed via `:apps:androidApp:installDebug` and launched; verified SQLDelight v3 (`ott_platform.db`) synced and cached Play-CMS menu, layout, and rails.
- **Web (`wasmJs`, `apps/webApp`)**: Production Wasm bundle (`:apps:webApp:wasmJsBrowserDistribution`) tested against Play-CMS (CORS enabled) and `localStorage` (`ott_web_cms_*_v7`) as an offline-only catalog mirror (network-first sync + prune).

## Test architecture diagram

```mermaid
flowchart LR
    subgraph CommonTest["commonTest"]
        UC["UseCaseTest"]
        VM["ViewModelTest"]
        Repo["RepositoryTest"]
        PC["PlayerControllerTest"]
    end

    subgraph Fakes["core/testing"]
        FakeRepo["FakeContentRepository"]
        FakePlayer["FakePlayerEngine"]
        Fixtures["Catalog fixtures"]
    end

    UC --> Fixtures
    VM --> FakeRepo
    Repo --> Fixtures
    PC --> FakePlayer
```

## Platform UI strategy notes

### Compose Multiplatform

- Put reusable component tests next to `core/ui` / `core/designsystem` when tooling supports the target.
- Keep screens injectable (ViewModel via DI) so UI tests can supply fakes.

### Android TV

- Assert focus moves across shelf rows with D-pad key events.
- Assert focused card has a visible focus indicator.

### Apple platforms

- iOS/macOS: XCUITest against accessibility identifiers mirrored from Compose semantics where possible.
- tvOS: XCUITest against SwiftUI identifiers; shared logic still covered by commonTest.
