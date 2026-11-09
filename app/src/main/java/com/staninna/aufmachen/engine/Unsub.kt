package com.staninna.aufmachen.engine

private val ANGLE = Regex("<([^>]+)>")

/** URIs of a raw List-Unsubscribe header, in order. Without angle brackets, the trimmed value is one link. */
fun parseUnsubLinks(header: String): List<String> {
    val links = ANGLE.findAll(header).map { it.groupValues[1] }.toList()
    if (links.isNotEmpty()) return links
    return if (header.isBlank()) emptyList() else listOf(header.trim())
}

fun pickUnsubMethod(links: List<String>, oneClick: Boolean): UnsubMethod {
    fun has(prefix: String) = links.any { it.trim().startsWith(prefix, ignoreCase = true) }
    return when {
        oneClick && has("https:") -> UnsubMethod.ONE_CLICK
        has("mailto:") -> UnsubMethod.EMAIL
        has("http:") || has("https:") -> UnsubMethod.WEB
        else -> UnsubMethod.NONE
    }
}
