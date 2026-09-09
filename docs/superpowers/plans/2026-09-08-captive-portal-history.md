# Captive Portal History & Stats Revamp Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate real captive portal session history from the Pronto Networks portal (`136.233.9.110`) into Latch, remove legacy local session accumulation while preserving live throughput speed graphs, enrich the Stats screen with usage trends and location analytics, and revamp the HTML export to look beautiful and modern.

**Architecture:** 
- A multiplatform `PortalHistoryClient` authenticates via HTTP cookie session against `http://136.233.9.110/registration/Main.jsp?wispId=1` -> `chooseAuth.do`, retrieves `CustomerSessionHistory.jsp`, and parses session records with a zero-dependency HTML parser.
- Room database is upgraded with a `portal_sessions` table for offline caching and instant loading.
- `SessionRepository` disengages legacy local byte-counter accumulation for past sessions while keeping live `ThroughputMonitor` speed graphs intact for active sessions.
- `StatsScreen` displays live connection cards when connected, followed by summary KPI cards, daily usage trend charts, location breakdown, and portal session cards with refresh capabilities.
- `ReportUtils` produces a modern, high-aesthetic HTML dashboard report for export with KPI metrics, styled tables, and print support.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose, Room KMP, StateFlow/Coroutines, Material 3, JUnit4.

**Spec:** GitHub Issue #35: "Integrate Captive Portal History into Account Details / Stats".

## Global Constraints
- **Navigation Invariant (`AGENTS.md` Rule 1)**: NEVER remove, simplify, rework, or refactor `PredictiveSlideBackContainer` or back transitions in `LatchNavGraph.kt`.
- **Live Speed Graph Invariant**: The live throughput speed graph on `HomeScreen` and active session card on `StatsScreen` must remain fully operational.
- **Cleartext Security**: Android `network_security_config.xml` must permit HTTP cleartext traffic to `136.233.9.110`.
- **Zero Heavy Dependencies**: The captive portal HTML parser must be implemented using pure Kotlin string/regex parsing without introducing heavyweight browser automation or external parsing libraries into the multiplatform runtime.

---

### Task 1: Core Portal History Data Models & HTML Parser

**Files:**
- Create: `core/src/commonMain/kotlin/com/vinnovateit/latch/core/model/PortalSessionRecord.kt`
- Create: `core/src/commonMain/kotlin/com/vinnovateit/latch/core/portal/PortalHistoryParser.kt`
- Test: `core/src/desktopTest/kotlin/com/vinnovateit/latch/core/portal/PortalHistoryParserTest.kt`

**Interfaces:**
- Produces: 
  - `data class PortalSessionRecord(val location: String, val macAddress: String, val loginTime: Long, val logoutTime: Long, val durationFormatted: String, val durationMillis: Long, val uploadBytes: Long, val downloadBytes: Long, val totalBytes: Long)`
  - `object PortalHistoryParser { fun parse(html: String): List<PortalSessionRecord>; fun parseBytes(str: String): Long; fun parseDate(str: String): Long }`

- [ ] **Step 1: Write the failing test for `PortalHistoryParser`**

Create `core/src/desktopTest/kotlin/com/vinnovateit/latch/core/portal/PortalHistoryParserTest.kt`:
```kotlin
package com.vinnovateit.latch.core.portal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortalHistoryParserTest {

    private val sampleHtml = """
        <html>
        <body>
        <table border="1">
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
            <tr bgcolor="#F3F3F3">
                <td>VIT-Vellore</td>
                <td>9e:85:16:b5:22:eb</td>
                <td>09/07/26 09:49:28 PM</td>
                <td>09/07/26 09:49:38 PM</td>
                <td>10 sec</td>
                <td>--</td>
                <td>--</td>
                <td>--</td>
            </tr>
        </table>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun testParseBytes() {
        assertEquals(0L, PortalHistoryParser.parseBytes("--"))
        assertEquals(0L, PortalHistoryParser.parseBytes(""))
        assertEquals(1024L, PortalHistoryParser.parseBytes("1.00 KB"))
        assertEquals(1048576L, PortalHistoryParser.parseBytes("1.00 MB"))
        assertEquals(1073741824L, PortalHistoryParser.parseBytes("1.00 GB"))
        assertEquals(15513L, PortalHistoryParser.parseBytes("15.15 KB"))
    }

    @Test
    fun testParseDate() {
        val millis = PortalHistoryParser.parseDate("09/07/26 11:39:30 PM")
        assertTrue(millis > 0)
    }

    @Test
    fun testParseHtml() {
        val records = PortalHistoryParser.parse(sampleHtml)
        assertEquals(2, records.size)

        val first = records[0]
        assertEquals("VIT-Vellore", first.location)
        assertEquals("9e:85:16:b5:22:eb", first.macAddress)
        assertEquals("13 min 46 sec", first.durationFormatted)
        assertEquals(15513L, first.uploadBytes)
        assertEquals(118804L, first.downloadBytes)
        assertEquals(134318L, first.totalBytes)
        assertTrue(first.logoutTime >= first.loginTime)

        val second = records[1]
        assertEquals("10 sec", second.durationFormatted)
        assertEquals(0L, second.uploadBytes)
        assertEquals(0L, second.downloadBytes)
        assertEquals(0L, second.totalBytes)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:desktopTest --tests "com.vinnovateit.latch.core.portal.PortalHistoryParserTest"`
