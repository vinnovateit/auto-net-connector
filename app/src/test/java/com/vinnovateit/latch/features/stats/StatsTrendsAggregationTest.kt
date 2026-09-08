package com.vinnovateit.latch.features.stats

import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.features.stats.components.aggregateDailyUsage
import com.vinnovateit.latch.features.stats.components.computeMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsTrendsAggregationTest {

    @Test
    fun testComputeMetrics() {
        val records = listOf(
            PortalSessionRecord("Hostel-D", "mac1", 1000L, 2000L, "10 min", 600000L, 1000L, 4000L, 5000L),
            PortalSessionRecord("Hostel-D", "mac1", 3000L, 4000L, "20 min", 1200000L, 2000L, 3000L, 5000L),
            PortalSessionRecord("SJT", "mac1", 5000L, 6000L, "30 min", 1800000L, 500L, 500L, 1000L)
        )

        val metrics = computeMetrics(records)
        assertEquals(11000L, metrics.totalBytes)
        assertEquals(3500L, metrics.totalUploadBytes)
        assertEquals(7500L, metrics.totalDownloadBytes)
        assertEquals(3, metrics.totalSessions)
        assertEquals(1200000L, metrics.averageDurationMs)
        assertEquals("Hostel-D", metrics.topLocation)
    }

    @Test
    fun testComputeMetricsEmpty() {
        val metrics = computeMetrics(emptyList())
        assertEquals(0L, metrics.totalBytes)
        assertEquals(0, metrics.totalSessions)
        assertEquals("None", metrics.topLocation)
    }

    @Test
    fun testAggregateDailyUsage() {
        val day1 = 1773000000000L // arbitrary epoch
        val records = listOf(
            PortalSessionRecord("Hostel", "mac", day1, day1 + 1000, "1m", 60000L, 100L, 200L, 300L),
            PortalSessionRecord("Hostel", "mac", day1 + 10000, day1 + 12000, "2m", 120000L, 200L, 300L, 500L)
        )

        val daily = aggregateDailyUsage(records, daysLimit = 7)
        assertEquals(1, daily.size)
        assertEquals(800L, daily[0].totalBytes)
        assertEquals(300L, daily[0].uploadBytes)
        assertEquals(500L, daily[0].downloadBytes)
    }

    @Test
    fun testAggregateDailyUsageEmpty() {
        val daily = aggregateDailyUsage(emptyList())
        assertEquals(0, daily.size)
    }
}
