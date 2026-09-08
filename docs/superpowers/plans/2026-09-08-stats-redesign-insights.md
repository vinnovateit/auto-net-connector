# Stats Redesign & Usage Insights Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the Stats experience by replacing the heavy, endless session list with rich usage insights (Peak Usage Time, Highest Usage Day, Daily/Weekly Averages), fixing bar chart dynamic scaling with slimmer bars, adding a persistent verbose file logger, cleaning date formatting (no weekdays, no year for current year), and moving Export and Resync actions into a top bar 3-dot overflow menu.

**Architecture:** 
- A multiplatform `LatchFileLogger` writes timestamped verbose log entries to a persistent text file (`latch_verbose.log`) across all portal sync, aggregation, and caching operations.
- A pure domain engine `StatsInsightsEngine` in `:core` computes Peak Usage Time (3-hour windows), Highest Usage Day, Daily Average, and Weekly Average from non-zero session records, with clean date formatting.
- Dynamic scaling in `HistoryBarChart` / `PortalDailyBarChart` is fixed by scaling the total bar height relative to dynamic `maxBytes` with proportional download/upload split, using slimmer 10.dp bars.
- In the UI (Android and Desktop), the endless list of past days is removed in favor of `UsageInsightsSection` cards and Today's Sessions, with the complete history accessible via "Export Full Report" in a top bar 3-dot overflow menu.
- Navigation invariant in `AGENTS.md` is strictly preserved: `PredictiveSlideBackContainer` in `LatchNavGraph.kt` is untouched.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose, Compose Multiplatform (Desktop), Room 2.7, Coroutines StateFlow, JUnit 4.

**Spec:** User instructions from conversation:
1. "remove no. of sessions from being shown for oreviosu days remove calendar. reduce the width of the bars, allow the bars to scale in size when bigger value becomes max. fix ths logic of it"
2. "add a verbose logger for what is does everytime. log to a txt fileor something."
3. "remvoe week frombeing shown. only show date. and for current year dont show year."
4. "and isnt it so bad to simply show the lsit of all the sessions and days? instead plan on what else can be shown. such as peak usage time, highest usage day. weekly average etc."
5. "and remove the full list from being shown and user can see that only by exporting list. move export into a 3 dot menu. add the resync histroy into teh 3 dot itself. break this to small tasks and make subagents do these with care."

## Global Constraints

- Never alter, replace, or simplify `PredictiveSlideBackContainer` or its transitions in `LatchNavGraph.kt`.
- Support both Android and JVM Desktop targets.
- Remove weekday names ("Mon", "Sun") from date displays; omit year if date is in the current year.
- Keep communication concise and minimal (`/less` mode).
- All tests must pass: `./gradlew :core:desktopTest :app:testDebugUnitTest`.

---

### Task 1: Persistent Verbose File Logger in `:core`

**Files:**
- Create: `core/src/commonMain/kotlin/com/vinnovateit/latch/core/platform/LatchFileLogger.kt`
- Modify: `core/src/commonMain/kotlin/com/vinnovateit/latch/core/domain/SessionRepository.kt`
- Modify: `core/src/commonMain/kotlin/com/vinnovateit/latch/core/portal/PortalHistoryClient.kt`
- Modify: `app/src/main/java/com/vinnovateit/latch/platform/LatchAppGraph.kt`
- Test: `core/src/desktopTest/kotlin/com/vinnovateit/latch/core/platform/LatchFileLoggerTest.kt`

**Interfaces:**
- Produces: `LatchFileLogger(logFile: java.io.File, maxSizeBytes: Long = 5_000_000L) : Logger`
- Exposes: `log(level: String, tag: String, message: String, throwable: Throwable? = null)`
- In `SessionRepository` and `PortalHistoryClient`: accept optional `logger: Logger = Platform.logger` and log every request, parsed record count, locked date set, and error to `logger.d` / `logger.e`.

- [ ] **Step 1: Write the failing test**

