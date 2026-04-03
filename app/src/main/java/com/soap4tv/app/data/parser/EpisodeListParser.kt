package com.soap4tv.app.data.parser

import com.soap4tv.app.data.model.Episode
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Helper: try colon-style attr first (data:foo), then hyphen-style (data-foo).
 * The site uses non-standard `data:attr` syntax; Jsoup may handle it differently across versions.
 */
private fun Element.dataAttr(name: String): String {
    val colon = attr("data:$name")
    if (colon.isNotBlank()) return colon
    return attr("data-$name")
}

object EpisodeListParser {

    /**
     * Parse the season page /soap/{slug}/{season}/ into a list of episodes.
     * Returns Pair(seasonTitle, episodes).
     */
    fun parseEpisodes(html: String): Pair<String, List<Episode>> {
        val doc = Jsoup.parse(html)
        val seasonTitle = doc.selectFirst(".episodes-season-badge")?.text() ?: ""
        val cards = doc.select(".episode-card")

        val episodes = cards.mapNotNull { card ->
            try {
                parseEpisodeCard(card)
            } catch (e: Exception) {
                null
            }
        }
        return seasonTitle to episodes
    }

    private fun parseEpisodeCard(card: Element): Episode {
        // Episode ID from collapse target: data-bs-target="#ep-{id}"
        val collapseTarget = card.selectFirst("[data-bs-target]")
            ?.attr("data-bs-target") ?: ""
        val episodeId = collapseTarget.removePrefix("#ep-").toIntOrNull() ?: 0

        // Play button: div.theme-play
        val playEl = card.selectFirst("div.theme-play")
        val eid = playEl?.dataAttr("eid")?.takeIf { it.isNotBlank() }
        val sid = playEl?.dataAttr("sid")?.takeIf { it.isNotBlank() }
        val hash = playEl?.dataAttr("hash")?.takeIf { it.isNotBlank() }
        val canPlay = playEl != null && (playEl.hasAttr("data:play") || playEl.hasAttr("data-play"))

        // Watch status: .episode-watched > div (class "yes" = watched)
        val watchedDiv = card.selectFirst(".episode-watched > div")
        val isWatched = watchedDiv?.hasClass("yes") ?: false

        // Quality
        val qualityBadge = card.selectFirst(".quality-badge")
        val qualityLabel = qualityBadge?.text() ?: ""
        val quality = card.dataAttr("quality").toIntOrNull() ?: 0

        // Translate / dub name
        val translateBadge = card.selectFirst(".translate-badge")
        val translateLabel = translateBadge?.text() ?: ""
        val translate = card.dataAttr("translate")

        // Titles
        val titleRu = card.selectFirst(".episode-title")?.text() ?: ""
        val titleEn = card.selectFirst(".episode-title-en")?.text() ?: ""

        // Metadata from collapse section
        val airDate = findMeta(card, "Первый показ")
        val stars = findMeta(card, "Звёзды")
        val writers = findMeta(card, "Сценаристы")
        val description = card.selectFirst(".spoiler-text")?.text() ?: ""

        return Episode(
            id = episodeId,
            number = card.dataAttr("episode").toIntOrNull() ?: 0,
            titleRu = titleRu,
            titleEn = titleEn,
            translate = translate,
            translateLabel = translateLabel,
            quality = quality,
            qualityLabel = qualityLabel,
            isWatched = isWatched,
            airDate = airDate,
            stars = stars,
            writers = writers,
            description = description,
            eid = eid,
            sid = sid,
            hash = hash,
            canPlay = canPlay
        )
    }

    private fun findMeta(card: Element, labelText: String): String {
        return card.select(".meta-row").find { row ->
            row.selectFirst(".meta-label")?.text()?.contains(labelText) == true
        }?.selectFirst(".meta-value")?.text() ?: ""
    }
}