Expected: FAIL with compilation error (unresolved reference `PortalHistoryParser`).

- [ ] **Step 3: Write minimal implementation**

Create `core/src/commonMain/kotlin/com/vinnovateit/latch/core/model/PortalSessionRecord.kt`:
```kotlin
package com.vinnovateit.latch.core.model

data class PortalSessionRecord(
    val location: String,
    val macAddress: String,
    val loginTime: Long,
    val logoutTime: Long,
    val durationFormatted: String,
    val durationMillis: Long,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val totalBytes: Long,
)
```

Create `core/src/commonMain/kotlin/com/vinnovateit/latch/core/portal/PortalHistoryParser.kt`:
```kotlin
package com.vinnovateit.latch.core.portal

import com.vinnovateit.latch.core.model.PortalSessionRecord
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

object PortalHistoryParser {
    private val ROW_PATTERN = Pattern.compile(
        "<tr[^>]*bgcolor=[\"']?(?:#DDDDDD|#F3F3F3)[\"']?[^>]*>(.*?)</tr>",
        Pattern.DOTALL or Pattern.CASE_INSENSITIVE
    )
    private val COL_PATTERN = Pattern.compile(
        "<td[^>]*>(.*?)</td>",
        Pattern.DOTALL or Pattern.CASE_INSENSITIVE
    )
    private val TAG_PATTERN = Pattern.compile("<[^>]+>")

    fun parse(html: String): List<PortalSessionRecord> {
        val records = mutableListOf<PortalSessionRecord>()
        val rowMatcher = ROW_PATTERN.matcher(html)

        while (rowMatcher.find()) {
            val rowContent = rowMatcher.group(1) ?: continue
            val colMatcher = COL_PATTERN.matcher(rowContent)
            val cols = mutableListOf<String>()

            while (colMatcher.find()) {
                val raw = colMatcher.group(1) ?: ""
                val clean = TAG_PATTERN.matcher(raw).replaceAll("").trim()
                cols.add(clean)
            }

            if (cols.size >= 8) {
                val location = cols[0]
                val mac = cols[1]
                val loginStr = cols[2]
                val logoutStr = cols[3]
                val durationStr = cols[4]
                val uploadStr = cols[5]
                val downloadStr = cols[6]
                val totalStr = cols[7]

                val loginTime = parseDate(loginStr)
                val logoutTime = parseDate(logoutStr)
                val durationMs = if (logoutTime >= loginTime && loginTime > 0) logoutTime - loginTime else 0L

                records.add(
                    PortalSessionRecord(
                        location = location,
                        macAddress = mac,
                        loginTime = loginTime,
                        logoutTime = logoutTime,
                        durationFormatted = durationStr,
                        durationMillis = durationMs,
                        uploadBytes = parseBytes(uploadStr),
                        downloadBytes = parseBytes(downloadStr),
                        totalBytes = parseBytes(totalStr)
                    )
                )
            }
        }
        return records
    }

    fun parseBytes(str: String): Long {
        val trimmed = str.trim()
        if (trimmed.isEmpty() || trimmed == "--") return 0L

        val parts = trimmed.split("\\s+".toRegex())
        if (parts.isEmpty()) return 0L

        val value = parts[0].toDoubleOrNull() ?: return 0L
        val unit = if (parts.size > 1) parts[1].uppercase() else "B"

        return when {
            unit.startsWith("GB") -> (value * 1024.0 * 1024.0 * 1024.0).toLong()
            unit.startsWith("MB") -> (value * 1024.0 * 1024.0).toLong()
            unit.startsWith("KB") -> (value * 1024.0).toLong()
            else -> value.toLong()
        }
    }

    fun parseDate(str: String): Long {
        val trimmed = str.trim()
        if (trimmed.isEmpty() || trimmed == "--") return 0L

        val formats = listOf(
            "MM/dd/yy hh:mm:ss a",
            "MM/dd/yyyy hh:mm:ss a",
            "dd-MM-yyyy HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                val date = sdf.parse(trimmed)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }
        return 0L
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:desktopTest --tests "com.vinnovateit.latch.core.portal.PortalHistoryParserTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/commonMain/kotlin/com/vinnovateit/latch/core/model/PortalSessionRecord.kt core/src/commonMain/kotlin/com/vinnovateit/latch/core/portal/PortalHistoryParser.kt core/src/desktopTest/kotlin/com/vinnovateit/latch/core/portal/PortalHistoryParserTest.kt
git commit -m "feat(core): add portal session record model and html table parser"
```

---

### Task 2: Captive Portal History HTTP Client

**Files:**
- Create: `core/src/commonMain/kotlin/com/vinnovateit/latch/core/portal/PortalHistoryClient.kt`
- Modify: `app/src/main/res/xml/network_security_config.xml`
- Test: `core/src/desktopTest/kotlin/com/vinnovateit/latch/core/portal/PortalHistoryClientTest.kt`

