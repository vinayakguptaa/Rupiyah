package com.krtky.financetracker.ui.screens.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.ui.components.SettingsBlock
import com.krtky.financetracker.ui.components.SettingsSegment
import com.krtky.financetracker.ui.components.SettingsSegmentedRow
import com.krtky.financetracker.ui.components.SettingsStatusText
import com.krtky.financetracker.ui.components.SettingsToggleRow
import com.krtky.financetracker.ui.viewmodel.SmsInboxViewModel

private val WINDOWS = listOf(24 to "24 h", 72 to "3 days", 168 to "7 days")

/**
 * "Sync SMS" + the stored inbox with each message's pipeline status and manual overrides.
 * [onOpenTransaction] opens a transaction; [onReviewText] opens the paste review form with text.
 */
@Composable
fun SmsInboxContent(
    onOpenTransaction: (String) -> Unit,
    onReviewText: (String) -> Unit,
    vm: SmsInboxViewModel = hiltViewModel(),
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val showIgnored by vm.showIgnored.collectAsStateWithLifecycle()
    val shapes = MaterialTheme.shapes
    var hours by rememberSaveable { mutableIntStateOf(24) }
    var permissionDenied by remember { mutableStateOf(false) }
    val readSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted) vm.sync(hours)
    }

    SettingsBlock(
        title = "Sync recent SMS",
        helpTitle = "How SMS import works",
        helpMessage = "Each bank SMS is saved first, then: filtered (bank sender, money wording), read by the " +
            "built-in parser, categorised from your own history, and only then sent to AI if something is " +
            "missing or unsure. The transaction is created as soon as the parser can read it, so an AI " +
            "failure never loses it. Sync also catches messages that arrived while the app could not read them.",
    ) {
        SettingsSegmentedRow {
            WINDOWS.forEach { (h, label) ->
                SettingsSegment(label = label, selected = hours == h, onClick = { hours = h })
            }
        }
        Button(
            onClick = {
                if (vm.hasReadPermission()) vm.sync(hours) else readSms.launch(Manifest.permission.READ_SMS)
            },
            enabled = !syncing,
            modifier = Modifier.fillMaxWidth(),
            shape = shapes.large,
        ) {
            if (syncing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Syncing…")
            } else {
                Text("Sync last ${WINDOWS.first { it.first == hours }.second}")
            }
        }
        if (permissionDenied) {
            SettingsStatusText(
                text = "SMS access was denied. On Pixel: App info → ⋮ → Allow restricted settings, then allow SMS.",
                positive = false,
            )
        }
        SettingsStatusText(
            text = listOfNotNull(
                "${summary.imported} imported",
                summary.duplicates.takeIf { it > 0 }?.let { "$it already in app" },
                summary.needsAi.takeIf { it > 0 }?.let { "$it need AI" },
                summary.failed.takeIf { it > 0 }?.let { "$it failed" },
                "${summary.ignored} ignored",
            ).joinToString(" · "),
            positive = summary.failed == 0 && summary.needsAi == 0,
        )
        if (summary.pendingAi > 0) {
            OutlinedButton(
                onClick = { vm.retryAi() },
                enabled = !syncing && vm.isAiConfigured(),
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
            ) {
                Text(if (vm.isAiConfigured()) "Run AI on ${summary.pendingAi} waiting" else "Set up AI to finish ${summary.pendingAi} waiting")
            }
        }
    }

    SettingsBlock(title = "Recent messages") {
        SettingsToggleRow(
            title = "Show ignored messages",
            subtitle = "OTPs, offers, reminders and other non-transactions",
            checked = showIgnored,
            onCheckedChange = vm::setShowIgnored,
        )
        if (rows.isEmpty()) {
            Text(
                "Nothing yet. Tap Sync to read recent messages.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        rows.forEach { row ->
            SmsRowCard(row) { action -> vm.act(row, action, onOpenTransaction, onReviewText) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmsRowCard(row: SmsInboxViewModel.Row, onAction: (SmsInboxViewModel.RowAction) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var expanded by rememberSaveable(row.id) { mutableStateOf(false) }
    val toneColor: Color = when (row.tone) {
        SmsInboxViewModel.Tone.GOOD -> scheme.primary
        SmsInboxViewModel.Tone.WARN -> scheme.tertiary
        SmsInboxViewModel.Tone.BAD -> scheme.error
        SmsInboxViewModel.Tone.NEUTRAL -> scheme.onSurfaceVariant
    }
    Surface(
        onClick = { expanded = !expanded },
        color = scheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.sender,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(row.statusLabel, style = MaterialTheme.typography.labelMedium, color = toneColor)
            }
            Text(row.time, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            row.headline?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            row.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = toneColor)
            }
            Text(
                row.body,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                row.busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Working…", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
                expanded && row.actions.isNotEmpty() -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    row.actions.forEach { action ->
                        val destructive = action == SmsInboxViewModel.RowAction.NOT_A_TRANSACTION
                        TextButton(onClick = { onAction(action) }) {
                            Text(action.label, color = if (destructive) scheme.error else scheme.primary)
                        }
                    }
                }
                !expanded && row.actions.isNotEmpty() -> Text(
                    "Tap for actions",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                )
            }
        }
    }
}
