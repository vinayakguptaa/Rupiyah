package com.krtky.financetracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.theme.RupiyahTheme

/** Shared FAB size so dock FAB and screen FABs match (56.dp). */
val AppFabSize = 56.dp

data class FabSpeedDialItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * Floating bottom navigation dock for the main tabs (Home, Activity, Tabs, Settings).
 * Opaque capsule over content with a sliding selection pill; optional side FAB.
 * FAB menu is an overlay above this row, so the dock never grows.
 */
@Composable
fun FloatingBottomNav(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    showFab: Boolean = false,
    fabIcon: ImageVector = Icons.Default.Add,
    fabContentDescription: String = "Add",
    onFabClick: (() -> Unit)? = null,
    fabMenuExpanded: Boolean = false,
    fabMenuItems: List<FabSpeedDialItem> = emptyList(),
) {
    val items = listOf(
        Triple("home", "Home", Icons.Default.Home),
        Triple("transactions", "Activity", Icons.AutoMirrored.Filled.List),
        Triple("tabs", "Tabs", Icons.Default.AccountBalanceWallet),
        Triple("settings", "Settings", Icons.Default.Settings),
    )
    val scheme = MaterialTheme.colorScheme
    val dockInset = 4.dp
    val dockShape = CircleShape
    val itemPillShape = CircleShape
    val fabSize = 56.dp
    val selectedIndex = items.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val selectedWeightTarget = 1.4f
    val unselectedWeightTarget = 0.85f
    val totalWeight = selectedWeightTarget + unselectedWeightTarget * (items.size - 1)

    Box(
        modifier = modifier
            .fillMaxWidth(0.85f)
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = dockShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.32f),
                    spotColor = Color.Black.copy(alpha = 0.40f),
                ),
            shape = dockShape,
            color = scheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(dockInset),
            ) {
                val targetPillWidth = maxWidth * (selectedWeightTarget / totalWeight)
                val targetOffset = maxWidth * (selectedIndex.toFloat() * unselectedWeightTarget / totalWeight)
                val pillWidth by animateDpAsState(
                    targetValue = targetPillWidth,
                    animationSpec = M3EMotion.spatialDefault(),
                    label = "pillWidth",
                )
                val indicatorOffset by animateDpAsState(
                    targetValue = targetOffset,
                    animationSpec = M3EMotion.spatialDefault(),
                    label = "navIndicatorX",
                )
                Box(
                    Modifier
                        .offset(x = indicatorOffset)
                        .width(pillWidth)
                        .fillMaxSize()
                        .clip(itemPillShape)
                        .background(scheme.primaryContainer),
                )
                Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEach { (route, label, icon) ->
                        val isSelected = selected == route
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                scheme.onPrimaryContainer
                            } else {
                                scheme.onSurfaceVariant
                            },
                            animationSpec = M3EMotion.effectsDefault(),
                            label = "navColor$route",
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.12f else 1f,
                            animationSpec = M3EMotion.spatialFast(),
                            label = "navIconScale$route",
                        )
                        val animatedItemWeight by animateFloatAsState(
                            targetValue = if (isSelected) selectedWeightTarget else unselectedWeightTarget,
                            animationSpec = M3EMotion.spatialDefault(),
                            label = "navWeight$route",
                        )
                        Column(
                            modifier = Modifier
                                .weight(animatedItemWeight)
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onSelect(route) },
                                )
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint = contentColor,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                            )
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = contentColor,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showFab) {
            Spacer(Modifier.size(fabSize))
        }
    }

        if (showFab && onFabClick != null) {
            // Menu is drawn in this overlay Box, not in the dock row, so the
            // capsule + 56.dp circle stay put. Items sit above that line.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = fabSize + 10.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val fromRight = TransformOrigin(1f, 0.5f)
                fabMenuItems.forEachIndexed { index, item ->
                    val fromFab = fabMenuItems.lastIndex - index
                    val enterDelay = fromFab * 40
                    AnimatedVisibility(
                        visible = fabMenuExpanded,
                        enter = fadeIn(
                            animationSpec = tween(160, enterDelay, FastOutSlowInEasing),
                        ) + scaleIn(
                            initialScale = 0.35f,
                            transformOrigin = fromRight,
                            animationSpec = tween(240, enterDelay, FastOutSlowInEasing),
                        ) + slideInHorizontally(
                            animationSpec = tween(240, enterDelay, FastOutSlowInEasing),
                        ) { width -> width / 3 },
                        exit = fadeOut(
                            animationSpec = tween(120, easing = FastOutSlowInEasing),
                        ) + scaleOut(
                            targetScale = 0.35f,
                            transformOrigin = fromRight,
                            animationSpec = tween(160, easing = FastOutSlowInEasing),
                        ) + slideOutHorizontally(
                            animationSpec = tween(160, easing = FastOutSlowInEasing),
                        ) { width -> width / 3 },
                    ) {
                        Surface(
                            onClick = item.onClick,
                            modifier = Modifier.height(fabSize),
                            shape = CircleShape,
                            color = scheme.secondaryContainer,
                            contentColor = scheme.onSecondaryContainer,
                            tonalElevation = 3.dp,
                            shadowElevation = 8.dp,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    item.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp),
                                )
                                Text(
                                    item.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
            Surface(
                onClick = onFabClick,
                modifier = Modifier
                    .size(fabSize)
                    .align(Alignment.BottomEnd)
                    .shadow(
                        elevation = 18.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.30f),
                        spotColor = Color.Black.copy(alpha = 0.38f),
                    ),
                shape = CircleShape,
                color = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
                shadowElevation = 0.dp,
                tonalElevation = 6.dp,
            ) {
                Box(Modifier.size(fabSize), contentAlignment = Alignment.Center) {
                    Icon(
                        if (fabMenuExpanded && fabMenuItems.isNotEmpty()) {
                            Icons.Default.Close
                        } else {
                            fabIcon
                        },
                        contentDescription = fabContentDescription,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "FloatingBottomNav")
@Composable
private fun FloatingBottomNavPreview() {
    RupiyahTheme {
        FloatingBottomNav(
            selected = "home",
            onSelect = {},
            showFab = true,
            onFabClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