```kotlin
// core/src/desktopTest/kotlin/com/vinnovateit/latch/core/platform/LatchFileLoggerTest.kt
package com.vinnovateit.latch.core.platform

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class LatchFileLoggerTest {
    @Test
    fun testFileLoggerWritesEntriesToFile() {
        val tempDir = createTempDir("logger_test")
        val logFile = File(tempDir, "test_log.txt")
        val logger = LatchFileLogger(logFile)

        logger.d("TEST_TAG", "Debug message 123")
        logger.w("TEST_TAG", "Warning message 456")
        logger.e("TEST_TAG", "Error message 789")

        assertTrue(logFile.exists(), "Log file should exist")
        val content = logFile.readText()
        assertTrue(content.contains("[DEBUG] [TEST_TAG] Debug message 123"))
        assertTrue(content.contains("[WARN] [TEST_TAG] Warning message 456"))
        assertTrue(content.contains("[ERROR] [TEST_TAG] Error message 789"))
        tempDir.deleteRecursively()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:desktopTest --tests "com.vinnovateit.latch.core.platform.LatchFileLoggerTest"`
Expected: FAIL (class not found)

- [ ] **Step 3: Write minimal implementation**

Implement `LatchFileLogger` in `core/src/commonMain/kotlin/com/vinnovateit/latch/core/platform/LatchFileLogger.kt`:
```kotlin
package com.vinnovateit.latch.core.platform

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LatchFileLogger(
    val logFile: File,
    private val maxSizeBytes: Long = 5 * 1024 * 1024L
) : Logger {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        try {
            val parent = logFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            if (logFile.exists() && logFile.length() > maxSizeBytes) {
                val backup = File(parent, "${logFile.name}.old")
                if (backup.exists()) backup.delete()
                logFile.renameTo(backup)
            }
            val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
            FileWriter(logFile, true).use { fw ->
                PrintWriter(fw).use { pw ->
                    pw.println("$timestamp [$level] [$tag] $message")
                    throwable?.let { t ->
                        t.printStackTrace(pw)
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    override fun d(tag: String, message: String) = log("DEBUG", tag, message)
    override fun w(tag: String, message: String) = log("WARN", tag, message)
    override fun e(tag: String, message: String, throwable: Throwable?) = log("ERROR", tag, message, throwable)
}
```

Add `CompositeLogger`:
```kotlin
class CompositeLogger(private val loggers: List<Logger>) : Logger {
    override fun d(tag: String, message: String) = loggers.forEach { it.d(tag, message) }
    override fun w(tag: String, message: String) = loggers.forEach { it.w(tag, message) }
    override fun e(tag: String, message: String, throwable: Throwable?) = loggers.forEach { it.e(tag, message, throwable) }
}
```