**Interfaces:**
- Consumes: `HttpTransport`, `PortalHistoryParser`, `NetworkHandle`
- Produces: `PortalHistoryClient.fetchHistory(username: String, password: String, handle: NetworkHandle? = null, host: String = "136.233.9.110"): Result<List<PortalSessionRecord>>`

- [ ] **Step 1: Write the failing test for `PortalHistoryClient`**

Create `core/src/desktopTest/kotlin/com/vinnovateit/latch/core/portal/PortalHistoryClientTest.kt`:
```kotlin
package com.vinnovateit.latch.core.portal

import com.vinnovateit.latch.core.platform.HttpTransport
import com.vinnovateit.latch.core.platform.NetworkHandle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortalHistoryClientTest {

    private class FakeHttpConnection(url: URL, val responseHtml: String, val setCookieHeader: String? = null) : HttpURLConnection(url) {
        private val outputStream = ByteArrayOutputStream()
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun getOutputStream(): OutputStream = outputStream
        override fun getInputStream(): InputStream = ByteArrayInputStream(responseHtml.toByteArray())
        override fun getResponseCode(): Int = 200
        override fun getHeaderField(name: String?): String? {
            if (name.equals("Set-Cookie", ignoreCase = true)) return setCookieHeader
            return null
        }
    }

    private class FakeHttpTransport(val mainHtml: String, val loginHtml: String, val historyHtml: String) : HttpTransport {
        override fun open(url: URL, handle: NetworkHandle?): HttpURLConnection {
            val urlStr = url.toString()
            return when {
                "Main.jsp" in urlStr -> FakeHttpConnection(url, mainHtml, "JSESSIONID=test-session-id; path=/registration")
                "chooseAuth.do" in urlStr -> FakeHttpConnection(url, loginHtml)
                "CustomerSessionHistory.jsp" in urlStr -> FakeHttpConnection(url, historyHtml)
                else -> FakeHttpConnection(url, "")
            }
        }
    }

    @Test
    fun testFetchHistorySuccess() {
        val mainHtml = """<form name="chooseAuthForm" action="/registration/chooseAuth.do;jsessionid=test-session-id"></form>"""
        val loginHtml = "<html>Logged in successfully</html>"
        val historyHtml = """
            <html>
            <table border="1">
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
            </html>
        """.trimIndent()

        val transport = FakeHttpTransport(mainHtml, loginHtml, historyHtml)
        val client = PortalHistoryClient(transport)

        val result = client.fetchHistory("24BDS0155", "zero")
        assertTrue(result.isSuccess)
        val list = result.getOrNull()!!
        assertEquals(1, list.size)
        assertEquals("VIT-Vellore", list[0].location)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:desktopTest --tests "com.vinnovateit.latch.core.portal.PortalHistoryClientTest"`
Expected: FAIL (unresolved class `PortalHistoryClient`).

- [ ] **Step 3: Implement `PortalHistoryClient` and update `network_security_config.xml`**

Modify `app/src/main/res/xml/network_security_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">phc.prontonetworks.com</domain>
        <domain includeSubdomains="true">clients3.google.com</domain>
        <domain includeSubdomains="true">136.233.9.110</domain>
    </domain-config>
</network-security-config>
```

