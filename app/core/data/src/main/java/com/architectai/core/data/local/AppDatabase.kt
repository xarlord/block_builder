package com.architectai.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CompositionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun compositionDao(): CompositionDao
}
