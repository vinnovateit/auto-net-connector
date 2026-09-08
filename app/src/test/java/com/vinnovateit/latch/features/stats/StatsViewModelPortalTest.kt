package com.vinnovateit.latch.features.stats

import com.vinnovateit.latch.core.model.DataUsage
import com.vinnovateit.latch.core.model.DateRangeFilter
import com.vinnovateit.latch.core.model.HistoryChartItem
import com.vinnovateit.latch.core.model.PortalSessionRecord
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
            formattedDate = "08 Sep",
            sessionCount = 3,
            durationMillis = 3600000L,
            durationFormatted = "1h 0m"
        )
        assertEquals(100L, item.usage.rxBytes)
        assertEquals(200L, item.usage.txBytes)
        assertEquals("08", item.label)
        assertEquals("08 Sep", item.formattedDate)
        assertEquals(3, item.sessionCount)
        assertEquals(3600000L, item.durationMillis)
        assertEquals("1h 0m", item.durationFormatted)
    }

    @Test
    fun testFormatDisplayDateOmitsWeekdayAndCurrentYear() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 8, 12, 0, 0)
        }
        val fixedNow = cal.timeInMillis

        // Same year (2026) -> "08 Mar"
        val formattedSameYear = com.vinnovateit.latch.common.util.formatDisplayDate(fixedNow, fixedNow)
        assertEquals("08 Mar", formattedSameYear)

        // Previous year (2025) -> "08 Mar 2025"
        cal.set(2025, Calendar.MARCH, 8, 12, 0, 0)
        val formattedOlderYear = com.vinnovateit.latch.common.util.formatDisplayDate(cal.timeInMillis, fixedNow)
        assertEquals("08 Mar 2025", formattedOlderYear)
    }

    @Test
    fun testChartKeyUniquenessAcrossItems() {
        val items = listOf(
            HistoryChartItem.BarData(DataUsage(100, 200), "01", 1000L, "01 Sep"),
            HistoryChartItem.MonthSeparator("Sep"),
            HistoryChartItem.CollapsedMonth("Aug", 500L),
            HistoryChartItem.BarData(DataUsage(300, 400), "02", 2000L, "02 Sep"),
            HistoryChartItem.BarData(DataUsage(500, 600), "03", 2000L, "03 Sep") // same timestamp edge case
        )
        val keys = items.mapIndexed { index, item ->
            when (item) {
                is HistoryChartItem.BarData -> "bar_${item.timestamp}_$index"
                is HistoryChartItem.MonthSeparator -> "month_${item.monthName}_$index"
                is HistoryChartItem.CollapsedMonth -> "collapsed_${item.monthName}_$index"
            }
        }
        assertEquals(items.size, keys.distinct().size)
    }

    @Test
    fun testAllDateRangeFiltersHaveValidDefinitions() {
        DateRangeFilter.entries.forEach { filter ->
            assertTrue(filter.label.isNotBlank())
        }
    }

    @Test
    fun testStatsColorPalettesDefinitions() {
        val palettes = com.vinnovateit.latch.common.util.StatsColorPalettes.PALETTES
        assertTrue(palettes.isNotEmpty())
        palettes.forEach { palette ->
            assertTrue(palette.name.isNotBlank())
            assertTrue(palette.description.isNotBlank())
        }
        val names = palettes.map { it.name }
        assertTrue(names.contains("Material Dynamic"))
        assertTrue(names.contains("Emerald & Ocean"))
        assertTrue(names.contains("Sunset & Violet"))
        assertTrue(names.contains("Neon Teal & Coral"))
        assertTrue(names.contains("Electric Cyan & Indigo"))
    }
}