Wire `LatchFileLogger` in `SessionRepository`, `PortalHistoryClient`, and Android's `LatchAppGraph.kt`.
In `SessionRepository.syncPortalHistory`:
Log "syncPortalHistory starting...", "incoming records: X", "locked past dates: Y", "merged records: Z".
In `PortalHistoryClient.fetchHistory`:
Log "Requesting portal history URL: ...", "Response code: ...", "Parsed X records".

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:desktopTest --tests "com.vinnovateit.latch.core.platform.LatchFileLoggerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/commonMain/kotlin/com/vinnovateit/latch/core/platform/LatchFileLogger.kt core/src/commonMain/kotlin/com/vinnovateit/latch/core/domain/SessionRepository.kt core/src/commonMain/kotlin/com/vinnovateit/latch/core/portal/PortalHistoryClient.kt core/src/desktopTest/kotlin/com/vinnovateit/latch/core/platform/LatchFileLoggerTest.kt app/src/main/java/com/vinnovateit/latch/platform/LatchAppGraph.kt
git commit -m "feat(core): add persistent verbose file logger for portal sync operations"
```

---

### Task 2: Usage Insights Computation Engine (`StatsInsightsEngine`)

**Files:**
- Create: `core/src/commonMain/kotlin/com/vinnovateit/latch/core/stats/StatsInsightsEngine.kt`
- Test: `core/src/desktopTest/kotlin/com/vinnovateit/latch/core/stats/StatsInsightsEngineTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class StatsInsights(
      val peakUsageTimeWindow: String, // e.g. "06:00 PM – 09:00 PM" or "N/A"
      val highestUsageDayFormatted: String, // e.g. "14.2 GB"
      val highestUsageDayDate: String, // e.g. "03 Mar" (or "03 Mar 2025" for older year)
      val highestUsageDayBytes: Long,
      val dailyAverageBytes: Long,
      val dailyAverageFormatted: Pair<String, String>,
      val weeklyAverageBytes: Long,
      val weeklyAverageFormatted: Pair<String, String>,
      val mostActiveSessionDurationFormatted: String,
      val mostActiveSessionBytes: Long,
      val mostActiveSessionFormatted: Pair<String, String>,
      val activeDaysCount: Int,
  )
  
  fun computeStatsInsights(sessions: List<PortalSessionRecord>, nowMillis: Long = System.currentTimeMillis()): StatsInsights
  fun formatInsightDate(timestamp: Long, nowMillis: Long = System.currentTimeMillis()): String
  ```

- [ ] **Step 1: Write the failing test**

```kotlin
// core/src/desktopTest/kotlin/com/vinnovateit/latch/core/stats/StatsInsightsEngineTest.kt
package com.vinnovateit.latch.core.stats

