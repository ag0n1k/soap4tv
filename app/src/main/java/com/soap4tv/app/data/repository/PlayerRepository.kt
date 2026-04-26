package com.soap4tv.app.data.repository

import android.util.Log
import com.soap4tv.app.data.model.PlaybackData
import com.soap4tv.app.data.model.SubtitleTrack
import com.soap4tv.app.data.network.Md5Util
import com.soap4tv.app.data.network.SoapApiClient
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PlayerRepository"

@Singleton
class PlayerRepository @Inject constructor(
    private val apiClient: SoapApiClient,
    private val authRepository: AuthRepository
) {
    /**
     * Get playback data for a series episode.
     * Requires: eid, sid, hash (all from play button data attributes).
     * Computes: md5(token + eid + sid + hash) as request hash.
     */
    suspend fun getSeriesPlaybackData(
        eid: String,
        sid: String,
        hash: String
    ): Result<PlaybackData> {
        Log.d(TAG, "getSeriesPlaybackData: eid=$eid, sid=$sid, hash=$hash")
        return authRepository.withTokenRefresh { token ->
            val computedHash = Md5Util.computePlayHash(
                token = token,
                eid = eid,
                sid = sid,
                hash = hash
            )
            Log.d(TAG, "API call: /api/v2/play/episode/$eid, computedHash=$computedHash")
            apiClient.apiPost(
                path = "/api/v2/play/episode/$eid",
                token = token,
                params = mapOf(
                    "eid" to eid,
                    "sid" to sid,
                    "hash" to computedHash
                )
            ).mapCatching { json ->
                Log.d(TAG, "API response: ${json.take(500)}")
                parsePlayResponse(json, eid, sid)
            }
        }
    }

    private fun parsePlayResponse(json: String, eid: String, sid: String): PlaybackData {
        if (json.isBlank() || !json.trimStart().startsWith("{")) {
            throw Exception("Server returned non-JSON (HTML?). First 200 chars: ${json.take(200)}")
        }
        val obj = JSONObject(json)
        val isOk = obj.optBoolean("ok", false) || obj.optInt("ok", 0) == 1
        if (!isOk) {
            Log.w(TAG, "Play API failed. Full response: $json")
            throw Exception("Play API error: ${obj.optString("msg", "unknown")}. Response: ${json.take(300)}")
        }

        val streamUrl = obj.getString("stream")
        val posterUrl = obj.optString("poster", "")
        val title = obj.optString("title", "")
        val startFrom = obj.optLong("start_from", 0L)

        // Subtitles from response
        val subsObj = obj.optJSONObject("subs")
        val subtitles = mutableListOf<SubtitleTrack>()

        if (subsObj != null) {
            if (subsObj.optBoolean("ru", false)) {
                subtitles.add(
                    SubtitleTrack(
                        label = "Русский",
                        url = "${SoapApiClient.BASE_URL}/subs/$sid/$eid/1.srt",
                        language = "ru"
                    )
                )
            }
            if (subsObj.optBoolean("en", false)) {
                subtitles.add(
                    SubtitleTrack(
                        label = "English",
                        url = "${SoapApiClient.BASE_URL}/subs/$sid/$eid/2.srt",
                        language = "en"
                    )
                )
            }
        }

        return PlaybackData(
            streamUrl = streamUrl,
            subtitles = subtitles,
            posterUrl = if (posterUrl.startsWith("http")) posterUrl
                else "${SoapApiClient.BASE_URL}$posterUrl",
            title = title,
            startFrom = startFrom,
            episodeId = eid.toIntOrNull() ?: 0
        )
    }

    /**
     * Save playback timestamp to server (series episodes only).
     */
    suspend fun saveTimestamp(eid: String, timeSeconds: Long): Result<Unit> {
        val token = authRepository.getToken() ?: return Result.failure(Exception("Not authenticated"))
        return apiClient.apiPost(
            path = "/api/v2/play/episode/$eid/savets/",
            token = token,
            params = mapOf(
                "eid" to eid,
                "time" to timeSeconds.toString()
            )
        ).map { }
    }
}
