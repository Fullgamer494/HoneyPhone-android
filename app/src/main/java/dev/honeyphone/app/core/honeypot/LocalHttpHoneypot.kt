package dev.honeyphone.app.core.honeypot

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import dev.honeyphone.app.BuildConfig
import dev.honeyphone.app.model.HoneypotEvent
import dev.honeyphone.app.model.HoneypotPhase
import dev.honeyphone.app.model.HoneypotProfile
import dev.honeyphone.app.model.HoneypotStatus
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import org.json.JSONObject

/**
 * User-started HTTP decoy. It binds only to the current Wi-Fi address, records request
 * headers only, and never reads or stores an HTTP request body.
 */
class LocalHttpHoneypot(
    context: Context,
    private val onStatus: (HoneypotStatus) -> Unit,
    private val onEvent: (HoneypotEvent) -> Unit,
    private val profile: HoneypotProfile = HoneypotProfile.LOGIN_PORTAL,
) {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var active = false
    @Volatile private var listener: ServerSocket? = null
    @Volatile private var activeClient: Socket? = null
    @Volatile private var registrationListener: NsdManager.RegistrationListener? = null

    fun start() {
        if (active) return
        active = true
        onStatus(HoneypotStatus(HoneypotPhase.STARTING))
        executor.execute {
            var errorStatus: HoneypotStatus? = null
            try {
                val wifiAddress = activeWifiAddress()
                if (!active) return@execute
                // ADB forwards to the device loopback address. The emulator's virtual Wi-Fi
                // address is separate, so allow all guest interfaces only in debug emulators.
                val bindAddress = if (BuildConfig.DEBUG && isEmulator()) {
                    InetAddress.getByName("0.0.0.0")
                } else {
                    wifiAddress
                }
                val server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(bindAddress, PORT), BACKLOG)
                }
                listener = server
                if (!active) {
                    server.close()
                    return@execute
                }
                val address = "http://${wifiAddress.hostAddress}:$PORT"
                onStatus(HoneypotStatus(phase = HoneypotPhase.LISTENING, address = address))
                advertiseService(address)

                while (active) {
                    val client = server.accept()
                    activeClient = client
                    try {
                        client.use { handleRequest(it) }
                    } catch (_: SocketTimeoutException) {
                        // Ignore incomplete clients without stopping the listener.
                    } catch (_: IOException) {
                        // A malformed or disconnected client is isolated to its socket.
                    } finally {
                        activeClient = null
                    }
                }
            } catch (exception: Exception) {
                if (active) {
                    val message = when (exception) {
                        is java.net.BindException -> "El puerto $PORT ya está ocupado. Cierra el otro servicio e inténtalo otra vez."
                        is SocketException -> "Se perdió la conexión Wi-Fi o se cerró el listener."
                        is IllegalStateException -> exception.message ?: "No se puede iniciar el señuelo en esta Wi-Fi."
                        else -> "No se pudo iniciar el señuelo en esta Wi-Fi. Comprueba que sigues conectado."
                    }
                    errorStatus = HoneypotStatus(HoneypotPhase.ERROR, message = message)
                    onStatus(errorStatus!!)
                }
            } finally {
                active = false
                unregisterService()
                listener?.runCatching { close() }
                listener = null
                activeClient?.runCatching { close() }
                activeClient = null
                executor.shutdown()
                if (errorStatus == null) onStatus(HoneypotStatus(HoneypotPhase.STOPPED))
            }
        }
    }

    fun stop() {
        active = false
        unregisterService()
        listener?.runCatching { close() }
        activeClient?.runCatching { close() }
        executor.shutdownNow()
        onStatus(HoneypotStatus(HoneypotPhase.STOPPED))
    }

    private fun activeWifiAddress(): InetAddress {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNetworks=manager.allNetworks.filter { network ->
            manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true
        }
        if(wifiNetworks.isEmpty()) throw IllegalStateException("Conéctate a una Wi-Fi para iniciar el señuelo.")
        return wifiNetworks.asSequence().flatMap { network ->
            manager.getLinkProperties(network)?.linkAddresses.orEmpty().asSequence().map { it.address }
        }.filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?: throw IllegalStateException("Android no informó una dirección IPv4 para esta Wi-Fi.")
    }

    private fun isEmulator(): Boolean =
        Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
            Build.MODEL.contains("sdk_gphone", ignoreCase = true)

    private fun advertiseService(address: String) {
        val service = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            port = PORT
        }
        val registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                if (!active) return
                onStatus(
                    HoneypotStatus(
                        phase = HoneypotPhase.LISTENING,
                        address = address,
                        serviceName = serviceInfo.serviceName,
                        serviceDiscoveryReady = true,
                    ),
                )
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                registrationListener = null
                if (active) {
                    onStatus(
                        HoneypotStatus(
                            phase = HoneypotPhase.LISTENING,
                            address = address,
                            message = "No se pudo anunciar HoneyPhone en la red (código $errorCode). Usa el enlace IP.",
                            serviceDiscoveryReady = false,
                        ),
                    )
                }
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = registration
        mainHandler.post {
            if (!active) return@post
            runCatching { nsdManager.registerService(service, NsdManager.PROTOCOL_DNS_SD, registration) }
                .onFailure {
                    registrationListener = null
                    if (active) {
                        onStatus(
                            HoneypotStatus(
                                phase = HoneypotPhase.LISTENING,
                                address = address,
                                message = "No se pudo anunciar HoneyPhone en la red. Usa el enlace IP.",
                                serviceDiscoveryReady = false,
                            ),
                        )
                    }
                }
        }
    }

    private fun unregisterService() {
        val registration = registrationListener ?: return
        registrationListener = null
        mainHandler.post {
            runCatching { nsdManager.unregisterService(registration) }
        }
    }

    private fun handleRequest(socket: Socket) {
        socket.soTimeout = READ_TIMEOUT_MS
        val request = readHeaders(socket) ?: run {
            writeResponse(socket, false, htmlResponse(400, "bad-request.html"))
            return
        }
        val peer = socket.inetAddress.hostAddress.orEmpty().take(MAX_ADDRESS_LENGTH)
        val response = responseFor(request)
        onEvent(
            HoneypotEvent(
                id = UUID.randomUUID().toString(),
                occurredAt = System.currentTimeMillis(),
                sourceAddress = peer,
                method = request.method,
                path = request.path,
                userAgent = request.userAgent,
                responseCode = response.status,
                requestDetails = JSONObject(request.details).put("source_port", socket.port).toString(),
            ),
        )
        writeResponse(socket, request.method == "HEAD", response)
    }

    private fun responseFor(request: ParsedRequest): DecoyResponse {
        val path = request.path.removeSuffix("/").ifBlank { "/" }
        val method = request.method

        if (method == "OPTIONS") {
            return emptyResponse(204, mapOf("Allow" to "GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS"))
        }
        val staticAsset = STATIC_ASSETS[path]
        if (staticAsset != null) {
            if (method !in setOf("GET", "HEAD")) return methodNotAllowed()
            return DecoyResponse(
                status = 200,
                body = readAssetBytes(staticAsset.first),
                contentType = staticAsset.second,
                headers = mapOf("Cache-Control" to "public, max-age=3600"),
            )
        }

        if (path in SENSITIVE_PATHS) {
            return if (method in setOf("GET", "HEAD")) htmlResponse(403, "forbidden.html") else methodNotAllowed()
        }

        if (path == "/upgrade") {
            if (method != "GET") return methodNotAllowed()
            val key = request.webSocketKey ?: return emptyResponse(426, mapOf("Upgrade" to "websocket"))
            val decodedKey = runCatching { Base64.getDecoder().decode(key) }.getOrNull()
            if (decodedKey?.size != 16) return htmlResponse(400, "bad-request.html")
            val accept = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-1").digest((key + WEBSOCKET_GUID).toByteArray(StandardCharsets.US_ASCII)),
            )
            return emptyResponse(
                101,
                mapOf("Connection" to "Upgrade", "Upgrade" to "websocket", "Sec-WebSocket-Accept" to accept),
            )
        }
        if (method == "GET" && path in setOf("/maintenance", "/api/maintenance")) {
            return htmlResponse(503, "maintenance.html")
        }
        if (method == "GET" && path in setOf("/not-found", "/missing")) {
            return htmlResponse(404, "not-found.html")
        }
        if (method == "GET" && path == "/bad-request") {
            return htmlResponse(400, "bad-request.html")
        }
        if (method == "GET" && path == "/robots.txt") {
            return textResponse(200, "User-agent: *\nDisallow: /\n", "text/plain; charset=utf-8")
        }

        if (path == "/login") {
            return when (method) {
                "GET", "HEAD" -> htmlResponse(200, "login.html")
                "POST" -> htmlResponse(401, "login-result.html")
                else -> methodNotAllowed()
            }
        }
        if (path == "/admin") return pageRequest(method, "admin.html")
        if (path == "/site") return pageRequest(method, "compromised.html")
        if (path == "/search") return pageRequest(method, "search.html")
        if (path == "/status") {
            if (method !in setOf("GET", "HEAD")) return methodNotAllowed()
            val headers = mapOf("ETag" to STATUS_ETAG, "Cache-Control" to "no-cache")
            return if (request.matchesStatusEtag) emptyResponse(304, headers)
            else htmlResponse(200, "status.html", headers)
        }
        if (path == "/") {
            val asset = when (profile) {
                HoneypotProfile.LOGIN_PORTAL -> "login.html"
                HoneypotProfile.ADMIN_CONSOLE -> "admin.html"
                HoneypotProfile.COMPROMISED_SITE -> "compromised.html"
            }
            return pageRequest(method, asset)
        }

        if (path == "/api/status") {
            return if (method in setOf("GET", "HEAD")) {
                textResponse(200, "{\"service\":\"available\",\"node\":\"honeyphone-local\"}", "application/json; charset=utf-8")
            } else methodNotAllowed()
        }
        if (path == "/api/session") {
            return when (method) {
                "POST" -> textResponse(201, "{\"created\":true}", "application/json; charset=utf-8")
                "DELETE" -> emptyResponse(204)
                else -> methodNotAllowed()
            }
        }
        if (path == "/api/support" || path == "/api/alerts") {
            return if (method == "POST") textResponse(202, "{\"accepted\":true}", "application/json; charset=utf-8") else methodNotAllowed()
        }
        if (path == "/api/settings") {
            return when (method) {
                "PUT" -> emptyResponse(204)
                "PATCH" -> textResponse(200, "{\"updated\":true}", "application/json; charset=utf-8")
                else -> methodNotAllowed()
            }
        }

        return if (method in setOf("GET", "HEAD")) htmlResponse(404, "not-found.html") else methodNotAllowed()
    }

    private fun pageRequest(method: String, asset: String): DecoyResponse =
        if (method in setOf("GET", "HEAD")) htmlResponse(200, asset) else methodNotAllowed()

    private fun htmlResponse(status: Int, asset: String, headers: Map<String, String> = emptyMap()) =
        DecoyResponse(status, readAssetBytes("honeypot/$asset"), "text/html; charset=utf-8", headers)

    private fun textResponse(status: Int, text: String, contentType: String, headers: Map<String, String> = emptyMap()) =
        DecoyResponse(status, text.toByteArray(StandardCharsets.UTF_8), contentType, headers)

    private fun emptyResponse(status: Int, headers: Map<String, String> = emptyMap()) =
        DecoyResponse(status, ByteArray(0), "text/plain; charset=utf-8", headers)

    private fun methodNotAllowed() = htmlResponse(405, "method-not-allowed.html", mapOf("Allow" to "GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS"))

    private fun readAssetBytes(asset: String): ByteArray = runCatching {
        appContext.assets.open(asset).use { it.readBytes() }
    }.getOrElse {
        "<!doctype html><html lang=\"es\"><meta charset=\"utf-8\"><title>HoneyPhone</title><body><h1>Señuelo local</h1></body></html>"
            .toByteArray(StandardCharsets.UTF_8)
    }

    /** Reads at most the HTTP request line and headers; a POST body is deliberately untouched. */
    private fun readHeaders(socket: Socket): ParsedRequest? {
        val bytes = ByteArrayOutputStream()
        val input = socket.getInputStream()
        var matched = 0
        var complete = false
        val terminator = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        while (bytes.size() < MAX_HEADER_BYTES) {
            val next = input.read()
            if (next < 0) break
            bytes.write(next)
            if (next.toByte() == terminator[matched]) {
                matched++
                if (matched == terminator.size) {
                    complete = true
                    break
                }
            } else {
                matched = if (next.toByte() == terminator[0]) 1 else 0
            }
        }
        if (!complete) return null
        val lines = bytes.toString(StandardCharsets.ISO_8859_1.name()).split("\r\n")
        val requestParts = lines.firstOrNull()?.trim()?.split(WHITESPACE, limit = 3) ?: return null
        if (requestParts.size < 2 || !METHOD.matches(requestParts[0])) return null
        val path = runCatching { URI(requestParts[1]).rawPath ?: "/" }.getOrNull()
            ?.take(MAX_PATH_LENGTH)
            ?.filter { it.isLetterOrDigit() || it in "/._~%-" }
            ?.takeIf { it.startsWith('/') }
            ?: return null
        val headers = lines.drop(1).mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) null else line.substring(0, separator).trim().lowercase(Locale.ROOT) to line.substring(separator + 1).trim()
        }.toMap()
        fun header(name: String, max: Int): String = headers[name].orEmpty()
            .filter { it.code in 32..126 }.take(max)
        val userAgent = header("user-agent", MAX_USER_AGENT_LENGTH)
        val matchesStatusEtag = header("if-none-match", 120).split(',').any { it.trim() == STATUS_ETAG || it.trim() == "*" }
        val webSocketKey = header("sec-websocket-key", 64).takeIf(String::isNotBlank)
        val details = JSONObject()
            .put("http_version", requestParts.getOrNull(2)?.takeIf { it.matches(Regex("HTTP/1\\.[01]")) } ?: "unknown")
            .put("host", header("host", 150))
            .put("referer", safeOriginAndPath(header("referer", 300)))
            .put("origin", safeOriginAndPath(header("origin", 300)))
            .put("accept_language", header("accept-language", 100))
            .put("accept", header("accept", 120))
            .put("content_type", header("content-type", 100))
            .put("content_length", header("content-length", 12).filter(Char::isDigit))
            .put("connection", header("connection", 40))
            .toString()
        return ParsedRequest(requestParts[0].uppercase(Locale.ROOT), path, userAgent, details, matchesStatusEtag, webSocketKey)
    }

    private fun safeOriginAndPath(value: String): String = runCatching {
        val uri = URI(value)
        val host = uri.host ?: return@runCatching ""
        val path = uri.rawPath.orEmpty().take(MAX_PATH_LENGTH).filter { it.isLetterOrDigit() || it in "/._~%-" }
        "${uri.scheme ?: "http"}://$host$path"
    }.getOrDefault("")

    private fun writeResponse(socket: Socket, headRequest: Boolean, response: DecoyResponse) {
        val noBodyStatus = response.status in setOf(101, 204, 304)
        val reason = REASON_PHRASES[response.status] ?: "Response"
        val headers = linkedMapOf(
            "Content-Type" to response.contentType,
            "Connection" to "close",
            "Cache-Control" to "no-store",
            "X-Content-Type-Options" to "nosniff",
            "X-Frame-Options" to "DENY",
        )
        if (!noBodyStatus) headers["Content-Length"] = response.body.size.toString()
        if (response.contentType.startsWith("text/html")) {
            headers["Content-Security-Policy"] = "default-src 'none'; img-src 'self'; style-src 'self'; script-src 'self'; connect-src 'self' ws:; form-action 'self'; base-uri 'none'; frame-ancestors 'none'"
            headers["Referrer-Policy"] = "no-referrer"
        }
        headers.putAll(response.headers)
        val headerBlock = buildString {
            append("HTTP/1.1 ${response.status} $reason\r\n")
            headers.forEach { (name, value) -> append("$name: $value\r\n") }
            append("\r\n")
        }
        socket.getOutputStream().apply {
            write(headerBlock.toByteArray(StandardCharsets.US_ASCII))
            if (!headRequest && !noBodyStatus) write(response.body)
            flush()
        }
    }

    private data class DecoyResponse(
        val status: Int,
        val body: ByteArray,
        val contentType: String,
        val headers: Map<String, String> = emptyMap(),
    )

    private data class ParsedRequest(
        val method: String,
        val path: String,
        val userAgent: String,
        val details: String,
        val matchesStatusEtag: Boolean,
        val webSocketKey: String?,
    )

    companion object {
        const val PORT = 8080
        const val BACKLOG = 16
        const val READ_TIMEOUT_MS = 2_000
        const val MAX_HEADER_BYTES = 4_096
        const val MAX_ADDRESS_LENGTH = 64
        const val MAX_PATH_LENGTH = 120
        const val MAX_USER_AGENT_LENGTH = 120
        const val SERVICE_NAME = "HoneyPhone"
        const val SERVICE_TYPE = "_http._tcp."
        const val LOGO_PATH = "/assets/honeyphone-logo.png"
        const val STATUS_ETAG = "\"honeyphone-status-v1\""
        const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val STATIC_ASSETS = mapOf(
            LOGO_PATH to ("honeypot/brand-logo.png" to "image/png"),
            "/favicon.ico" to ("honeypot/brand-logo.png" to "image/png"),
            "/assets/decoy.css" to ("honeypot/decoy.css" to "text/css; charset=utf-8"),
            "/assets/flows.js" to ("honeypot/flows.js" to "text/javascript; charset=utf-8"),
        )
        val SENSITIVE_PATHS = setOf("/.env", "/.git/config")
        val REASON_PHRASES = mapOf(
            101 to "Switching Protocols",
            200 to "OK",
            201 to "Created",
            202 to "Accepted",
            204 to "No Content",
            304 to "Not Modified",
            400 to "Bad Request",
            401 to "Unauthorized",
            403 to "Forbidden",
            404 to "Not Found",
            405 to "Method Not Allowed",
            426 to "Upgrade Required",
            503 to "Service Unavailable",
        )
        val WHITESPACE = Regex("\\s+")
        val METHOD = Regex("[A-Za-z]{1,12}")
    }
}