Create `core/src/commonMain/kotlin/com/vinnovateit/latch/core/portal/PortalHistoryClient.kt`:
```kotlin
package com.vinnovateit.latch.core.portal

import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.core.platform.HttpTransport
import com.vinnovateit.latch.core.platform.NetworkHandle
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

class PortalHistoryClient(
    private val transport: HttpTransport
) {
    companion object {
        const val DEFAULT_PORTAL_HOST = "136.233.9.110"
        private const val CONNECT_TIMEOUT_MS = 6000
        private const val READ_TIMEOUT_MS = 8000
    }

    fun fetchHistory(
        userId: String,
        password: String,
        handle: NetworkHandle? = null,
        host: String = DEFAULT_PORTAL_HOST
    ): Result<List<PortalSessionRecord>> {
        return try {
            val mainUrl = URL("http://$host/registration/Main.jsp?wispId=1")
            val conn1 = transport.open(mainUrl, handle)
            conn1.connectTimeout = CONNECT_TIMEOUT_MS
            conn1.readTimeout = READ_TIMEOUT_MS
            conn1.instanceFollowRedirects = false
            conn1.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")

            val mainHtml = conn1.inputStream.bufferedReader().use { it.readText() }
            val setCookieHeader = conn1.getHeaderField("Set-Cookie")
            var cookie = setCookieHeader?.substringBefore(";") ?: ""

            // Extract chooseAuth.do action URL (which may contain ;jsessionid=)
            val actionMatcher = Pattern.compile("action=[\"']([^\"']*chooseAuth\\.do[^\"']*)[\"']", Pattern.CASE_INSENSITIVE).matcher(mainHtml)
            val actionPath = if (actionMatcher.find()) actionMatcher.group(1) else "/registration/chooseAuth.do"
            val loginUrl = if (actionPath.startsWith("http")) URL(actionPath) else URL("http://$host$actionPath")

            // Step 2: POST credentials
            val postData = buildString {
                append("loginUserId=").append(URLEncoder.encode(userId, "UTF-8"))
                append("&authType=").append(URLEncoder.encode("Pronto", "UTF-8"))
                append("&loginPassword=").append(URLEncoder.encode(password, "UTF-8"))
                append("&submit=").append(URLEncoder.encode("Login", "UTF-8"))
            }

            val conn2 = transport.open(loginUrl, handle)
            conn2.requestMethod = "POST"
            conn2.doOutput = true
            conn2.instanceFollowRedirects = false
            conn2.connectTimeout = CONNECT_TIMEOUT_MS
            conn2.readTimeout = READ_TIMEOUT_MS
            conn2.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn2.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
            if (cookie.isNotEmpty()) {
                conn2.setRequestProperty("Cookie", cookie)
            }

            conn2.outputStream.bufferedWriter().use { it.write(postData) }
            val conn2Cookie = conn2.getHeaderField("Set-Cookie")
            if (!conn2Cookie.isNullOrEmpty()) {
                cookie = conn2Cookie.substringBefore(";")
            }
            conn2.inputStream.bufferedReader().use { it.readText() }

            // Step 3: GET CustomerSessionHistory.jsp
            val historyUrl = URL("http://$host/registration/main.do?content_key=%2FCustomerSessionHistory.jsp")
            val conn3 = transport.open(historyUrl, handle)
            conn3.requestMethod = "GET"
            conn3.instanceFollowRedirects = true
            conn3.connectTimeout = CONNECT_TIMEOUT_MS
            conn3.readTimeout = READ_TIMEOUT_MS
            conn3.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
            if (cookie.isNotEmpty()) {
                conn3.setRequestProperty("Cookie", cookie)
            }

            val historyHtml = conn3.inputStream.bufferedReader().use { it.readText() }
            val records = PortalHistoryParser.parse(historyHtml)
            Result.success(records)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:desktopTest --tests "com.vinnovateit.latch.core.portal.PortalHistoryClientTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/commonMain/kotlin/com/vinnovateit/latch/core/portal/PortalHistoryClient.kt core/src/desktopTest/kotlin/com/vinnovateit/latch/core/portal/PortalHistoryClientTest.kt app/src/main/res/xml/network_security_config.xml
git commit -m "feat(core): implement captive portal history http client"
```

---

### Task 3: Room Database Entity & Storage for Portal History

**Files:**
- Modify: `core/src/commonMain/kotlin/com/vinnovateit/latch/core/data/StatsDatabase.kt`
- Test: `core/src/desktopTest/kotlin/com/vinnovateit/latch/core/data/PortalDatabaseTest.kt`

**Interfaces:**
- Consumes: `PortalSessionRecord`
- Produces: 
  - `@Entity(tableName = "portal_sessions") data class PortalSessionEntity(...)`
  - `StatsDao.insertAllPortalSessions(sessions: List<PortalSessionEntity>)`
  - `StatsDao.getAllPortalSessions(): Flow<List<PortalSessionEntity>>`
  - `StatsDao.clearAllPortalSessions()`

- [ ] **Step 1: Write the failing test for `PortalSessionEntity` and DAO**

Create `core/src/desktopTest/kotlin/com/vinnovateit/latch/core/data/PortalDatabaseTest.kt`:
```kotlin
package com.vinnovateit.latch.core.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PortalDatabaseTest {
    private lateinit var db: LatchDatabase
    private lateinit var dao: StatsDao

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<LatchDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        dao = db.statsDao()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun testInsertAndRetrievePortalSessions() = runTest {
        val session1 = PortalSessionEntity(
            location = "VIT-Vellore",
            macAddress = "9e:85:16:b5:22:eb",
            loginTime = 1000L,
            logoutTime = 2000L,
            durationFormatted = "16 min",
            durationMillis = 1000L,
            uploadBytes = 1000L,
            downloadBytes = 5000L,
            totalBytes = 6000L
        )

        dao.insertAllPortalSessions(listOf(session1))
        val list = dao.getAllPortalSessions().first()
        assertEquals(1, list.size)
        assertEquals("VIT-Vellore", list[0].location)
        assertEquals(6000L, list[0].totalBytes)

        dao.clearAllPortalSessions()
        val empty = dao.getAllPortalSessions().first()
        assertEquals(0, empty.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:desktopTest --tests "com.vinnovateit.latch.core.data.PortalDatabaseTest"`
Expected: FAIL (unresolved `PortalSessionEntity`).

- [ ] **Step 3: Update `StatsDatabase.kt`**

