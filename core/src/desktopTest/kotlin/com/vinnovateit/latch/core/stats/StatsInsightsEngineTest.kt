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
    fun testFormatDisplayDateOmitsWeekdayAndCurrentYear() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 8, 12, 0, 0)
        }
        val fixedNow = cal.timeInMillis

        // Same year (2026) -> "08 Mar"
        val formattedSameYear = formatDisplayDate(fixedNow, fixedNow)
        assertEquals("08 Mar", formattedSameYear)

        // Previous year (2025) -> "08 Mar 2025"
        cal.set(2025, Calendar.MARCH, 8, 12, 0, 0)
        val formattedOlderYear = formatDisplayDate(cal.timeInMillis, fixedNow)
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

    @Test
    fun testEmptySessionsProducesZeroStatsInsights() {
        val insights = computeStatsInsights(emptyList<PortalSessionRecord>())
        assertEquals("N/A", insights.peakUsageTimeWindow)
        assertEquals("0 B", insights.highestUsageDayFormatted)
        assertEquals("N/A", insights.highestUsageDayDate)
        assertEquals(0L, insights.highestUsageDayBytes)
        assertEquals(0L, insights.dailyAverageBytes)
        assertEquals("0" to "B", insights.dailyAverageFormatted)
        assertEquals(0L, insights.weeklyAverageBytes)
        assertEquals("0" to "B", insights.weeklyAverageFormatted)
        assertEquals("0m", insights.mostActiveSessionDurationFormatted)
        assertEquals(0L, insights.mostActiveSessionBytes)
        assertEquals("0" to "B", insights.mostActiveSessionFormatted)
        assertEquals(0, insights.activeDaysCount)
    }
}
