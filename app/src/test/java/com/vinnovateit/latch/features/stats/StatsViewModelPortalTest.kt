package com.vinnovateit.latch.features.stats

import com.vinnovateit.latch.core.model.DataUsage
import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.features.stats.components.HistoryChartItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

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

    @Test
    fun testDateRangeFilterLabels() {
        assertEquals("Last 30", DateRangeFilter.LAST_30_DAYS.label)
        assertEquals("Last 60", DateRangeFilter.LAST_60_DAYS.label)
        assertEquals("Last 90", DateRangeFilter.LAST_90_DAYS.label)
        assertEquals("This Month", DateRangeFilter.THIS_MONTH.label)
        assertEquals("This Year", DateRangeFilter.THIS_YEAR.label)
        assertEquals("YTD", DateRangeFilter.YTD.label)
        assertEquals("Last Year", DateRangeFilter.LAST_YEAR.label)
        assertEquals("All Time", DateRangeFilter.ALL_TIME.label)
        assertEquals(8, DateRangeFilter.entries.size)
    }

    @Test
    fun testHistoryChartItemBarData() {
        val item = HistoryChartItem.BarData(
            usage = DataUsage(100L, 200L),
            label = "08",
            timestamp = 1700000000000L,
            formattedDate = "08 Sep"
        )
        assertEquals(100L, item.usage.rxBytes)
        assertEquals(200L, item.usage.txBytes)
        assertEquals("08", item.label)
        assertEquals("08 Sep", item.formattedDate)
    }
}
