package com.staninna.aufmachen.ui.unsubscribe

import com.staninna.aufmachen.engine.UnsubMethod
import com.staninna.aufmachen.engine.unsubMethod
import java.net.URI
import java.net.URLDecoder

/** A parsed `mailto:` unsubscribe URI. */
data class Mailto(val address: String, val subject: String, val body: String)

private fun pct(s: String): String =
    runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)

private fun form(s: String): String =
    runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

/**
 * Parses `mailto:addr?subject=..&body=..`. Returns null when the URI is not a mailto or has no address.
 * The address keeps a literal plus. In subject and body a plus means a space, as senders write it.
 * [subject] falls back to "unsubscribe" when the URI has none.
 */
fun parseMailto(uri: String): Mailto? {
    val s = uri.trim()
    if (!s.startsWith("mailto:", ignoreCase = true)) return null
    val rest = s.substring("mailto:".length)
    val q = rest.indexOf('?')
    val addrPart = if (q >= 0) rest.substring(0, q) else rest
    val query = if (q >= 0) rest.substring(q + 1) else ""
    val address = pct(addrPart).split(',').first().trim()
    if (address.isEmpty() || '@' !in address) return null
    var subject = ""
    var body = ""
    query.split('&').filter { it.isNotEmpty() }.forEach { p ->
        val i = p.indexOf('=')
        if (i < 0) return@forEach
        when (p.substring(0, i).lowercase()) {
            "subject" -> subject = form(p.substring(i + 1))
            "body" -> body = form(p.substring(i + 1))
        }
    }
    return Mailto(address, subject.ifBlank { DEFAULT_SUBJECT }, body.ifBlank { DEFAULT_SUBJECT })
}

const val DEFAULT_SUBJECT = "unsubscribe"

/** Host of [url], or the url itself when it has none. */
fun hostOf(url: String): String =
    runCatching { URI(url.trim()).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url.trim()

enum class UnsubStep { ONE_CLICK, EMAIL, WEB }

/** What the app will do for one sender. [fellBack] is true when an email step became a web step. */
data class UnsubPlan(
    val step: UnsubStep,
    val link: String,
    val mailto: Mailto? = null,
    val fellBack: Boolean = false,
)

private fun firstLink(links: List<String>, vararg prefixes: String): String? =
    links.map { it.trim() }.firstOrNull { l -> prefixes.any { l.startsWith(it, ignoreCase = true) } }

/**
 * Chooses the step for a sender. Returns null when nothing can be done (no link, or an email-only
 * sender while no old-account mail settings exist). [canSendMail] is true when IMAP/SMTP is configured.
 */
fun planUnsub(links: List<String>, oneClick: Boolean, canSendMail: Boolean): UnsubPlan? {
    val web = firstLink(links, "https:", "http:")
    return when (unsubMethod(links, oneClick)) {
        UnsubMethod.ONE_CLICK -> firstLink(links, "https:")?.let { UnsubPlan(UnsubStep.ONE_CLICK, it) }
        UnsubMethod.EMAIL -> {
            val raw = firstLink(links, "mailto:")
            val mt = raw?.let(::parseMailto)
            when {
                mt != null && canSendMail -> UnsubPlan(UnsubStep.EMAIL, raw, mt)
                web != null -> UnsubPlan(UnsubStep.WEB, web, fellBack = true)
                else -> null
            }
        }
        UnsubMethod.WEB -> web?.let { UnsubPlan(UnsubStep.WEB, it) }
        UnsubMethod.NONE -> null
    }
}
