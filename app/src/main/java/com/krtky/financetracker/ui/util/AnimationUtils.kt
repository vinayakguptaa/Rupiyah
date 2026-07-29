package com.krtky.financetracker.ui.util

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.unit.IntOffset
import com.krtky.financetracker.ui.theme.M3EMotion

fun fadeInSlideUp(
    animationSpec: FiniteAnimationSpec<Float> = M3EMotion.effectsDefault(),
    spatialSpec: FiniteAnimationSpec<IntOffset> = M3EMotion.spatialDefault(),
    fraction: Int = 12,
): EnterTransition = fadeIn(animationSpec) + slideInVertically(spatialSpec) { it / fraction }

fun fadeInSlideDown(
    animationSpec: FiniteAnimationSpec<Float> = M3EMotion.effectsDefault(),
    spatialSpec: FiniteAnimationSpec<IntOffset> = M3EMotion.spatialDefault(),
    fraction: Int = 12,
): EnterTransition = fadeIn(animationSpec) + slideInVertically(spatialSpec) { -it / fraction }
