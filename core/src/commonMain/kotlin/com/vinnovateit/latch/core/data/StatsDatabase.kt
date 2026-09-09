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
import androidx.room.Transaction
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.Flow

/**
 * Session history -- shared by desktop and Android as of version 3.
 *
 * Differences from Android's original schema, both deliberate:
 *
 *  - Timestamps are `Long` epoch millis rather than `java.util.Date`, so no
 *    @TypeConverter is needed. Room stores Date as INTEGER anyway, so the
 *    column definition is identical -- and every read site immediately
 *    called .time on the Date regardless.
 *  - The `daily_usage` table is gone. It had five DAO methods and zero
 *    callers on both platforms.
 *
 * version = 3 to match Android's on-disk version exactly (its schema
 * already went 1 -> 2 -> 3, dropping daily_usage along the way -- see
 * GitHub #70). Room refuses to open a database at a version *lower* than
 * what's stored, so this can't stay at the "fresh desktop database" version
 * 1 it started at once Android adopts this class.
 *
 * No migration is registered: verified empirically (a throwaway Room
 * database built with Android's real production entities -- Long id, Date
 * fields + TypeConverters, version 3 -- reopens cleanly under this exact
 * entity with the data intact) that the two are schema-compatible, since
 * Room's type affinity maps Date-via-TypeConverter and Int/Long primary
 * keys onto the same SQLite INTEGER column either way.
 */
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
    val isManual: Boolean = false,
)

val MIGRATION_3_TO_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS `portal_sessions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `location` TEXT NOT NULL,
                `macAddress` TEXT NOT NULL,
                `loginTime` INTEGER NOT NULL,
                `logoutTime` INTEGER NOT NULL,
                `durationFormatted` TEXT NOT NULL,
                `durationMillis` INTEGER NOT NULL,
                `uploadBytes` INTEGER NOT NULL,
                `downloadBytes` INTEGER NOT NULL,
                `totalBytes` INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

val MIGRATION_4_TO_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
        connection.execSQL("ALTER TABLE `portal_sessions` ADD COLUMN `isManual` INTEGER NOT NULL DEFAULT 0")
    }
}

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

    @Transaction
    suspend fun replacePortalSessions(sessions: List<PortalSessionEntity>) {
        clearAllPortalSessions()
        insertAllPortalSessions(sessions)
    }
}

@Database(entities = [Session::class, PortalSessionEntity::class], version = 5, exportSchema = false)
@ConstructedBy(LatchDatabaseConstructor::class)
abstract class LatchDatabase : RoomDatabase() {
    abstract fun statsDao(): StatsDao
}

/**
 * Room KMP requirement: we declare the expect, KSP generates the actual.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object LatchDatabaseConstructor : RoomDatabaseConstructor<LatchDatabase> {
    override fun initialize(): LatchDatabase
}
