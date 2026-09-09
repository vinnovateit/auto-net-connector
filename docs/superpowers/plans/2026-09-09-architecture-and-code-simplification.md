# Architecture Deepening & Code Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the top findings from the Architectural Review and Ponytail Audit: collapse shallow modules, eliminate dead code, unify settings/status flows across platforms, and restore test isolation.

**Architecture:** Deepen `LatchEngine` to directly own connection status lifecycle and terminal auto-reset, eliminate global mutable singletons, purge dead legacy session accumulation and unused forwarders, and unify platform settings and graphics utilities.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Coroutines & StateFlow, Room Multiplatform, Android Jetpack.

**Spec:** Architectural Review Report (`/tmp/architecture-review-1788949192.html`) and Ponytail Audit Report.

## Global Constraints
- Strictly preserve `PredictiveSlideBackContainer` structure and transitions in `LatchNavGraph.kt` (Invariant 1 in `AGENTS.md`).
- Never use force push (`git push --force`). Forward commits and standard merges only.
- Local CI verification must pass before completion:
  - Android: `./gradlew compileDebugKotlin :app:testDebugUnitTest`
  - Desktop & Core: `./gradlew :desktop:compileKotlinDesktop :core:desktopTest :cli:build`
  - Smoke: `./gradlew :desktop:smoke`

---

## Big Task 1: Status Lifecycle Consolidation (Deepen LatchEngine)

### Subtask 1.1: Collapse ConnectionStatusManager into LatchEngine
**Files:**
- Modify: `core/src/commonMain/kotlin/com/vinnovateit/latch/core/wifi/ConnectionStatus.kt`
- Modify: `core/src/commonMain/kotlin/com/vinnovateit/latch/core/engine/LatchEngine.kt`
- Modify: `core/src/desktopTest/kotlin/com/vinnovateit/latch/core/engine/LatchEngineLoginFailureTest.kt`

- [ ] **Step 1: Remove ConnectionStatusManager global singleton from ConnectionStatus.kt**
Delete `object ConnectionStatusManager` from lines 58-76 of `ConnectionStatus.kt`.

- [ ] **Step 2: Add internal status StateFlow and postStatus() method to LatchEngine.kt**
Inside `LatchEngine.kt`:
```kotlin
private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Idle)
override val status: StateFlow<ConnectionStatus> = _status.asStateFlow()
private var statusResetJob: kotlinx.coroutines.Job? = null

private fun postStatus(newStatus: ConnectionStatus) {
    _status.value = newStatus
    statusResetJob?.cancel()
    if (newStatus is ConnectionStatus.Success || newStatus is ConnectionStatus.Failed) {
        statusResetJob = engineScope.launch {
            kotlinx.coroutines.delay(2000)
            if (_status.value == newStatus) {
                _status.value = ConnectionStatus.Idle
            }
        }
    }
}
```
Replace all 15 occurrences of `ConnectionStatusManager.postStatus(...)` with `postStatus(...)`.
Remove `import com.vinnovateit.latch.core.wifi.ConnectionStatusManager`.

- [ ] **Step 3: Update test to read engine.status instead of ConnectionStatusManager.status**
In `LatchEngineLoginFailureTest.kt`:
Remove `import com.vinnovateit.latch.core.wifi.ConnectionStatusManager`.
Change line 63 to `val status = engine.status.value`.

- [ ] **Step 4: Run test suite**
Run: `./gradlew :core:desktopTest`
Expected: All tests pass.

- [ ] **Step 5: Commit**
`git commit -m "refactor(core): collapse ConnectionStatusManager into LatchEngine"`

### Subtask 1.2: Streamline Android Status Modeling
**Files:**
- Modify: `app/src/main/java/com/vinnovateit/latch/platform/EngineStatusBridge.kt`
- Modify: `app/src/main/java/com/vinnovateit/latch/features/wifi/manager/ConnectionStatus.kt`
- Modify: `app/src/main/java/com/vinnovateit/latch/features/home/HomeScreen.kt`

- [ ] **Step 1: Clean up unused import in HomeScreen.kt**
Remove unused `import com.vinnovateit.latch.features.wifi.manager.ConnectionStatus` in `HomeScreen.kt`.

- [ ] **Step 2: Update docstrings and bridge references**
Update `EngineStatusBridge.kt` and `ConnectionStatus.kt` docstrings confirming `ConnectionStatusManager` is eliminated.

- [ ] **Step 3: Run Android and Desktop builds**
Run: `./gradlew compileDebugKotlin :app:testDebugUnitTest :desktop:compileKotlinDesktop`
Expected: All pass.

