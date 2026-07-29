package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.ui.components.chrome.StackTopBar
import com.krtky.financetracker.ui.theme.Dimens
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.viewmodel.AccountsViewModel

/**
 * Summary of payment accounts:
 * - Cash (mode)
 * - Digital accounts (named banks/wallets + unlabelled Digital)
 */
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    vm: AccountsViewModel = hiltViewModel(),
) {
    // NavHost handles predictive back (no intercepting BackHandler).
    val banks by vm.bankAccounts.collectAsStateWithLifecycle()
    val balances by vm.accountBalances.collectAsStateWithLifecycle()
    val defaultDigital by vm.defaultDigitalAccount.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    val cashBal = balances["Cash"] ?: 0L
    val digitalOrphan = balances["Digital"] ?: 0L
    val digitalNamed = remember(banks, balances) {
        banks.map { name ->
            val bal = balances.entries.firstOrNull { it.key.equals(name, true) }?.value ?: 0L
            name to bal
        }
    }
    val orphanDigitalNames = remember(banks, balances) {
        val known = (banks + listOf("Cash", "Digital")).map { it.lowercase() }.toSet()
        balances.filterKeys { it.lowercase() !in known }.toList()
    }
    val digitalTotal = digitalNamed.sumOf { it.second } + digitalOrphan + orphanDigitalNames.sumOf { it.second }
    val grandTotal = cashBal + digitalTotal

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = Dimens.ScreenHorizontal,
            end = Dimens.ScreenHorizontal,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
    ) {
        item {
            StackTopBar(
                title = "Accounts",
                subtitle = "Where money sits by payment method",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Account settings")
                    }
                },
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = scheme.primaryContainer,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Total across accounts",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                    Text(
                        grandTotal.inr(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Cash", style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text(cashBal.inr(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = scheme.onPrimaryContainer)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Digital", style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text(digitalTotal.inr(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = scheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Payment modes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        item {
            ModeCard(
                title = "Cash",
                subtitle = "Physical cash",
                balance = cashBal,
                icon = Icons.Default.Payments,
                accent = scheme.secondary,
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = scheme.surfaceContainerHigh,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(scheme.tertiary.copy(alpha = 0.95f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.AccountBalance, null, tint = scheme.tertiary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Digital",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Banks & UPI wallets",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            digitalTotal.inr(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = scheme.tertiary,
                        )
                    }

                    if (digitalNamed.isEmpty() && digitalOrphan == 0L && orphanDigitalNames.isEmpty()) {
                        Text(
                            "No digital accounts yet. Add banks in Settings → Accounts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }

                    digitalNamed.forEach { (name, bal) ->
                        DigitalAccountRow(
                            name = name,
                            balance = bal,
                            isDefault = defaultDigital.equals(name, true),
                        )
                    }
                    if (digitalOrphan != 0L) {
                        DigitalAccountRow(
                            name = "Unspecified digital",
                            balance = digitalOrphan,
                            isDefault = false,
                        )
                    }
                    orphanDigitalNames.forEach { (name, bal) ->
                        DigitalAccountRow(
                            name = name,
                            balance = bal,
                            isDefault = false,
                            hint = "Detected from transactions",
                        )
                    }
                }
            }
        }

        item {
            Surface(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = scheme.surfaceContainerHighest,
            ) {
                Text(
                    "Manage accounts & default digital bank",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    balance: Long,
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(accent.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Text(
                balance.inr(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
    }
}

@Composable
private fun DigitalAccountRow(
    name: String,
    balance: Long,
    isDefault: Boolean,
    hint: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerHighest, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.AccountBalance,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (isDefault) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = CircleShape, color = scheme.primaryContainer) {
                        Text(
                            "Default",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
        Text(
            balance.inr(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (balance < 0) scheme.error else scheme.onSurface,
        )
    }
}
