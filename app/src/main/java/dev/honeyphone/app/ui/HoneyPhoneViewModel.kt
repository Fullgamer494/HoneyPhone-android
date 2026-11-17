
package dev.honeyphone.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.honeyphone.app.data.AssetDemoReviewRepository
import dev.honeyphone.app.data.RoomReviewRepository
import dev.honeyphone.app.model.AppDestination
import dev.honeyphone.app.model.HoneyPhoneUiState
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

class HoneyPhoneViewModel(application: Application) : AndroidViewModel(application) {
    private val demoRepository = AssetDemoReviewRepository(application)
    private val reviews = RoomReviewRepository(application)
    private val _state = MutableStateFlow(HoneyPhoneUiState(scenarios = demoRepository.scenarios()))
    val state: StateFlow<HoneyPhoneUiState> = _state.asStateFlow()
    private var captureJob: Job? = null
    private var resultParent = AppDestination.HOME
    private var galleryParent = AppDestination.HOME

    fun goBack() {
        if (_state.value.destination == AppDestination.LIVE_REVIEW) { cancelReview(); return }
        update { copy(destination=when(destination) {
            AppDestination.DETAIL -> AppDestination.RESULT
            AppDestination.RESULT -> resultParent
            AppDestination.DEMO_GALLERY -> galleryParent
            else -> AppDestination.HOME
        }, selectedSignal=null) }
    }

    init {
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
    }

    private fun stopCapture() {
        if(captureJob?.isActive == true) { captureJob?.cancel(); update { copy(working=false) } }
        captureJob=null
    }
    fun setNetworkLabel(value:String) = update { copy(networkLabelDraft=value.take(40)) }
    fun cancelReview() = openHome()
    fun openHome() { stopCapture(); update { copy(destination=AppDestination.HOME, selectedSignal=null) } }
    fun openHistory() { stopCapture(); update { copy(destination=AppDestination.HISTORY, selectedSignal=null, message=null) }; refreshHistory() }
    fun openSettings() { stopCapture(); update { copy(destination=AppDestination.SETTINGS, selectedSignal=null) } }
    fun openLivePreview() {
        if(_state.value.working || !validManualLabel(_state.value.networkLabelDraft)) return
        val label=_state.value.networkLabelDraft.trim().takeIf { it.isNotEmpty() }
        resultParent = AppDestination.HOME
        update { copy(destination=AppDestination.LIVE_REVIEW, working=true, message=null, selectedSignal=null) }
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
    fun openDetail(signal: SignalPresentation) = update { copy(destination=AppDestination.DETAIL, selectedSignal=signal) }

    fun saveDemo() {
        val current=_state.value.review ?: return
        viewModelScope.launch {
            update { copy(working=true,message=null) }
            try {
                val saved=reviews.saveDemo(current)
                update { copy(review=saved,localSaved=true,working=false,message="Ejemplo DEMO guardado. Siempre se identificará como una simulación.") }
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
            try { val message=reviews.refreshRemote(); update { copy(working=false,message=message) } }
            catch(e:Exception) { update { copy(working=false,message="No pudimos conectar con el servidor. Tus revisiones guardadas siguen disponibles.") } }
        }
    }
    fun openHistoryReview(checkId: String) {
        resultParent = AppDestination.HISTORY
        viewModelScope.launch {
            update { copy(working=true,message=null) }
            try {
                val pending=_state.value.history.find { it.checkId==checkId }?.syncStatus != "synced"
                val review=if(pending)reviews.localReview(checkId) else reviews.openRemoteDetail(checkId)
                update { copy(destination=AppDestination.RESULT,review=review,localSaved=true,working=false,message=if(pending) "Copia local: esta revisión todavía no está enviada." else if(review.source==ReviewSource.DEMO) "DEMO leída desde el servidor." else "Detalle leído desde el servidor.") }
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
            try { val json=reviews.exportJson(id); update { copy(exportJson=json,message="Exportación JSON lista para compartir.") } }
            catch(e:Exception) { update { copy(message=e.message) } }
        }
    }
    fun clearMessage() = update { copy(message=null,exportJson=null) }

    private inline fun update(transform: HoneyPhoneUiState.() -> HoneyPhoneUiState) { _state.value=_state.value.transform() }
}
