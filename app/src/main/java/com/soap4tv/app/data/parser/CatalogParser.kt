package com.soap4tv.app.data.parser

import com.soap4tv.app.data.model.ContinueWatchingItem
import com.soap4tv.app.data.model.Series
import com.soap4tv.app.data.network.SoapApiClient
import org.jsoup.Jsoup

object CatalogParser {

    /**
     * Parse "Продолжить просмотр" section from /sort/my/.
     * Each item: <div class="continue-item"><a href="/soap/{slug}/{season}/?continue={ep}">...
     */
    fun parseContinueWatching(html: String): List<ContinueWatchingItem> {
        val doc = Jsoup.parse(html)
        val items = doc.select(".continue-section .continue-item")
        return items.mapNotNullLogging("parseContinueWatching") { item ->
            val link = item.selectFirst("a") ?: return@mapNotNullLogging null
            val href = link.attr("href") // /soap/Elementary/1/?continue=7
            val regex = Regex("""/soap/([^/]+)/(\d+)/\?continue=(\d+)""")
            val match = regex.find(href) ?: return@mapNotNullLogging null
            val slug = match.groupValues[1]
            val season = match.groupValues[2].toIntOrNull() ?: 1
            val episodeNum = match.groupValues[3].toIntOrNull() ?: 1

            val imgSrc = item.selectFirst("img")?.attr("src") ?: ""
            val screenshotUrl = if (imgSrc.startsWith("http")) imgSrc
            else "${SoapApiClient.BASE_URL}$imgSrc"

            ContinueWatchingItem(
                slug = slug,
                title = item.selectFirst(".continue-title")?.text() ?: slug,
                episode = item.selectFirst(".continue-episode")?.text() ?: "s${season}e${episodeNum}",
                season = season,
                episodeNum = episodeNum,
                screenshotUrl = screenshotUrl
            )
        }
    }

    fun parseSeries(html: String): List<Series> {
        val doc = Jsoup.parse(html)
        val items = doc.select("ul#soap > li.poster-item")
        return items.mapNotNullLogging("parseSeries") { li ->
            val id = li.id().removePrefix("soap-").toIntOrNull() ?: return@mapNotNullLogging null
            val link = li.selectFirst("a.d-block") ?: return@mapNotNullLogging null
            val href = link.attr("href") // "/soap/{slug}/"
            val slug = href.removePrefix("/soap/").removeSuffix("/")
            val name = li.selectFirst("span.name")?.text() ?: return@mapNotNullLogging null
            val imgEl = li.selectFirst("img.lazy") ?: li.selectFirst("img")
            val coverPath = imgEl?.attr("original-src")?.takeIf { it.isNotBlank() }
                ?: imgEl?.attr("src") ?: ""
            val coverUrl = if (coverPath.startsWith("http")) coverPath
            else "${SoapApiClient.BASE_URL}$coverPath"

            Series(
                id = id,
                name = name,
                slug = slug,
                coverUrl = coverUrl,
                year = li.dataAttr("year").toIntOrNull() ?: 0,
                likes = li.dataAttr("likes").toIntOrNull() ?: 0,
                imdbRating = li.dataAttr("imdb").toDoubleOrNull() ?: 0.0,
                genres = li.dataAttr("genre")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() },
                isComplete = li.dataAttr("complete") == "1",
                isUhd = li.dataAttr("uhd").isNotBlank(),
                isWatching = li.dataAttr("watching").isNotBlank(),
                hasNewEpisodes = li.dataAttr("new").isNotBlank()
            )
        }
    }
}
