package com.staninna.aufmachen.engine

// Ported verbatim from mail_triage.py.

val FREEMAIL: Set<String> = setOf(
    "aol.com", "casema.nl", "chello.nl", "gmail.com", "gmx.com", "gmx.de", "gmx.net", "googlemail.com",
    "hetnet.nl", "home.nl", "hotmail.com", "hotmail.nl", "icloud.com", "kpnmail.nl", "live.com", "live.nl",
    "mac.com", "mail.com", "me.com", "msn.com", "online.nl", "outlook.com", "outlook.nl", "planet.nl",
    "pm.me", "proton.me", "protonmail.com", "quicknet.nl", "telfort.nl", "tuta.com", "tuta.io",
    "tutanota.com", "tutanota.de", "upcmail.nl", "web.de", "xs4all.nl", "yahoo.com", "yahoo.nl", "ziggo.nl",
    "zonnet.nl",
)

val MULTI_PART_TLDS: Set<String> = setOf(
    "ac.uk", "co.in", "co.jp", "co.nz", "co.uk", "co.za", "com.au", "com.br", "com.mx", "com.sg", "com.tr",
    "gov.uk", "net.au", "org.uk",
)

val NOREPLY_RE = Regex(
    "no-?reply|donotreply|do-not-reply|notification|mailer|" +
    "newsletter|^news@|^info@|marketing|^service@|bounce|" +
    "^alerts?@|^updates?@|^hello@|^team@|^support@",
    RegexOption.IGNORE_CASE,
)

/** Registrable domain of a host. Same rules as base_domain() in the prototype. */
fun baseDomain(host: String): String {
    val h = host.lowercase().trim { it == '.' || it == ' ' || it == '>' }
    val parts = h.split(".")
    if (parts.size >= 3 && parts.takeLast(2).joinToString(".") in MULTI_PART_TLDS) {
        return parts.takeLast(3).joinToString(".")
    }
    return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else h
}
