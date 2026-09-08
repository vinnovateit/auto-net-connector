package com.vinnovateit.latch.core.stats

import com.vinnovateit.latch.core.model.HistoryChartItem
import com.vinnovateit.latch.core.model.PortalSessionRecord
import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsChartEngineTest {

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