Modify `core/src/commonMain/kotlin/com/vinnovateit/latch/core/data/StatsDatabase.kt` to define `PortalSessionEntity` and update `StatsDao` and `LatchDatabase`:
```kotlin
package com.vinnovateit.latch.core.data

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTime: Long,
    val endTime: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val maxRxBps: Long,
    val maxTxBps: Long,
)

@Entity(tableName = "portal_sessions")
data class PortalSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val location: String,
    val macAddress: String,
    val loginTime: Long,
    val logoutTime: Long,
    val durationFormatted: String,
    val durationMillis: Long,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val totalBytes: Long,
)

@Dao
interface StatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session): Long

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Query("DELETE FROM sessions")
    suspend fun clearAllSessions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPortalSessions(sessions: List<PortalSessionEntity>)

    @Query("SELECT * FROM portal_sessions ORDER BY loginTime DESC")
    fun getAllPortalSessions(): Flow<List<PortalSessionEntity>>

    @Query("DELETE FROM portal_sessions")
    suspend fun clearAllPortalSessions()
}

@Database(entities = [Session::class, PortalSessionEntity::class], version = 4, exportSchema = false)
@ConstructedBy(LatchDatabaseConstructor::class)
abstract class LatchDatabase : RoomDatabase() {
    abstract fun statsDao(): StatsDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object LatchDatabaseConstructor : RoomDatabaseConstructor<LatchDatabase> {
    override fun initialize(): LatchDatabase
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:desktopTest --tests "com.vinnovateit.latch.core.data.PortalDatabaseTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/commonMain/kotlin/com/vinnovateit/latch/core/data/StatsDatabase.kt core/src/desktopTest/kotlin/com/vinnovateit/latch/core/data/PortalDatabaseTest.kt
git commit -m "feat(core): add portal_sessions entity and dao methods"
```

---

### Task 4: Refactor Session Repository for Portal History & Live Graphs

**Files:**
- Modify: `core/src/commonMain/kotlin/com/vinnovateit/latch/core/domain/SessionRepository.kt`
- Test: `core/src/desktopTest/kotlin/com/vinnovateit/latch/core/domain/PortalSessionRepositoryTest.kt`

**Interfaces:**
- Consumes: `StatsDao`, `ThroughputMonitor`, `PortalHistoryClient`
- Produces: 
  - `SessionRepository.portalHistory: StateFlow<List<PortalSessionRecord>>`
  - `SessionRepository.isSyncing: StateFlow<Boolean>`
  - `SessionRepository.syncPortalHistory(userId: String, password: String): Result<Unit>`
  - Disengages `statsDao.insertSession` in `finishActiveSession()`, but maintains `liveStatus` and `ThroughputMonitor` speed graphs intact.

- [ ] **Step 1: Write the failing test for `SessionRepository` portal sync**

Create `core/src/desktopTest/kotlin/com/vinnovateit/latch/core/domain/PortalSessionRepositoryTest.kt`:
```kotlin
package com.vinnovateit.latch.core.domain

import com.vinnovateit.latch.core.data.LatchDatabase
import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.core.platform.ByteCounters
import com.vinnovateit.latch.core.platform.HttpTransport
import com.vinnovateit.latch.core.portal.PortalHistoryClient
import com.vinnovateit.latch.core.stats.ThroughputMonitor
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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

    private class StubCounters : ByteCounters {
        override fun readRxBytes(): Long = 0L
        override fun readTxBytes(): Long = 0L
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
    fun testSyncPortalHistoryUpdatesStateFlow() = runTest {
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:desktopTest --tests "com.vinnovateit.latch.core.domain.PortalSessionRepositoryTest"`
Expected: FAIL (`portalClient` not recognized or `syncPortalHistory` not defined).

- [ ] **Step 3: Refactor `SessionRepository.kt`**

Modify `core/src/commonMain/kotlin/com/vinnovateit/latch/core/domain/SessionRepository.kt`:
- Add `portalClient: PortalHistoryClient? = null` constructor parameter.
- Add `_portalHistory = MutableStateFlow<List<PortalSessionRecord>>(emptyList())`.
- Add `val portalHistory = _portalHistory.asStateFlow()`.
- Add `_isSyncing = MutableStateFlow(false)`.
- Add `val isSyncing = _isSyncing.asStateFlow()`.
- In `initialize()`:
  Collect `statsDao.getAllPortalSessions()` and map `PortalSessionEntity` to `PortalSessionRecord` emitted to `_portalHistory`.
- In `finishActiveSession()`: Remove `statsDao.insertSession(session)` so low-accuracy local sessions are no longer inserted into session history, but retain `liveStatus`, `ThroughputMonitor` start/stop, and `onSessionChanged` callbacks.
- Add `suspend fun syncPortalHistory(userId: String, password: String, host: String = PortalHistoryClient.DEFAULT_PORTAL_HOST): Result<Unit>`:
  Calls `portalClient?.fetchHistory(...)`, converts records to `PortalSessionEntity`, clears and inserts into `statsDao`, and updates state.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:desktopTest --tests "com.vinnovateit.latch.core.domain.PortalSessionRepositoryTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/commonMain/kotlin/com/vinnovateit/latch/core/domain/SessionRepository.kt core/src/desktopTest/kotlin/com/vinnovateit/latch/core/domain/PortalSessionRepositoryTest.kt
