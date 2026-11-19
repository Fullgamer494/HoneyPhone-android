package dev.honeyphone.app.core.network

import dev.honeyphone.app.BuildConfig
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ApiFailure(val status: Int, val code: String, message: String) : IOException(message)

class NetworkApi {
    private val base = BuildConfig.API_BASE_URL
    private val connectTimeout = 5000
    private val readTimeout = 8000

    fun post(payload: CheckPayload): JSONObject = request("POST", "network-checks", payload.json())
    fun health(): JSONObject {
        val healthUrl=base.substringBefore("api/v1/")+"healthz"
        val connection=(URL(healthUrl).openConnection() as HttpURLConnection).apply {
            requestMethod="GET"; connectTimeout=this@NetworkApi.connectTimeout; readTimeout=this@NetworkApi.readTimeout; setRequestProperty("Accept","application/json")
        }
        try {
            val status=connection.responseCode
            val stream=if(status in 200..299)connection.inputStream else connection.errorStream
            val body=stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if(status !in 200..299) throw ApiFailure(status,"HEALTH_$status","API o base de datos no disponible")
            return JSONObject(body)
        } finally { connection.disconnect() }
    }
    fun list(installationId: String): List<JSONObject> {
        val encodedInstallationId = URLEncoder.encode(installationId, StandardCharsets.UTF_8.name())
        val items = mutableListOf<JSONObject>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null

        do {
            val cursorQuery = cursor?.let {
                "&cursor=${URLEncoder.encode(it, StandardCharsets.UTF_8.name())}"
            }.orEmpty()
            val page = request(
                "GET",
                "network-checks?installation_id=$encodedInstallationId&limit=50$cursorQuery",
            )
            val pageItems = page.getJSONArray("items")
            for (index in 0 until pageItems.length()) {
                items += pageItems.getJSONObject(index)
            }

            cursor = if (page.isNull("next_cursor")) null else page.optString("next_cursor").takeIf(String::isNotBlank)
            if (cursor != null && !seenCursors.add(cursor)) {
                throw IOException("La API devolvió un cursor de paginación repetido.")
            }
        } while (cursor != null)

        return items
    }
    fun get(id: String): JSONObject = request("GET", "network-checks/$id")
    fun delete(id: String) { request("DELETE", "network-checks/$id") }
    fun registerDevice(id: String, version: String) = request("POST", "devices?installation_id=$id&app_version=$version")
    fun devices(): List<JSONObject> = request("GET", "devices").getJSONArray("items").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    fun device(id: String): JSONObject = request("GET", "devices/$id")
    fun updateDevice(id: String, name: String): JSONObject = request("PATCH", "devices/$id", JSONObject().put("device_name", name).toString())
    fun revokeDevice(id: String): JSONObject = request("DELETE", "devices/$id")
    fun upsertHoneypotSession(session: JSONObject): JSONObject = request("POST", "honeypot/sessions", session.toString())
    fun listHoneypotSessions(installationId: String): JSONObject = request("GET", "honeypot/sessions?installation_id=$installationId&limit=50")
    fun listHoneypotEvents(sessionId: String): JSONObject = request("GET", "honeypot/sessions/$sessionId/events?limit=50")
    fun postHoneypotEvents(sessionId: String, events: JSONArray): JSONObject = request("POST", "honeypot/sessions/$sessionId/events", JSONObject().put("events", events).toString())

    private fun request(method: String, path: String, body: String? = null): JSONObject {
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = this@NetworkApi.connectTimeout
            readTimeout = this@NetworkApi.readTimeout
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val raw = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val error = runCatching { JSONObject(raw) }.getOrNull()
                throw ApiFailure(status, error?.optString("code").takeUnless { it.isNullOrBlank() } ?: "HTTP_$status", error?.optString("message").takeUnless { it.isNullOrBlank() } ?: "No fue posible completar la solicitud.")
            }
            if (status == 204) return JSONObject()
            return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }
}
