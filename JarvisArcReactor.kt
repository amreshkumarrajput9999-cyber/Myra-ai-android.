package com.myra.ai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myra.ai.ui.theme.*

enum class AssistantState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

@Composable
fun JarvisArcReactor(
    state: AssistantState,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    audioRms: Float = 0f,
    onClick: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "reactor")

    // Slow rotation for outer ring
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == AssistantState.THINKING) 3000 else 18000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_rot"
    )

    // Reverse rotation for inner ring
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == AssistantState.THINKING) 2500 else 14000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_rot"
    )

    // Pulse scale for core breathing
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == AssistantState.LISTENING) 800 else 2400,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val (accentColor, glowColor) = when (state) {
        AssistantState.LISTENING -> Pair(MyraEmerald, Color(0x6610B981))
        AssistantState.THINKING -> Pair(MyraPurple, Color(0x66A855F7))
        AssistantState.SPEAKING -> Pair(MyraSky, Color(0x6638BDF8))
        AssistantState.ERROR -> Pair(MyraRed, Color(0x66EF4444))
        AssistantState.IDLE -> Pair(MyraCyan, Color(0x5500F0FF))
    }

    Box(
        modifier = modifier
            .size(size)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val radius = size.toPx() / 2f - 16f

            // 1. Ambient Background Radial Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor, Color.Transparent),
                    center = center,
                    radius = radius * 1.2f
                ),
                radius = radius * (if (state == AssistantState.LISTENING) 1.25f else 1.05f)
            )

            // 2. Outermost Static HUD Ring with Compass Dashes
            drawCircle(
                color = accentColor.copy(alpha = 0.35f),
                radius = radius,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 18f), 0f)
                )
            )

            // 3. Rotating Outer Ring
            drawArc(
                color = accentColor,
                startAngle = outerRotation,
                sweepAngle = 100f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx())
            )
            drawArc(
                color = accentColor,
                startAngle = outerRotation + 180f,
                sweepAngle = 60f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx())
            )

            // 4. Counter-rotating Inner Segmented Ring
            drawArc(
                color = MyraBlue,
                startAngle = innerRotation,
                sweepAngle = 140f,
                useCenter = false,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 12f), 0f)
                )
            )
            drawArc(
                color = MyraBlue,
                startAngle = innerRotation + 180f,
                sweepAngle = 80f,
                useCenter = false,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 12f), 0f)
                )
            )

            // 5. Pulsing AI Core Energy Sphere (reacts to voice RMS level when listening)
            val rmsBoost = (audioRms.coerceIn(0f, 10f) / 10f) * 12f
            val coreRadius = (radius * 0.42f * corePulse) + rmsBoost

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        accentColor,
                        MyraDarkBg
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius
            )

            // 6. Center Tactical Hexagon Frame
            drawCircle(
                color = accentColor,
                radius = coreRadius * 0.45f,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx()
            )
        }
    }
}
