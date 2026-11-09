package com.staninna.aufmachen.engine

import java.text.Normalizer

/** Small text helpers shared by the parsers. Kept separate from any other domain helper on purpose. */
internal object ParserText {
    private val multiPartSecond = setOf("co", "com", "org", "net", "gov", "edu", "ac", "or", "ne")
    private val noiseTokens = setOf(
        "team", "support", "the", "no", "reply", "noreply", "customer", "service", "services", "info",
        "mail", "klantenservice", "de", "het", "een", "via", "notifications", "official",
    )

    /** Registrable domain of a host or URL, multi-part TLD aware (co.uk and similar). Lowercase. */
    fun registrable(hostOrUrl: String): String? {
        var h = hostOrUrl.trim().lowercase()
        h = h.substringAfter("://", h).substringBefore('/').substringBefore('?').substringBefore('#')
        h = h.substringAfterLast('@').substringBefore(':').trim('.')
        if (h.isEmpty() || !h.contains('.')) return null
        val parts = h.split('.')
        if (parts.any { it.isEmpty() }) return null
        val n = parts.size
        val take = if (n >= 3 && parts[n - 1].length == 2 && parts[n - 2] in multiPartSecond) 3 else 2
        return parts.takeLast(minOf(take, n)).joinToString(".")
    }

    /** Domain label without the public suffix, e.g. "example" for example.co.uk. */
    fun brandLabel(domain: String): String? {
        val reg = registrable(domain) ?: return null
        return reg.substringBefore('.').takeIf { it.length >= 3 }
    }

    fun normalize(s: String): String {
        val d = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return d.replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    private fun tokens(s: String): List<String> {
        val all = normalize(s).split(' ').filter { it.isNotEmpty() }
        val kept = all.filter { it !in noiseTokens }
        return kept.ifEmpty { all }
    }

    fun levenshteinRatio(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return 1.0 - prev[b.length].toDouble() / maxOf(a.length, b.length)
    }

    /** Fuzzy similarity of two names in 0..1. 1.0 only for equal normalised names. */
    fun nameSimilarity(a: String, b: String): Double {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return 0.0
        if (na == nb) return 1.0
        val ta = tokens(a)
        val tb = tokens(b)
        val ja = ta.joinToString(" ")
        val jb = tb.joinToString(" ")
        if (ja == jb) return 0.95
        var best = levenshteinRatio(ja, jb)
        best = maxOf(best, levenshteinRatio(ja.replace(" ", ""), jb.replace(" ", "")))
        val sa = ta.toSet()
        val sb = tb.toSet()
        val inter = sa.intersect(sb).size
        if (inter > 0) {
            best = maxOf(best, inter.toDouble() / sa.union(sb).size)
            val small = if (sa.size <= sb.size) sa else sb
            val big = if (small === sa) sb else sa
            if (big.containsAll(small) && small.joinToString("").length >= 3) best = maxOf(best, 0.9)
        }
        if (minOf(ja.length, jb.length) < 3) return if (ja == jb) 0.95 else 0.0
        return best
    }

    /** Names a candidate is known by: display names and the brand labels of its domains. */
    fun candidateNames(c: ProofCandidate): List<String> =
        (c.displayNames + c.domains.mapNotNull { brandLabel(it) }).filter { it.isNotBlank() }.distinct()
}
