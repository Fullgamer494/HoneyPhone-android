package dev.honeyphone.app.ui

import android.graphics.Color as AndroidColor
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

@Composable
internal fun HoneypotFlytrap(
    isListening: Boolean,
    latestEventId: String?,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
) {
    val context = LocalContext.current
    val webView = remember(context) { FlytrapWebView(context, latestEventId) }
    DisposableEffect(webView) {
        onDispose { webView.release() }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = if (isListening) {
                    "Venus atrapamoscas abierta; el señuelo está escuchando"
                } else {
                    "Venus atrapamoscas cerrada; el señuelo está detenido"
                }
            },
        update = { it.updateState(isListening, latestEventId) },
    )
}

private class FlytrapWebView(
    context: android.content.Context,
    initialEventId: String?,
) : WebView(context) {
    private var pageReady = false
    private var isListening = false
    private var latestEventId = initialEventId
    private var shouldAnimateEvent = false
    private var appliedListening: Boolean? = null
    private var appliedEventId: String? = null
    private var appliedAnimation = false

    init {
        setBackgroundColor(AndroidColor.BLACK)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            builtInZoomControls = false
            displayZoomControls = false
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.host != "appassets.androidplatform.net"

            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
                applyStateIfNeeded()
            }
        }

        var html = context.assets.open("honeypot_trap.html").bufferedReader().use { it.readText() }
        for (name in listOf("open", "closed", "fly", "moth", "wasp", "roach")) {
            val png = context.assets.open("flytrap_$name.png").use { it.readBytes() }
            html = html.replace("{{${name.uppercase()}}}", Base64.encodeToString(png, Base64.NO_WRAP))
        }
        loadDataWithBaseURL(
            "https://appassets.androidplatform.net/assets/",
            html,
            "text/html",
            "UTF-8",
            null,
        )
    }

    fun updateState(listening: Boolean, eventId: String?) {
        if (eventId != null && eventId != latestEventId) shouldAnimateEvent = true
        latestEventId = eventId
        isListening = listening
        applyStateIfNeeded()
    }

    private fun applyStateIfNeeded() {
        if (!pageReady) return
        if (appliedListening == isListening && appliedEventId == latestEventId &&
            appliedAnimation == shouldAnimateEvent
        ) return

        val eventId = JSONObject.quote(latestEventId ?: "")
        evaluateJavascript(
            "window.honeyphoneSetState($isListening,$eventId,$shouldAnimateEvent);",
            null,
        )
        appliedListening = isListening
        appliedEventId = latestEventId
        appliedAnimation = shouldAnimateEvent
        shouldAnimateEvent = false
    }

    fun release() {
        stopLoading()
        webViewClient = WebViewClient()
        loadUrl("about:blank")
        destroy()
    }
}