- [ ] **Step 4: Commit**
`git commit -m "refactor(app): clean up status bridge and unused connection status imports"`

---

## Big Task 2: Dead Code & Legacy Shim Cleanup

### Subtask 2.1: Purge Legacy Session Accumulation and Forwarders
**Files:**
- Modify: `app/src/main/java/com/vinnovateit/latch/features/stats/StatsViewModel.kt`
- Modify: `app/src/main/java/com/vinnovateit/latch/features/stats/StatsScreen.kt`
- Modify: `app/src/main/java/com/vinnovateit/latch/features/stats/components/StatsList.kt`
- Modify: `core/src/commonMain/kotlin/com/vinnovateit/latch/core/stats/ReportUtils.kt`
- Modify: `app/src/main/java/com/vinnovateit/latch/common/util/ReportUtils.kt`
- Modify: `app/src/main/java/com/vinnovateit/latch/navigation/LatchNavGraph.kt`

- [x] **Step 1: Delete unused historyToShow and mergeSessions in StatsViewModel**
Remove deprecated `mergeSessions` and unused `historyToShow` flow.

- [x] **Step 2: Clean up historyToShow in StatsScreen and StatsList**
Remove `historyToShow` parameter and unused empty check.

- [x] **Step 3: Delete legacy generateHtmlReport(List<SessionSummary>) and unused app wrapper**
Delete legacy SessionSummary overload from `core/.../ReportUtils.kt`.
Remove unused `generateHtmlReport` import from `LatchNavGraph.kt`.

- [x] **Step 4: Verify and commit**
Run: `./gradlew :app:testDebugUnitTest :core:desktopTest`
`git commit -m "chore(stats): purge legacy session accumulation and unused report overload"`

### Subtask 2.2: Purge Boilerplate & Unused Capabilities
**Files:**
- Delete: `app/src/main/java/com/vinnovateit/latch/ui/theme/Test.kt`
- Delete: `app/src/test/java/com/vinnovateit/latch/ExampleUnitTest.kt`
- Modify: `app/src/main/java/com/vinnovateit/latch/common/util/StatsUtils.kt`
- Modify: `core/src/commonMain/kotlin/com/vinnovateit/latch/core/platform/Platform.kt`

- [x] **Step 1: Delete Test.kt and ExampleUnitTest.kt**
Remove dead test file in production theme directory and template unit test.

- [x] **Step 2: Remove inline trampoline functions in StatsUtils.kt**
Direct callers to import `com.vinnovateit.latch.core.stats.*`.

- [x] **Step 3: Remove unused supportsDynamicColor capability**
Strip `supportsDynamicColor` property from `PlatformCapabilities` and implementations.

- [x] **Step 4: Verify and commit**
Run: `./gradlew :core:desktopTest :app:testDebugUnitTest :desktop:compileKotlinDesktop`
`git commit -m "chore(core): remove dead template tests and unused capability flags"`

---

## Big Task 3: Settings Architecture Unification

### Subtask 3.1: Route Android Callers to Core SettingsManager
- [x] **Step 1: Wire reactive settings observers in LatchAppGraph**
Listen to `SettingsManager.settingsChanged` for `ACTION_SETTINGS_CHANGED` widget broadcasts, and `SettingsManager.autoLogin` for foreground service start/logout dispatch.
- [x] **Step 2: Update all 18 Android callers to core SettingsManager**
Migrate all UI screens, components, services, and navigation to `com.vinnovateit.latch.core.settings.SettingsManager`.

### Subtask 3.2: Delete Android SettingsManager Shallow Wrapper
- [x] **Step 1: Remove redundant SettingsManager.initialize() calls**
Remove unused context initialization from `MainActivity`, and redirect `TileService` & `WidgetUpdater` to `LatchAppGraph.initialize()`.
- [x] **Step 2: Delete app SettingsManager wrapper and redundant unit test**
Delete `app/.../features/settings/manager/SettingsManager.kt` and `SettingsManagerTest.kt`.
- [x] **Step 3: Run full verification and commit**
Run: `./gradlew assembleDebug testDebugUnitTest :app:lintDebug :desktop:compileKotlinDesktop :core:desktopTest :cli:test :desktop:smoke`
`git commit -m "refactor(settings): unify settings on core SettingsManager and eliminate Android wrapper"`

---

## Big Task 4: Probing & Network Deepening

### Subtask 4.1: Deepen CaptivePortalDetector into PortalProbe
### Subtask 4.2: Shrink LoginResult and Campus SSID Matching
