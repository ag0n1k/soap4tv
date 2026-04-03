package com.soap4tv.app.data.repository

import com.soap4tv.app.data.model.Movie
import com.soap4tv.app.data.model.Series
import com.soap4tv.app.data.network.SoapApiClient
import com.soap4tv.app.data.parser.CatalogParser
import com.soap4tv.app.data.parser.MovieCatalogParser
import org.jsoup.Jsoup
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val apiClient: SoapApiClient
) {
    /**
     * Server-side search via /search/?q=
     * Returns Pair(series, movies) from results.
     */
    suspend fun search(query: String): Result<Pair<List<Series>, List<Movie>>> {
        if (query.isBlank()) return Result.success(Pair(emptyList(), emptyList()))

        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        return apiClient.fetchPage("/search/?q=$encoded").map { html ->
            val doc = Jsoup.parse(html)

            // Parse series results (id="soap-{id}")
            val seriesItems = doc.select("li[id^=soap-]")
            val seriesHtml = if (seriesItems.isNotEmpty()) {
                "<ul id=\"soap\">${seriesItems.outerHtml()}</ul>"
            } else ""
            val series = if (seriesHtml.isNotEmpty()) CatalogParser.parseSeries(seriesHtml) else emptyList()

            // Parse movie results (id="movie-{id}")
            val movieItems = doc.select("li[id^=movie-]")
            val movieHtml = if (movieItems.isNotEmpty()) movieItems.outerHtml() else ""
            val movies = if (movieHtml.isNotEmpty()) MovieCatalogParser.parseMovies(movieHtml) else emptyList()

            Pair(series, movies)
        }
    }
}
