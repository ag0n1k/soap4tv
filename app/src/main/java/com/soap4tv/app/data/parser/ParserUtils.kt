package com.soap4tv.app.data.parser

import android.util.Log
import org.jsoup.nodes.Element

private const val TAG = "Soap4tvParser"

/**
 * Site uses non-standard `data:attr` (colon) as well as standard `data-attr` (hyphen).
 * Older Jsoup versions normalize inconsistently; try both.
 */
internal fun Element.dataAttr(name: String): String {
    val colon = attr("data:$name")
    if (colon.isNotBlank()) return colon
    return attr("data-$name")
}

/**
 * Map + skip-on-failure with debug log so we can diagnose silent drops.
 * Replaces bare `try { ... } catch { null }` in parsers.
 */
internal inline fun <T, R : Any> Iterable<T>.mapNotNullLogging(
    parserName: String,
    transform: (T) -> R?
): List<R> = mapNotNull { input ->
    try {
        transform(input)
    } catch (e: Exception) {
        Log.w(TAG, "$parserName: skipped an item — ${e.javaClass.simpleName}: ${e.message}")
        null
    }
}
