package com.soap4tv.app.data.parser

import org.jsoup.Jsoup

object TokenParser {
    /**
     * The token lives in `<div id="token" data:token="...">`. Both helpers below take a
     * regex fast path first: they run on every session check and token refresh, and a full
     * Jsoup parse of the ~1 MB catalog page costs hundreds of milliseconds on TV hardware.
     * Jsoup stays as the fallback so any markup the regex does not anticipate still works.
     */
    private val TOKEN_TAG = Regex("""<[^<>]*\bid=["']token["'][^<>]*>""", RegexOption.IGNORE_CASE)
    private val TOKEN_ATTR = Regex("""\bdata[:-]token=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    /**
     * Extract API token from authenticated page HTML.
     */
    fun parseToken(html: String): String? {
        TOKEN_TAG.find(html)?.let { tag ->
            TOKEN_ATTR.find(tag.value)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return parseTokenWithJsoup(html)
    }

    /**
     * Check if the page belongs to an authenticated session.
     */
    fun isAuthenticated(html: String): Boolean {
        if (TOKEN_TAG.containsMatchIn(html)) return true
        return Jsoup.parse(html).selectFirst("#token") != null
    }

    private fun parseTokenWithJsoup(html: String): String? {
        val doc = Jsoup.parse(html)
        val tokenEl = doc.selectFirst("#token")
        val tokenVal = tokenEl?.attr("data:token")?.takeIf { it.isNotBlank() }
            ?: tokenEl?.attr("data-token")?.takeIf { it.isNotBlank() }
        return tokenVal
            ?: doc.selectFirst("input[name=token]")?.`val`()?.takeIf { it.isNotBlank() }
    }
}
