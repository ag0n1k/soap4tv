package com.soap4tv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.soap4tv.app.data.local.entity.WatchProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchProgressDao {

    @Query("SELECT * FROM watch_progress WHERE isCompleted = 0 ORDER BY lastWatched DESC LIMIT 20")
    fun getContinueWatching(): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress ORDER BY lastWatched DESC")
    fun getAllProgress(): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE episodeId = :episodeId")
    suspend fun getProgress(episodeId: Int): WatchProgressEntity?

    @Query("SELECT * FROM watch_progress WHERE contentId = :contentId ORDER BY seasonNumber, episodeNumber")
    suspend fun getProgressForContent(contentId: String): List<WatchProgressEntity>

    @Upsert
    suspend fun upsert(entity: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE episodeId = :episodeId")
    suspend fun delete(episodeId: Int)

    @Query("DELETE FROM watch_progress")
    suspend fun deleteAll()
}
