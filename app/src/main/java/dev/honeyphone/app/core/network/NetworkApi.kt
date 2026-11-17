package dev.honeyphone.app.core.network

import dev.honeyphone.app.BuildConfig
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiFailure(val status: Int, val code: String, message: String) : IOException(message)

class NetworkApi {
    private val base = BuildConfig.API_BASE_URL
    private val connectTimeout = 5000
    private val readTimeout = 8000

    fun post(payload: CheckPayload): JSONObject = request("POST", "network-checks", payload.json())
    fun list(installationId: String): List<JSONObject> {
        val page = request("GET", "network-checks?installation_id=$installationId&limit=50")
        val items = page.getJSONArray("items")
        return (0 until items.length()).map { items.getJSONObject(it) }
    }
    fun get(id: String): JSONObject = request("GET", "network-checks/$id")
    fun delete(id: String) { request("DELETE", "network-checks/$id") }

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
