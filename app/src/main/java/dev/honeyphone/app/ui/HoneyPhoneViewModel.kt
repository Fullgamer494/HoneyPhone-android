
package dev.honeyphone.app.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.honeyphone.app.data.AssetDemoReviewRepository
import dev.honeyphone.app.data.HoneypotEventRepository
import dev.honeyphone.app.data.RoomReviewRepository
import dev.honeyphone.app.core.auth.SecureSessionStore
import dev.honeyphone.app.core.network.NetworkApi
import dev.honeyphone.app.core.database.HoneyPhoneDatabase
import dev.honeyphone.app.core.honeypot.HoneypotBackgroundService
import dev.honeyphone.app.core.honeypot.HoneypotRuntime
import dev.honeyphone.app.model.AppDestination
import dev.honeyphone.app.model.HoneyPhoneUiState
import dev.honeyphone.app.model.HoneypotPhase
import dev.honeyphone.app.model.HoneypotStatus
import dev.honeyphone.app.model.HoneypotProfile
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
import java.net.Inet4Address
import java.util.UUID
import dev.honeyphone.app.model.HoneypotSessionInfo
import dev.honeyphone.app.model.HoneypotRemoteEvent
import dev.honeyphone.app.model.ApiConnectionState
import dev.honeyphone.app.BuildConfig

