package com.soap4tv.app.data.parser

import org.jsoup.Jsoup

object TokenParser {
    /**
     * Extract API token from authenticated page HTML.
     * Token is in: <div id="token" data:token="...">
     */
    fun parseToken(html: String): String? {
        val doc = Jsoup.parse(html)
        val tokenEl = doc.selectFirst("#token")
        val tokenVal = tokenEl?.attr("data:token")?.takeIf { it.isNotBlank() }
            ?: tokenEl?.attr("data-token")?.takeIf { it.isNotBlank() }
        return tokenVal
            ?: doc.selectFirst("input[name=token]")?.`val`()?.takeIf { it.isNotBlank() }
    }

    /**
     * Check if the page belongs to an authenticated session.
     */
    fun isAuthenticated(html: String): Boolean {
        val doc = Jsoup.parse(html)
        return doc.selectFirst("#token") != null
    }
}
