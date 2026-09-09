# Desktop Window Controls, Centered Resizable Window, 1:1 Onboarding Port & Instant Stats Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate Desktop Stats screen opening lag by precomputing stats asynchronously in `:core`, switch Desktop window to OS-native outer window buttons, make the window resizable and centered, and port Android's Onboarding flow 1:1 to Desktop using shared core models with zero duplication.

**Architecture:** 
1. **Core Precomputation Engine**: Move `AggregatedDayRecord` and day-aggregation logic to `:core`. Expose precomputed `StateFlow`s (`aggregatedDayRecords`, `overviewMetrics`, `insights`, `chartItems`) from `SessionRepository` on `Dispatchers.Default`. Both Android `StatsViewModel` and Desktop `StatsScreen` consume these directly, eliminating UI-thread jank and delay.
2. **Desktop Window Architecture**: Update `LatchWindow.kt` to `undecorated = false`, `transparent = false`, `resizable = true`, and `position = WindowPosition(Alignment.Center)` with minimum size bounds. Remove inner `WindowControlButtons` from `LatchTopBar.kt` and `LatchRoot.kt` so native OS outer window controls take over.
3. **1:1 Onboarding Port**: Shared onboarding data in `:core` (`OnboardingModels.kt`), Compose Multiplatform vector drawables for `ic_hand_left.xml`, `ic_hand_right.xml`, and `captive_portal_24px.xml`. Desktop `OnboardingScreen` mirrors Android 1:1: `HandsConnectAnimation`, morphing FAB shape (`circle` -> `rounded square` -> `leaf shape`), rotation animation, credential setup integration, and autostart/tray onboarding, gated by `SettingsManager.hasSeenOnboarding`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Jetpack Compose, Room Multiplatform, StateFlow / Coroutines, Java AWT Windowing.

**Spec:** PR #142 invariants, Issue #35 portal history, Desktop UI/UX requirements.

## Global Constraints

- Never use force push (`--force` or `--force-with-lease`). Forward commits only.
- Never touch `PredictiveSlideBackContainer` in `LatchNavGraph.kt`.
- Preserve `:core` target independence (headless KMP library with no Android/AWT GUI dependencies).
- Validate all local CI commands:
  - `./gradlew :desktop:compileKotlinDesktop :core:desktopTest :cli:build`
  - `./gradlew :core:desktopTest :cli:test :desktop:smoke`
  - `./gradlew assembleDebug testDebugUnitTest :app:lintDebug`

---

### Task 1: Move AggregatedDayRecord & Day Aggregation to :core

**Files:**
- Create: `core/src/commonMain/kotlin/com/vinnovateit/latch/core/stats/StatsAggregationEngine.kt`
- Modify: `core/src/commonMain/kotlin/com/vinnovateit/latch/core/model/StatsModels.kt`
- Modify: `app/src/main/java/com/vinnovateit/latch/features/stats/StatsViewModel.kt:30-45`
- Test: `core/src/desktopTest/kotlin/com/vinnovateit/latch/core/stats/StatsAggregationEngineTest.kt`

**Interfaces:**
- Produces: `data class AggregatedDayRecord(...)` in `com.vinnovateit.latch.core.model`
- Produces: `fun aggregateDays(sessions: List<PortalSessionRecord>, nowMillis: Long = System.currentTimeMillis()): List<AggregatedDayRecord>` in `com.vinnovateit.latch.core.stats`

- [ ] **Step 1: Write failing test for aggregateDays in :core**

```kotlin
package com.vinnovateit.latch.core.stats

import com.vinnovateit.latch.core.model.PortalSessionRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsAggregationEngineTest {
    @Test
    fun testAggregateDaysGroupsAndCalculatesCorrectly() {
        val now = 1773000000000L
        val session1 = PortalSessionRecord(
            location = "Hostel",
            macAddress = "AA:BB:CC:DD:EE:FF",
            loginTime = now - 1000L,
            logoutTime = now,
            durationFormatted = "1s",
            durationMillis = 1000L,
            uploadBytes = 200L,
            downloadBytes = 800L,
            totalBytes = 1000L,
        )
        val session2 = PortalSessionRecord(
            location = "Hostel",
            macAddress = "AA:BB:CC:DD:EE:FF",
            loginTime = now - 2000L,
            logoutTime = now - 1000L,
            durationFormatted = "1s",
            durationMillis = 1000L,
            uploadBytes = 300L,
            downloadBytes = 700L,
            totalBytes = 1000L,
        )

        val aggregated = aggregateDays(listOf(session1, session2), nowMillis = now)
        assertEquals(1, aggregated.size)
        val day = aggregated[0]
        assertEquals(2, day.sessionCount)
        assertEquals(2000L, day.totalBytes)
        assertEquals(1500L, day.downloadBytes)
        assertEquals(500L, day.uploadBytes)
        assertEquals(2000L, day.totalDurationMillis)
        assertTrue(day.isToday)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:desktopTest --tests "com.vinnovateit.latch.core.stats.StatsAggregationEngineTest"`
