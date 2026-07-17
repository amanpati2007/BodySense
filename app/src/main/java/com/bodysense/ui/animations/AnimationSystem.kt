package com.bodysense.ui.animations

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Reusable animation specifications for a premium, medical-grade feel.
 */
object Motion {
    // 700ms tween for smooth, calm transitions
    val premiumTween = tween<Float>(durationMillis = 700)
    val premiumIntOffsetTween = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = 700)
    
    // Spring physics for natural bounces
    val premiumSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 200f)
    
    // Screen navigation transitions
    val slideInLeft: EnterTransition = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = premiumIntOffsetTween
    ) + fadeIn(premiumTween)
    
    val slideOutLeft: ExitTransition = slideOutHorizontally(
        targetOffsetX = { -it / 3 },
        animationSpec = premiumIntOffsetTween
    ) + fadeOut(premiumTween)
    
    val slideInRight: EnterTransition = slideInHorizontally(
        initialOffsetX = { -it / 3 },
        animationSpec = premiumIntOffsetTween
    ) + fadeIn(premiumTween)
    
    val slideOutRight: ExitTransition = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = premiumIntOffsetTween
    ) + fadeOut(premiumTween)
}

/**
 * Premium microinteraction modifier for clickable elements.
 * Scales down slightly with spring physics when pressed.
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.97f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "bounceClick"
    )
    
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null, // Disable default ripple for a cleaner look
            onClick = onClick
        )
}
