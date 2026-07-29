package com.krtky.financetracker.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.theme.RobotoFlex
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.viewmodel.ClassifyViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassifyTransactionSheet(
    transactionId: String,
    onDismiss: () -> Unit,
    vm: ClassifyViewModel = hiltViewModel(),
) {
    val txn by vm.transaction.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val funds by vm.funds.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val shapes = MaterialTheme.shapes
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    var categoryId by remember { mutableStateOf<Long?>(null) }
    var fundId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var receiptUri by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(transactionId) {
        vm.open(transactionId)
        categoryId = null
        fundId = null
        note = ""
        receiptUri = null
        saving = false
    }

    ModalBottomSheet(
        onDismissRequest = {
            vm.clear()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = scheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Classify payment",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RobotoFlex,
            )
            val t = txn
            if (t != null) {
                val sign = if (t.type == TransactionType.EXPENSE) "−" else "+"
                val party = t.counterparty ?: t.merchant ?: t.paymentMethod ?: "Payment"
                val amountColor =
                    if (t.type == TransactionType.EXPENSE) scheme.error else scheme.primary
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.extraLarge,
                    color = scheme.surfaceContainerLowest,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "$sign${t.amountPaise.inr()}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            fontFamily = RobotoFlex,
                            color = amountColor,
                        )
                        Text(
                            party,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!t.placeName.isNullOrBlank()) {
                            Text(
                                t.placeName!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            if (t.type == TransactionType.EXPENSE) "Expense" else "Income",
                            style = MaterialTheme.typography.labelLarge,
                            color = amountColor,
                        )
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    M3LoadingIndicator(size = 36.dp, strokeWidth = 3.dp)
                }
            }

            Text(
                "Category",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurfaceVariant,
            )
            ChipCarousel {
                FilterChip(
                    selected = categoryId == null,
                    onClick = { categoryId = null },
                    label = { Text("None") },
                    shape = shapes.large,
                )
                categories.forEach { c ->
                    FilterChip(
                        selected = categoryId == c.id,
                        onClick = { categoryId = c.id },
                        label = { Text(c.name) },
                        leadingIcon = {
                            Icon(CategoryIcons.iconFor(c.icon, c.name), null, Modifier.size(18.dp))
                        },
                        shape = shapes.large,
                    )
                }
            }

            Text(
                "Fund",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurfaceVariant,
            )
            ChipCarousel {
                FilterChip(
                    selected = fundId == null,
                    onClick = { fundId = null },
                    label = { Text("None") },
                    shape = shapes.large,
                )
                funds.forEach { f ->
                    FilterChip(
                        selected = fundId == f.fund.id,
                        onClick = { fundId = f.fund.id },
                        label = { Text(f.fund.name) },
                        shape = shapes.large,
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = shapes.large,
            )
            ReceiptAttachmentField(
                localUri = receiptUri,
                onUriChange = { receiptUri = it },
            )
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Later") }
                Button(
                    onClick = {
                        if (saving) return@Button
                        scope.launch {
                            saving = true
                            vm.save(categoryId, fundId, note, receiptLocalUri = receiptUri)
                            NotificationManagerCompat.from(context)
                                .cancel(10_000 + (transactionId.hashCode() and 0xFFFF))
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1.4f).height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                    ),
                ) {
                    Text("Confirm", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
