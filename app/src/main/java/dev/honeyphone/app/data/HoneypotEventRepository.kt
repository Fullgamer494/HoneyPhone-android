package dev.honeyphone.app.data

import android.content.Context
import dev.honeyphone.app.core.database.HoneyPhoneDatabase
import dev.honeyphone.app.core.database.HoneypotEventEntity
import dev.honeyphone.app.model.HoneypotEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import dev.honeyphone.app.core.network.NetworkApi
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.time.Instant

class HoneypotEventRepository(context: Context) {
    private val dao = HoneyPhoneDatabase.get(context).honeypotDao()

    fun observeRecent(): Flow<List<HoneypotEvent>> = dao.observeRecent().map { rows ->
        rows.map { it.toModel() }
    }

    suspend fun record(event: HoneypotEvent) {
        if (event.method.isBlank() || !event.path.startsWith('/') || event.sourceAddress.isBlank()) return
        dao.insert(event.toEntity())
        dao.retainLatestHundred()
    }

    suspend fun syncPending() {
        val api=NetworkApi()
        dao.pendingUpload().groupBy { it.sessionId!! }.forEach { (sessionId, rows) ->
            val payload=JSONArray()
            rows.forEach { row -> payload.put(JSONObject().put("event_id",row.id)
                .put("occurred_at",Instant.ofEpochMilli(row.occurredAt).toString())
                .put("source_ip_masked",maskAddress(row.sourceAddress))
                .put("http_method",row.method.takeIf { it in setOf("GET","HEAD","POST") } ?: "OTHER")
                .put("request_path",row.path.takeIf { it in setOf("/","/admin","/login","/robots.txt") } ?: "/other")
                .put("user_agent_family",userAgentFamily(row.userAgent))
                .put("content_length",0)
                .put("response_code",row.responseCode)) }
            runCatching { api.postHoneypotEvents(sessionId,payload); dao.markSynced(rows.map { it.id }) }
        }
    }

    suspend fun clear() = dao.deleteAll()

    private fun HoneypotEventEntity.toModel() = HoneypotEvent(id, occurredAt, sourceAddress, method, path, userAgent, sessionId, responseCode, requestDetails)
    private fun HoneypotEvent.toEntity() = HoneypotEventEntity(id, occurredAt, sourceAddress, method, path, userAgent, sessionId, responseCode, requestDetails = requestDetails)

    private fun maskAddress(value:String):String {
        val address=InetAddress.getByName(value)
        val bytes=address.address.copyOf()
        if(bytes.size==4) { bytes[3]=0; return InetAddress.getByAddress(bytes).hostAddress+"/24" }
        for(i in 8 until bytes.size) bytes[i]=0
        return InetAddress.getByAddress(bytes).hostAddress.substringBefore('%')+"/64"
    }

    private fun userAgentFamily(value:String):String = when {
        value.contains("bot",true)||value.contains("scan",true)||value.contains("nmap",true)||value.contains("python-requests",true)||value.contains("curl",true) -> "SCANNER"
        value.contains("mozilla",true)||value.contains("chrome",true)||value.contains("safari",true)||value.contains("firefox",true) -> "BROWSER"
        value.isBlank() -> "UNKNOWN"
        else -> "OTHER"
    }
}