class HoneyPhoneViewModel(application: Application) : AndroidViewModel(application) {
    private val demoRepository = AssetDemoReviewRepository(application)
    private val reviews = RoomReviewRepository(application)
    private val honeypotEvents = HoneypotEventRepository(application)
    private val sessionStore = SecureSessionStore(application)
    private val accountApi = NetworkApi(application)
    private val _state = MutableStateFlow(HoneyPhoneUiState(scenarios = demoRepository.scenarios(), authBusy = sessionStore.accessToken() != null))
    val state: StateFlow<HoneyPhoneUiState> = _state.asStateFlow()
    private var captureJob: Job? = null
    private var localReviewJob: Job? = null
    private var localHoneypotJob: Job? = null
    private var resultParent = AppDestination.HOME
    private var galleryParent = AppDestination.HOME
    private val connectivityManager = application.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { refreshWifiAvailability() }
        override fun onLost(network: Network) { refreshWifiAvailability() }
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) { refreshWifiAvailability() }
    }

    fun goBack() {
        if (_state.value.selectedSignal != null) { update { copy(selectedSignal=null) }; return }
        if (_state.value.destination == AppDestination.LIVE_REVIEW) { cancelReview(); return }
        update { copy(destination=when(destination) {
            AppDestination.DETAIL -> AppDestination.RESULT
            AppDestination.RESULT -> resultParent
            AppDestination.DEMO_GALLERY -> galleryParent
            else -> AppDestination.HOME
        }, selectedSignal=null) }
    }

    init {
        refreshWifiAvailability()
        runCatching { connectivityManager.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),networkCallback) }
        if (sessionStore.accessToken() != null) restoreSession()
        viewModelScope.launch {
            HoneypotRuntime.status.collect { status -> update { copy(honeypotStatus=status) } }
        }
    }

    private fun observeAccountData() {
        localReviewJob?.cancel()
        localHoneypotJob?.cancel()
        localReviewJob=viewModelScope.launch {
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
        }
        localHoneypotJob=viewModelScope.launch {
            honeypotEvents.observeRecent().collect { events -> update { copy(honeypotEvents=events) } }
        }
    }

    private fun restoreSession() {
        viewModelScope.launch {
            try {
                val account = withContext(Dispatchers.IO) { accountApi.account() }
                withContext(Dispatchers.IO) { HoneyPhoneDatabase.bindAccount(getApplication(),account.getString("user_id")) }
                observeAccountData()
                val email = account.optString("email").takeIf(String::isNotBlank)
                update { copy(authenticated=true,accountEmail=email,authMessage=null) }
                withContext(Dispatchers.IO) { reviews.registerDevice() }
                refreshApiStatus()
                refreshHoneypotSessions()
            } catch (e: Exception) {
                if ((e as? dev.honeyphone.app.core.network.ApiFailure)?.status == 401) sessionStore.clear()
                if (sessionStore.accessToken() != null && (e as? dev.honeyphone.app.core.network.ApiFailure)?.status != 401) {
                    sessionStore.accountId()?.let { id -> withContext(Dispatchers.IO) { HoneyPhoneDatabase.bindAccount(getApplication(),id) }; observeAccountData() }
                    update { copy(authenticated=true,accountEmail=null,authMessage="Sin conexión con el servidor. Tus datos locales siguen disponibles.") }
                } else {
                    update { copy(authenticated=false,accountEmail=null,authMessage="La sesión venció. Inicia sesión de nuevo.") }
                }
            }
        }
    }

    fun authenticate(email:String,password:String,createAccount:Boolean) {
        viewModelScope.launch {
            update { copy(authBusy=true,authMessage=null) }
            try {
                val result=withContext(Dispatchers.IO) {
                    if(createAccount) accountApi.registerAccount(email.trim(),password) else accountApi.login(email.trim(),password)
                }
                sessionStore.saveAccessToken(result.getString("access_token"))
                val account=result.getJSONObject("user")
                withContext(Dispatchers.IO) { HoneyPhoneDatabase.bindAccount(getApplication(),account.getString("user_id")) }
                observeAccountData()
                update { copy(authenticated=true,authBusy=false,accountEmail=account.optString("email"),authMessage=null) }
                runCatching { withContext(Dispatchers.IO) { reviews.registerDevice() } }
                refreshApiStatus()
                refreshHoneypotSessions()
            } catch (e:Exception) {
                val token=sessionStore.accessToken()
                val message=(e as? dev.honeyphone.app.core.network.ApiFailure)?.message
                    ?: if(BuildConfig.API_BASE_URL.isBlank()) "La app no tiene configurada la dirección segura de la API."
                    else "No se pudo conectar o crear la cuenta. Revisa tu conexión e inténtalo de nuevo."
                if(token != null && (e as? dev.honeyphone.app.core.network.ApiFailure)?.status != 401) {
                    update { copy(authenticated=true,authBusy=false,authMessage="Acceso guardado. Sin conexión temporal con el servidor.") }
                    return@launch
                }
                sessionStore.clear()
                update { copy(authBusy=false,authenticated=false,accountEmail=null,authMessage=message) }
            }
        }
    }

    fun authenticateWithGoogle(idToken:String) {
        viewModelScope.launch {
            update { copy(authBusy=true,authMessage=null) }
            try {
                val result=withContext(Dispatchers.IO) { accountApi.googleLogin(idToken) }
                sessionStore.saveAccessToken(result.getString("access_token"))
                val account=result.getJSONObject("user")
                withContext(Dispatchers.IO) { HoneyPhoneDatabase.bindAccount(getApplication(),account.getString("user_id")) }
                observeAccountData()
                update { copy(authenticated=true,authBusy=false,accountEmail=account.optString("email"),authMessage=null) }
                runCatching { withContext(Dispatchers.IO) { reviews.registerDevice() } }
                refreshApiStatus()
                refreshHoneypotSessions()
            } catch (e:Exception) {
                val token=sessionStore.accessToken()
                val message=(e as? dev.honeyphone.app.core.network.ApiFailure)?.message
                    ?: "No se pudo completar el acceso con Google. Inténtalo de nuevo."
                if(token != null && (e as? dev.honeyphone.app.core.network.ApiFailure)?.status != 401) {
                    update { copy(authenticated=true,authBusy=false,authMessage="Acceso guardado. Sin conexión temporal con el servidor.") }
                    return@launch
                }
                sessionStore.clear()
                update { copy(authBusy=false,authenticated=false,accountEmail=null,authMessage=message) }
            }
        }
    }

    fun signOut() {
        sessionStore.clear()
        localReviewJob?.cancel(); localHoneypotJob?.cancel()
        update { copy(authenticated=false,accountEmail=null,authMessage=null,history=emptyList(),honeypotEvents=emptyList(),honeypotSessions=emptyList(),remoteHoneypotEvents=emptyList(),selectedHoneypotSessionId=null,apiConnection=ApiConnectionState.OFFLINE) }
    }

    private fun refreshWifiAvailability(): Boolean {
        val available=connectivityManager.allNetworks.any { network ->
            connectivityManager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true &&
                connectivityManager.getLinkProperties(network)?.linkAddresses?.any { it.address is Inet4Address && !it.address.isLoopbackAddress && !it.address.isLinkLocalAddress }==true
        }
        update { copy(wifiConnected=available) }
        return available
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
    fun openHome() { stopCapture(); update { copy(destination=AppDestination.HOME, activeTab=AppDestination.HOME, selectedSignal=null) } }
    fun openHistory() { stopCapture(); update { copy(destination=AppDestination.HISTORY, activeTab=AppDestination.HISTORY, selectedSignal=null, message=null) }; refreshHistory() }
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
    fun openSettings() { stopCapture(); update { copy(destination=AppDestination.SETTINGS, activeTab=AppDestination.SETTINGS, selectedSignal=null) }; refreshDevices() }
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
        galleryParent = if (_state.value.destination == AppDestination.SETTINGS) AppDestination.SETTINGS else AppDestination.HOME
        update { copy(destination=AppDestination.DEMO_GALLERY, selectedSignal=null) }
    }
    fun openDemo(checkId: String) {
        resultParent = AppDestination.DEMO_GALLERY
        val review=demoRepository.loadDemo(checkId)
        update { copy(destination=AppDestination.RESULT, review=review,localSaved=false, selectedSignal=null, message="Datos de demostración. No son una lectura del teléfono.") }
    }
    fun openDetail(signal: SignalPresentation) = update { copy(selectedSignal=signal) }

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

    fun selectHoneypotProfile(profile:HoneypotProfile) {
        if(_state.value.honeypotStatus.phase in setOf(HoneypotPhase.STARTING,HoneypotPhase.LISTENING)) return
        update { copy(selectedHoneypotProfile=profile,honeypotStatus=honeypotStatus.copy(profile=profile)) }
    }

    fun startHoneypot() {
        if (_state.value.honeypotStatus.phase in setOf(HoneypotPhase.STARTING, HoneypotPhase.LISTENING)) return
        val profile=_state.value.selectedHoneypotProfile
        if(!refreshWifiAvailability()) {
            HoneypotRuntime.update(HoneypotStatus(HoneypotPhase.ERROR,message="Conecta este teléfono a una red Wi‑Fi antes de activar el señuelo.",profile=profile))
            return
        }
        val id=UUID.randomUUID().toString()
        val startedAt=Instant.now().toString()
        update { copy(honeypotStatus=HoneypotStatus(HoneypotPhase.STARTING,profile=profile),message=null) }
        runCatching { HoneypotBackgroundService.start(getApplication(),profile,id,startedAt) }
            .onFailure { error -> HoneypotRuntime.update(HoneypotStatus(HoneypotPhase.ERROR,message=error.message ?: "Android no pudo iniciar el servicio del señuelo.",profile=profile)) }
        refreshHoneypotSessions(id)
    }

    fun stopHoneypot() {
        HoneypotBackgroundService.stop(getApplication())
        HoneypotRuntime.update(HoneypotStatus(profile=_state.value.selectedHoneypotProfile))
    }

    fun clearHoneypotEvents() {
        viewModelScope.launch(Dispatchers.IO) { honeypotEvents.clear() }
    }

    override fun onCleared() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        super.onCleared()
    }

    private inline fun update(transform: HoneyPhoneUiState.() -> HoneyPhoneUiState) { _state.value=_state.value.transform() }
}
