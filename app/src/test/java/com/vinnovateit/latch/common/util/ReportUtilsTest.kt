package com.vinnovateit.latch.common.util

import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.core.stats.generatePortalHtmlReport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class ReportUtilsTest {

    @Test
    fun testGeneratePortalHtmlReportContainsMetricsAndStyling() {
        val records = listOf(
            PortalSessionRecord(
                location = "VIT-Vellore",
                macAddress = "9e:85:16:b5:22:eb",
                loginTime = 1773000000000L,
                logoutTime = 1773001000000L,
                durationFormatted = "16 min 40 sec",
                durationMillis = 1000000L,
                uploadBytes = 2097152L,
                downloadBytes = 8388608L,
                totalBytes = 10485760L
            )
        )

        val output = ByteArrayOutputStream()
        generatePortalHtmlReport(
            sessions = records,
            outputStream = output,
            appVersion = "1.4",
            userId = "24BDS0155"
        )

        val html = output.toString("UTF-8")
        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("24BDS0155"))
        assertTrue(html.contains("VIT-Vellore"))
        assertTrue(html.contains("16 min 40 sec"))
        assertTrue(html.contains("card"))
        assertTrue(html.contains("@media print"))
        assertTrue(html.contains("1 session"))
        assertFalse(html.contains(" sess<"))
        assertFalse(html.contains(" · "))
    }

    @Test
    fun testGeneratePortalHtmlReportEmptySessions() {
        val output = ByteArrayOutputStream()
        generatePortalHtmlReport(
            sessions = emptyList(),
            outputStream = output,
            appVersion = "1.4",
            userId = ""
        )

        val html = output.toString("UTF-8")
        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("No session data available."))
        assertTrue(html.contains("0 sessions"))
        assertTrue(html.contains("N/A"))
        assertFalse(html.contains("User: <strong>"))
    }

    @Test
    fun testGeneratePortalHtmlReportMultipleSessionsAndTopLocation() {
        val records = listOf(
            PortalSessionRecord(
                location = "Hostel-A",
                macAddress = "aa:bb:cc:dd:ee:01",
                loginTime = 1773000000000L,
                logoutTime = 1773003600000L,
                durationFormatted = "1 hr 0 min",
                durationMillis = 3600000L,
                uploadBytes = 1073741824L,
                downloadBytes = 2147483648L,
                totalBytes = 3221225472L
            ),
            PortalSessionRecord(
                location = "Library",
                macAddress = "aa:bb:cc:dd:ee:02",
                loginTime = 1773010000000L,
                logoutTime = 1773011800000L,
                durationFormatted = "30 min 0 sec",
                durationMillis = 1800000L,
                uploadBytes = 52428800L,
                downloadBytes = 104857600L,
                totalBytes = 157286400L
            ),
            PortalSessionRecord(
                location = "Library",
                macAddress = "aa:bb:cc:dd:ee:03",
                loginTime = 1773020000000L,
                logoutTime = 1773023600000L,
                durationFormatted = "1 hr 0 min",
                durationMillis = 3600000L,
                uploadBytes = 52428800L,
                downloadBytes = 104857600L,
                totalBytes = 157286400L
            )
        )

        val output = ByteArrayOutputStream()
        generatePortalHtmlReport(
            sessions = records,
            outputStream = output,
            appVersion = "2.0",
            userId = "TEST_USER"
        )

        val html = output.toString("UTF-8")
        assertTrue(html.contains("3 sessions"))
        assertTrue(html.contains("Library"))
        assertTrue(html.contains("Hostel-A"))
        assertTrue(html.contains("TEST_USER"))
        assertTrue(html.contains("GB"))
    }
}