git commit -m "refactor(core): integrate portal history sync and remove legacy session accumulation"
```

---

### Task 5: Redesign Beautiful HTML Report Generator

**Files:**
- Modify: `app/src/main/java/com/vinnovateit/latch/common/util/ReportUtils.kt`
- Create: `app/src/test/java/com/vinnovateit/latch/common/util/ReportUtilsTest.kt`

**Interfaces:**
- Consumes: `List<PortalSessionRecord>`, `OutputStream`, `appVersion: String`, `userId: String?`
- Produces: `generatePortalHtmlReport(sessions: List<PortalSessionRecord>, outputStream: OutputStream, appVersion: String, userId: String = "")`

- [ ] **Step 1: Write the failing test for `generatePortalHtmlReport`**

Create `app/src/test/java/com/vinnovateit/latch/common/util/ReportUtilsTest.kt`:
```kotlin
package com.vinnovateit.latch.common.util

import com.vinnovateit.latch.core.model.PortalSessionRecord
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
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vinnovateit.latch.common.util.ReportUtilsTest"`
Expected: FAIL (unresolved `generatePortalHtmlReport`).

- [ ] **Step 3: Implement modern, beautiful HTML report in `ReportUtils.kt`**

Modify `app/src/main/java/com/vinnovateit/latch/common/util/ReportUtils.kt`:
Implement `generatePortalHtmlReport`:
- Header featuring modern typography, deep charcoal glassmorphism background (`#0d1117`), cyan/emerald accents (`#00e676`, `#00b0ff`), rounded corners, subtle gradients.
- 4 KPI Stat Cards:
  1. Total Data Transfer (formatted in GB/MB) with sub-pills for Upload and Download.
  2. Total Active Duration (sum of durationMillis formatted in hours and minutes).
  3. Total Sessions Logged (e.g. "82 sessions").
  4. Top Location (most frequent location name).
- Modern Data Table:
  - Columns: `#`, `Location`, `Login Time`, `Logout Time`, `Duration`, `Upload`, `Download`, `Total Data`.
  - Badges for locations and durations.
  - Hover effects on table rows.
- Full `@media print` rules for clean, paginated PDF export.
- Retain backward-compatible `generateHtmlReport` overload pointing to the new generator.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vinnovateit.latch.common.util.ReportUtilsTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/vinnovateit/latch/common/util/ReportUtils.kt app/src/test/java/com/vinnovateit/latch/common/util/ReportUtilsTest.kt
git commit -m "feat(app): redesign html report generator with modern dashboard styling"
```

---

### Task 6: Stats Trends & Visualizations Components

**Files:**
- Create: `app/src/main/java/com/vinnovateit/latch/features/stats/components/StatsMetricsSummary.kt`
- Create: `app/src/main/java/com/vinnovateit/latch/features/stats/components/PortalUsageTrends.kt`
- Test: `app/src/test/java/com/vinnovateit/latch/features/stats/StatsTrendsAggregationTest.kt`

**Interfaces:**
- Consumes: `List<PortalSessionRecord>`
- Produces: 
  - `data class DailyUsageTrend(val dateLabel: String, val timestamp: Long, val uploadBytes: Long, val downloadBytes: Long, val totalBytes: Long)`
  - `fun aggregateDailyUsage(sessions: List<PortalSessionRecord>, daysLimit: Int = 14): List<DailyUsageTrend>`
  - `fun computeMetrics(sessions: List<PortalSessionRecord>): StatsOverviewMetrics`
  - Composable `StatsMetricsSummary(metrics: StatsOverviewMetrics)`
  - Composable `PortalUsageTrends(trends: List<DailyUsageTrend>)`

- [ ] **Step 1: Write the failing test for trends aggregation**

Create `app/src/test/java/com/vinnovateit/latch/features/stats/StatsTrendsAggregationTest.kt`:
```kotlin
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vinnovateit.latch.features.stats.StatsTrendsAggregationTest"`
Expected: FAIL (unresolved references `computeMetrics`, `aggregateDailyUsage`).

- [ ] **Step 3: Implement `StatsMetricsSummary.kt` and `PortalUsageTrends.kt`**

