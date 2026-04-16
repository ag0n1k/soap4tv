package com.soap4tv.app.di

import android.content.Context
import androidx.room.Room
import com.soap4tv.app.data.local.AppDatabase
import com.soap4tv.app.data.local.dao.WatchProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "soap4tv.db"
        )
            // Watch-progress is a local cache — rebuilding it after a schema bump
            // is acceptable and avoids crashing users on upgrade until we ship proper migrations.
            .fallbackToDestructiveMigration(false)
            .build()
    }

    @Provides
    fun provideWatchProgressDao(database: AppDatabase): WatchProgressDao {
        return database.watchProgressDao()
    }
}
