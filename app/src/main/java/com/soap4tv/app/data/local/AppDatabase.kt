package com.soap4tv.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.soap4tv.app.data.local.dao.WatchProgressDao
import com.soap4tv.app.data.local.entity.WatchProgressEntity

@Database(
    entities = [WatchProgressEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchProgressDao(): WatchProgressDao
}
