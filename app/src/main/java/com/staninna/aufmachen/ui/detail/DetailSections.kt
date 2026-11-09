package com.staninna.aufmachen.ui.detail

import androidx.compose.foundation.Canvas
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
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun HeaderSection(r: DetailState.Ready, onTier: () -> Unit, onUnmerge: (String) -> Unit) {
    val s = r.service
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(s.name, style = MaterialTheme.typography.headlineSmall)
        if (s.displayNames.isNotEmpty()) {
            Text(s.displayNames.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TierChip(s.tier, s.tierPinned, onTier)
            Text(stringResource(R.string.detail_score, s.stats.score), style = MaterialTheme.typography.bodyMedium)
        }
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
        val rows = whyRows(s.components)
        Text(stringResource(R.string.detail_why), style = MaterialTheme.typography.labelLarge)
        if (rows.isEmpty()) {
            Text(stringResource(R.string.detail_why_empty), style = MaterialTheme.typography.bodySmall)
        } else rows.forEach {
            Row(Modifier.fillMaxWidth()) {
                Text(it.key, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(signedPoints(it.points), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun StatsSection(s: ServiceView) {
    val ctx = LocalContext.current
    val st = s.stats
    val method = when (unsubMethod(s.unsubLinks, st.unsubOneClick)) {
        UnsubMethod.ONE_CLICK -> R.string.detail_unsub_oneclick
        UnsubMethod.EMAIL -> R.string.detail_unsub_email
        UnsubMethod.WEB -> R.string.detail_unsub_web
        UnsubMethod.NONE -> R.string.detail_unsub_none
    }
    val cells = listOf(
        R.string.detail_stat_mails to "${st.mails}",
        R.string.detail_stat_last12 to "${st.last12}",
        R.string.detail_stat_per_year to String.format(Locale.getDefault(), "%.1f", st.perYearRate),
        R.string.detail_stat_first to fmtDay(st.firstEpochDay),
        R.string.detail_stat_last to fmtDay(st.lastEpochDay),
        R.string.detail_stat_days_since to (st.daysSince?.toString() ?: "-"),
        R.string.detail_stat_active_years to "${st.activeYears}",
        R.string.detail_stat_read to pct(st.readPct),
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
        R.string.detail_stat_unsub to ctx.getString(method),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.detail_stats))
        cells.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (row.size == 1) Column(Modifier.weight(1f)) {}
            }
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

@Composable
fun AddressSection(s: ServiceView, onCopy: (String) -> Unit, onChange: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(stringResource(R.string.detail_new_address))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val a = s.newAddress
            if (a == null) {
                Text(stringResource(R.string.detail_no_address), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            } else {
                TextButton(onClick = { onCopy(a) }, modifier = Modifier.weight(1f)) {
                    Text(a, style = MaterialTheme.typography.bodyLarge)
                }
            }
            TextButton(onClick = onChange) { Text(stringResource(R.string.detail_change)) }
        }
        if (s.newAddress != null) {
            Text(
                stringResource(if (s.state.assignedAddress != null) R.string.detail_address_custom else R.string.detail_address_bucket),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun LoginSection(s: ServiceView, onGoogle: (Boolean) -> Unit, onSwitched: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(stringResource(R.string.detail_login))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.detail_google_login), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(s.state.googleLogin, onGoogle)
        }
        if (s.state.googleLogin) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.detail_login_banner), style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(s.state.loginSwitched, onSwitched)
                        Text(stringResource(R.string.detail_login_switched))
                    }
                }
            }
        }
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

@Composable
fun TimelineSection(timeline: List<TimelineEntity>, proofs: List<ProofEntity>, onOpenProof: (ProofEntity) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle(stringResource(R.string.detail_timeline))
        if (timeline.isEmpty()) Text(stringResource(R.string.detail_timeline_empty), style = MaterialTheme.typography.bodySmall)
        timeline.sortedByDescending { it.at }.forEach { e ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(kindIcon(e.kind), null, Modifier.size(18.dp).padding(top = 2.dp))
                Column {
                    Text(e.text, style = MaterialTheme.typography.bodyMedium)
                    Text(fmtMillis(e.at), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (proofs.isNotEmpty()) {
            Text(stringResource(R.string.detail_proofs), style = MaterialTheme.typography.labelLarge)
            proofs.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.receivedDate ?: fmtMillis(p.createdAt), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.detail_proof_to, p.recipient), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onOpenProof(p) }) { Text(stringResource(R.string.detail_open_pdf)) }
                }
            }
        }
    }
}

@Composable
fun StepsSection(s: ServiceView, onTick: (Int, Boolean) -> Unit, onOpenSettings: (String) -> Unit) {
    val st = s.state
    val steps = s.steps
    val gotchas = s.gotchas
    val hasAny = steps.isNotEmpty() || gotchas.isNotEmpty() || st.settingsUrl != null ||
        st.privacyContact != null || !st.claudeNotes.isNullOrBlank()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(stringResource(R.string.detail_steps))
        if (!hasAny) {
            Text(stringResource(R.string.detail_steps_empty), style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        if (steps.isNotEmpty()) {
            val done = s.stepsDone
            Text(
                pluralStringResource(R.plurals.detail_steps_progress, steps.size, done.count { it < steps.size }, steps.size),
                style = MaterialTheme.typography.labelSmall,
            )
            steps.forEachIndexed { i, text ->
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(i in done, { onTick(i, it) })
                    Text(text, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        st.settingsUrl?.let { url ->
            Button(onClick = { onOpenSettings(url) }) { Text(stringResource(R.string.detail_open_settings)) }
        }
        if (gotchas.isNotEmpty()) {
            Text(stringResource(R.string.detail_gotchas), style = MaterialTheme.typography.labelLarge)
            gotchas.forEach { Text(stringResource(R.string.detail_bullet, it), style = MaterialTheme.typography.bodyMedium) }
        }
        st.privacyContact?.let {
            Text(stringResource(R.string.detail_privacy_contact, it), style = MaterialTheme.typography.bodyMedium)
        }
        if (!st.claudeNotes.isNullOrBlank()) {
            Text(stringResource(R.string.detail_notes), style = MaterialTheme.typography.labelLarge)
            Text(st.claudeNotes, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** One entry shared by the button list and the overflow menu. */
class DetailAction(val label: Int, val onClick: () -> Unit)

@Composable
fun ActionsSection(actions: List<DetailAction>, hintAddProof: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(stringResource(R.string.detail_actions))
        actions.forEach {
            OutlinedButton(onClick = it.onClick, modifier = Modifier.fillMaxWidth()) { Text(stringResource(it.label)) }
        }
        if (hintAddProof) Text(stringResource(R.string.detail_add_proof_hint), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

