package com.staninna.aufmachen.engine

internal object ClaudePromptBuilder {
    private const val FORMAT = """SETTINGS_URL: <direct url or NONE>
PRIVACY_CONTACT: <privacy/DPO email or NONE>
STEPS:
1. ...
2. ...
GOTCHAS:
- ..."""

    fun migrate(f: PromptFacts): String = build(
        f,
        "Change my account email from Gmail to my new Tuta address on a custom domain.",
        migrate = true,
    )

    fun delete(f: PromptFacts): String = build(
        f,
        "Delete my account and my data.",
        migrate = false,
    )

    private fun clean(s: String) = s.replace("@", "").replace(Regex("\\s+"), " ").trim()

    private fun build(f: PromptFacts, goal: String, migrate: Boolean): String {
        val sb = StringBuilder()
        sb.appendLine("I need help with an online account. Facts about the service:")
        sb.appendLine("- Service: ${clean(f.name)}")
        if (f.domains.isNotEmpty()) sb.appendLine("- Domains: ${f.domains.joinToString(", ") { clean(it) }}")
        if (f.displayNames.isNotEmpty()) {
            sb.appendLine("- Sender display names: ${f.displayNames.joinToString(", ") { clean(it) }}")
        }
        if (f.category.isNotBlank()) sb.appendLine("- Category: ${clean(f.category)}")
        val signals = buildList {
            if (f.hasSecurityMails) add("sends security or 2FA mails")
            if (f.hasBilling) add("sends billing mails")
            if (f.hasOrders) add("sends order mails")
            if (f.googleLoginSuspected) add("I probably sign in with Google")
            f.dormantYears?.let { add("dormant, no mail for about $it year${if (it == 1) "" else "s"}") }
        }
        if (signals.isNotEmpty()) sb.appendLine("- Signals: ${signals.joinToString("; ")}")
        sb.appendLine()
        sb.appendLine("Goal: $goal")
        sb.appendLine()
        if (migrate && f.googleLoginSuspected) {
            sb.appendLine(
                "I probably log in with Google here. Make the first step switching the login method " +
                    "(set a password or another login) before the email can be changed.",
            )
            sb.appendLine()
        }
        if (!migrate) {
            sb.appendLine("Include how to reach the privacy officer for a GDPR erasure request.")
            sb.appendLine()
        }
        sb.appendLine("Answer in exactly this block and write nothing else after it. Use NONE when unknown.")
        sb.appendLine("Do not guess URLs or addresses you are not sure about.")
        sb.appendLine()
        sb.appendLine("```")
        sb.appendLine(FORMAT)
        sb.append("```")
        return sb.toString()
    }
}
