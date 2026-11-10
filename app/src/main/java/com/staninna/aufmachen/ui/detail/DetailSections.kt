package com.staninna.aufmachen.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import com.staninna.aufmachen.ui.common.StatusChip
import com.staninna.aufmachen.ui.common.statusLabel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.staninna.aufmachen.R
import com.staninna.aufmachen.data.ProofEntity
import com.staninna.aufmachen.data.ServiceView
import com.staninna.aufmachen.data.TimelineEntity
import com.staninna.aufmachen.data.TimelineKind
import com.staninna.aufmachen.engine.UnsubMethod
import com.staninna.aufmachen.engine.unsubMethod
import com.staninna.aufmachen.ui.common.TierChip
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val dateTimeFmt: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private fun fmtDay(day: Long?): String = day?.let { LocalDate.ofEpochDay(it).format(dateFmt) } ?: "-"
private fun fmtMillis(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dateTimeFmt)
private fun pct(v: Double): String = "${Math.round(v * 100)}%"

@Composable
fun HeaderSection(s: ServiceView, onTier: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(s.name, style = MaterialTheme.typography.titleLarge)
        val sub = listOf(s.displayNames.firstOrNull().orEmpty(), s.stats.category).filter { it.isNotBlank() }
        if (sub.isNotEmpty()) {
            Text(
                sub.joinToString(" \u00B7 "), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TierChip(s.tier, s.tierPinned, onTier)
            StatusChip(s)
            Text(
                stringResource(R.string.detail_score, s.stats.score), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The one thing to do next, picked by [nextStep]. */
@Composable
fun NextStepCard(
    s: ServiceView,
    step: NextStep,
    alert: String?,
    onAsk: () -> Unit,
    onPaste: () -> Unit,
    onMarkChanged: () -> Unit,
    onAddProof: () -> Unit,
    onTick: (Int, Boolean) -> Unit,
    onOpenSettings: (String) -> Unit,
) {
    val failed = step == NextStep.FAILED
    Card(
        Modifier.fillMaxWidth(),
        colors = if (failed) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (step) {
                NextStep.FAILED -> {
                    Text(alert ?: stringResource(R.string.status_failed), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onAsk, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.detail_next_ask_again))
                    }
                }
                NextStep.ASK -> {
                    Button(onClick = onAsk, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.detail_next_ask)) }
                    OutlinedButton(onClick = onPaste, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.detail_act_paste))
                    }
                }
                NextStep.STEPS -> {
                    StepsChecklist(s, onTick)
                    s.state.settingsUrl?.let { url ->
                        OutlinedButton(onClick = { onOpenSettings(url) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.detail_open_settings))
                        }
                    }
                    Button(onClick = onMarkChanged, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.detail_act_changed))
                    }
                }
                NextStep.ADD_PROOF -> {
                    Button(onClick = onAddProof, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.detail_act_add_proof))
                    }
                    OutlinedButton(onClick = onAsk, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.detail_next_ask))
                    }
                }
                NextStep.DONE -> {
                    val label = statusLabel(s.status)
                    val at = s.state.statusChangedAt
                    Text(
                        if (at == null) label else stringResource(
                            R.string.detail_done_line, label,
                            Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepsChecklist(s: ServiceView, onTick: (Int, Boolean) -> Unit) {
    val steps = s.steps
    val done = s.stepsDone
    Column {
        Text(
            pluralStringResource(R.plurals.detail_steps_progress, steps.size, done.count { it < steps.size }, steps.size),
            style = MaterialTheme.typography.labelMedium,
        )
        steps.forEachIndexed { i, text ->
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(i in done, { onTick(i, it) })
                Text(text, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun AddressRow(s: ServiceView, onCopy: (String) -> Unit, onChange: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.detail_new_address), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val a = s.newAddress
            Text(
                a ?: stringResource(R.string.detail_no_address), style = MaterialTheme.typography.bodyMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        s.newAddress?.let { a ->
            IconButton(onClick = { onCopy(a) }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.detail_copy)) }
        }
        TextButton(onClick = onChange) { Text(stringResource(R.string.detail_change)) }
    }
}

/** A header row with a chevron. Shows [summary] while collapsed. */
@Composable
fun CollapsibleSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (!expanded && summary.isNotEmpty()) {
                    Text(
                        summary, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                stringResource(if (expanded) R.string.detail_collapse else R.string.detail_expand),
            )
        }
        if (expanded) Column(Modifier.padding(bottom = 4.dp)) { content() }
    }
}

@Composable
fun WhyContent(s: ServiceView) {
    val rows = whyRows(s.components)
    if (rows.isEmpty()) {
        Text(stringResource(R.string.detail_why_empty), style = MaterialTheme.typography.bodyMedium)
    } else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach {
            Row(Modifier.fillMaxWidth()) {
                Text(it.key, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(signedPoints(it.points), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun statsSummary(s: ServiceView): String {
    val st = s.stats
    val mails = pluralStringResource(R.plurals.detail_mails_count, st.mails, st.mails)
    val d = st.daysSince
    return if (d == null) mails else stringResource(
        R.string.detail_stats_summary, mails, pluralStringResource(R.plurals.detail_days_ago, d.toInt(), d.toInt()),
    )
}

/** How many stats show before "Show all". */
private const val STATS_SHOWN = 8

@Composable
fun StatsContent(s: ServiceView) {
    val ctx = LocalContext.current
    val st = s.stats
    val method = when (unsubMethod(s.unsubLinks, st.unsubOneClick)) {
        UnsubMethod.ONE_CLICK -> R.string.detail_unsub_oneclick
        UnsubMethod.EMAIL -> R.string.detail_unsub_email
        UnsubMethod.WEB -> R.string.detail_unsub_web
        UnsubMethod.NONE -> R.string.detail_unsub_none
    }
    // The first STATS_SHOWN are the useful ones; the rest sit behind "Show all".
    val cells = listOf(
        R.string.detail_stat_mails to "${st.mails}",
        R.string.detail_stat_last12 to "${st.last12}",
        R.string.detail_stat_first to fmtDay(st.firstEpochDay),
        R.string.detail_stat_last to fmtDay(st.lastEpochDay),
        R.string.detail_stat_days_since to (st.daysSince?.toString() ?: "-"),
        R.string.detail_stat_per_year to String.format(Locale.getDefault(), "%.1f", st.perYearRate),
        R.string.detail_stat_read to pct(st.readPct),
        R.string.detail_stat_unsub to ctx.getString(method),
        R.string.detail_stat_active_years to "${st.activeYears}",
        R.string.detail_stat_important to pct(st.importantPct),
        R.string.detail_stat_starred to "${st.starred}",
        R.string.detail_stat_you_sent to "${st.youSent}",
        R.string.detail_stat_security to "${st.security}",
        R.string.detail_stat_welcome to "${st.welcome}",
        R.string.detail_stat_orders to "${st.orders}",
        R.string.detail_stat_billing to "${st.billing}",
        R.string.detail_stat_marketing to "${st.marketing}",
        R.string.detail_stat_bill_months to "${st.billMonths}",
        R.string.detail_stat_regularity to (st.regularity?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: "-"),
        R.string.detail_stat_newsletter to pct(st.newsletterPct),
        R.string.detail_stat_promo to pct(st.promoPct),
        R.string.detail_stat_updates to pct(st.updatesPct),
        R.string.detail_stat_social to pct(st.socialPct),
        R.string.detail_stat_attachments to "${st.attachments}",
        R.string.detail_stat_senders to "${s.senderAddrs.size}",
    )
    var all by rememberSaveable { mutableStateOf(false) }
    val shown = if (all) cells else cells.take(STATS_SHOWN)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(label), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (row.size == 1) Column(Modifier.weight(1f)) {}
            }
        }
        TextButton(onClick = { all = !all }) {
            Text(stringResource(if (all) R.string.detail_show_fewer else R.string.detail_show_all))
        }
        Sparkline(s.perYear)
    }
}

@Composable
private fun Sparkline(perYear: Map<Int, Int>) {
    val bars = sparkBars(perYear)
    if (bars.isEmpty()) return
    val color = MaterialTheme.colorScheme.primary
    Text(stringResource(R.string.detail_per_year_chart), style = MaterialTheme.typography.labelSmall)
    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (bars.size - 1)) / bars.size
        bars.forEachIndexed { i, (_, f) ->
            val h = (size.height * f).coerceAtLeast(if (f > 0f) 2.dp.toPx() else 1.dp.toPx())
            drawRoundRect(
                color, Offset(i * (w + gap), size.height - h), Size(w.coerceAtLeast(1f), h), CornerRadius(2.dp.toPx()),
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${bars.first().first}", style = MaterialTheme.typography.labelSmall)
        Text("${bars.last().first}", style = MaterialTheme.typography.labelSmall)
    }
}

private fun kindIcon(kind: String): ImageVector = when (kind) {
    TimelineKind.PROOF -> Icons.Default.Description
    TimelineKind.WATCHDOG -> Icons.Default.Warning
    TimelineKind.NOTE -> Icons.Default.StickyNote2
    TimelineKind.SENT -> Icons.AutoMirrored.Filled.Send
    TimelineKind.REPLY -> Icons.AutoMirrored.Filled.Reply
    else -> Icons.Default.Flag
}

/** Whether the Claude's notes section has anything to show. [stepsInCard]: the card already lists the steps. */
fun hasNotes(s: ServiceView, stepsInCard: Boolean): Boolean {
    val st = s.state
    return (!stepsInCard && (s.steps.isNotEmpty() || st.settingsUrl != null)) || s.gotchas.isNotEmpty() ||
        st.privacyContact != null || !st.claudeNotes.isNullOrBlank()
}

@Composable
fun notesSummary(s: ServiceView, stepsInCard: Boolean): String {
    val parts = buildList {
        if (!stepsInCard && s.steps.isNotEmpty()) add(stringResource(R.string.detail_paste_steps, s.steps.size))
        if (s.gotchas.isNotEmpty()) add(stringResource(R.string.detail_paste_gotchas, s.gotchas.size))
        if (s.state.privacyContact != null) add(stringResource(R.string.detail_privacy_short))
        if (isEmpty()) add(stringResource(R.string.detail_notes_saved))
    }
    return parts.joinToString(", ")
}

@Composable
fun NotesContent(s: ServiceView, stepsInCard: Boolean, onTick: (Int, Boolean) -> Unit, onOpenSettings: (String) -> Unit) {
    val st = s.state
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!stepsInCard) {
            if (s.steps.isNotEmpty()) StepsChecklist(s, onTick)
            st.settingsUrl?.let { url ->
                OutlinedButton(onClick = { onOpenSettings(url) }) { Text(stringResource(R.string.detail_open_settings)) }
            }
        }
        if (s.gotchas.isNotEmpty()) {
            Text(stringResource(R.string.detail_gotchas), style = MaterialTheme.typography.labelLarge)
            s.gotchas.forEach { Text(stringResource(R.string.detail_bullet, it), style = MaterialTheme.typography.bodyMedium) }
        }
        st.privacyContact?.let {
            Text(stringResource(R.string.detail_privacy_contact, it), style = MaterialTheme.typography.bodyMedium)
        }
        if (!st.claudeNotes.isNullOrBlank()) {
            Text(st.claudeNotes, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun HistoryContent(timeline: List<TimelineEntity>, proofs: List<ProofEntity>, onOpenProof: (ProofEntity) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (timeline.isEmpty()) Text(stringResource(R.string.detail_timeline_empty), style = MaterialTheme.typography.bodyMedium)
        timeline.sortedByDescending { it.at }.forEach { e ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(kindIcon(e.kind), null, Modifier.size(18.dp).padding(top = 2.dp))
                Column {
                    Text(e.text, style = MaterialTheme.typography.bodyMedium)
                    Text(fmtMillis(e.at), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (proofs.isNotEmpty()) {
            Text(stringResource(R.string.detail_proofs), style = MaterialTheme.typography.labelLarge)
            proofs.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.receivedDate ?: fmtMillis(p.createdAt), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.detail_proof_to, p.recipient), style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { onOpenProof(p) }) { Text(stringResource(R.string.detail_open_pdf)) }
                }
            }
        }
    }
}

@Composable
fun aboutSummary(s: ServiceView): String {
    val parts = buildList {
        add(pluralStringResource(R.plurals.detail_domains_count, s.domains.size, s.domains.size))
        if (s.state.googleLogin) add(stringResource(R.string.detail_google_login))
    }
    return parts.joinToString(", ")
}

@Composable
fun AboutContent(
    r: DetailState.Ready,
    onUnmerge: (String) -> Unit,
    onGoogle: (Boolean) -> Unit,
    onSwitched: (Boolean) -> Unit,
) {
    val s = r.service
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.detail_domains), style = MaterialTheme.typography.labelLarge)
        s.domains.forEach { d ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (d in r.mergedDomains) stringResource(R.string.detail_domain_merged, d) else d,
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                )
                if (d != s.domain) TextButton(onClick = { onUnmerge(d) }) { Text(stringResource(R.string.detail_unmerge)) }
            }
        }
        if (s.displayNames.isNotEmpty()) {
            Text(stringResource(R.string.detail_display_names), style = MaterialTheme.typography.labelLarge)
            Text(s.displayNames.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            pluralStringResource(R.plurals.detail_senders_count, s.senderAddrs.size, s.senderAddrs.size),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.detail_google_login), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(s.state.googleLogin, onGoogle)
        }
        if (s.state.googleLogin) {
            Text(stringResource(R.string.detail_login_banner), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(s.state.loginSwitched, onSwitched)
                Text(stringResource(R.string.detail_login_switched))
            }
        }
    }
}

/** One entry in the overflow menu. */
class DetailAction(val label: Int, val onClick: () -> Unit)
