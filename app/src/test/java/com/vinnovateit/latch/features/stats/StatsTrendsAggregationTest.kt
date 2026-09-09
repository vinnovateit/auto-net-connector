package com.vinnovateit.latch.features.stats

import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.core.model.computeMetrics
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
    }

    @Test
    fun testComputeMetricsEmpty() {
        val metrics = computeMetrics(emptyList())
        assertEquals(0L, metrics.totalBytes)
        assertEquals(0, metrics.totalSessions)
    }
}

