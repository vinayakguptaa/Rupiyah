package com.krtky.financetracker.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.ui.components.DeleteConfirmSheet
import com.krtky.financetracker.ui.components.SettingsBlock
import com.krtky.financetracker.ui.components.SettingsButtonStack
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.categoryColor
import com.krtky.financetracker.ui.util.onCategoryColor
import com.krtky.financetracker.ui.viewmodel.SettingsViewModel

@Composable
fun CategoriesSettingsContent(vm: SettingsViewModel) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    var newCategory by remember { mutableStateOf("") }
    var newCategoryIcon by remember { mutableStateOf("category") }
    var newCategoryColor by remember { mutableStateOf(0xFF0B6E4FL) }
    var editCategoryId by remember { mutableStateOf<Long?>(null) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var categoryPendingDelete by remember { mutableStateOf<Long?>(null) }

    SettingsBlock(
        title = "Spending categories",
        helpTitle = "Categories",
        helpMessage = "Tap a row to change name, icon, or color. Tap + to add. Delete only if you no longer use that category.",
    ) {
        if (categories.isEmpty()) {
            Text("No categories yet.", color = scheme.onSurfaceVariant)
        }
        categories.forEachIndexed { index, c ->
            if (index > 0) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        editCategoryId = c.id
                        newCategory = c.name
                        newCategoryIcon = c.icon
                        newCategoryColor = c.color
                        showCategorySheet = true
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val catColor = categoryColor(c.color) ?: Color(c.color)
                Box(
                    Modifier
                        .size(40.dp)
                        .background(catColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        CategoryIcons.iconFor(c.icon, c.name),
                        contentDescription = null,
                        tint = onCategoryColor(catColor),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        buildString {
                            append(c.icon)
                            if (c.isQuickAction) append(" · quick action")
                            if (c.isSystem) append(" · seeded")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { categoryPendingDelete = c.id }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = scheme.error)
                }
            }
        }
        Spacer(Modifier.height(56.dp))
    }

    Box(Modifier.fillMaxWidth()) {
        FloatingActionButton(
            onClick = {
                editCategoryId = null
                newCategory = ""
                newCategoryIcon = "category"
                showCategorySheet = true
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(bottom = 8.dp),
            shape = shapes.large,
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
        ) { Icon(Icons.Default.Add, contentDescription = "Add category") }
    }

    if (showCategorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (editCategoryId == null) "Add category" else "Edit category",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                    newCategory,
                    { newCategory = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.medium,
                )
                val previewColor = categoryColor(newCategoryColor) ?: Color(newCategoryColor)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .background(previewColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            CategoryIcons.iconFor(newCategoryIcon, newCategory),
                            contentDescription = null,
                            tint = onCategoryColor(previewColor),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            newCategory.ifBlank { "Category preview" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "How this looks on transaction lists",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryIcons.all.forEach { entry ->
                        val selected = newCategoryIcon == entry.id
                        Surface(
                            onClick = { newCategoryIcon = entry.id },
                            shape = CircleShape,
                            color = if (selected) previewColor else scheme.surfaceContainerHighest,
                            border = if (selected) BorderStroke(2.dp, scheme.outline) else null,
                        ) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                    entry.icon,
                                    contentDescription = entry.label,
                                    tint = if (selected) onCategoryColor(previewColor) else scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Text("Color", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val categoryColors = listOf(
                        0xFFE74C3CL, 0xFFE67E22L, 0xFFF1C40FL, 0xFF2ECC71L,
                        0xFF1ABC9CL, 0xFF3498DBL, 0xFF9B59B6L, 0xFFE91E63L,
                        0xFF795548L, 0xFF607D8BL, 0xFF34495EL, 0xFF7F8C8DL,
                        0xFF0B6E4FL,
                    )
                    categoryColors.forEach { colorLong ->
                        val color = categoryColor(colorLong) ?: Color(colorLong)
                        val selected = newCategoryColor == colorLong
                        Surface(
                            onClick = { newCategoryColor = colorLong },
                            shape = CircleShape,
                            color = color,
                            border = if (selected) {
                                BorderStroke(3.dp, scheme.onSurface)
                            } else {
                                BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f))
                            },
                        ) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                if (selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = onCategoryColor(color),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                SettingsButtonStack {
                    Button(
                        onClick = {
                            val id = editCategoryId
                            if (id == null) vm.addCategory(newCategory, newCategoryIcon, newCategoryColor, true)
                            else vm.updateCategory(id, newCategory, newCategoryIcon, newCategoryColor, true)
                            newCategory = ""
                            newCategoryIcon = "category"
                            newCategoryColor = 0xFF0B6E4FL
                            editCategoryId = null
                            showCategorySheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.extraLarge,
                        enabled = newCategory.isNotBlank(),
                    ) { Text(if (editCategoryId == null) "Add category" else "Save changes") }
                    OutlinedButton(
                        onClick = { showCategorySheet = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.extraLarge,
                    ) { Text("Cancel") }
                }
            }
        }
    }

    categoryPendingDelete?.let { id ->
        DeleteConfirmSheet(
            title = "Delete category?",
            message = "This category will be removed from the list.",
            onDismiss = { categoryPendingDelete = null },
            onConfirmDelete = {
                vm.deleteCategory(id)
                categoryPendingDelete = null
            },
        )
    }
}