Create `app/src/main/java/com/vinnovateit/latch/features/stats/components/StatsMetricsSummary.kt`:
```kotlin
package com.vinnovateit.latch.features.stats.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vinnovateit.latch.common.util.formatBytes
import com.vinnovateit.latch.common.util.formatDurationDynamic
import com.vinnovateit.latch.core.model.PortalSessionRecord

data class StatsOverviewMetrics(
    val totalBytes: Long,
    val totalUploadBytes: Long,
    val totalDownloadBytes: Long,
    val totalSessions: Int,
    val averageDurationMs: Long,
    val topLocation: String
)

fun computeMetrics(sessions: List<PortalSessionRecord>): StatsOverviewMetrics {
    if (sessions.isEmpty()) {
        return StatsOverviewMetrics(0L, 0L, 0L, 0, 0L, "None")
    }
    val total = sessions.sumOf { it.totalBytes }
    val ul = sessions.sumOf { it.uploadBytes }
    val dl = sessions.sumOf { it.downloadBytes }
    val avgDur = (sessions.map { it.durationMillis }.average()).toLong()
    val topLoc = sessions.groupBy { it.location }
        .maxByOrNull { it.value.size }?.key ?: "Unknown"

    return StatsOverviewMetrics(
        totalBytes = total,
        totalUploadBytes = ul,
        totalDownloadBytes = dl,
        totalSessions = sessions.size,
        averageDurationMs = avgDur,
        topLocation = topLoc
    )
}

@Composable
fun StatsMetricsSummary(
    metrics: StatsOverviewMetrics,
    modifier: Modifier = Modifier
) {
    val totalFmt = formatBytes(metrics.totalBytes)
    val ulFmt = formatBytes(metrics.totalUploadBytes)
    val dlFmt = formatBytes(metrics.totalDownloadBytes)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Total Data",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${totalFmt.first} ${totalFmt.second}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "↓ ${dlFmt.first} ${dlFmt.second}  ↑ ${ulFmt.first} ${ulFmt.second}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Sessions / Avg",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${metrics.totalSessions} sessions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Avg ${formatDurationDynamic(metrics.averageDurationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

Create `app/src/main/java/com/vinnovateit/latch/features/stats/components/PortalUsageTrends.kt`:
```kotlin
package com.vinnovateit.latch.features.stats.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vinnovateit.latch.common.util.formatDate
import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.ui.theme.ColorGraphDownload
import com.vinnovateit.latch.ui.theme.ColorGraphUpload

data class DailyUsageTrend(
    val dateLabel: String,
    val timestamp: Long,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val totalBytes: Long
)

fun aggregateDailyUsage(sessions: List<PortalSessionRecord>, daysLimit: Int = 14): List<DailyUsageTrend> {
    if (sessions.isEmpty()) return emptyList()

    return sessions
        .filter { it.loginTime > 0 }
        .groupBy { formatDate(it.loginTime, "yyyy-MM-dd") }
        .map { (dateKey, list) ->
            val firstTimestamp = list.minOf { it.loginTime }
            val ul = list.sumOf { it.uploadBytes }
            val dl = list.sumOf { it.downloadBytes }
            val label = formatDate(firstTimestamp, "dd MMM")
            DailyUsageTrend(
                dateLabel = label,
                timestamp = firstTimestamp,
                uploadBytes = ul,
                downloadBytes = dl,
                totalBytes = ul + dl
            )
        }
        .sortedBy { it.timestamp }
        .takeLast(daysLimit)
}

