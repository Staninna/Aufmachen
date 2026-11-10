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

    private val marks = Regex("\\p{M}+")
    private val nonAlnum = Regex("[^a-z0-9]+")

    fun normalize(s: String): String {
        val d = marks.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")
        return nonAlnum.replace(d, " ").trim()
    }

    private fun tokens(norm: String): List<String> {
        val all = norm.split(' ').filter { it.isNotEmpty() }
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

    /** A name prepared once for repeated [nameSimilarity] calls. */
    class Prepared internal constructor(
        val norm: String,
        val joined: String,
        val joinedNoSpace: String,
        val tokenSet: Set<String>,
    )

    fun prepare(s: String): Prepared {
        val norm = normalize(s)
        val t = tokens(norm)
        val joined = t.joinToString(" ")
        return Prepared(norm, joined, joined.replace(" ", ""), t.toSet())
    }

    /** Fuzzy similarity of two names in 0..1. 1.0 only for equal normalised names. */
    fun nameSimilarity(a: String, b: String): Double = nameSimilarity(prepare(a), prepare(b))

    fun nameSimilarity(a: Prepared, b: Prepared): Double {
        if (a.norm.isEmpty() || b.norm.isEmpty()) return 0.0
        if (a.norm == b.norm) return 1.0
        val ja = a.joined
        val jb = b.joined
        if (ja == jb) return 0.95
        if (minOf(ja.length, jb.length) < 3) return 0.0
        var best = levenshteinRatio(ja, jb)
        best = maxOf(best, levenshteinRatio(a.joinedNoSpace, b.joinedNoSpace))
        val sa = a.tokenSet
        val sb = b.tokenSet
        val inter = sa.count { it in sb }
        if (inter > 0) {
            best = maxOf(best, inter.toDouble() / (sa.size + sb.size - inter))
            val small = if (sa.size <= sb.size) sa else sb
            val big = if (small === sa) sb else sa
            if (big.containsAll(small) && small.joinToString("").length >= 3) best = maxOf(best, 0.9)
        }
        return best
    }

    /**
     * Same as [nameSimilarity] when the result is at least [min], else some value below [min].
     * Skips the edit-distance work for pairs that cannot reach [min], which keeps big pastes fast.
     */
    fun nameSimilarityAtLeast(a: Prepared, b: Prepared, min: Double): Double {
        if (a.norm.isEmpty() || b.norm.isEmpty()) return 0.0
        if (a.norm == b.norm) return 1.0
        val ja = a.joined
        val jb = b.joined
        if (ja == jb) return 0.95
        if (minOf(ja.length, jb.length) < 3) return 0.0
        var best = 0.0
        val sa = a.tokenSet
        val sb = b.tokenSet
        val inter = sa.count { it in sb }
        if (inter > 0) {
            best = inter.toDouble() / (sa.size + sb.size - inter)
            val small = if (sa.size <= sb.size) sa else sb
            val big = if (small === sa) sb else sa
            if (big.containsAll(small) && small.joinToString("").length >= 3) best = maxOf(best, 0.9)
        }
        val floor = maxOf(min, best)
        best = maxOf(best, boundedRatio(ja, jb, floor))
        best = maxOf(best, boundedRatio(a.joinedNoSpace, b.joinedNoSpace, maxOf(floor, best)))
        return best
    }

    /** [levenshteinRatio] when it is at least [min], else 0. */
    private fun boundedRatio(a: String, b: String, min: Double): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val maxLen = maxOf(a.length, b.length)
        val maxDist = ((1.0 - min) * maxLen + 1e-9).toInt()
        if (kotlin.math.abs(a.length - b.length) > maxDist) return 0.0
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                val v = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > maxDist) return 0.0
            val t = prev; prev = cur; cur = t
        }
        val d = prev[b.length]
        return if (d > maxDist) 0.0 else 1.0 - d.toDouble() / maxLen
    }

    /** Names a candidate is known by: display names and the brand labels of its domains. */
    fun candidateNames(c: ProofCandidate): List<String> =
        (c.displayNames + c.domains.mapNotNull { brandLabel(it) }).filter { it.isNotBlank() }.distinct()
}
