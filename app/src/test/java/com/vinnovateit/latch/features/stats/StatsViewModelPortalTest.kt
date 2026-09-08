package com.vinnovateit.latch.features.stats

import com.vinnovateit.latch.core.model.PortalSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsViewModelPortalTest {
    @Test
    fun testPortalSessionModelIntegrity() {
        val record = PortalSessionRecord(
            location = "Hostel-A",
            macAddress = "aa:bb:cc:dd:ee:ff",
            loginTime = 1000L,
            logoutTime = 2000L,
            durationFormatted = "16 min",
            durationMillis = 1000L,
            uploadBytes = 500L,
            downloadBytes = 1500L,
            totalBytes = 2000L
        )
        assertEquals("Hostel-A", record.location)
        assertEquals(2000L, record.totalBytes)
    }
}
