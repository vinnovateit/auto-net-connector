# Project Guidelines & Architecture Notes

## 1. Android Compose Navigation & Predictive Back (STRICT ARCHITECTURAL INVARIANT)
- **Status**: Verified and mandated by the user. NEVER remove, simplify, rework, or refactor this implementation.
- **Implementation Design (`LatchNavGraph.kt`)**:
  - `PredictiveSlideBackContainer`: Custom wrapper utilizing Android's `PredictiveBackHandler` and Compose `Animatable(0f)`.
  - **Gesture Tracking**: `progress.snapTo(event.progress)` tracks finger movement continuously during the edge swipe.
  - **Commit on Release**: When the user commits the back gesture, `progress.animateTo(1f)` smoothly finishes the slide to the right edge *before* calling `onBackPressed()`. This eliminates any frame jerking, momentary flashes, or snap-backs.
  - **Parallax Background**: Renders `backgroundContent` (`HomeScreen`) directly behind the sliding child screen (`SETTINGS`, `STATS`, `MEET_THE_TEAM`), translating it with 1/3 parallax `(progress.value - 1f) * (screenWidth / 3f)` and a subtle black depth scrim (`alpha = (1f - progress.value) * 0.25f`).
  - **Cancellation**: If the user cancels the swipe, `progress.animateTo(0f)` smoothly springs back to the origin.
  - **Programmatic Back Clicks**: Top bar back buttons invoke `triggerBack` which plays the same `progress.animateTo(1f)` slide-out.
  - **NavHost Pop Transitions**: `popEnterTransition` and `popExitTransition` are set to `EnterTransition.None` / `ExitTransition.None` so `NavHost` does not attempt to replay a duplicate animation on top of the container.
- **Rule**: Never replace this with standard `NavHost` scale/fade defaults, do not remove `PredictiveSlideBackContainer`, and do not attempt to "simplify" it into single-line calls.

## 2. AGP & Gradle Architecture
- **AGP 9 Modern DSL**: The project uses AGP 9 with `android.builtInKotlin=true` and `android.newDsl=true` in `gradle.properties`.
- **Kotlin Multiplatform Library**: The `:core` module applies `com.android.kotlin.multiplatform.library` configured via `kotlin { android { ... } }`.
- **App Module**: The `:app` module applies `com.android.application` and compiles Kotlin directly through AGP's built-in Kotlin integration.

## 3. Database & Target Separation
- **Room Multiplatform**: Room expect/actual database constructors live in `commonMain` with target compilers assigned via `dependencies { add("kspDesktop", ...) ; add("kspAndroid", ...) }`.
- **Desktop Module**: JVM Compose desktop application configured with jpackage native distribution bundlers.
- **CLI Module**: Lightweight JVM-only application independent of GUI dependencies.

## 4. Security Invariants, Network Isolation & CI Gates (Mandated via PR #142)
- **Local CI Validation Commands (Run before push/completion)**:
  - Android: `./gradlew assembleDebug testDebugUnitTest :app:lintDebug`
  - Desktop & Core: `./gradlew :desktop:compileKotlinDesktop :core:desktopTest :cli:build`
  - Smoke tests: `./gradlew :core:desktopTest :cli:test :desktop:smoke`
- **CI Trigger Invariant**:
  - `android.yml` and `desktop.yml` run on `pull_request` targeting `main`. If PR branch has conflicts with `main`, GitHub cannot generate the merge ref, silently blocking CI from running. Always keep PR rebased/merged cleanly against `main`.
- **Portal Credential Security (STRICT)**:
  - **No Cellular Leaks**: Portal sync (`fetchHistory`) MUST bind directly to the Wi-Fi `NetworkHandle`. Never fall back to the default network on Android (which stays cellular on captive portals, leaking credentials in plaintext off-network).
  - **Account WebView Host Gate**: The credential WebView must NEVER navigate to or inject credentials into non-portal hosts. Validate URL host (`136.233.9.110`) and delegate all non-portal links to external system browser via `platform.systemActions.openUrl`.
- **Engine Network Handle Resolution**:
  - `LatchEngine` must resolve and cache `activeHandle()` even when starting already connected (when desktop pollers do not emit `WifiEvent.Available`), so subsequent `WifiEvent.Lost` triggers session teardown.
- **Health Check & Sync Concurrency**:
  - Keep 3-strike debounce on health checks and unlatch before re-login attempts.
  - Claim portal sync slots atomically to prevent overlapping background syncs.
- **Settings & Theme Integrity**:
  - `SettingsManager.clearAll()` must wipe all settings, not a subset.
  - Preserve theme color resolution mappings (e.g. Pink must resolve to Pink, not fallback to Red).
- **Git & Push Safety (ABSOLUTE INVARIANT)**:
  - NEVER use force push (`git push --force` or `--force-with-lease`) under any circumstances. All git updates must be standard forward commits or merges to preserve team commits and avoid overwriting work.


