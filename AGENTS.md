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

## 5. Code Quality, Lifecycle & Security Invariants (Learned from rugbedbugg Reviews)
- **HttpURLConnection Resource Leakage**:
  - Always disconnect `HttpURLConnection` in a `finally` block (or `use {}`). Never call `disconnect()` solely at the end of the `try` block where network exceptions (`connect()`, timeout) skip it and leak file descriptors / sockets (Issue #66).
- **External Intent Safety (No Uncaught ActivityNotFoundException)**:
  - Any external `Intent` (e.g. `Intent.ACTION_VIEW` for URLs or browser redirects) must be wrapped in try/catch for `ActivityNotFoundException` or routed through `platform.systemActions.openUrl`. Never call `context.startActivity` bare (Issue #77).
- **Telemetry Rates vs Byte Counts**:
  - Graph scaling and speed readouts MUST use rate fields (`rxBps` / `txBps`), NEVER raw delta byte counters (`rxBytes` / `txBytes`). Raw byte counts depend on poll interval duration and warp scale (Issue #67).
  - Stored session reports and history screens must read persisted `maxRxBps` / `maxTxBps` from the database entity, not recompute from `history` (which is empty for historical DB sessions) (Issue #65).
- **Throughput Baseline Resilience**:
  - `ThroughputMonitor.start()` must never early-return on an initial null counter sample. Keep monitor loop active and latch baseline on the first valid sample (`hasBaseline`) so connect-time hiccups don't kill session stats (Issue #60).
- **Credential Storage Cache Invalidation & Salt Hygiene**:
  - Cache flags (e.g. `has_credentials`) must be wiped whenever the encrypted store is reset or cleared (`clearCorruptedState`). Stale cache flags must never disable credential re-entry (Issue #68).
  - Linux credential encryption must combine per-install random salt (`.creds_salt`, 0600 POSIX permissions) with machine/user identifiers, never derive keys solely from static `/etc/machine-id` (Issue #64).
  - Never introduce parallel unencrypted credential storage (e.g. Room `CredentialDatabase`) (Issue #70).
- **Linux Wi-Fi Radio Detection**:
  - Linux Wi-Fi detection must consult rfkill (`/sys/class/rfkill/*/soft` and `/hard` for `type == wlan`) and verify `operstate` rather than treating interface existence as enabled (Issue #61).
- **Zero-Line Files & Dead Receiver Chains**:
  - Never leave empty 0-byte Kotlin files in tree (Issue #78).
  - Do not keep manifest `<receiver>` entries without `<intent-filter>` or dead caller chains (Issue #70, #81).
  - Delete write-only fields (`notificationSent`) immediately (Issue #69).
- **Dependency & Compiler Lockstep**:
  - Kotlin compiler bumps must always be paired with exact matching KSP version.
  - Root `build.gradle.kts` buildscript constraints must match library version declarations.



