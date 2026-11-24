package dev.honeyphone.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import dev.honeyphone.app.R
import kotlin.math.PI
import kotlin.math.sin

private data class StartupMotionPoint(
    val progress: Float,
    val rotation: Float,
    val scale: Float,
)

private data class StartupMotion(val rotation: Float, val scale: Float)

private val startupMotionPoints = listOf(
    StartupMotionPoint(0.00f, -12f, 0.86f),
    StartupMotionPoint(0.12f, -5f, 0.94f),
    StartupMotionPoint(0.38f, 12f, 1.12f),
    StartupMotionPoint(0.60f, 30f, 1.34f),
    StartupMotionPoint(0.73f, 40f, 1.40f),
    StartupMotionPoint(0.84f, 24f, 1.22f),
    StartupMotionPoint(0.94f, 5f, 0.90f),
    StartupMotionPoint(1.00f, 0f, 1.00f),
)

@Composable
fun StartupAnimationOverlay(onAnimationStarted: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        onAnimationStarted()
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 4_800, easing = LinearEasing),
        )
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.honeyphone_startup_frame),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val motion = startupMotionAt(progress.value)
                val squeeze = sin(progress.value * PI).toFloat() * 0.055f
                rotationZ = motion.rotation
                scaleX = motion.scale * (1f + squeeze)
                scaleY = motion.scale * (1f - squeeze * 0.7f)
            },
        )
    }
}

private fun startupMotionAt(progress: Float): StartupMotion {
    val value = progress.coerceIn(0f, 1f)
    for (index in 0 until startupMotionPoints.lastIndex) {
        val from = startupMotionPoints[index]
        val to = startupMotionPoints[index + 1]
        if (value <= to.progress) {
            val segment = ((value - from.progress) / (to.progress - from.progress)).coerceIn(0f, 1f)
            val eased = segment * segment * (3f - 2f * segment)
            return StartupMotion(
                rotation = from.rotation + (to.rotation - from.rotation) * eased,
                scale = from.scale + (to.scale - from.scale) * eased,
            )
        }
    }
    val last = startupMotionPoints.last()
    return StartupMotion(last.rotation, last.scale)
}
