package com.staninna.aufmachen.engine

internal object ClaudeAnswerParse {
    private val keyRe = Regex(
        "^[\\s>#*_]*(SETTINGS[_ ]URL|PRIVACY[_ ]CONTACT|STEPS|GOTCHAS)\\s*[*_]*\\s*:\\s*[*_]*\\s*(.*)$",
        RegexOption.IGNORE_CASE,
    )
    private val itemRe = Regex("^\\s*(?:\\d+\\s*[.)]|[-*•])\\s+(.*)$")
    private val linkRe = Regex("\\[[^\\]]*]\\(([^)\\s]+)\\)")
    private val urlRe = Regex("https?://[^\\s<>\")\\]]+", RegexOption.IGNORE_CASE)
    private val emailRe = Regex("[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val noneRe = Regex("^(none|n/?a|null|-|unknown|not available)?[.\\s]*$", RegexOption.IGNORE_CASE)

    fun parse(text: String): ClaudeAnswer? {
        var settings: String? = null
        var privacy: String? = null
        var foundSettings = false
        var foundSteps = false
        val steps = mutableListOf<String>()
        val gotchas = mutableListOf<String>()
        var current: String? = null
        var afterBlank = false

        fun addTo(list: MutableList<String>, s: String) {
            val c = cleanText(s)
            if (c.isNotEmpty() && !noneRe.matches(c)) list.add(c)
        }

        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith("```")) { afterBlank = true; continue }
            if (line.isEmpty()) { afterBlank = true; continue }
            val key = keyRe.matchEntire(line)
            if (key != null) {
                current = key.groupValues[1].uppercase().replace(' ', '_')
                val rest = key.groupValues[2].trim()
                afterBlank = false
                when (current) {
                    "SETTINGS_URL" -> { foundSettings = true; settings = extractUrl(rest) }
                    "PRIVACY_CONTACT" -> privacy = extractEmail(rest)
                    "STEPS" -> { foundSteps = true; if (rest.isNotEmpty()) addTo(steps, itemRe.matchEntire(rest)?.groupValues?.get(1) ?: rest) }
                    "GOTCHAS" -> if (rest.isNotEmpty()) addTo(gotchas, itemRe.matchEntire(rest)?.groupValues?.get(1) ?: rest)
                }
                continue
            }
            val list = when (current) { "STEPS" -> steps; "GOTCHAS" -> gotchas; else -> null } ?: continue
            val item = itemRe.matchEntire(line)
            if (item != null) {
                addTo(list, item.groupValues[1])
            } else if (afterBlank && list.isNotEmpty()) {
                current = null // prose after the block
            } else if (list.isEmpty()) {
                addTo(list, line)
            } else {
                val joined = list.last() + " " + cleanText(line)
                list[list.lastIndex] = joined.trim()
            }
            afterBlank = false
        }
        if (!foundSteps && settings == null) return null
        if (steps.isEmpty() && settings == null) return null
        return ClaudeAnswer(settings, privacy, steps, gotchas)
    }

    private fun cleanText(s: String) = s.replace("**", "").replace("__", "").trim()

    private fun extractUrl(v: String): String? {
        val s = cleanText(v)
        val link = linkRe.find(s)?.groupValues?.get(1)
        val url = link ?: urlRe.find(s)?.value
        return url?.trim()?.trimEnd('.', ',', ';', ')', '>', '`')?.takeIf { it.isNotEmpty() }
    }

    private fun extractEmail(v: String): String? {
        val s = cleanText(v).replace("mailto:", "", ignoreCase = true)
        return emailRe.find(s)?.value?.trimEnd('.', ',')
    }
}
