package dev.honeyphone.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.view.View
import android.widget.ImageView
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

private const val STALKING_SCENE = "stalking-idle.webp"
private const val CLOSED_IDLE_SCENE = "closed-idle.webp"

private val devourScenes = listOf(
    "devour-fly.webp",
    "devour-moth.webp",
    "devour-wasp.webp",
    "devour-roach.webp",
)

private data class FlytrapScene(val file: String, val revision: Int)

@Composable
internal fun HoneypotFlytrap(
    isListening: Boolean,
    latestEventId: String?,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
) {
    var scene by remember(isListening) {
        mutableStateOf(FlytrapScene(if (isListening) STALKING_SCENE else CLOSED_IDLE_SCENE, 0))
    }
    var previousEventId by remember { mutableStateOf(latestEventId) }
    var wasListening by remember { mutableStateOf(isListening) }
    var nextDevourScene by remember { mutableIntStateOf(0) }

    LaunchedEffect(isListening, latestEventId) {
        if (!isListening) {
            previousEventId = latestEventId
            wasListening = false
            scene = FlytrapScene(CLOSED_IDLE_SCENE, scene.revision + 1)
            return@LaunchedEffect
        }

        if (!wasListening) {
            wasListening = true
            previousEventId = latestEventId
            if (scene.file != STALKING_SCENE) {
                scene = FlytrapScene(STALKING_SCENE, scene.revision + 1)
            }
            return@LaunchedEffect
        }

        if (latestEventId != null && latestEventId != previousEventId) {
            previousEventId = latestEventId
            val devourScene = devourScenes[nextDevourScene % devourScenes.size]
            nextDevourScene++
            scene = FlytrapScene(devourScene, scene.revision + 1)
            delay(8_500)
            scene = FlytrapScene(STALKING_SCENE, scene.revision + 1)
        } else {
            previousEventId = latestEventId
            if (scene.file != STALKING_SCENE) {
                scene = FlytrapScene(STALKING_SCENE, scene.revision + 1)
            }
        }
    }

    Box(
        modifier.size(size).semantics {
            contentDescription = if (isListening) {
                "Atrapamoscas HoneyPhone animada; el señuelo está escuchando"
            } else {
                "Atrapamoscas HoneyPhone animada; el señuelo está detenido"
            }
        },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context -> FlytrapAnimationView(context, scene) },
            update = { view -> view.show(scene) },
        )
    }
}

@SuppressLint("NewApi")
private class FlytrapAnimationView(context: Context, initialScene: FlytrapScene) : ImageView(context) {
    private var activeScene: FlytrapScene? = null

    init {
        scaleType = ScaleType.FIT_CENTER
        setBackgroundColor(AndroidColor.TRANSPARENT)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        show(initialScene)
    }

    fun show(scene: FlytrapScene) {
        if (activeScene == scene) return
        activeScene = scene
        (drawable as? AnimatedImageDrawable)?.stop()

        val source = ImageDecoder.createSource(context.assets, "honeypot/webp/${scene.file}")
        val next = ImageDecoder.decodeDrawable(source)
        setImageDrawable(next)
        if (next is AnimatedImageDrawable) {
            next.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            next.start()
        }
    }
}
