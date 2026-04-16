package com.soap4tv.app.data.parser

import android.util.Log
import com.soap4tv.app.data.model.Season
import com.soap4tv.app.data.model.SeriesDetail
import com.soap4tv.app.data.network.SoapApiClient
import org.jsoup.Jsoup

object SeriesDetailParser {

    private const val TAG = "SeriesDetailParser"

    fun parseSeriesDetail(html: String, slug: String): SeriesDetail {
        val doc = Jsoup.parse(html)

        // ID from series cover or page metadata
        val id = extractSeriesId(html, slug)

        // Title (h1 with optional RU span inside)
        val titleEl = doc.selectFirst("h1.soap-title")
        val titleRu = titleEl?.selectFirst("span.soap-title-ru")?.text()
            ?.removeSurrounding("(", ")") ?: ""
        val title = titleEl?.ownText()?.trim()
            ?: doc.selectFirst("title")?.text()?.substringBefore("|")?.trim() ?: ""

        // Description
        val description = doc.selectFirst("p.soap-description")?.text() ?: ""

        // Info grid rows
        val infoRows = doc.select(".info-grid .info-row")
        val infoMap = infoRows.associate { row ->
            val label = row.selectFirst(".info-label")?.text()?.trim()?.trimEnd(':') ?: ""
            val value = row.selectFirst(".info-value")?.text()?.trim() ?: ""
            label to value
        }

        // Ratings
        val soapRating = doc.selectFirst(".current_rating")?.text()?.trim() ?: "--"
        val imdbRating = doc.select(".rating-item").find { it.text().contains("IMDb") }
            ?.selectFirst("a")?.text()?.trim() ?: ""
        val kpRating = doc.select(".rating-item").find {
            it.text().contains("Кинопоиск") || it.text().contains("KP")
        }?.selectFirst("a")?.text()?.trim() ?: ""

        // Seasons: look for poster-item elements that link to season pages
        val seasons = mutableListOf<Season>()
        doc.select("li.poster-item").forEach { li ->
            val link = li.selectFirst("a") ?: return@forEach
            val href = link.attr("href")
            // Season links look like /soap/{slug}/1/, /soap/{slug}/2/
            val seasonRegex = Regex("""/soap/[^/]+/(\d+)/$""")
            val match = seasonRegex.find(href) ?: return@forEach
            val seasonNum = match.groupValues[1].toIntOrNull() ?: return@forEach
            val imgEl = li.selectFirst("img.lazy") ?: li.selectFirst("img")
            val coverPath = imgEl?.attr("original-src")?.takeIf { it.isNotBlank() }
                ?: imgEl?.attr("src") ?: ""
            val coverUrl = if (coverPath.startsWith("http")) coverPath
                else "${SoapApiClient.BASE_URL}$coverPath"
            seasons.add(Season(number = seasonNum, coverUrl = coverUrl, url = href))
        }
        seasons.sortBy { it.number }

        // Cover: use first season's cover or construct from series id
        val coverUrl = seasons.firstOrNull()?.coverUrl
            ?: "${SoapApiClient.BASE_URL}/assets/covers/soap/$id.jpg"

        // Watch button state
        val isWatching = doc.selectFirst(".watching-btn.active, .watch-btn.active") != null

        // Watch count
        val watchCount = doc.selectFirst("span.count")?.text()?.toIntOrNull() ?: 0

        return SeriesDetail(
            id = id,
            slug = slug,
            title = title,
            titleRu = titleRu,
            description = description,
            status = infoMap["Статус"] ?: "",
            year = infoMap["Год выхода"]?.toIntOrNull() ?: 0,
            duration = infoMap["Длительность"] ?: "",
            country = infoMap["Страна"] ?: "",
            network = infoMap["Канал"] ?: "",
            genres = infoMap["Жанр"] ?: "",
            actors = infoMap["Актёры"] ?: "",
            soapRating = soapRating,
            imdbRating = imdbRating,
            kinopoiskRating = kpRating,
            coverUrl = coverUrl,
            seasons = seasons,
            watchCount = watchCount,
            isWatching = isWatching
        )
    }

    private fun extractSeriesId(html: String, slug: String): Int {
        // Try watch/unwatch API URL first.
        val watchRegex = Regex("""api/v2/soap/(?:watch|unwatch)/(\d+)""")
        watchRegex.find(html)?.let { return it.groupValues[1].toIntOrNull() ?: 0 }

        // Fall back to `soap-<id>` anchors/ids on the page.
        val soapIdRegex = Regex("""soap-(\d+)""")
        soapIdRegex.find(html)?.let { return it.groupValues[1].toIntOrNull() ?: 0 }

        // Last resort — return 0 instead of slug.hashCode(), which created a
        // fake numeric id that polluted caches and broke API calls silently.
        Log.w(TAG, "Could not extract series id for slug=$slug; returning 0")
        return 0
    }
}
