package dev.honeyphone.app.core.honeypot

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.honeyphone.app.model.HoneypotEvent
import dev.honeyphone.app.model.HoneypotPhase
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
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

/**
 * User-started HTTP decoy. It binds only to the current Wi-Fi address, records request
 * headers only, and never reads or stores an HTTP request body.
 */
class LocalHttpHoneypot(
    context: Context,
    private val onStatus: (HoneypotStatus) -> Unit,
    private val onEvent: (HoneypotEvent) -> Unit,
) {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var active = false
    @Volatile private var listener: ServerSocket? = null
    @Volatile private var activeClient: Socket? = null

    fun start() {
        if (active) return
        active = true
        onStatus(HoneypotStatus(HoneypotPhase.STARTING))
        executor.execute {
            var errorStatus: HoneypotStatus? = null
            try {
                val wifiAddress = activeWifiAddress()
                if (!active) return@execute
                val server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(wifiAddress, PORT), BACKLOG)
                }
                listener = server
                if (!active) {
                    server.close()
                    return@execute
                }
                onStatus(HoneypotStatus(HoneypotPhase.LISTENING, "http://${wifiAddress.hostAddress}:$PORT"))

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
        listener?.runCatching { close() }
        activeClient?.runCatching { close() }
        executor.shutdownNow()
        onStatus(HoneypotStatus(HoneypotPhase.STOPPED))
    }

    private fun activeWifiAddress(): InetAddress {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: throw IllegalStateException("Conéctate a una Wi-Fi para iniciar el señuelo.")
        val capabilities = manager.getNetworkCapabilities(network)
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
            throw IllegalStateException("Conéctate a una Wi-Fi para iniciar el señuelo.")
        }
        return manager.getLinkProperties(network)?.linkAddresses
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?: throw IllegalStateException("Android no informó una dirección IPv4 para esta Wi-Fi.")
    }

    private fun handleRequest(socket: Socket) {
        socket.soTimeout = READ_TIMEOUT_MS
        val request = readHeaders(socket) ?: run {
            writeResponse(socket, 400, "Solicitud incompleta")
            return
        }
        val peer = socket.inetAddress.hostAddress.orEmpty().take(MAX_ADDRESS_LENGTH)
        val status = if (
            request.path in DECOY_PATHS ||
            request.path.startsWith("/admin/") ||
            request.path.startsWith("/api/")
        ) 200 else 404
        onEvent(
            HoneypotEvent(
                id = UUID.randomUUID().toString(),
                occurredAt = System.currentTimeMillis(),
                sourceAddress = peer,
                method = request.method,
                path = request.path,
                userAgent = request.userAgent,
                responseCode = status,
            ),
        )
        writeResponse(socket, status, "No encontrado")
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
        val userAgent = lines.drop(1).firstNotNullOfOrNull { line ->
            if (line.startsWith("User-Agent:", ignoreCase = true)) line.substringAfter(':').trim() else null
        }.orEmpty().filter { it.code in 32..126 }.take(MAX_USER_AGENT_LENGTH)
        return ParsedRequest(requestParts[0].uppercase(Locale.ROOT), path, userAgent)
    }

    private fun writeResponse(socket: Socket, status: Int, content: String) {
        val body = """
            <!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width">
            <title>HoneyPhone Device Console</title><body style="font:16px sans-serif;background:#101713;color:#eaf1e8;padding:2rem">
            <h1>Device console</h1><p>Local network management interface</p><p>Service status: ready</p>
            <a href="/admin">Administration</a></body></html>
        """.trimIndent().takeIf { status == 200 } ?: "<!doctype html><title>Not found</title><h1>$content</h1>"
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        val reason = if (status == 200) "OK" else if (status == 400) "Bad Request" else "Not Found"
        val headers = "HTTP/1.1 $status $reason\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bodyBytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n"
        socket.getOutputStream().apply {
            write(headers.toByteArray(StandardCharsets.US_ASCII))
            write(bodyBytes)
            flush()
        }
    }

    private data class ParsedRequest(val method: String, val path: String, val userAgent: String)

    private companion object {
        const val PORT = 8080
        const val BACKLOG = 16
        const val READ_TIMEOUT_MS = 2_000
        const val MAX_HEADER_BYTES = 4_096
        const val MAX_ADDRESS_LENGTH = 64
        const val MAX_PATH_LENGTH = 120
        const val MAX_USER_AGENT_LENGTH = 120
        val WHITESPACE = Regex("\\s+")
        val METHOD = Regex("[A-Za-z]{1,12}")
        val DECOY_PATHS = setOf("/", "/admin", "/login", "/robots.txt", "/.env", "/.git/config", "/api/status")
    }
}
