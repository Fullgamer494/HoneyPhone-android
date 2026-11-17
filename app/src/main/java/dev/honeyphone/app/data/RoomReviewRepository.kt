package dev.honeyphone.app.data
import android.content.Context
import android.os.Build
import dev.honeyphone.app.BuildConfig
import dev.honeyphone.app.core.database.HoneyPhoneDatabase
import dev.honeyphone.app.core.network.AndroidSignalReader
import dev.honeyphone.app.core.network.ApiFailure
import dev.honeyphone.app.core.network.CheckPayload
import dev.honeyphone.app.core.network.NetworkApi
import dev.honeyphone.app.core.network.SignalPayload
import dev.honeyphone.app.model.ReviewPresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
class RoomReviewRepository(private val context: Context) {
    private val dao = HoneyPhoneDatabase.get(context).reviewDao()
    private val api = NetworkApi()
    private val syncMutex = Mutex()
    fun observeReviews() = dao.observeAll()
    suspend fun recoverInterruptedSyncs() = withContext(Dispatchers.IO) { dao.recoverInterruptedSyncs() }
    private fun installationId(): String {
        val prefs = context.getSharedPreferences("honeyphone_local", Context.MODE_PRIVATE)
        return prefs.getString("installation_id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("installation_id", it).apply() }
    }
    suspend fun captureAndSave(label: String?): ReviewPresentation = withContext(Dispatchers.IO) {
        val started = Instant.now().toString()
        val reading = AndroidSignalReader(context).read()
        val payload = CheckPayload(CheckPayload.freshId(), installationId(), "LIVE", BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT, started, Instant.now().toString(), if(reading.networkChanged) "CANCELLED" else "COMPLETED", reading.signals, label, reading.metrics)
        currentCoroutineContext().ensureActive()
        dao.saveLocal(payload.reviewEntity(), payload.signalEntities(), payload.outboxEntity())
        ReviewMapper.toPresentation(dao.get(payload.checkId)!!, dao.signals(payload.checkId))
    }
    suspend fun saveDemo(template: ReviewPresentation): ReviewPresentation = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val signals = template.signals.map { s ->
            val evidence = when (s.kind.name) {
                "WIFI_SECURITY" -> if (s.reasonCode in setOf("OPEN_NETWORK", "LEGACY_SECURITY", "OS_SECURITY_REPORTED")) JSONObject().put("security_type", if (s.reasonCode == "OPEN_NETWORK") "OPEN" else if (s.reasonCode == "LEGACY_SECURITY") "WEP" else "SAE").put("reported_by_os", true) else JSONObject().put("reason", s.reasonCode)
                "CAPTIVE_PORTAL" -> JSONObject().put("reason", s.reasonCode).also { if (s.reasonCode == "OS_PORTAL_NOT_DETECTED" || s.reasonCode == "PORTAL_DETECTED") { it.remove("reason"); it.put("reported_by_os", true) } }
                else -> if (s.reasonCode in setOf("NOT_CONFIGURED", "TIMEOUT", "NETWORK_ERROR")) JSONObject().put("reason", s.reasonCode) else JSONObject()
            }
            SignalPayload(CheckPayload.freshId(), s.kind.name, s.outcome.name, s.reasonCode, evidence)
        }
        val payload = CheckPayload(CheckPayload.freshId(), installationId(), "DEMO", BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT, now, now, "COMPLETED", signals)
        dao.saveLocal(payload.reviewEntity(), payload.signalEntities(), payload.outboxEntity())
        ReviewMapper.toPresentation(dao.get(payload.checkId)!!, dao.signals(payload.checkId))
    }
    suspend fun sync(id: String): String = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val review = dao.get(id) ?: throw IllegalStateException("La revisión ya no está guardada.")
            val outbox = dao.outbox(id) ?: return@withLock "Esta revisión ya está sincronizada."
            if (outbox.status == "syncing") return@withLock "La sincronización ya está en curso."
            dao.setStatus(id, "syncing", null)
            try {
                val payload = CheckPayload.fromStored(review.payloadJson)
                val response = api.post(payload)
                val parsed = CheckPayload.fromResponse(response)
                dao.saveRemote(review.copy(verdict=response.optString("verdict"), hasAttention=response.optBoolean("has_attention"), origin="SYNCED"), parsed.signalEntities())
                dao.setStatus(id, "synced", null)
                "Sincronizada. El servidor confirmó la revisión."
            } catch (e: ApiFailure) {
                dao.setStatus(id, "failed", e.message)
                if (e.status == 409) "El servidor tiene otra versión de esta revisión. Conservamos tu copia local para revisarla (conflicto 409)."
                else "El servidor respondió " + e.status + ". La revisión permanece guardada para reintentar."
            } catch (e: Exception) {
                dao.setStatus(id, "failed", e.message ?: "API no disponible")
                "Guardada en tu teléfono. No pudimos conectar con el servidor; puedes reintentar más tarde."
            }
        }
    }
    suspend fun refreshRemote(): String = withContext(Dispatchers.IO) {
        val remote = api.list(installationId())
        remote.forEach { obj ->
            val payload = CheckPayload.fromResponse(obj)
            val existing = dao.get(payload.checkId)
            if (existing == null || existing.origin != "LOCAL_PENDING") dao.saveRemote(payload.reviewEntity(obj.optString("verdict"), obj.optBoolean("has_attention"), "REMOTE"), payload.signalEntities())
        }
        if (remote.size == 1) "Se recuperó 1 revisión del servidor." else "Se recuperaron " + remote.size + " revisiones del servidor."
    }
    suspend fun openRemoteDetail(id: String): ReviewPresentation = withContext(Dispatchers.IO) {
        val json = api.get(id)
        val payload = CheckPayload.fromResponse(json)
        val existing = dao.get(id)
        if (existing == null || existing.origin != "LOCAL_PENDING") dao.saveRemote(payload.reviewEntity(json.optString("verdict"), json.optBoolean("has_attention"), "REMOTE"), payload.signalEntities())
        ReviewMapper.toPresentation(dao.get(id)!!, dao.signals(id))
    }
    suspend fun localReview(id: String): ReviewPresentation = withContext(Dispatchers.IO) {
        val row=dao.get(id) ?: throw IllegalStateException("No se encontró la copia local.")
        ReviewMapper.toPresentation(row,dao.signals(id))
    }
    suspend fun delete(id: String): String = withContext(Dispatchers.IO) {
        val review = dao.get(id) ?: return@withContext "La revisión ya no existe."
        val queued = dao.outbox(id)
        if (queued == null || queued.status != "pending") {
            try { api.delete(id) } catch(e: ApiFailure) { if(e.status != 404) throw e }
        }
        dao.deleteReview(id)
        "Revisión eliminada del teléfono y del servidor cuando estaba sincronizada."
    }
    suspend fun exportJson(id: String): String = withContext(Dispatchers.IO) { dao.get(id)?.payloadJson ?: throw IllegalStateException("No se encontró la revisión.") }
    suspend fun status(id: String): String = withContext(Dispatchers.IO) {
        dao.outbox(id)?.let { if (it.lastError.isNullOrBlank()) it.status else it.status + ": " + it.lastError } ?: "synced"
    }
}
