package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Reusable animated refresh icon that rotates smoothly on its center axis.
 * Supports continuous rotation when [isRotating] is true, plus tactile spin response.
 */
@Composable
fun RotatingRefreshIcon(
    isRotating: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Refresh,
    contentDescription: String? = "Refresh",
    tint: Color = LocalContentColor.current
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RefreshRotationTransition")
    val infiniteAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RefreshRotationAngle"
    )

    // Smooth return to 0 or animated spin
    val rotationAnim = remember { Animatable(0f) }

    LaunchedEffect(isRotating) {
        if (!isRotating && rotationAnim.value != 0f) {
            rotationAnim.animateTo(
                targetValue = 360f,
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            )
            rotationAnim.snapTo(0f)
        }
    }

    val displayAngle = if (isRotating) infiniteAngle else rotationAnim.value

    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.graphicsLayer {
            rotationZ = displayAngle
        }
    )
}

/**
 * Animated Refresh IconButton that immediately spins 360° on click,
 * remains spinning continuously if [isRotating] is true,
 * and has bounce press feedback.
 */
@Composable
fun RotatingRefreshIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isRotating: Boolean = false,
    icon: ImageVector = Icons.Default.Refresh,
    contentDescription: String? = "Refresh",
    tint: Color = LocalContentColor.current,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true
) {
    val scope = rememberCoroutineScope()
    val clickAngle = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "BtnInfiniteRefresh")
    val infiniteAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "InfiniteAngle"
    )

    IconButton(
        onClick = {
            scope.launch {
                clickAngle.snapTo(0f)
                clickAngle.animateTo(
                    targetValue = 360f,
                    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                )
                clickAngle.snapTo(0f)
            }
            onClick()
        },
        enabled = enabled,
        modifier = modifier.bounceClick()
    ) {
        val currentAngle = if (isRotating) infiniteAngle else clickAngle.value
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    rotationZ = currentAngle
                }
        )
    }
}
