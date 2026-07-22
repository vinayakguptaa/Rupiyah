package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.ActivityTxnCard
import com.krtky.financetracker.ui.components.EmptyState
import com.krtky.financetracker.ui.components.M3LoadingIndicator
import com.krtky.financetracker.ui.components.TimeRangeChips
import com.krtky.financetracker.ui.util.formatDateTime
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.viewmodel.FundDetailViewModel
import kotlinx.coroutines.launch

@Composable
fun FundDetailScreen(
    fundId: Long,
    onBack: () -> Unit,
    onOpenTxn: (String) -> Unit,
    vm: FundDetailViewModel = hiltViewModel(),
) {
    val fund by vm.fund.collectAsStateWithLifecycle()
    val txns by vm.transactions.collectAsStateWithLifecycle()
    val timeRange by vm.timeRange.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(fundId) { vm.load(fundId) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                fund?.fund?.name ?: "Fund",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete fund", tint = scheme.error)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (fund == null) {
            Column(
                Modifier.fillMaxWidth().padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                M3LoadingIndicator()
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = scheme.primaryContainer,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Balance", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer.copy(alpha = 0.75f))
                    Text(
                        fund!!.balancePaise.inr(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "In ${fund!!.creditedPaise.inr()} · Out ${fund!!.debitedPaise.inr()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            TimeRangeChips(selected = timeRange, onSelect = vm::setTimeRange)
            Spacer(Modifier.height(12.dp))
            Text("Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (txns.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.ReceiptLong,
                    title = stringResource(R.string.empty_fund_txns_title),
                    body = stringResource(R.string.empty_fund_txns_body),
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(txns, key = { it.id }) { t ->
                    val party = t.counterparty ?: t.merchant ?: t.note ?: "Transaction"
                    val sign = if (t.type == TransactionType.EXPENSE) "-" else "+"
                    ActivityTxnCard(
                        title = party,
                        subtitle = listOfNotNull(t.occurredAt.formatDateTime(), t.categoryName, t.paymentMethod)
                            .joinToString(" · "),
                        amount = "$sign${t.amountPaise.inr()}",
                        amountColor = if (t.type == TransactionType.EXPENSE) scheme.error else scheme.primary,
                         icon = CategoryIcons.iconFor(null, t.categoryName),
                        onClick = { onOpenTxn(t.id) },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete fund?") },
            text = { Text("This archives the fund. Existing transactions keep their history.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (vm.deleteFund()) onBack()
                        confirmDelete = false
                    }
                }) { Text("Delete", color = scheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
