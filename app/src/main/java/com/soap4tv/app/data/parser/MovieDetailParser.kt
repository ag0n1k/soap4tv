package com.soap4tv.app.data.parser

import com.soap4tv.app.data.model.MovieDetail
import com.soap4tv.app.data.model.SubtitleTrack
import com.soap4tv.app.data.network.SoapApiClient
import org.jsoup.Jsoup

object MovieDetailParser {

    private val HLS_REGEX = Regex("""file\s*:\s*"(https?://[^"]+)"""")
    private val SUBTITLE_REGEX = Regex("""subtitle\s*:\s*'([^']+)'""")
    private val POSTER_REGEX = Regex("""poster\s*:\s*"(/[^"]+)"""")
    private val TITLE_REGEX = Regex("""title\s*:\s*"([^"]+)"""")

    fun parseMovieDetail(html: String, id: Int): MovieDetail {
        val doc = Jsoup.parse(html)

        // Title from <title> tag: "Movie Name (Year) | soap4youand.me"
        val pageTitle = doc.selectFirst("title")?.text()
            ?.substringBefore("|")?.trim() ?: ""

        // HLS URL embedded in JS
        val hlsUrl = HLS_REGEX.find(html)?.groupValues?.get(1) ?: ""

        // Subtitles: '[Русский]/assets/subsm/817/ru.srt,[English]/assets/subsm/817/en.srt'
        val subtitleStr = SUBTITLE_REGEX.find(html)?.groupValues?.get(1) ?: ""
        val subtitles = parseSubtitles(subtitleStr, id)

        // Poster
        val posterPath = POSTER_REGEX.find(html)?.groupValues?.get(1) ?: ""
        val posterUrl = if (posterPath.startsWith("http")) posterPath
            else "${SoapApiClient.BASE_URL}$posterPath"

        // Info grid
        val infoRows = doc.select(".info-row")
        val infoMap = infoRows.associate { row ->
            val label = row.selectFirst(".info-label")?.text()?.trim()?.trimEnd(':') ?: ""
            val value = row.selectFirst(".info-value")?.text()?.trim() ?: ""
            label to value
        }

        // Year from title or info
        val year = pageTitle.substringAfterLast("(").substringBefore(")").toIntOrNull()
            ?: infoMap["год"]?.toIntOrNull() ?: 0

        // Description
        val description = doc.selectFirst(".movie-description, p.description, .soap-description")
            ?.text() ?: ""

        // Bookmark and watch state
        val bookmarkBtn = doc.selectFirst(".bookmark-btn")
        val isBookmarked = bookmarkBtn?.attr("data-action") == "unlike"
        val watchedBtn = doc.selectFirst(".watched-btn")
        val isWatched = watchedBtn?.attr("data-action") == "unwatch"

        // Watch/likes count
        val watchCount = doc.selectFirst("#watch_count")?.text()?.toIntOrNull() ?: 0

        return MovieDetail(
            id = id,
            title = pageTitle,
            year = year,
            duration = infoMap["длительность"] ?: infoMap["Длительность"] ?: "",
            countries = infoMap["страны"] ?: infoMap["Страна"] ?: "",
            qualities = infoMap["качество"] ?: infoMap["Качество"] ?: "",
            genres = infoMap["жанры"] ?: infoMap["Жанр"] ?: "",
            description = description,
            hlsUrl = hlsUrl,
            subtitles = subtitles,
            posterUrl = posterUrl,
            infoRows = infoMap,
            watchCount = watchCount,
            userRating = 0f, // User's personal rating, fetched separately if needed
            isBookmarked = isBookmarked,
            isWatched = isWatched
        )
    }

    /**
     * Parse subtitle string: '[Русский]/path,[English]/path'
     */
    fun parseSubtitles(subtitleStr: String, movieId: Int): List<SubtitleTrack> {
        if (subtitleStr.isBlank()) return emptyList()
        return subtitleStr.split(",").mapNotNull { part ->
            val labelMatch = Regex("""\[([^\]]+)]\s*(.+)""").find(part.trim()) ?: return@mapNotNull null
            val label = labelMatch.groupValues[1]
            val path = labelMatch.groupValues[2].trim()
            val url = if (path.startsWith("http")) path else "${SoapApiClient.BASE_URL}$path"
            val language = when {
                label.contains("рус", ignoreCase = true) -> "ru"
                label.contains("eng", ignoreCase = true) ||
                    label.contains("engl", ignoreCase = true) -> "en"
                else -> label.lowercase().take(2)
            }
            SubtitleTrack(label = label, url = url, language = language)
        }
    }
}
