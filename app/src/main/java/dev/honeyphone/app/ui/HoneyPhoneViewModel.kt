
package dev.honeyphone.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.honeyphone.app.data.AssetDemoReviewRepository
import dev.honeyphone.app.data.HoneypotEventRepository
import dev.honeyphone.app.data.RoomReviewRepository
import dev.honeyphone.app.core.honeypot.LocalHttpHoneypot
import dev.honeyphone.app.model.AppDestination
import dev.honeyphone.app.model.HoneyPhoneUiState
import dev.honeyphone.app.model.HoneypotPhase
import dev.honeyphone.app.model.HoneypotStatus
import dev.honeyphone.app.model.ReviewHistoryItem
import dev.honeyphone.app.model.ReviewSource
import dev.honeyphone.app.model.SignalPresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import dev.honeyphone.app.model.WifiMetrics
import dev.honeyphone.app.model.validManualLabel
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import dev.honeyphone.app.model.HoneypotSessionInfo
import dev.honeyphone.app.model.HoneypotRemoteEvent
import dev.honeyphone.app.model.ApiConnectionState

class HoneyPhoneViewModel(application: Application) : AndroidViewModel(application) {
    private val demoRepository = AssetDemoReviewRepository(application)
    private val reviews = RoomReviewRepository(application)
    private val honeypotEvents = HoneypotEventRepository(application)
    private val _state = MutableStateFlow(HoneyPhoneUiState(scenarios = demoRepository.scenarios()))
    val state: StateFlow<HoneyPhoneUiState> = _state.asStateFlow()
    private var captureJob: Job? = null
    private var resultParent = AppDestination.HOME
    private var galleryParent = AppDestination.HOME
    private var honeypotServer: LocalHttpHoneypot? = null
    private var honeypotSessionId: String? = null
    private var honeypotSessionStartedAt: String? = null

    fun goBack() {
        if (_state.value.destination == AppDestination.LIVE_REVIEW) { cancelReview(); return }
        if (_state.value.destination == AppDestination.HONEYPOT) stopHoneypot()
        update { copy(destination=when(destination) {
            AppDestination.DETAIL -> AppDestination.RESULT
            AppDestination.RESULT -> resultParent
            AppDestination.DEMO_GALLERY -> galleryParent
            else -> AppDestination.HOME
        }, selectedSignal=null) }
    }

    init {
        refreshApiStatus()
        refreshHoneypotSessions()
        viewModelScope.launch {
            reviews.recoverInterruptedSyncs()
            reviews.observeReviews().collect { rows ->
                val items = rows.map { row ->
                    val review = row.review
                    ReviewHistoryItem(review.checkId, if(review.source=="DEMO") ReviewSource.DEMO else ReviewSource.LIVE,
                        runCatching { dev.honeyphone.app.model.ReviewVerdict.valueOf(review.verdict) }.getOrDefault(dev.honeyphone.app.model.ReviewVerdict.INCOMPLETE),
                        review.createdAt, row.syncStatus, review.origin, review.networkLabel,
                        runCatching { WifiMetrics.fromJson(review.metricsJson?.let(::JSONObject)) }.getOrNull())
                }
                _state.value = _state.value.copy(history=items)
            }
        }
        viewModelScope.launch {
            honeypotEvents.observeRecent().collect { events -> update { copy(honeypotEvents=events) } }
        }
    }

    fun refreshApiStatus() {
        viewModelScope.launch {
            val result=runCatching { reviews.checkApiHealth() }.getOrNull()
            update { copy(apiConnection=if(result?.optString("status")=="ok" && result.optString("database")=="ready") ApiConnectionState.ONLINE else ApiConnectionState.OFFLINE) }
        }
    }

