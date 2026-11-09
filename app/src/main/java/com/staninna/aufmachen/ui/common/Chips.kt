package com.staninna.aufmachen.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.staninna.aufmachen.R
import com.staninna.aufmachen.data.ServiceView
import com.staninna.aufmachen.model.Status
import com.staninna.aufmachen.model.Tier
import com.staninna.aufmachen.ui.theme.accent

@Composable
fun tierLabel(t: Tier): String = stringResource(
    when (t) {
        Tier.CRITICAL -> R.string.tier_critical
        Tier.IMPORTANT -> R.string.tier_important
        Tier.ACCOUNT -> R.string.tier_account
        Tier.LOW -> R.string.tier_low
        Tier.TRASH -> R.string.tier_trash
    },
)

@Composable
fun statusLabel(s: Status): String = stringResource(
    when (s) {
        Status.TODO -> R.string.status_todo
        Status.CHANGED -> R.string.status_changed
        Status.VERIFIED -> R.string.status_verified
        Status.DELETED -> R.string.status_deleted
        Status.DELETION_REQUESTED -> R.string.status_deletion_requested
        Status.UNSUBSCRIBED -> R.string.status_unsubscribed
        Status.IGNORED -> R.string.status_ignored
    },
)

/** Tier chip with a colored dot. [pinned] adds a pin marker. */
@Composable
fun TierChip(tier: Tier, pinned: Boolean = false, onClick: () -> Unit = {}) {
    AssistChip(
        onClick = onClick,
        label = { Text(tierLabel(tier)) },
        leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(tier.accent())) },
        trailingIcon = if (pinned) {
            { Icon(Icons.Default.PushPin, null, Modifier.size(16.dp)) }
        } else null,
    )
}

/** Status chip; shows "Failed: still mailing Gmail" when the watchdog flagged the service. */
@Composable
fun StatusChip(service: ServiceView, onClick: () -> Unit = {}) {
    val failed = service.state.failed
    AssistChip(
        onClick = onClick,
        label = { Text(if (failed) stringResource(R.string.status_failed) else statusLabel(service.status)) },
        colors = if (failed) AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            labelColor = MaterialTheme.colorScheme.onErrorContainer,
        ) else AssistChipDefaults.assistChipColors(),
    )
}

/** Thin vertical tier stripe for list rows. Put it first in a Row with IntrinsicSize.Min height. */
@Composable
fun TierStripe(tier: Tier) {
    Box(Modifier.width(4.dp).fillMaxHeight().background(tier.accent()))
}
