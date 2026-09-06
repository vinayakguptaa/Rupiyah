package com.krtky.financetracker.ui.components.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.krtky.financetracker.R

/** One row in [OverflowMenuButton]. Nested [children] open as a submenu. */
data class OverflowMenuItem(
    val label: String,
    val enabled: Boolean = true,
    val children: List<OverflowMenuItem> = emptyList(),
    val onClick: () -> Unit = {},
)

/**
 * Header ⋮ menu. Use this instead of stacking IconButtons on [ScreenHeader] / [StackTopBar].
 */
@Composable
fun OverflowMenuButton(
    items: List<OverflowMenuItem>,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(R.string.cd_more_options),
) {
    if (items.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    var submenu by remember { mutableStateOf<List<OverflowMenuItem>?>(null) }
    Box(modifier) {
        IconButton(
            onClick = {
                expanded = true
                submenu = null
            },
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = contentDescription)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                submenu = null
            },
        ) {
            val nested = submenu
            if (nested != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.overflow_back)) },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    },
                    onClick = { submenu = null },
                )
                nested.forEach { item ->
                    OverflowRow(
                        item = item,
                        onOpenChildren = { submenu = it },
                        onRan = {
                            expanded = false
                            submenu = null
                        },
                    )
                }
            } else {
                items.forEach { item ->
                    OverflowRow(
                        item = item,
                        onOpenChildren = { submenu = it },
                        onRan = {
                            expanded = false
                            submenu = null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OverflowRow(
    item: OverflowMenuItem,
    onOpenChildren: (List<OverflowMenuItem>) -> Unit,
    onRan: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(item.label) },
        enabled = item.enabled,
        onClick = {
            if (item.children.isNotEmpty()) {
                onOpenChildren(item.children)
            } else {
                onRan()
                item.onClick()
            }
        },
    )
}