    private fun stopCapture() {
        if(captureJob?.isActive == true) { captureJob?.cancel(); update { copy(working=false) } }
        captureJob=null
    }
    fun setNetworkLabel(value:String) = update { copy(networkLabelDraft=value.take(40)) }
    fun cancelReview() = openHome()
    fun openHome() { stopCapture(); stopHoneypot(); update { copy(destination=AppDestination.HOME, activeTab=AppDestination.HOME, selectedSignal=null) } }
    fun openHistory() { stopCapture(); stopHoneypot(); update { copy(destination=AppDestination.HISTORY, activeTab=AppDestination.HISTORY, selectedSignal=null, message=null) }; refreshHistory() }
    fun openHoneypot() { stopCapture(); update { copy(destination=AppDestination.HONEYPOT, activeTab=AppDestination.HONEYPOT, selectedSignal=null) }; refreshHoneypotSessions() }
    fun refreshHoneypotSessions(selectId:String? = _state.value.selectedHoneypotSessionId) {
        viewModelScope.launch {
            try {
                val page=reviews.honeypotSessions().getJSONArray("items")
                val sessions=(0 until page.length()).map { i -> page.getJSONObject(i).let { s ->
                    HoneypotSessionInfo(s.getString("session_id"),Instant.parse(s.getString("started_at")).toEpochMilli(),if(s.isNull("ended_at"))null else Instant.parse(s.getString("ended_at")).toEpochMilli(),s.getString("state"),s.getInt("listen_port"))
                } }
                val chosen=sessions.firstOrNull { it.sessionId==selectId }?.sessionId ?: sessions.firstOrNull()?.sessionId
                val events=if(chosen==null) emptyList() else reviews.honeypotEvents(chosen).getJSONArray("items").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { e ->
                    HoneypotRemoteEvent(e.getString("event_id"),Instant.parse(e.getString("occurred_at")).toEpochMilli(),e.getString("source_ip_masked"),e.getString("http_method"),e.getString("request_path"),e.optString("user_agent_family").takeIf(String::isNotBlank),e.getInt("response_code"))
                } } }
                update { copy(apiConnection=ApiConnectionState.ONLINE,honeypotSessions=sessions,remoteHoneypotEvents=events,selectedHoneypotSessionId=chosen,honeypotRemoteMessage="Historial remoto actualizado.") }
            } catch (_:Exception) { update { copy(apiConnection=ApiConnectionState.OFFLINE,honeypotRemoteMessage="No se pudo consultar el historial remoto. Los eventos locales siguen disponibles.") } }
        }
    }
    fun openSettings() { stopCapture(); stopHoneypot(); update { copy(destination=AppDestination.SETTINGS, activeTab=AppDestination.SETTINGS, selectedSignal=null) }; refreshDevices() }
    fun refreshDevices() { viewModelScope.launch {
        try {
            val id=reviews.installationId()
            reviews.registerDevice()
            val devices=reviews.listDevices()
            val own=devices.firstOrNull { it.installationId==id }
            update { copy(apiConnection=ApiConnectionState.ONLINE,devices=devices,currentInstallationId=id,
                deviceNameDraft=own?.deviceName.orEmpty(),deviceMessage=if(own?.active==false)
                    "El servidor responde, pero esta instalación está desactivada y no puede enviar datos. Pide al administrador que la habilite."
                else "Dispositivo conectado al servidor.") }
        } catch (_:Exception) {
            update { copy(apiConnection=ApiConnectionState.OFFLINE,deviceMessage="No se pudo conectar con el servidor. La identidad local se conserva.") }
        }
    } }
    fun setDeviceName(value:String) = update { copy(deviceNameDraft=value.take(64)) }
    fun saveDeviceName() { viewModelScope.launch { try { reviews.renameDevice(_state.value.deviceNameDraft.trim()); update { copy(devices=reviews.listDevices(),deviceMessage="Nombre actualizado.") } } catch (_:Exception) { update { copy(deviceMessage="No se pudo actualizar el dispositivo.") } } } }
    fun revokeDevice() { viewModelScope.launch { try { reviews.revokeDevice(); update { copy(devices=reviews.listDevices(),deviceMessage="Dispositivo revocado. No podrá enviar comprobaciones.") } } catch (_:Exception) { update { copy(deviceMessage="No se pudo revocar el dispositivo.") } } } }
    fun openLivePreview() {
        if(_state.value.working || !validManualLabel(_state.value.networkLabelDraft)) return
        stopHoneypot()
        val label=_state.value.networkLabelDraft.trim().takeIf { it.isNotEmpty() }
        resultParent = AppDestination.HOME
        update { copy(destination=AppDestination.LIVE_REVIEW, activeTab=AppDestination.HOME, working=true, message=null, selectedSignal=null) }
        captureJob = viewModelScope.launch {
            try {
                val review=reviews.captureAndSave(label)
                update { copy(destination=AppDestination.RESULT, review=review, localSaved=true, working=false, message="La revisión está guardada en tu teléfono. Puedes enviarla al servidor cuando lo decidas.") }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { update { copy(working=false, message="No se pudo completar y guardar la revisión. Puedes intentarlo otra vez.") } }
        }
    }
    fun openDemoGallery() {
        stopCapture()
        stopHoneypot()
        galleryParent = if (_state.value.destination == AppDestination.SETTINGS) AppDestination.SETTINGS else AppDestination.HOME
        update { copy(destination=AppDestination.DEMO_GALLERY, selectedSignal=null) }
    }
    fun openDemo(checkId: String) {
        resultParent = AppDestination.DEMO_GALLERY
        val review=demoRepository.loadDemo(checkId)
        update { copy(destination=AppDestination.RESULT, review=review,localSaved=false, selectedSignal=null, message="Datos de demostración. No son una lectura del teléfono.") }
    }
    fun openDetail(signal: SignalPresentation) = update { copy(destination=AppDestination.DETAIL, selectedSignal=signal) }

    fun saveDemo() {
        val current=_state.value.review ?: return
        viewModelScope.launch {
            update { copy(working=true,message=null) }
            try {
                val saved=reviews.saveDemo(current)
                update { copy(review=saved,localSaved=true,working=false,message="Ejemplo guardado. Siempre se identificará como una simulación.") }
            } catch(e:Exception) { update { copy(working=false,message=e.message ?: "No se pudo guardar la demostración.") } }
        }
    }
    fun syncCurrent() {
        val id=_state.value.review?.checkId ?: return
        viewModelScope.launch {
            update { copy(working=true,message=null) }
            val message=reviews.sync(id)
            update { copy(working=false,message=message) }
        }
    }
    fun refreshHistory() {
        viewModelScope.launch {
            update { copy(working=true,message=null) }
            try { val message=reviews.refreshRemote(); update { copy(apiConnection=ApiConnectionState.ONLINE,working=false,message=message) } }
            catch(e:Exception) { update { copy(apiConnection=ApiConnectionState.OFFLINE,working=false,message="No pudimos conectar con el servidor. Tus revisiones guardadas siguen disponibles.") } }
        }
    }
    fun openHistoryReview(checkId: String) {
        resultParent = AppDestination.HISTORY
        viewModelScope.launch {
            update { copy(working=true,message=null) }
            try {
                val pending=_state.value.history.find { it.checkId==checkId }?.syncStatus != "synced"
                val review=if(pending)reviews.localReview(checkId) else reviews.openRemoteDetail(checkId)
                update { copy(destination=AppDestination.RESULT,review=review,localSaved=true,working=false,message=if(pending) "Copia local: esta revisión todavía no está enviada." else if(review.source==ReviewSource.DEMO) "Ejemplo leído desde el servidor." else "Detalle leído desde el servidor.") }
            } catch(e:Exception) {
                try { val local=reviews.localReview(checkId); update { copy(destination=AppDestination.RESULT,review=local,localSaved=true,working=false,message="Sin conexión con el servidor. Estás viendo la copia guardada en tu teléfono.") } }
                catch(_:Exception) { update { copy(working=false,message="No se pudo abrir el detalle remoto ni encontrar una copia local.") } }
            }
        }
    }
    fun deleteCurrent() {
        val id=_state.value.review?.checkId ?: return
        viewModelScope.launch {
            update { copy(working=true,message=null) }
            try { val message=reviews.delete(id); update { copy(destination=AppDestination.HISTORY,review=null,localSaved=false,working=false,message=message) } }
            catch(e:Exception) { update { copy(working=false,message="No se pudo borrar del servidor. La copia local se conserva.") } }
        }
    }
    fun exportCurrent() {
        val id=_state.value.review?.checkId ?: return
        viewModelScope.launch {
            try { val json=reviews.exportJson(id); update { copy(exportJson=json,message="El archivo de esta revisión está listo para compartir.") } }
            catch(e:Exception) { update { copy(message=e.message) } }
        }
    }
    fun clearMessage() = update { copy(message=null,exportJson=null) }

    fun startHoneypot() {
        if (_state.value.honeypotStatus.phase in setOf(HoneypotPhase.STARTING, HoneypotPhase.LISTENING)) return
        update { copy(honeypotStatus=HoneypotStatus(HoneypotPhase.STARTING),message=null) }
        viewModelScope.launch {
            val id=UUID.randomUUID().toString()
            val startedAt=Instant.now().toString()
            try {
                withContext(Dispatchers.IO) {
                    reviews.registerDevice()
                    reviews.saveHoneypotSession(JSONObject().put("session_id",id).put("installation_id",reviews.installationId())
                        .put("source","LIVE").put("listen_port",8080).put("started_at",startedAt).put("ended_at",JSONObject.NULL).put("state","RUNNING"))
                }
                honeypotSessionId=id
                honeypotSessionStartedAt=startedAt
                val server = LocalHttpHoneypot(getApplication(),
                    onStatus = { status ->
                        update { copy(honeypotStatus=status) }
                        if(status.phase==HoneypotPhase.ERROR) closeHoneypotSession("INTERRUPTED")
                    },
                    onEvent = { event -> viewModelScope.launch(Dispatchers.IO) {
                        honeypotEvents.record(event.copy(sessionId=id))
                        honeypotEvents.syncPending()
                    } },
                )
                honeypotServer=server
                server.start()
                refreshHoneypotSessions(id)
            } catch (_:Exception) {
                update { copy(honeypotStatus=HoneypotStatus(HoneypotPhase.ERROR,message="No se pudo preparar la sesión con el servidor. Comprueba la conexión y que esta instalación esté habilitada.")) }
            }
        }
    }

    fun stopHoneypot() {
        honeypotServer?.stop()
        honeypotServer = null
        update { copy(honeypotStatus=HoneypotStatus()) }
        closeHoneypotSession("STOPPED")
    }

    private fun closeHoneypotSession(finalState:String) {
        val id=honeypotSessionId ?: return
        val started=honeypotSessionStartedAt ?: return
        honeypotSessionId=null
        honeypotSessionStartedAt=null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { reviews.saveHoneypotSession(JSONObject().put("session_id",id).put("installation_id",reviews.installationId())
                .put("source","LIVE").put("listen_port",8080).put("started_at",started).put("ended_at",Instant.now().toString()).put("state",finalState)) }
                .onSuccess { refreshHoneypotSessions(id) }
                .onFailure { update { copy(honeypotStatus=HoneypotStatus(HoneypotPhase.STOPPED,message="El listener se detuvo, pero no se pudo sincronizar el cierre de sesión.")) } }
        }
    }

    fun clearHoneypotEvents() {
        viewModelScope.launch(Dispatchers.IO) { honeypotEvents.clear() }
    }

    override fun onCleared() {
        honeypotServer?.stop()
        honeypotServer = null
        closeHoneypotSession("INTERRUPTED")
        super.onCleared()
    }

    private inline fun update(transform: HoneyPhoneUiState.() -> HoneyPhoneUiState) { _state.value=_state.value.transform() }
}
