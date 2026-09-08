package com.vinnovateit.latch.core.portal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortalLogoutParserTest {

    private val sampleLogoutHtml = """
        <html><!-- iPass Logout comment
          <?xml version="1.0" encoding="UTF-8"?>
          <WISPAccessGatewayParam
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:noNamespaceSchemaLocation="http://www.acmewisp.com/WISPAccessGatewayParam.xsd">
            <LogoffReply>
              <MessageType>130</MessageType>
              <ResponseCode>150</ResponseCode>
            </LogoffReply>
          </WISPAccessGatewayParam>
        --><head><title> Logout successful </title>
        </head>
        <body>
        <h1> You have successfully logged out. </h1>
        Time Spent : 0H:9M:52S
        Bytes Sent : 39745
        Bytes Received : 2607
        Card Value Remaining : 


        </body></html>
    """.trimIndent()

    @Test
    fun `parse valid logout response extracts duration and byte metrics`() {
        val fixedLogoutTime = 1773000000000L
        val record = PortalLogoutParser.parse(sampleLogoutHtml, logoutTimeMillis = fixedLogoutTime)

        assertNotNull(record)
        assertTrue(record.isManual)
        assertEquals(fixedLogoutTime, record.logoutTime)
        // 0 hours, 9 minutes, 52 seconds = 9 * 60 + 52 = 592 seconds = 592,000 ms
        val expectedDurationMs = (9 * 60 + 52) * 1000L
        assertEquals(expectedDurationMs, record.durationMillis)
        assertEquals(fixedLogoutTime - expectedDurationMs, record.loginTime)
        assertEquals(39745L, record.uploadBytes)
        assertEquals(2607L, record.downloadBytes)
        assertEquals(39745L + 2607L, record.totalBytes)
        assertEquals("9 min 52 sec", record.durationFormatted)
    }

    @Test
    fun `parse with multi-hour duration formats correctly`() {
        val html = """
            <body>
            Time Spent : 2H:15M:0S
            Bytes Sent : 1000000
            Bytes Received : 5000000
            </body>
        """.trimIndent()
        val record = PortalLogoutParser.parse(html, logoutTimeMillis = 10000000L)
        assertNotNull(record)
        val expectedMs = (2 * 3600 + 15 * 60) * 1000L
        assertEquals(expectedMs, record.durationMillis)
        assertEquals("2 hr 15 min", record.durationFormatted)
        assertEquals(1000000L, record.uploadBytes)
        assertEquals(5000000L, record.downloadBytes)
        assertEquals(6000000L, record.totalBytes)
    }

    @Test
    fun `parse invalid html returns null`() {
        val invalidHtml = "<html><body>Some random page</body></html>"
        val record = PortalLogoutParser.parse(invalidHtml)
        assertNull(record)
    }
}
