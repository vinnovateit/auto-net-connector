package com.vinnovateit.latch.core.stats

import com.vinnovateit.latch.core.model.HistoryChartItem
import com.vinnovateit.latch.core.model.PortalSessionRecord
import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsChartEngineTest {

    @Test
    fun `today gets a bar even when the earliest record is later in the day`() {
        // Earliest activity at 20:15, and it is only 06:00 today. The day cursor
        // used to inherit that 20:15, overshoot `now`, and drop today entirely.
        val earliest = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 10, 20, 15, 30)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 15, 6, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val sessions = listOf(
            PortalSessionRecord(
                location = "Hostel",
                macAddress = "AA:BB:CC:DD:EE:FF",
                loginTime = earliest,
                logoutTime = earliest + 3_600_000L,
                durationFormatted = "1h",
                durationMillis = 3_600_000L,
                uploadBytes = 10_000_000L,
                downloadBytes = 50_000_000L,
                totalBytes = 60_000_000L,
            )
        )

        val items = computeChartItems(
            sessions,
            liveRxBytes = 1_234L,
            liveTxBytes = 567L,
            nowMillis = now,
        )

        val bars = items.filterIsInstance<HistoryChartItem.BarData>()
        val todayBar = bars.find { it.label == "15" }
        assertTrue(todayBar != null, "today should have a bar; got ${bars.map { it.label }}")
        assertEquals(1_234L, todayBar.usage.rxBytes)
        assertEquals(567L, todayBar.usage.txBytes)
    }

    @Test
    fun testComputeChartItems_basicBucketing() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 15, 12, 0, 0)
        }.timeInMillis

        val recordTime = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 10, 10, 0, 0)
        }.timeInMillis

        val sessions = listOf(
            PortalSessionRecord(
                location = "Hostel",
                macAddress = "AA:BB:CC:DD:EE:FF",
                loginTime = recordTime,
                logoutTime = recordTime + 3600000L,
                durationFormatted = "1h",
                durationMillis = 3600000L,
                uploadBytes = 10_000_000L,
                downloadBytes = 50_000_000L,
                totalBytes = 60_000_000L
            )
        )

        val items = computeChartItems(sessions, nowMillis = now)
        assertTrue(items.isNotEmpty())

        val separators = items.filterIsInstance<HistoryChartItem.MonthSeparator>()
        assertTrue(separators.any { it.monthName.startsWith("Mar") })

        val bars = items.filterIsInstance<HistoryChartItem.BarData>()
        val march10Bar = bars.find { it.label == "10" }
        assertTrue(march10Bar != null)
        assertEquals(50_000_000L, march10Bar.usage.rxBytes)
        assertEquals(10_000_000L, march10Bar.usage.txBytes)
        assertEquals(1, march10Bar.sessionCount)
    }
}