Expected: FAIL (unresolved reference: `aggregateDays`)

- [ ] **Step 3: Implement AggregatedDayRecord & aggregateDays in :core**

In `core/src/commonMain/kotlin/com/vinnovateit/latch/core/model/StatsModels.kt`:
```kotlin
data class AggregatedDayRecord(
    val dayTimestamp: Long,
    val dateFormatted: String,
    val downloadBytes: Long,
    val uploadBytes: Long,
    val totalBytes: Long,
    val downloadFormatted: Pair<String, String>,
    val uploadFormatted: Pair<String, String>,
    val totalFormatted: Pair<String, String>,
    val sessionCount: Int,
    val totalDurationMillis: Long,
    val durationFormatted: String,
    val isToday: Boolean = false,
)
```

In `core/src/commonMain/kotlin/com/vinnovateit/latch/core/stats/StatsAggregationEngine.kt`:
```kotlin
package com.vinnovateit.latch.core.stats

import com.vinnovateit.latch.core.model.AggregatedDayRecord
import com.vinnovateit.latch.core.model.PortalSessionRecord
import java.text.SimpleDateFormat
import java.util.Locale

private val dayKeyFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
private val displayDateFormat = ThreadLocal.withInitial { SimpleDateFormat("EEE, dd MMM yyyy", Locale.US) }

fun aggregateDays(
    sessions: List<PortalSessionRecord>,
    nowMillis: Long = System.currentTimeMillis(),
): List<AggregatedDayRecord> {
    val dayKeyFmt = dayKeyFormat.get() ?: SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val displayFmt = displayDateFormat.get() ?: SimpleDateFormat("EEE, dd MMM yyyy", Locale.US)
    val todayKey = dayKeyFmt.format(java.util.Date(nowMillis))

    return sessions
        .filter { it.loginTime > 0 }
        .groupBy { dayKeyFmt.format(java.util.Date(it.loginTime)) }
        .map { (dateKey, daySessions) ->
            val first = daySessions.first()
            val dl = daySessions.sumOf { it.downloadBytes }
            val ul = daySessions.sumOf { it.uploadBytes }
            val total = daySessions.sumOf { it.totalBytes.coerceAtLeast(it.downloadBytes + it.uploadBytes) }
            val totalDur = daySessions.sumOf { it.durationMillis }
            val isToday = dateKey == todayKey
            AggregatedDayRecord(
                dayTimestamp = first.loginTime,
                dateFormatted = if (isToday) "Today" else displayFmt.format(java.util.Date(first.loginTime)),
                downloadBytes = dl,
                uploadBytes = ul,
                totalBytes = total,
                downloadFormatted = formatBytes(dl),
                uploadFormatted = formatBytes(ul),
                totalFormatted = formatBytes(total),
                sessionCount = daySessions.size,
                totalDurationMillis = totalDur,
                durationFormatted = formatDurationDynamic(totalDur),
                isToday = isToday,
            )
        }
        .sortedByDescending { it.dayTimestamp }
}
```

