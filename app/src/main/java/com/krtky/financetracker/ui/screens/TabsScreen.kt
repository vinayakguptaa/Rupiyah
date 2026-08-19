package com.krtky.financetracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.TabBalance
import com.krtky.financetracker.ui.components.EmptyState
import com.krtky.financetracker.ui.components.chrome.ScreenHeader
import com.krtky.financetracker.ui.theme.Dimens
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.theme.NavContentInsets
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.inrCompact
import com.krtky.financetracker.ui.viewmodel.TabsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsScreen(
    onOpenTab: (Long) -> Unit = {},
    /** Incremented by the floating nav FAB to open create sheet. */
    createRequestTick: Int = 0,
    vm: TabsViewModel = hiltViewModel(),
) {
    val tabs by vm.tabs.collectAsStateWithLifecycle()
    val archivedTabs by vm.archivedTabs.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var adjustTabId by remember { mutableStateOf<Long?>(null) }
    var adjustTabName by remember { mutableStateOf("") }
    var editName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(40)
        ready = true
    }
    LaunchedEffect(createRequestTick) {
        if (createRequestTick > 0) {
            showCreate = true
        }
    }
    val openTabs = remember(tabs) { tabs.filter { !it.isSettled() } }
    val settledTabs = remember(tabs) { tabs.filter { it.isSettled() } }
    val netOpen = tabs.sumOf { it.balancePaise }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = Dimens.ScreenHorizontal),
            verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
            contentPadding = PaddingValues(bottom = NavContentInsets.bottom),
        ) {
            item {
                ScreenHeader(
                    title = "Tabs",
                    subtitle = "Loans, shared trips, or IOUs — who owes whom",
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = scheme.surfaceContainerHigh,
                ) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(
                            stringResource(R.string.tabs_net_owed),
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (netOpen == 0L) "₹0" else netOpen.let { if (it < 0) -it else it }.inrCompact(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (netOpen < 0L) scheme.error else scheme.onSurface,
                        )
                        if (netOpen != 0L) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (netOpen < 0L) "You owe them" else "They owe you",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (openTabs.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.AccountBalanceWallet,
                        title = stringResource(R.string.empty_funds_title),
                        body = when {
                            settledTabs.isNotEmpty() || archivedTabs.isNotEmpty() ->
                                "No open balances — settled and archived tabs are below."
                            else -> stringResource(R.string.empty_funds_body)
                        },
                        actionLabel = stringResource(R.string.empty_funds_action),
                        onAction = { showCreate = true },
                    )
                }
            }

            itemsIndexed(openTabs, key = { _, f -> f.tab.id }) { index, f ->
                AnimatedVisibility(
                    visible = ready,
                    enter = fadeIn(M3EMotion.effectsDefault()) +
                        slideInVertically(M3EMotion.spatialDefault()) { it / 8 },
                ) {
                    val colors = listOf(
                        scheme.primary to scheme.onPrimary,
                        scheme.tertiary to scheme.onTertiary,
                        scheme.secondary to scheme.onSecondary,
                        scheme.primaryContainer to scheme.onPrimaryContainer,
                        scheme.tertiaryContainer to scheme.onTertiaryContainer,
                    )[index % 5]
                    SimpleTabCard(
                        tab = f,
                        headerColor = colors.first,
                        onHeaderColor = colors.second,
                        onOpen = { onOpenTab(f.tab.id) },
                        onAdjust = {
                            adjustTabId = f.tab.id
                            adjustTabName = f.tab.name
                            editName = f.tab.name
                            showAdjust = true
                        },
                    )
                }
            }

            if (settledTabs.isNotEmpty()) {
                item {
                    SectionLabel(
                        title = stringResource(R.string.tabs_settled_title),
                        subtitle = "Zero balance · history kept",
                    )
                }
                items(settledTabs, key = { "settled-${it.tab.id}" }) { row ->
                    QuietTabRow(
                        tab = row,
                        caption = "Settled",
                        onOpen = { onOpenTab(row.tab.id) },
                        trailing = {
                            TextButton(
                                onClick = {
                                    adjustTabId = row.tab.id
                                    adjustTabName = row.tab.name
                                    editName = row.tab.name
                                    showAdjust = true
                                },
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = "Manage",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                    )
                }
            }

            if (archivedTabs.isNotEmpty()) {
                item {
                    SectionLabel(
                        title = stringResource(R.string.tabs_archived_title),
                        subtitle = stringResource(R.string.tabs_archived_subtitle),
                    )
                }
                items(archivedTabs, key = { "arch-${it.tab.id}" }) { row ->
                    QuietTabRow(
                        tab = row,
                        caption = if (row.isSettled()) {
                            "Archived · settled"
                        } else if (row.youOweThem()) {
                            "Archived · you owe ${(-row.balancePaise).inr()}"
                        } else {
                            "Archived · they owe ${row.balancePaise.inr()}"
                        },
                        onOpen = { onOpenTab(row.tab.id) },
                        trailing = {
                            TextButton(
                                onClick = { scope.launch { vm.restore(row.tab.id) } },
                            ) {
                                Icon(
                                    Icons.Default.Unarchive,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.tabs_restore))
                            }
                        },
                    )
                }
            }
        }
    }

    if (showCreate) {
        ModalBottomSheet(
            onDismissRequest = { showCreate = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("New tab", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                )
                Button(
                    onClick = {
                        scope.launch {
                            vm.create(newName)
                            newName = ""
                            showCreate = false
                        }
                    },
                    enabled = newName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Create") }
                OutlinedButton(
                    onClick = { showCreate = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Cancel") }
            }
        }
    }

    if (showAdjust && adjustTabId != null) {
        ModalBottomSheet(
            onDismissRequest = { showAdjust = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Manage $adjustTabName", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                )
                Button(
                    onClick = {
                        scope.launch {
                            vm.rename(adjustTabId!!, editName)
                            adjustTabName = editName.trim()
                            showAdjust = false
                        }
                    },
                    enabled = editName.isNotBlank() && editName.trim() != adjustTabName,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text(stringResource(R.string.tabs_save_name)) }
                Text(
                    "Archiving keeps the tab's transaction history but hides it from this list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        scope.launch {
                            vm.delete(adjustTabId!!)
                            showAdjust = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Archive tab", color = scheme.error) }
                OutlinedButton(
                    onClick = { showAdjust = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String, subtitle: String) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuietTabRow(
    tab: TabBalance,
    caption: String,
    onOpen: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Text(
                    tab.tab.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            trailing()
        }
    }
}

@Composable
private fun SimpleTabCard(
    tab: TabBalance,
    headerColor: Color,
    onHeaderColor: Color,
    onOpen: () -> Unit,
    onAdjust: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val youOweThem = tab.youOweThem()
    val cardColor = if (youOweThem) scheme.error else headerColor
    val onCard = if (youOweThem) scheme.onError else onHeaderColor

    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = cardColor,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    tab.tab.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = onCard,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (youOweThem) {
                        "You owe ${(-tab.balancePaise).inr()}"
                    } else {
                        "They owe ${tab.balancePaise.inr()}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = onCard.copy(alpha = 0.9f),
                )
            }
            Surface(
                onClick = onAdjust,
                shape = CircleShape,
                color = onCard.copy(alpha = 0.18f),
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Tune, null, tint = onCard, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
