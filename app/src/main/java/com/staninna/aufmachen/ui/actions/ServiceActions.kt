package com.staninna.aufmachen.ui.actions

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.staninna.aufmachen.R
import com.staninna.aufmachen.data.ServiceView
import com.staninna.aufmachen.engine.ClaudePrompt
import com.staninna.aufmachen.engine.PromptFacts
import java.time.LocalDate

/** Service facts for the Ask Claude prompt. Never addresses or subjects. */
fun ServiceView.promptFacts(): PromptFacts = PromptFacts(
    name = displayNames.firstOrNull() ?: domain,
    domains = domains,
    displayNames = displayNames.take(5),
    category = stats.category,
    hasSecurityMails = stats.security > 0,
    hasBilling = stats.billing > 0,
    hasOrders = stats.orders > 0,
    googleLoginSuspected = state.googleLogin,
    dormantYears = if (stats.dormant) stats.daysSince?.let { (it / 365).toInt() } else null,
)

enum class ClaudeGoal { MIGRATE, DELETE }

/** Builds the prompt, copies it, opens the Claude app (or claude.ai) and shows a toast. */
fun askClaude(ctx: Context, service: ServiceView, goal: ClaudeGoal) {
    val facts = service.promptFacts()
    val prompt = if (goal == ClaudeGoal.MIGRATE) ClaudePrompt.migrate(facts) else ClaudePrompt.delete(facts)
    copyText(ctx, "Claude prompt", prompt, toast = false)
    Toast.makeText(ctx, R.string.common_prompt_copied, Toast.LENGTH_LONG).show()
    val app = ctx.packageManager.getLaunchIntentForPackage(CLAUDE_PACKAGE)
    if (app != null) {
        ctx.startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } else {
        openUrl(ctx, "https://claude.ai/new")
    }
}

const val CLAUDE_PACKAGE = "com.anthropic.claude"

fun copyText(ctx: Context, label: String, text: String, toast: Boolean = true) {
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    if (toast) Toast.makeText(ctx, R.string.common_copied, Toast.LENGTH_SHORT).show()
}

/** Reads plain text from the clipboard, or null. Call from a foreground UI action. */
fun readClipboard(ctx: Context): String? {
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(ctx)?.toString()
}

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, R.string.common_no_browser, Toast.LENGTH_SHORT).show()
    }
}

/** Gmail web search for mail from any of [domains]. */
fun gmailSearchUrl(domains: List<String>): String {
    val q = if (domains.size == 1) "from:${domains[0]}" else domains.joinToString(" OR ", "from:(", ")")
    return "https://mail.google.com/mail/u/0/#search/" + Uri.encode(q)
}

/** "today", "3 days ago", "5 months ago", "2 years ago", or "never" for null. */
fun relativeDay(ctx: Context, epochDay: Long?, today: LocalDate = LocalDate.now()): String {
    if (epochDay == null) return ctx.getString(R.string.common_never)
    val days = (today.toEpochDay() - epochDay).toInt().coerceAtLeast(0)
    val r = ctx.resources
    return when {
        days == 0 -> ctx.getString(R.string.common_today)
        days < 60 -> r.getQuantityString(R.plurals.common_days_ago, days, days)
        days < 730 -> (days / 30).let { r.getQuantityString(R.plurals.common_months_ago, it, it) }
        else -> (days / 365).let { r.getQuantityString(R.plurals.common_years_ago, it, it) }
    }
}
