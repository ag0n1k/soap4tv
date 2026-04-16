package com.soap4tv.app.data.parser

import com.soap4tv.app.data.model.Movie
import com.soap4tv.app.data.network.SoapApiClient
import org.jsoup.Jsoup

object MovieCatalogParser {

    fun parseMovies(html: String): List<Movie> {
        val doc = Jsoup.parse(html)
        val items = doc.select("li[id^=movie-]")
        return items.mapNotNullLogging("parseMovies") { li ->
            val id = li.id().removePrefix("movie-").toIntOrNull() ?: return@mapNotNullLogging null
            val name = li.selectFirst("span.name")?.text() ?: return@mapNotNullLogging null
            val imgEl = li.selectFirst("img")
            val coverPath = imgEl?.attr("src")?.takeIf { it.isNotBlank() } ?: ""
            val coverUrl = if (coverPath.startsWith("http")) coverPath
            else "${SoapApiClient.BASE_URL}$coverPath"

            // Use dataAttr() helper — site mixes `data:attr` (colon) and `data-attr` (hyphen).
            val year = li.dataAttr("year").toIntOrNull()
                ?: li.selectFirst("span.year")?.text()?.toIntOrNull()
                ?: 0

            Movie(
                id = id,
                name = name,
                coverUrl = coverUrl,
                year = year,
                likes = li.dataAttr("likes").toIntOrNull() ?: 0,
                imdbRating = li.dataAttr("imdb").toDoubleOrNull() ?: 0.0,
                isWatching = li.dataAttr("watching").let { it.isNotBlank() && it != "0" }
            )
        }
    }
}
