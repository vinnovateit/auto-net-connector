package com.vinnovateit.latch.core.platform.android

import android.content.Context
import androidx.room.Room
import com.vinnovateit.latch.core.data.LatchDatabase

import com.vinnovateit.latch.core.data.MIGRATION_3_TO_4
import com.vinnovateit.latch.core.data.MIGRATION_4_TO_5

/** Mirrors desktop's DatabaseFactory.kt -- same "latch_database" name Android already used. */
fun buildDatabase(context: Context): LatchDatabase =
    Room.databaseBuilder(context.applicationContext, LatchDatabase::class.java, "latch_database")
        .addMigrations(MIGRATION_3_TO_4, MIGRATION_4_TO_5)
        .fallbackToDestructiveMigration(dropAllTables = false)
        .build()

