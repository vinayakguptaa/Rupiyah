package com.krtky.financetracker.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.domain.model.Money
import com.krtky.financetracker.ui.viewmodel.AddCashViewModel
import com.krtky.financetracker.ui.viewmodel.PasteParseResult

@Composable
fun AddEntryOverlays(
    showPaste: Boolean,
    shareText: String?,
    showTransfer: Boolean,
    transferFromId: Long? = null,
    transferToId: Long? = null,
    transferAmount: String = "",
    transferNote: String = "",
    onDismissPaste: () -> Unit,
    onDismissTransfer: () -> Unit,
    onPasteResult: (PasteParseResult) -> Unit,
    vm: AddCashViewModel = hiltViewModel(),
) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val accountBalances by vm.accountBalances.collectAsStateWithLifecycle()

    if (showPaste) {
        PasteAiParseSheet(
            llmReady = vm.isLlmReady(),
            onDismiss = onDismissPaste,
            onParse = { vm.parsePastedText(it) },
            onApply = { result ->
                onDismissPaste()
                onPasteResult(result)
            },
            initialText = shareText.orEmpty(),
            autoParse = !shareText.isNullOrBlank(),
        )
    }

    if (showTransfer) {
        SelfTransferSheet(
            accounts = accounts,
            accountBalances = accountBalances,
            initialAmount = transferAmount,
            initialFromAccountId = transferFromId,
            initialToAccountId = transferToId,
            initialNote = transferNote,
            onDismiss = onDismissTransfer,
            onTransfer = { fromId, toId, amountText, note ->
                vm.saveSelfTransfer(
                    amountText = amountText,
                    fromAccountId = fromId,
                    toAccountId = toId,
                    note = note,
                )
            },
        )
    }
}

fun amountTextFromPaise(paise: Long): String {
    if (paise % 100L == 0L) return (paise / 100L).toString()
    return "%.2f".format(Money(paise).toRupees())
}