@Composable
fun PortalUsageTrends(
    trends: List<DailyUsageTrend>,
    modifier: Modifier = Modifier
) {
    if (trends.isEmpty()) return

    val maxDaily = trends.maxOfOrNull { it.totalBytes }?.coerceAtLeast(1L) ?: 1L
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Daily Trends",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            trends.forEach { item ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val barHeight = 120.dp
                    val frac = (item.totalBytes.toFloat() / maxDaily).coerceIn(0.1f, 1f)

                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(barHeight),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Canvas(modifier = Modifier.fillMaxWidth().height(barHeight * frac)) {
                            val ulFrac = if (item.totalBytes > 0) item.uploadBytes.toFloat() / item.totalBytes else 0f
                            val dlFrac = 1f - ulFrac

                            val dlHeight = size.height * dlFrac
                            val ulHeight = size.height * ulFrac

                            // Download portion (bottom)
                            drawRoundRect(
                                color = ColorGraphDownload,
                                topLeft = Offset(0f, size.height - dlHeight),
                                size = Size(size.width, dlHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                            // Upload portion (top)
                            if (ulHeight > 0f) {
                                drawRoundRect(
                                    color = ColorGraphUpload,
                                    topLeft = Offset(0f, size.height - dlHeight - ulHeight),
                                    size = Size(size.width, ulHeight),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vinnovateit.latch.features.stats.StatsTrendsAggregationTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/vinnovateit/latch/features/stats/components/StatsMetricsSummary.kt app/src/main/java/com/vinnovateit/latch/features/stats/components/PortalUsageTrends.kt app/src/test/java/com/vinnovateit/latch/features/stats/StatsTrendsAggregationTest.kt
git commit -m "feat(app): add stats metrics summary and portal usage trend charts"
```

---

### Task 7: Adapt Stats Screen, ViewModel, and App Graph

**Files:**
- Modify: `app/src/main/java/com/vinnovateit/latch/features/stats/StatsViewModel.kt`
- Modify: `app/src/main/java/com/vinnovateit/latch/features/stats/components/StatsItemCard.kt`
- Modify: `app/src/main/java/com/vinnovateit/latch/features/stats/components/StatsList.kt`
- Modify: `app/src/main/java/com/vinnovateit/latch/features/stats/StatsScreen.kt`
- Modify: `app/src/main/java/com/vinnovateit/latch/navigation/LatchNavGraph.kt`
- Modify: `app/src/main/java/com/vinnovateit/latch/platform/LatchAppGraph.kt`

**Interfaces:**
- Consumes: `SessionRepository.portalHistory`, `SessionRepository.isSyncing`, `PortalSessionRecord`, `generatePortalHtmlReport`
- Produces: 
  - Updated `StatsViewModel` exposing `portalHistory`, `isSyncing`, `syncError`, and `refreshHistory()`
  - Updated `StatsItemCard` rendering `PortalSessionRecord`
  - Pull-to-refresh & manual sync in `StatsScreen`
  - Integration with report export in `LatchNavGraph.kt`

- [ ] **Step 1: Write ViewModel unit test for portal history sync trigger**

Create `app/src/test/java/com/vinnovateit/latch/features/stats/StatsViewModelPortalTest.kt`:
```kotlin
package com.vinnovateit.latch.features.stats

import com.vinnovateit.latch.core.model.PortalSessionRecord
import org.junit.Assert.assertNotNull
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
        assertNotNull(record)
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.vinnovateit.latch.features.stats.StatsViewModelPortalTest"`
Expected: PASS

- [ ] **Step 3: Update `LatchAppGraph.kt`**

Modify `app/src/main/java/com/vinnovateit/latch/platform/LatchAppGraph.kt`:
In `initialize()`:
Construct `PortalHistoryClient(platform.httpTransport)` and pass it to `SessionRepository(database.statsDao(), throughput, portalClient = portalClient)`.
On launch, if credentials exist, invoke `sessions.syncPortalHistory(credentials.userId()!!, credentials.password()!!)`.

- [ ] **Step 4: Update `StatsViewModel.kt`**

Modify `app/src/main/java/com/vinnovateit/latch/features/stats/StatsViewModel.kt`:
- Expose `portalHistory: StateFlow<List<PortalSessionRecord>> = LatchAppGraph.sessions.portalHistory`.
- Expose `isSyncing: StateFlow<Boolean> = LatchAppGraph.sessions.isSyncing`.
- Add `fun refreshHistory()` that launches a coroutine to call `LatchAppGraph.sessions.syncPortalHistory(...)` with stored credentials.

- [ ] **Step 5: Update `StatsItemCard.kt` to render `PortalSessionRecord`**

Modify `app/src/main/java/com/vinnovateit/latch/features/stats/components/StatsItemCard.kt`:
- Accept `session: PortalSessionRecord`.
- Display:
  - Location pill with icon (e.g. `VIT-Vellore`).
  - Formatted login timestamp (`SimpleDateFormat("E, dd MMM • hh:mm a")`).
  - Duration pill (`session.durationFormatted`).
  - Upload (`↑ ${ul.first} ${ul.second}`) and Download (`↓ ${dl.first} ${dl.second}`) metrics.
  - Prominent Total Data badge.

- [ ] **Step 6: Update `StatsList.kt` and `StatsScreen.kt`**

Modify `app/src/main/java/com/vinnovateit/latch/features/stats/components/StatsList.kt` and `StatsScreen.kt`:
- When live (`isLive`), show the active `SessionCard` with the live speed graph (preserving live speed graph and connection tracking).
- Below active card, show `StatsMetricsSummary(metrics)`.
- Below metrics, show `PortalUsageTrends(trends)`.
- Display the list of `PortalSessionRecord` cards.
- Add sync button / indicator in top bar (next to export report icon).

- [ ] **Step 7: Update `LatchNavGraph.kt` export invocation**

Modify `app/src/main/java/com/vinnovateit/latch/navigation/LatchNavGraph.kt`:
- In `LatchRoutes.STATS`, update `generateHtmlReport` call to pass `statsViewModel.portalHistory.value` and stored `userId` into `generatePortalHtmlReport(...)`.
- **STRICT**: Preserve `PredictiveSlideBackContainer` without changing its signature or logic.

- [ ] **Step 8: Run unit and desktop tests to verify everything passes**

Run: `./gradlew :core:desktopTest`
Expected: BUILD SUCCESSFUL

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/vinnovateit/latch/platform/LatchAppGraph.kt app/src/main/java/com/vinnovateit/latch/features/stats/StatsViewModel.kt app/src/main/java/com/vinnovateit/latch/features/stats/components/StatsItemCard.kt app/src/main/java/com/vinnovateit/latch/features/stats/components/StatsList.kt app/src/main/java/com/vinnovateit/latch/features/stats/StatsScreen.kt app/src/main/java/com/vinnovateit/latch/navigation/LatchNavGraph.kt
git commit -m "feat(app): adapt stats screen with portal history, trends, and beautiful export"
```

---

### Task 8: Full End-to-End Build & Verification

**Files:**
- None (Build verification & regression testing)

- [ ] **Step 1: Execute full core tests**

Run: `./gradlew :core:desktopTest`
Expected: ALL PASS

- [ ] **Step 2: Execute full app tests and assemble debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Verification of Issue #35 resolution**

Ensure:
1. Historical sessions are retrieved directly from Pronto Networks captive portal via pure HTTP.
2. Local session accumulation has been replaced with portal history.
3. Live speed graph and connection monitoring are preserved.
4. Stats screen displays summary KPI cards, daily usage trends, and portal records.
5. Export feature produces a modern, styled HTML dashboard.
