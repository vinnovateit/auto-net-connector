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
