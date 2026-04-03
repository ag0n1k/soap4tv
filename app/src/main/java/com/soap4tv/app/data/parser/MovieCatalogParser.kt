package com.soap4tv.app.data.parser

import com.soap4tv.app.data.model.Movie
import com.soap4tv.app.data.network.SoapApiClient
import org.jsoup.Jsoup

object MovieCatalogParser {

    fun parseMovies(html: String): List<Movie> {
        val doc = Jsoup.parse(html)
        // Selector: li[id^="movie-"] — all li elements whose id starts with "movie-"
        val items = doc.select("li[id^=movie-]")
        return items.mapNotNull { li ->
            try {
                val id = li.id().removePrefix("movie-").toIntOrNull() ?: return@mapNotNull null
                val name = li.selectFirst("span.name")?.text() ?: return@mapNotNull null
                val imgEl = li.selectFirst("img")
                val coverPath = imgEl?.attr("src")?.takeIf { it.isNotBlank() } ?: ""
                val coverUrl = if (coverPath.startsWith("http")) coverPath
                    else "${SoapApiClient.BASE_URL}$coverPath"

                // Year from data:year attribute or span.year
                val year = li.attr("data:year").toIntOrNull()
                    ?: li.selectFirst("span.year")?.text()?.toIntOrNull()
                    ?: 0

                Movie(
                    id = id,
                    name = name,
                    coverUrl = coverUrl,
                    year = year,
                    likes = li.attr("data:likes").toIntOrNull() ?: 0,
                    imdbRating = li.attr("data:imdb").toDoubleOrNull() ?: 0.0,
                    isWatching = li.attr("data:watching").let { it.isNotBlank() && it != "0" }
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
