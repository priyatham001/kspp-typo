package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Reusable animated refresh icon that rotates smoothly on its center axis while an operation is in progress.
 * Immediately starts rotating on active state and resets to 0 degrees when idle.
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
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RefreshRotationAngle"
    )

    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.graphicsLayer {
            rotationZ = if (isRotating) angle else 0f
        }
    )
}
