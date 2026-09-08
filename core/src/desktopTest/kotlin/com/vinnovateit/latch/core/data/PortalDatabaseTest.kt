package com.vinnovateit.latch.core.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    fun testInsertAndRetrievePortalSessions() = runBlocking {
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

    @Test
    fun testMigration3To4() {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(":memory:")
        try {
            // Create version 3 schema with sessions table
            connection.execSQL("""
                CREATE TABLE IF NOT EXISTS `sessions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `startTime` INTEGER NOT NULL,
                    `endTime` INTEGER NOT NULL,
                    `rxBytes` INTEGER NOT NULL,
                    `txBytes` INTEGER NOT NULL,
                    `maxRxBps` INTEGER NOT NULL,
                    `maxTxBps` INTEGER NOT NULL
                )
            """.trimIndent())

            // Run migration 3 to 4
            MIGRATION_3_TO_4.migrate(connection)

            // Verify portal_sessions table exists by inserting into it
            connection.execSQL("""
                INSERT INTO `portal_sessions` (
                    `location`, `macAddress`, `loginTime`, `logoutTime`, `durationFormatted`,
                    `durationMillis`, `uploadBytes`, `downloadBytes`, `totalBytes`
                ) VALUES ('Hostel', 'mac', 10, 20, '10s', 10, 100, 200, 300)
            """.trimIndent())
        } finally {
            connection.close()
        }
    }
}

