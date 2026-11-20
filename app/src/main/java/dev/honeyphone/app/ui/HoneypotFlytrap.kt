package dev.honeyphone.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.honeyphone.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun HoneypotFlytrap(
    isListening: Boolean,
    latestEventId: String?,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
) {
    val jaw = remember { Animatable(if (isListening) 0f else 1f) }
    val flight = remember { Animatable(0f) }
    var seenEventId by remember { mutableStateOf(latestEventId) }
    var insectIndex by remember { mutableIntStateOf(-1) }
    var nextInsect by remember { mutableIntStateOf(0) }
    val insects = listOf(R.drawable.flytrap_fly, R.drawable.flytrap_moth, R.drawable.flytrap_wasp, R.drawable.flytrap_roach)

    LaunchedEffect(isListening, latestEventId) {
        if (!isListening) {
            insectIndex = -1
            jaw.animateTo(1f, tween(420))
            seenEventId = latestEventId
            return@LaunchedEffect
        }
        jaw.animateTo(0f, tween(420))
        if (latestEventId != null && latestEventId != seenEventId) {
            seenEventId = latestEventId
            insectIndex = nextInsect % insects.size
            nextInsect++
            flight.snapTo(0f)
            val flightJob = launch { flight.animateTo(1f, tween(2800)) }
            delay(900)
            jaw.animateTo(1f, tween(260))
            delay(800)
            jaw.animateTo(0f, tween(520))
            flightJob.join()
            insectIndex = -1
        } else {
            seenEventId = latestEventId
        }
    }

    Box(modifier.size(size).semantics {
        contentDescription = if (isListening) "Venus atrapamoscas abierta; el señuelo está escuchando" else "Venus atrapamoscas cerrada; el señuelo está detenido"
    }) {
        Image(
            painter = painterResource(R.drawable.flytrap_open),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - jaw.value },
        )
        Image(
            painter = painterResource(R.drawable.flytrap_closed),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = jaw.value },
        )
        if (insectIndex >= 0) {
            Image(
                painter = painterResource(insects[insectIndex]),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val progress = flight.value
                    alpha = when {
                        progress < 0.12f -> progress / 0.12f
                        progress < 0.48f -> 1f
                        progress < 0.72f -> (0.72f - progress) / 0.24f
                        else -> 0f
                    }
                    translationX = -this.size.width * (1f - progress) * 0.35f
                    translationY = -this.size.height * (1f - progress) * 0.3f
                    rotationZ = (1f - progress) * -14f
                    scaleX = 0.8f + progress * 0.2f
                    scaleY = scaleX
                },
            )
        }
    }
}
