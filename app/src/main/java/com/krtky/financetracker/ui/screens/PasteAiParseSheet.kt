package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.ui.viewmodel.PasteParseResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasteAiParseSheet(
    llmReady: Boolean,
    onDismiss: () -> Unit,
    onParse: suspend (String) -> Result<PasteParseResult>,
    onApply: (PasteParseResult) -> Unit,
    initialText: String = "",
    autoParse: Boolean = false,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        AddFromTextContent(
            llmReady = llmReady,
            initialText = initialText,
            autoParse = autoParse,
            onParse = onParse,
            onApply = onApply,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        )
    }
}
