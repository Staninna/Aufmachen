package com.staninna.aufmachen.engine

internal object TutaPdfParse {
    private val emailRe = Regex("[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val titleRe = Regex("^(.*\\S)\\s+-\\s+Tuta\\s*$")
    private val toLineRe = Regex("^to\\s*:\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val toBlockRe = Regex("^to\\s*:?\\s*$", RegexOption.IGNORE_CASE)
    private val replyRe = Regex("^reply[\\s-]*to\\s*:?\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val dateRe = Regex(
        "(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)[a-z]*,?\\s+[A-Za-z]{3,9}\\.?\\s+\\d{1,2},?\\s+\\d{4}(?:\\s*[•·*|,-]?\\s*\\d{1,2}:\\d{2}(?:\\s*[AaPp][Mm])?)?",
    )
    private const val HEADER_WINDOW = 20

    fun parse(title: String?, text: String, links: List<String>): TutaPdfFields? {
        val lines = text.lines().map { it.replace(' ', ' ').trim() }.filter { it.isNotEmpty() }
        val account = title?.let { titleRe.matchEntire(it.trim())?.groupValues?.get(1)?.trim() }
        val head = lines.take(HEADER_WINDOW)
        val consumed = mutableSetOf<Int>()

        var to: String? = null
        var toIdx = -1
        for ((i, l) in head.withIndex()) {
            val m = toLineRe.matchEntire(l) ?: continue
            toIdx = i
            consumed += i
            var rest = m.groupValues[1]
            dateRe.find(rest)?.let { rest = rest.removeRange(it.range) }
            to = emailRe.find(rest)?.value
            break
        }
        if (toIdx < 0 && account == null) return null

        var dateLine: String? = null
        for ((i, l) in head.withIndex()) {
            val d = dateRe.find(l) ?: continue
            dateLine = d.value.trim()
            consumed += i
            break
        }

        var replyTo: String? = null
        for ((i, l) in head.withIndex()) {
            val m = replyRe.matchEntire(l) ?: continue
            consumed += i
            var addr = emailRe.find(m.groupValues[1])?.value
            if (addr == null) {
                for (k in i + 1..minOf(i + 2, head.lastIndex)) {
                    emailRe.find(head[k])?.let { addr = it.value; consumed += k }
                    if (addr != null) break
                }
            }
            replyTo = addr
            break
        }

        for ((i, l) in head.withIndex()) {
            if (!toBlockRe.matches(l)) continue
            consumed += i
            if (i + 1 <= head.lastIndex) {
                val a = emailRe.find(head[i + 1])
                if (a != null && !replyRe.matches(head[i + 1])) {
                    consumed += i + 1
                    if (to == null) to = a.value
                }
            }
            break
        }

        val nameIdx = head.indices.firstOrNull { it !in consumed && !isHeaderish(head[it]) }
        val displayName = nameIdx?.let { head[it] }.orEmpty()
        if (nameIdx != null) consumed += nameIdx

        val lastHeader = consumed.maxOrNull() ?: -1
        val body = lines.drop(lastHeader + 1).joinToString("\n")
        return TutaPdfFields(
            title = title,
            account = account,
            displayName = displayName,
            to = to,
            dateLine = dateLine,
            replyTo = replyTo,
            bodyText = body,
            links = links,
        )
    }

    private fun isHeaderish(l: String) =
        toLineRe.matches(l) || toBlockRe.matches(l) || replyRe.matches(l) || dateRe.containsMatchIn(l)
}