import com.vinnovateit.latch.core.model.PortalSessionRecord
import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsInsightsEngineTest {

    @Test
    fun testFormatInsightDateOmitsWeekdayAndCurrentYear() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 8, 12, 0, 0)
        }
        val fixedNow = cal.timeInMillis

        // Same year (2026) -> "08 Mar"
        val formattedSameYear = formatInsightDate(fixedNow, fixedNow)
        assertEquals("08 Mar", formattedSameYear)

        // Previous year (2025) -> "08 Mar 2025"
        cal.set(2025, Calendar.MARCH, 8, 12, 0, 0)
        val formattedOlderYear = formatInsightDate(cal.timeInMillis, fixedNow)
        assertEquals("08 Mar 2025", formattedOlderYear)
    }

    @Test
    fun testComputeStatsInsightsCalculatesAccurateMetrics() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 1, 14, 0, 0) }
        val session1 = PortalSessionRecord(
            location = "Hostel",
            macAddress = "AA:BB:CC",
            loginTime = cal.timeInMillis,
            logoutTime = cal.timeInMillis + 3600_000L,
            durationFormatted = "01:00:00",
            durationMillis = 3600_000L,
            uploadBytes = 500_000_000L,
            downloadBytes = 1_500_000_000L,
            totalBytes = 2_000_000_000L,
        )

        cal.set(2026, Calendar.MARCH, 2, 20, 0, 0)
        val session2 = PortalSessionRecord(
            location = "Hostel",
            macAddress = "AA:BB:CC",
            loginTime = cal.timeInMillis,
            logoutTime = cal.timeInMillis + 7200_000L,
            durationFormatted = "02:00:00",
            durationMillis = 7200_000L,
            uploadBytes = 1_000_000_000L,
            downloadBytes = 4_000_000_000L,
            totalBytes = 5_000_000_000L,
        )

        val insights = computeStatsInsights(listOf(session1, session2), cal.timeInMillis)
        assertEquals("02 Mar", insights.highestUsageDayDate)
        assertEquals(5_000_000_000L, insights.highestUsageDayBytes)
        assertEquals(2, insights.activeDaysCount)
        assertEquals(3_500_000_000L, insights.dailyAverageBytes) // (2GB + 5GB)/2
        assertEquals(24_500_000_000L, insights.weeklyAverageBytes) // 3.5GB * 7
        assertTrue(insights.peakUsageTimeWindow.contains("PM"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:desktopTest --tests "com.vinnovateit.latch.core.stats.StatsInsightsEngineTest"`
Expected: FAIL (class not found)

- [ ] **Step 3: Implement `StatsInsightsEngine`**

Implement `core/src/commonMain/kotlin/com/vinnovateit/latch/core/stats/StatsInsightsEngine.kt`:
```kotlin
package com.vinnovateit.latch.core.stats

import com.vinnovateit.latch.core.model.PortalSessionRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class StatsInsights(
    val peakUsageTimeWindow: String,
    val highestUsageDayFormatted: String,
    val highestUsageDayDate: String,
    val highestUsageDayBytes: Long,
    val dailyAverageBytes: Long,
    val dailyAverageFormatted: Pair<String, String>,
    val weeklyAverageBytes: Long,
    val weeklyAverageFormatted: Pair<String, String>,
    val mostActiveSessionDurationFormatted: String,
    val mostActiveSessionBytes: Long,
    val mostActiveSessionFormatted: Pair<String, String>,
    val activeDaysCount: Int,
)

fun formatInsightDate(timestamp: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val recordYear = cal.get(Calendar.YEAR)
    val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val currentYear = nowCal.get(Calendar.YEAR)

    val pattern = if (recordYear == currentYear) "dd MMM" else "dd MMM yyyy"
    return SimpleDateFormat(pattern, Locale.US).format(cal.time)
}

fun computeStatsInsights(
    sessions: List<PortalSessionRecord>,
    nowMillis: Long = System.currentTimeMillis()
): StatsInsights {
    val nonZero = sessions.filter { it.loginTime > 0 && (it.uploadBytes > 0 || it.downloadBytes > 0) }
    if (nonZero.isEmpty()) {
        val zeroPair = formatBytes(0L)
        return StatsInsights(
            peakUsageTimeWindow = "N/A",
            highestUsageDayFormatted = "0 B",
            highestUsageDayDate = "N/A",
            highestUsageDayBytes = 0L,
            dailyAverageBytes = 0L,
            dailyAverageFormatted = zeroPair,
            weeklyAverageBytes = 0L,
            weeklyAverageFormatted = zeroPair,
            mostActiveSessionDurationFormatted = "0m",
            mostActiveSessionBytes = 0L,
            mostActiveSessionFormatted = zeroPair,
            activeDaysCount = 0,
        )
    }

    // 1. Peak usage 3-hour window
    val windowBytes = LongArray(8)
    val windowCal = Calendar.getInstance()
    for (s in nonZero) {
        windowCal.timeInMillis = s.loginTime
        val hour = windowCal.get(Calendar.HOUR_OF_DAY)
        val windowIdx = (hour / 3).coerceIn(0, 7)
        val bytes = s.totalBytes.coerceAtLeast(s.downloadBytes + s.uploadBytes)
        windowBytes[windowIdx] += bytes
    }
    var bestWindowIdx = 0
    var maxWindowBytes = -1L
    for (i in 0 until 8) {
        if (windowBytes[i] > maxWindowBytes) {
            maxWindowBytes = windowBytes[i]
            bestWindowIdx = i
        }
    }
    val startHour = bestWindowIdx * 3
    val endHour = startHour + 3
    fun formatHour(h: Int): String {
        val ampm = if (h < 12 || h == 24) "AM" else "PM"
        val h12 = when (val mod = h % 12) {
            0 -> 12
            else -> mod
        }
        return String.format(Locale.US, "%02d:00 %s", h12, ampm)
    }
    val peakUsageTimeWindow = "${formatHour(startHour)} – ${formatHour(endHour)}"

    // 2. Highest Usage Day
    val dayGroups = nonZero.groupBy { formatDate(it.loginTime, "yyyy-MM-dd") }
    var highestDayBytes = 0L
    var highestDayTimestamp = 0L
    for ((_, daySessions) in dayGroups) {
        val dayTotal = daySessions.sumOf { it.totalBytes.coerceAtLeast(it.downloadBytes + it.uploadBytes) }
        if (dayTotal > highestDayBytes) {
            highestDayBytes = dayTotal
            highestDayTimestamp = daySessions.first().loginTime
        }
    }
    val highestDayDate = if (highestDayTimestamp > 0) formatInsightDate(highestDayTimestamp, nowMillis) else "N/A"
    val highestUsageFormatted = formatBytes(highestDayBytes)

    // 3. Daily & Weekly Averages
    val totalBytesAll = nonZero.sumOf { it.totalBytes.coerceAtLeast(it.downloadBytes + it.uploadBytes) }
    val activeDays = dayGroups.size.coerceAtLeast(1)
    val dailyAverageBytes = totalBytesAll / activeDays
    val weeklyAverageBytes = dailyAverageBytes * 7L

    // 4. Most active session
    val topSession = nonZero.maxByOrNull { it.totalBytes.coerceAtLeast(it.downloadBytes + it.uploadBytes) }
        ?: nonZero.first()
    val topSessionBytes = topSession.totalBytes.coerceAtLeast(topSession.downloadBytes + topSession.uploadBytes)

    return StatsInsights(
        peakUsageTimeWindow = peakUsageTimeWindow,
        highestUsageDayFormatted = "${highestUsageFormatted.first} ${highestUsageFormatted.second}",
        highestUsageDayDate = highestDayDate,
        highestUsageDayBytes = highestDayBytes,
        dailyAverageBytes = dailyAverageBytes,
        dailyAverageFormatted = formatBytes(dailyAverageBytes),
        weeklyAverageBytes = weeklyAverageBytes,
        weeklyAverageFormatted = formatBytes(weeklyAverageBytes),
        mostActiveSessionDurationFormatted = formatDurationDynamic(topSession.durationMillis),
        mostActiveSessionBytes = topSessionBytes,
        mostActiveSessionFormatted = formatBytes(topSessionBytes),
        activeDaysCount = activeDays,
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:desktopTest --tests "com.vinnovateit.latch.core.stats.StatsInsightsEngineTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/commonMain/kotlin/com/vinnovateit/latch/core/stats/StatsInsightsEngine.kt core/src/desktopTest/kotlin/com/vinnovateit/latch/core/stats/StatsInsightsEngineTest.kt
git commit -m "feat(core): implement usage insights engine with peak time and clean date formatting"
```

---

### Task 3: Bar Chart Polishing & Dynamic Scaling Fix

**Files:**
- Modify: `app/src/main/java/com/vinnovateit/latch/features/stats/components/PortalUsageTrends.kt`
- Modify: `desktop/src/commonMain/kotlin/com/vinnovateit/latch/ui/screens/StatsScreen.kt`

**Requirements:**
- Reduce bar width from 18/28.dp down to 10.dp.
- Reduce bar item container width to 24.dp.
- Fix scaling logic:
  - Total bar height fraction: `val totalFrac = (totalBytes.toFloat() / maxBytes.toFloat()).coerceIn(0.06f, 1f)`
  - Bar renders with full height = `chartMaxHeight * totalFrac`.
  - Inside the bar, upload fraction = `uploadBytes.toFloat() / totalBytes.coerceAtLeast(1L)` and download fraction = `1f - uploadFrac`.
  - When a larger value enters the dataset, `maxBytes` updates dynamically and all bars scale proportionally without distortion.
  - Bar date labels use `formatInsightDate`: no weekday, no year for current year!

- [ ] **Step 1: Write test for dynamic scaling calculation**

Create `core/src/desktopTest/kotlin/com/vinnovateit/latch/core/stats/BarChartScalingTest.kt`:
```kotlin
package com.vinnovateit.latch.core.stats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BarChartScalingTest {
    @Test
    fun testDynamicScalingProportions() {
        val day1 = 1_000_000_000L // 1 GB
        val day2 = 10_000_000_000L // 10 GB
        val maxBytes = maxOf(day1, day2)

        val frac1 = (day1.toFloat() / maxBytes.toFloat()).coerceIn(0.06f, 1f)
        val frac2 = (day2.toFloat() / maxBytes.toFloat()).coerceIn(0.06f, 1f)

        assertEquals(0.1f, frac1, 0.01f)
        assertEquals(1.0f, frac2, 0.01f)

        // When day 3 with 20 GB arrives:
        val day3 = 20_000_000_000L
        val newMax = maxOf(day1, day2, day3)
        val updatedFrac2 = (day2.toFloat() / newMax.toFloat()).coerceIn(0.06f, 1f)
        assertEquals(0.5f, updatedFrac2, 0.01f)
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew :core:desktopTest --tests "com.vinnovateit.latch.core.stats.BarChartScalingTest"`
Expected: PASS

- [ ] **Step 3: Update `PortalUsageTrends.kt` (Android) and `StatsScreen.kt` (Desktop)**

In `PortalUsageTrends.kt`:
- Use bar width = `10.dp`, container width = `24.dp`, spacing = `6.dp`.
- Total bar height = `barHeight * ((item.totalBytes.toFloat() / maxDaily.toFloat()).coerceIn(0.06f, 1f))`.
- Draw download portion and upload portion cleanly stacked.
- Remove weekday and year from labels using `formatInsightDate(item.timestamp)`.

In desktop `PortalDailyBarChart`:
- Apply the same 10.dp bar width and proportional dynamic scaling.
- Replace date label in selected bar header: format without weekday (use `formatInsightDate`).

- [ ] **Step 4: Run unit tests**

Run: `./gradlew :core:desktopTest :app:testDebugUnitTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/vinnovateit/latch/features/stats/components/PortalUsageTrends.kt desktop/src/commonMain/kotlin/com/vinnovateit/latch/ui/screens/StatsScreen.kt core/src/desktopTest/kotlin/com/vinnovateit/latch/core/stats/BarChartScalingTest.kt
git commit -m "fix(stats): refine bar chart width to 10dp and fix dynamic scaling math"
```

---

### Task 4: Top Bar 3-Dot Overflow Menu (Android & Desktop)

**Files:**
- Modify: `app/src/main/java/com/vinnovateit/latch/features/stats/StatsScreen.kt`
- Modify: `desktop/src/commonMain/kotlin/com/vinnovateit/latch/ui/screens/StatsScreen.kt`

**Requirements:**
- On Android: In `StatsTopBar`, replace standalone Export icon button with a 3-dot overflow menu (`Icons.Rounded.MoreVert`).
  - Menu contains:
    - "Export Full Report" (triggers `onSaveReport()`)
    - "Resync Portal History" (triggers `statsViewModel.refreshHistory(force = true)`)
- On Desktop: In `LatchDetailHeader` / top actions:
  - Add 3-dot overflow menu / action button for "Export Full Report" and "Resync Portal History".

- [ ] **Step 1: Update Android `StatsTopBar` in `StatsScreen.kt`**

```kotlin
var menuExpanded by remember { mutableStateOf(false) }

Box(
  modifier = Modifier
    .align(Alignment.TopEnd)
    .padding(end = 12.dp, top = 4.dp)
) {
  IconButton(onClick = { menuExpanded = true }) {
    Icon(
      imageVector = Icons.Rounded.MoreVert,
      contentDescription = "More Options",
      tint = MaterialTheme.colorScheme.primary
    )
  }
  DropdownMenu(
    expanded = menuExpanded,
    onDismissRequest = { menuExpanded = false }
  ) {
    DropdownMenuItem(
      text = { Text("Export Full Report") },
      onClick = {
        menuExpanded = false
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onSaveReport()
      },
      leadingIcon = {
        Icon(com.vinnovateit.latch.ui.icons.ExportNotes, contentDescription = null)
      }
    )
    DropdownMenuItem(
      text = { Text("Resync History") },
      onClick = {
        menuExpanded = false
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onResyncHistory()
      },
      leadingIcon = {
        Icon(Icons.Rounded.Refresh, contentDescription = null)
      }
    )
  }
}
```

- [ ] **Step 2: Update Desktop top bar actions in `StatsScreen.kt`**

Add similar `DropdownMenu` with `Icons.Rounded.MoreVert` (or `LatchIcons.MoreVert`) on Desktop with "Export Full Report" and "Resync History".

- [ ] **Step 3: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest :core:desktopTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/vinnovateit/latch/features/stats/StatsScreen.kt desktop/src/commonMain/kotlin/com/vinnovateit/latch/ui/screens/StatsScreen.kt
git commit -m "feat(stats): move export and resync into 3-dot overflow menu"
```

---

### Task 5: Stats UI Redesign — Replace Raw History List with Insights Dashboard

**Files:**
- Create: `app/src/main/java/com/vinnovateit/latch/features/stats/components/UsageInsightsCards.kt`
- Modify: `app/src/main/java/com/vinnovateit/latch/features/stats/StatsViewModel.kt`
- Modify: `app/src/main/java/com/vinnovateit/latch/features/stats/components/StatsList.kt`
- Modify: `desktop/src/commonMain/kotlin/com/vinnovateit/latch/ui/screens/StatsScreen.kt`

**Requirements:**
- Expose `statsInsights: StateFlow<StatsInsights>` from `StatsViewModel`.
- In `StatsList.kt`:
  - Remove the endless `"Previous Days"` LazyColumn items.
  - Insert `UsageInsightsCards(insights = statsInsights)` containing:
    - **Peak Usage Time** (e.g. "06:00 PM – 09:00 PM")
    - **Highest Usage Day** (e.g. "14.2 GB · 03 Mar")
    - **Daily Average** (e.g. "1.8 GB / day")
    - **Weekly Average** (e.g. "12.6 GB / week")
  - Retain `Today's Sessions` items if today has active/recent sessions.
  - Full past session records are viewed via "Export Full Report".
- In Desktop `StatsScreen.kt`:
  - Replace the endless past days list with the equivalent insights cards layout and today's sessions.

- [ ] **Step 1: Write test for ViewModel exposing insights**

Update `app/src/test/java/com/vinnovateit/latch/features/stats/StatsViewModelPortalTest.kt` to assert `viewModel.statsInsights.value` is calculated from portal history.

- [ ] **Step 2: Create `UsageInsightsCards.kt` in `app`**

Implement modern Expressive Material 3 2x2 grid or grouped card layout for the 4 key metrics:
- Peak Time
- Top Day
- Daily Average
- Weekly Average

- [ ] **Step 3: Update `StatsViewModel.kt` & `StatsList.kt`**

- Connect `statsInsights` StateFlow in `StatsViewModel`.
- In `StatsList.kt`, remove `olderDayRecords` list and render `UsageInsightsCards`.
- Update Desktop `StatsScreen.kt` with equivalent insights layout.

- [ ] **Step 4: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest :core:desktopTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/vinnovateit/latch/features/stats/components/UsageInsightsCards.kt app/src/main/java/com/vinnovateit/latch/features/stats/StatsViewModel.kt app/src/main/java/com/vinnovateit/latch/features/stats/components/StatsList.kt desktop/src/commonMain/kotlin/com/vinnovateit/latch/ui/screens/StatsScreen.kt app/src/test/java/com/vinnovateit/latch/features/stats/StatsViewModelPortalTest.kt
git commit -m "feat(stats): replace raw past days list with rich usage insights cards"
```

---

### Task 6: Verification, End-to-End Build & Desktop Distribution

**Files:**
- Verification only

- [ ] **Step 1: Run complete unit test suite**

Run: `./gradlew :core:desktopTest :app:testDebugUnitTest`
Expected: ALL PASS

- [ ] **Step 2: Build desktop RPM package**

Run: `./gradlew :desktop:packageReleaseRpm`
Expected: BUILD SUCCESSFUL with release RPM artifact

- [ ] **Step 3: Final sanity verification**

Check git diff and status to ensure clean working tree and no regressions in `LatchNavGraph.kt`.