Update Android `StatsViewModel.kt` to use `com.vinnovateit.latch.core.model.AggregatedDayRecord` and `aggregateDays(sessions)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:desktopTest --tests "com.vinnovateit.latch.core.stats.StatsAggregationEngineTest"` and `./gradlew :app:compileDebugKotlin`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/commonMain/kotlin/com/vinnovateit/latch/core/model/StatsModels.kt core/src/commonMain/kotlin/com/vinnovateit/latch/core/stats/StatsAggregationEngine.kt core/src/desktopTest/kotlin/com/vinnovateit/latch/core/stats/StatsAggregationEngineTest.kt app/src/main/java/com/vinnovateit/latch/features/stats/StatsViewModel.kt
git commit -m "refactor(core): move AggregatedDayRecord and aggregateDays to :core"
```

---

### Task 2: Background Precomputation in SessionRepository (Instant Stats Load)

**Files:**
- Modify: `core/src/commonMain/kotlin/com/vinnovateit/latch/core/domain/SessionRepository.kt:70-120`
- Test: `core/src/desktopTest/kotlin/com/vinnovateit/latch/core/domain/PortalSessionRepositoryTest.kt`

**Interfaces:**
- Produces: `SessionRepository.aggregatedDayRecords: StateFlow<List<AggregatedDayRecord>>`
- Produces: `SessionRepository.overviewMetrics: StateFlow<StatsOverviewMetrics>`
- Produces: `SessionRepository.statsInsights: StateFlow<StatsInsights>`
- Produces: `SessionRepository.chartItems: StateFlow<List<HistoryChartItem>>`

- [ ] **Step 1: Write test checking precomputed flows in SessionRepository**

```kotlin
    @Test
    fun testPrecomputedStatsFlowsEmitWhenPortalHistoryUpdated() = runTest {
        val session = PortalSessionRecord(
            location = "Hostel",
            macAddress = "AA:BB:CC",
            loginTime = System.currentTimeMillis(),
            logoutTime = System.currentTimeMillis() + 60_000L,
            durationFormatted = "1m",
            durationMillis = 60_000L,
            uploadBytes = 1_000_000L,
            downloadBytes = 4_000_000L,
            totalBytes = 5_000_000L,
        )
        fakeDao.insertPortalSession(session.toEntity())
        repository.initialize()
        advanceUntilIdle()

        val dayRecords = repository.aggregatedDayRecords.value
        assertEquals(1, dayRecords.size)
        val metrics = repository.overviewMetrics.value
        assertEquals(5_000_000L, metrics.totalBytes)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:desktopTest --tests "com.vinnovateit.latch.core.domain.PortalSessionRepositoryTest"`
Expected: FAIL (`aggregatedDayRecords` not resolved)

- [ ] **Step 3: Implement precomputation in SessionRepository**

In `SessionRepository.kt`:
```kotlin
    private val _aggregatedDayRecords = MutableStateFlow<List<AggregatedDayRecord>>(emptyList())
    val aggregatedDayRecords: StateFlow<List<AggregatedDayRecord>> = _aggregatedDayRecords.asStateFlow()

    private val _overviewMetrics = MutableStateFlow(computeMetrics(emptyList()))
    val overviewMetrics: StateFlow<StatsOverviewMetrics> = _overviewMetrics.asStateFlow()

    private val _statsInsights = MutableStateFlow(computeStatsInsights(emptyList()))
    val statsInsights: StateFlow<StatsInsights> = _statsInsights.asStateFlow()

    private val _chartItems = MutableStateFlow<List<HistoryChartItem>>(emptyList())
    val chartItems: StateFlow<List<HistoryChartItem>> = _chartItems.asStateFlow()
```
Inside `initialize()`:
```kotlin
        scope.launch(Dispatchers.Default) {
            portalHistory.collect { records ->
                val nonZero = records.filter { it.uploadBytes > 0L || it.downloadBytes > 0L }
                _aggregatedDayRecords.value = aggregateDays(nonZero)
                _overviewMetrics.value = computeMetrics(nonZero)
                _statsInsights.value = computeStatsInsights(nonZero)
                _chartItems.value = computeChartItems(nonZero)
            }
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:desktopTest --tests "com.vinnovateit.latch.core.domain.PortalSessionRepositoryTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/commonMain/kotlin/com/vinnovateit/latch/core/domain/SessionRepository.kt core/src/desktopTest/kotlin/com/vinnovateit/latch/core/domain/PortalSessionRepositoryTest.kt
git commit -m "feat(core): precompute stats aggregation, metrics, insights and chart flows"
```

---

### Task 3: Optimize Desktop StatsScreen with Precomputed Flows

**Files:**
- Modify: `desktop/src/commonMain/kotlin/com/vinnovateit/latch/ui/screens/StatsScreen.kt`

- [ ] **Step 1: Replace UI-thread computations with StateFlow collection in StatsScreen.kt**

Replace lines 182-225 in `StatsScreen.kt`:
Old:
```kotlin
    val nonZeroHistory = remember(portalHistory) {
        portalHistory.filter { it.uploadBytes > 0L || it.downloadBytes > 0L }
    }
    val metrics = remember(nonZeroHistory) { computeMetrics(nonZeroHistory) }
    val insights = remember(nonZeroHistory) { computeStatsInsights(nonZeroHistory) }
    val chartItems = remember(nonZeroHistory, liveStatus) {
        val liveRx = liveStatus?.totalRxBytes ?: 0L
        val liveTx = liveStatus?.totalTxBytes ?: 0L
        computeChartItems(nonZeroHistory, liveRxBytes = liveRx, liveTxBytes = liveTx)
    }
    val allDayRecords = remember(nonZeroHistory, todayKey) { ... }
```
New:
```kotlin
    val allDayRecords by sessions.aggregatedDayRecords.collectAsStateWithLifecycle()
    val metrics by sessions.overviewMetrics.collectAsStateWithLifecycle()
    val insights by sessions.statsInsights.collectAsStateWithLifecycle()
    val chartItems by sessions.chartItems.collectAsStateWithLifecycle()
```
Remove empty-history blocking spinner so screen renders immediately using cached database data.

- [ ] **Step 2: Verify desktop compilation and smoke tests**

Run: `./gradlew :desktop:compileKotlinDesktop :desktop:smoke`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add desktop/src/commonMain/kotlin/com/vinnovateit/latch/ui/screens/StatsScreen.kt
git commit -m "perf(desktop): consume precomputed stats flows for instant screen opening"
```

---

### Task 4: Desktop Window Outer Controls, Resizable & Centered Window

**Files:**
- Modify: `desktop/src/commonMain/kotlin/com/vinnovateit/latch/desktop/LatchWindow.kt`
- Modify: `desktop/src/commonMain/kotlin/com/vinnovateit/latch/ui/LatchRoot.kt:60-90, 278-285`
- Modify: `desktop/src/commonMain/kotlin/com/vinnovateit/latch/ui/components/LatchTopBar.kt:55-98`
- Modify: `desktop/src/desktopMain/kotlin/com/vinnovateit/latch/desktop/Main.kt:161-187`

**Interfaces:**
- `LatchWindow`: `undecorated = false`, `transparent = false`, `resizable = true`, `position = WindowPosition(Alignment.Center)`.
- `LatchRoot`: No `onMinimize` / `onClose` required, remove `WindowControlButtons` inner overlay.

- [ ] **Step 1: Update LatchWindow to decorated, resizable, and centered**

In `LatchWindow.kt`:
```kotlin
@Composable
internal fun LatchWindow(
    visible: Boolean,
    restoreTrigger: Int = 0,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val initialSize = remember { DpSize(420.dp, 740.dp) }
    val state = rememberWindowState(
        position = WindowPosition(Alignment.Center),
        size = initialSize,
    )

    LaunchedEffect(visible, restoreTrigger) {
        if (visible) {
            state.isMinimized = false
        }
    }

    Window(
        visible = visible,
        onCloseRequest = onCloseRequest,
        state = state,
        resizable = true,
        undecorated = false,
        transparent = false,
        title = "Latch",
        icon = remember { LatchIcon.brand() },
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = java.awt.Dimension(360, 600)
        }

        LatchTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                content()
            }
        }
    }
}
```

- [ ] **Step 2: Remove inner WindowControlButtons overlay from LatchRoot.kt and LatchTopBar.kt**

- In `LatchRoot.kt`: remove lines 278-284 (`WindowControlButtons(...)`).
- In `LatchRoot.kt`: remove `onMinimize` and `onClose` parameters.
- In `LatchTopBar.kt`: remove unused `WindowControlButtons` composable.

- [ ] **Step 3: Update Main.kt to pass clean content block to LatchWindow**

- [ ] **Step 4: Verify desktop compilation**

Run: `./gradlew :desktop:compileKotlinDesktop`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add desktop/src/commonMain/kotlin/com/vinnovateit/latch/desktop/LatchWindow.kt desktop/src/commonMain/kotlin/com/vinnovateit/latch/ui/LatchRoot.kt desktop/src/commonMain/kotlin/com/vinnovateit/latch/ui/components/LatchTopBar.kt desktop/src/desktopMain/kotlin/com/vinnovateit/latch/desktop/Main.kt
git commit -m "feat(desktop): use outer window buttons, enable window resizing, and center window"
```

---

### Task 5: 1:1 Onboarding Port from Android to Desktop

**Files:**
- Create: `core/src/commonMain/kotlin/com/vinnovateit/latch/core/onboarding/OnboardingModels.kt`
- Create: `desktop/src/commonMain/composeResources/drawable/ic_hand_left.xml`
- Create: `desktop/src/commonMain/composeResources/drawable/ic_hand_right.xml`
- Create: `desktop/src/commonMain/composeResources/drawable/captive_portal_24px.xml`
- Create: `desktop/src/commonMain/kotlin/com/vinnovateit/latch/ui/onboarding/DesktopOnboardingScreen.kt`
- Create: `desktop/src/commonMain/kotlin/com/vinnovateit/latch/ui/onboarding/DesktopOnboardingComponents.kt`
- Modify: `desktop/src/commonMain/kotlin/com/vinnovateit/latch/ui/LatchRoot.kt`

**Interfaces:**
- Produces: `DesktopOnboardingScreen(onComplete: () -> Unit, onNavigateToCredentials: () -> Unit)`
- Produces: `HandsConnectAnimation()` with vector hands
- Produces: `LatchSetupBottomBar` with morphing FAB button and page indicator

- [ ] **Step 1: Copy XML vector drawables to desktop composeResources/drawable**

Copy `app/src/main/res/drawable/ic_hand_left.xml`, `ic_hand_right.xml`, and `captive_portal_24px.xml` into `desktop/src/commonMain/composeResources/drawable/`.

- [ ] **Step 2: Create shared OnboardingModels in :core**

In `core/src/commonMain/kotlin/com/vinnovateit/latch/core/onboarding/OnboardingModels.kt`:
```kotlin
package com.vinnovateit.latch.core.onboarding

data class OnboardingSlide(
    val title: String,
    val description: String,
)
```

- [ ] **Step 3: Implement DesktopOnboardingComponents.kt**

- `HandsConnectAnimation`: Port from `app/src/main/java/com/vinnovateit/latch/common/ui/ThemedDrawables.kt`.
- `LatchSetupBottomBar`: Port from `app/src/main/java/com/vinnovateit/latch/features/onboarding/components/OnboardingNav.kt` with morphing FAB shape (`circle` -> `rounded square` -> `leaf shape`), rotation animation, next/finish button, and page indicator dots.

- [ ] **Step 4: Implement DesktopOnboardingScreen.kt**

Port Android's 6 slides:
1. Welcome to Latch (Moderniz font, HandsConnectAnimation, Satoshi text)
2. How it Works (Captive portal icon, explanation)
3. Background & Tray (Desktop counterpart of notification service)
4. Your Account (Credentials setup with direct jump to `CredentialsScreen`)
5. Convenient Access (System Tray / Shortcuts)
6. You're Ready! (Green checkmark, Finish button)
On finish: calls `SettingsManager.setHasSeenOnboarding(true)`.

- [ ] **Step 5: Wire DesktopOnboardingScreen into LatchRoot.kt**

In `LatchRoot.kt`:
```kotlin
val hasSeenOnboarding by SettingsManager.hasSeenOnboarding.collectAsStateWithLifecycle()
...
val currentRootScreen = when {
    !hasSeenOnboarding -> "Onboarding"
    !hasCredentials || editingCredentials -> "Credentials"
    showAbout -> "About"
    showUpdateScreen -> "Update"
    else -> "Main"
}
```

- [ ] **Step 6: Verify compilation and tests**

Run: `./gradlew :desktop:compileKotlinDesktop :core:desktopTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add core/src/commonMain/kotlin/com/vinnovateit/latch/core/onboarding/OnboardingModels.kt desktop/src/commonMain/composeResources/drawable/ desktop/src/commonMain/kotlin/com/vinnovateit/latch/ui/onboarding/ desktop/src/commonMain/kotlin/com/vinnovateit/latch/ui/LatchRoot.kt
git commit -m "feat(desktop): port android onboarding 1:1 with morphing fab and shared core models"
```

---

### Task 6: Full Verification & Release Packaging

**Files:**
- All modified files

- [ ] **Step 1: Run complete multiplatform CI suite locally**
Run:
```bash
./gradlew assembleDebug testDebugUnitTest :app:lintDebug
./gradlew :desktop:compileKotlinDesktop :core:desktopTest :cli:build
./gradlew :core:desktopTest :cli:test :desktop:smoke
```
Expected: All suites PASS.

- [ ] **Step 2: Package Release RPM**
Run:
```bash
./gradlew :desktop:packageReleaseRpm
```
Expected: RPM generated in `desktop/build/compose/binaries/main-release/rpm/`.

- [ ] **Step 3: Push forward commit to origin**
Run:
```bash
git push origin feat/portal-history-stats-redesign
```
(Strict Rule: NO `--force`).
