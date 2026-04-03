package com.soap4tv.app.data.repository

import com.soap4tv.app.data.local.dao.WatchProgressDao
import com.soap4tv.app.data.local.entity.WatchProgressEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchProgressRepository @Inject constructor(
    private val watchProgressDao: WatchProgressDao,
    private val playerRepository: PlayerRepository
) {
    fun getContinueWatching(): Flow<List<WatchProgressEntity>> =
        watchProgressDao.getContinueWatching()

    fun getAllProgress(): Flow<List<WatchProgressEntity>> =
        watchProgressDao.getAllProgress()

    suspend fun getProgress(episodeId: Int): WatchProgressEntity? =
        watchProgressDao.getProgress(episodeId)

    /**
     * Save or update watch progress.
     * Also triggers server-side timestamp save for series.
     */
    suspend fun saveProgress(
        episodeId: Int,
        contentType: String,
        contentId: String,
        seasonNumber: Int,
        episodeNumber: Int,
        title: String,
        seriesTitle: String,
        coverUrl: String,
        positionMs: Long,
        durationMs: Long
    ) {
        val isCompleted = durationMs > 0 && (positionMs.toDouble() / durationMs) > 0.9

        val entity = WatchProgressEntity(
            episodeId = episodeId,
            contentType = contentType,
            contentId = contentId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            title = title,
            seriesTitle = seriesTitle,
            coverUrl = coverUrl,
            positionMs = positionMs,
            durationMs = durationMs,
            lastWatched = System.currentTimeMillis(),
            isCompleted = isCompleted
        )
        watchProgressDao.upsert(entity)

        // Sync timestamp to server for series episodes
        if (contentType == "series" && positionMs > 0) {
            playerRepository.saveTimestamp(
                eid = episodeId.toString(),
                timeSeconds = positionMs / 1000
            )
        }
    }

    suspend fun deleteProgress(episodeId: Int) = watchProgressDao.delete(episodeId)
    suspend fun clearAll() = watchProgressDao.deleteAll()
}
