package com.staninna.aufmachen.engine

internal object ProofRanker {
    private const val W_REPLY = 0.65
    private const val W_NAME = 0.6
    private const val W_BRAND = 0.1
    private const val NAME_MIN_SIM = 0.85

    private val ignoredLinkDomains = setOf(
        "facebook.com", "fb.com", "fb.me", "instagram.com", "x.com", "twitter.com", "t.co", "linkedin.com",
        "youtube.com", "youtu.be", "tiktok.com", "reddit.com", "mastodon.social", "mastodon.online", "github.com",
        "githubusercontent.com", "pinterest.com", "snapchat.com", "whatsapp.com", "threads.net", "bsky.app",
        "googleapis.com", "gstatic.com", "google-analytics.com", "googletagmanager.com", "google.com",
        "doubleclick.net", "googleusercontent.com", "cloudfront.net", "akamai.net", "akamaihd.net",
        "akamaized.net", "fastly.net", "cloudflare.com", "list-manage.com", "mailchimp.com", "sendgrid.net",
        "mailgun.org", "w3.org", "schema.org", "wikipedia.org", "apple.com", "medium.com",
    )
    private val urlRe = Regex("https?://[^\\s<>\")\\]]+", RegexOption.IGNORE_CASE)

    fun rank(fields: TutaPdfFields, candidates: List<ProofCandidate>): List<ProofMatch> {
        val replyDomain = fields.replyTo?.substringAfterLast('@')?.let { ParserText.registrable(it) }
        val linkCounts = linkDomainCounts(fields)
        val body = fields.bodyText.lowercase()

        return candidates.map { c ->
            val domains = c.domains.mapNotNull { ParserText.registrable(it) }.toSet()
            val reasons = mutableListOf<String>()
            var miss = 1.0

            if (replyDomain != null && replyDomain in domains) {
                miss *= 1 - W_REPLY
                reasons += "Reply-To domain $replyDomain matches"
            }

            val nameSim = c.displayNames.maxOfOrNull { ParserText.nameSimilarity(fields.displayName, it) } ?: 0.0
            val bestName = if (nameSim >= NAME_MIN_SIM) {
                c.displayNames.maxByOrNull { ParserText.nameSimilarity(fields.displayName, it) }
            } else null
            if (bestName != null) {
                miss *= 1 - W_NAME * nameSim
                reasons += if (nameSim >= 1.0) "Sender name matches \"$bestName\""
                else "Sender name is close to \"$bestName\""
            }

            val hits = domains.filter { (linkCounts[it] ?: 0) > 0 }
            if (hits.isNotEmpty()) {
                val n = hits.sumOf { linkCounts.getValue(it) }
                val w = minOf(0.4, 0.2 + 0.05 * (n - 1))
                miss *= 1 - w
                reasons += "Links to ${hits.joinToString(", ")} ($n in the mail)"
            }

            val brands = domains.mapNotNull { ParserText.brandLabel(it) }.distinct()
                .filter { Regex("(?<![a-z0-9])${Regex.escape(it)}(?![a-z0-9])").containsMatchIn(body) }
            if (brands.isNotEmpty()) {
                miss *= 1 - W_BRAND
                reasons += "Mentions ${brands.joinToString(", ")} in the text"
            }
            ProofMatch(c.groupKey, (1 - miss).coerceIn(0.0, 1.0), reasons)
        }.sortedByDescending { it.score }
    }

    private fun linkDomainCounts(f: TutaPdfFields): Map<String, Int> {
        val urls = (f.links + urlRe.findAll(f.bodyText).map { it.value }.toList()).map { it.trimEnd('/', '.') }.toSet()
        val counts = mutableMapOf<String, Int>()
        for (u in urls) {
            val d = ParserText.registrable(u) ?: continue
            if (d in ignoredLinkDomains) continue
            counts[d] = (counts[d] ?: 0) + 1
        }
        return counts
    }
}
