package com.staninna.aufmachen.engine

internal object GoogleConn {
    private const val THRESHOLD = 0.82

    private val chromeExact = setOf(
        "third party apps & services", "third party apps and services", "third-party apps & services",
        "your connections to third party apps & services", "your connections to third-party apps & services",
        "sign in with google", "has some account access", "has account access", "manage", "learn more",
        "see all", "see details", "back", "search", "search google account", "google account", "home",
        "personal info", "data & privacy", "security", "people & sharing", "payments & subscriptions", "about",
        "privacy", "terms", "help", "feedback", "privacy policy", "terms of service", "skip to main content",
        "navigation menu", "apps & services", "connections", "sign in options", "delete connection",
        "apps en services van derden", "uw verbindingen met apps en services van derden",
        "je verbindingen met apps en services van derden", "inloggen met google", "heeft beperkte toegang tot het account",
        "heeft toegang tot een deel van het account", "beheren", "meer informatie", "alles weergeven",
        "details bekijken", "terug", "zoeken", "google-account", "startpagina", "persoonlijke gegevens",
        "gegevens en privacy", "beveiliging", "personen en delen", "betalingen en abonnementen",
        "over", "privacybeleid", "gebruiksvoorwaarden", "feedback", "help", "verbinding verwijderen",
        // "Linked apps" layout (2026).
        "linked apps", "manage data sharing", "review your linked apps", "learn about linked apps",
        "search linked apps by name", "view by", "done", "privacytermshelpabout", "close", "menu", "more",
        "gekoppelde apps", "gegevensdeling beheren", "je gekoppelde apps bekijken", "uw gekoppelde apps bekijken",
        "meer informatie over gekoppelde apps", "gekoppelde apps zoeken op naam", "weergeven op", "bekijken op",
        "klaar", "gereed", "privacyvoorwaardenhulpover", "sluiten", "meer",
    )
    private val chromePrefixes = listOf(
        "since ", "sinds ", "has access", "has some", "heeft toegang", "heeft beperkte", "signed in with",
        "ingelogd met", "your connections", "uw verbindingen", "je verbindingen", "third-party", "third party",
        "showing ", "toont ", "last used", "laatst gebruikt", "access to ", "toegang tot ",
        "see the data", "bekijk de gegevens", "learn about ", "search linked", "zoek gekoppelde",
    )
    private val dateOnly = Regex(
        "^(?:\\d{1,2}\\s+\\p{L}{3,9}\\.?\\s+\\d{4}|\\p{L}{3,9}\\.?\\s+\\d{1,2},?\\s+\\d{4})$",
    )
    private val subjectHints = listOf(
        "access", "connected", "connection", "signed in with google", "sign in with google", "sign-in with google",
        "third-party", "third party", "toegang", "verbonden", "verbinding", "gekoppeld", "ingelogd met google",
        "inloggen met google", "derden",
    )
    private val quoted = Regex("[\"“”'‘’«»]([^\"“”'‘’«»]{2,40})[\"“”'‘’«»]")

    fun parse(clipboard: String): List<String> {
        val seen = LinkedHashMap<String, String>()
        for (raw in clipboard.lines()) {
            val line = raw.replace(' ', ' ').trim().trim('•', '·').trim()
            if (line.isEmpty() || isChrome(line)) continue
            seen.putIfAbsent(line.lowercase(), line)
        }
        return seen.values.toList()
    }

    private fun isChrome(line: String): Boolean {
        val l = line.lowercase().trimEnd('.', ':')
        if (l in chromeExact) return true
        if (chromePrefixes.any { l.startsWith(it) }) return true
        if ('@' in line) return true
        if (dateOnly.matches(line)) return true
        if (l.all { !it.isLetter() }) return true
        if (l.length > 60 || l.split(' ').size > 6) return true
        return false
    }

    fun match(names: List<String>, candidates: List<ProofCandidate>): List<NameMatch> {
        if (names.isEmpty() || candidates.isEmpty()) return emptyList()
        // Prepare every candidate name once; the old per-pair normalisation froze the UI on big pastes.
        val prepared = candidates.map { c -> c.groupKey to ParserText.candidateNames(c).map(ParserText::prepare) }
        return names.mapNotNull { n ->
            // Store names often carry a tagline: "Coinbase: Buy Crypto & Stocks", "Lime - #RideGreen".
            val pns = listOf(n, brandPart(n)).distinct().map(ParserText::prepare)
            var best: NameMatch? = null
            for ((key, cn) in prepared) {
                var s = 0.0
                for (p in cn) for (pn in pns) {
                    s = maxOf(s, ParserText.nameSimilarityAtLeast(pn, p, maxOf(THRESHOLD, best?.score ?: 0.0)))
                }
                if (s >= THRESHOLD && (best == null || s > best.score)) best = NameMatch(n, key, s)
            }
            best
        }
    }

    private val tagline = Regex("\\s*(?::|\\s[-–—|]\\s).*$")

    private fun brandPart(name: String): String = tagline.replace(name, "").trim().ifEmpty { name }

    fun isThirdPartyAccessHint(fromAddr: String, subject: String): Boolean {
        val host = fromAddr.substringAfterLast('@').lowercase().trim().trimEnd('>')
        val google = host == "google.com" || host == "accounts.google.com" || host == "googlemail.com" ||
            host.endsWith(".google.com")
        if (!google) return false
        val s = subject.lowercase()
        return subjectHints.any { it in s }
    }

    fun candidatesFromSubject(subject: String, candidates: List<ProofCandidate>): List<NameMatch> {
        val found = LinkedHashMap<String, NameMatch>()
        val normSubject = " " + ParserText.normalize(subject) + " "
        for (c in candidates) {
            for (n in ParserText.candidateNames(c)) {
                val nn = ParserText.normalize(n)
                if (nn.length >= 3 && " $nn " in normSubject) {
                    val m = NameMatch(n, c.groupKey, 1.0)
                    if ((found[c.groupKey]?.score ?: 0.0) < 1.0) found[c.groupKey] = m
                    break
                }
            }
        }
        val quotedNames = quoted.findAll(subject).map { it.groupValues[1].trim() }.toList()
        for (m in match(quotedNames, candidates)) {
            if ((found[m.groupKey]?.score ?: 0.0) < m.score) found[m.groupKey] = m
        }
        return found.values.sortedByDescending { it.score }
    }
}
