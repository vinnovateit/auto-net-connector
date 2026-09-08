package com.vinnovateit.latch.core.domain

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.vinnovateit.latch.core.data.LatchDatabase
import com.vinnovateit.latch.core.platform.ByteCounts
import com.vinnovateit.latch.core.platform.ByteCounterSource
import com.vinnovateit.latch.core.platform.HttpTransport
import com.vinnovateit.latch.core.portal.PortalHistoryClient
import com.vinnovateit.latch.core.stats.ThroughputMonitor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortalSessionRepositoryTest {
    private lateinit var db: LatchDatabase

    private class StubCounters : ByteCounterSource {
        override fun sample(): ByteCounts = ByteCounts(0L, 0L)
    }

    private class FakePortalTransport : HttpTransport {
        override fun open(url: URL, handle: com.vinnovateit.latch.core.platform.NetworkHandle?): HttpURLConnection {
            return object : HttpURLConnection(url) {
                override fun connect() {}
                override fun disconnect() {}
                override fun usingProxy(): Boolean = false
                override fun getInputStream(): InputStream {
                    val html = when {
                        "Main.jsp" in url.toString() -> """<form name="chooseAuthForm" action="/registration/chooseAuth.do"></form>"""
                        "CustomerSessionHistory.jsp" in url.toString() -> """
                            <table>
                                <tr bgcolor="#DDDDDD">
                                    <td>VIT-Vellore</td>
                                    <td>9e:85:16:b5:22:eb</td>
                                    <td>09/07/26 11:39:30 PM</td>
                                    <td>09/07/26 11:53:16 PM</td>
                                    <td>13 min 46 sec</td>
                                    <td>15.15 KB</td>
                                    <td>116.02 KB</td>
                                    <td>131.17 KB</td>
                                </tr>
                            </table>
                        """.trimIndent()
                        else -> "OK"
                    }
                    return ByteArrayInputStream(html.toByteArray())
                }
                override fun getOutputStream() = java.io.ByteArrayOutputStream()
                override fun getResponseCode() = 200
            }
        }
    }

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<LatchDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun testSyncPortalHistoryUpdatesStateFlow() = runBlocking {
        val throughput = ThroughputMonitor(StubCounters())
        val portalClient = PortalHistoryClient(FakePortalTransport())
        val repo = SessionRepository(
            statsDao = db.statsDao(),
            throughput = throughput,
            portalClient = portalClient
        )
        repo.initialize()

        val syncRes = repo.syncPortalHistory("24BDS0155", "zero")
        assertTrue(syncRes.isSuccess)

        val history = repo.portalHistory.first { it.isNotEmpty() }
        assertEquals(1, history.size)
        assertEquals("VIT-Vellore", history[0].location)
    }
}
