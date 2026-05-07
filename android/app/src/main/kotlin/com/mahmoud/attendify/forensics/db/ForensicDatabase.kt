package com.mahmoud.attendify.forensics.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ForensicAuditEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ForensicDatabase : RoomDatabase() {

    abstract fun auditDao(): ForensicAuditDao
}