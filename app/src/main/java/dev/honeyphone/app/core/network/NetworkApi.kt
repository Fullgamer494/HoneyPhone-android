package dev.honeyphone.app.core.network

import android.content.Context
import dev.honeyphone.app.BuildConfig
import dev.honeyphone.app.core.auth.SecureSessionStore
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ApiFailure(val status: Int, val code: String, message: String) : IOException(message)

class NetworkApi(context: Context) {
    private val base = BuildConfig.API_BASE_URL
    private val sessionStore = SecureSessionStore(context.applicationContext)
    private val connectTimeout = 5000
    private val readTimeout = 8000

    fun post(payload: CheckPayload): JSONObject = request("POST", "network-checks", payload.json())
    fun registerAccount(email: String, password: String): JSONObject = request("POST", "auth/register", JSONObject().put("email", email).put("password", password).toString(), authenticated = false)
    fun login(email: String, password: String): JSONObject = request("POST", "auth/login", JSONObject().put("email", email).put("password", password).toString(), authenticated = false)
    fun requestEmailVerification(email: String): JSONObject = request("POST", "auth/verify-email/request", JSONObject().put("email", email).toString(), authenticated = false)
    fun requestPasswordReset(email: String): JSONObject = request("POST", "auth/password/forgot", JSONObject().put("email", email).toString(), authenticated = false)
    fun googleLogin(idToken: String): JSONObject = request("POST", "auth/google", JSONObject().put("id_token", idToken).toString(), authenticated = false)
    fun account(): JSONObject = request("GET", "auth/me")
    fun deleteAccount() { request("DELETE", "auth/me") }
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
        return fetchAllCursorPages { cursor ->
            val cursorQuery = cursor?.let {
                "&cursor=${URLEncoder.encode(it, StandardCharsets.UTF_8.name())}"
            }.orEmpty()
            val page = request(
                "GET",
                "network-checks?installation_id=$encodedInstallationId&limit=50$cursorQuery",
            )
            val pageItems = page.getJSONArray("items")
            val items = (0 until pageItems.length()).map(pageItems::getJSONObject)
            val nextCursor = if (page.isNull("next_cursor")) null else page.optString("next_cursor")
            CursorPage(items, nextCursor)
        }
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

    private fun request(method: String, path: String, body: String? = null, authenticated: Boolean = true): JSONObject {
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = this@NetworkApi.connectTimeout
            readTimeout = this@NetworkApi.readTimeout
            setRequestProperty("Accept", "application/json")
            if (authenticated) sessionStore.accessToken()?.let { setRequestProperty("Authorization", "Bearer $it") }
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
                val detail = error?.opt("detail")
                val nested = detail as? JSONObject
                val validationMessage = (detail as? JSONArray)?.let { errors ->
                    (0 until errors.length()).mapNotNull { index ->
                        errors.optJSONObject(index)?.optString("msg")?.takeIf(String::isNotBlank)
                    }.distinct().joinToString(" ").takeIf(String::isNotBlank)
                }
                val code = error?.optString("code")?.takeIf(String::isNotBlank)
                    ?: nested?.optString("code")?.takeIf(String::isNotBlank)
                    ?: "HTTP_$status"
                val message = error?.optString("message")?.takeIf(String::isNotBlank)
                    ?: nested?.optString("message")?.takeIf(String::isNotBlank)
                    ?: validationMessage
                    ?: (detail as? String)?.takeIf(String::isNotBlank)
                    ?: "No fue posible completar la solicitud."
                throw ApiFailure(status, code, message)
            }
            if (status == 204) return JSONObject()
            return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }
}
