package dev.honeyphone.app.core.honeypot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.honeyphone.app.MainActivity
import dev.honeyphone.app.R
import dev.honeyphone.app.data.HoneypotEventRepository
import dev.honeyphone.app.data.RoomReviewRepository
import dev.honeyphone.app.model.HoneypotPhase
import dev.honeyphone.app.model.HoneypotProfile
import dev.honeyphone.app.model.HoneypotStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.Instant

class HoneypotBackgroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionMutex = Mutex()
    private var listener: LocalHttpHoneypot? = null
    private var profile = HoneypotProfile.LOGIN_PORTAL
    private var sessionId = ""
    private var startedAt = ""
    private var wifiMonitor: ConnectivityManager.NetworkCallback? = null
    private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopListener("STOPPED")
            return START_NOT_STICKY
        }
        if (listener != null) return START_STICKY

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        profile = intent?.getStringExtra(EXTRA_PROFILE)?.let { runCatching { HoneypotProfile.valueOf(it) }.getOrNull() }
            ?: prefs.getString(KEY_PROFILE, null)?.let { runCatching { HoneypotProfile.valueOf(it) }.getOrNull() }
            ?: HoneypotProfile.LOGIN_PORTAL
        sessionId = intent?.getStringExtra(EXTRA_SESSION_ID) ?: prefs.getString(KEY_SESSION_ID, null).orEmpty()
        startedAt = intent?.getStringExtra(EXTRA_STARTED_AT) ?: prefs.getString(KEY_STARTED_AT, null).orEmpty()
        if (sessionId.isBlank()) sessionId = java.util.UUID.randomUUID().toString()
        if (startedAt.isBlank()) startedAt = Instant.now().toString()
        prefs.edit().putBoolean(KEY_ACTIVE, true).putString(KEY_PROFILE, profile.name)
            .putString(KEY_SESSION_ID, sessionId).putString(KEY_STARTED_AT, startedAt).apply()

        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            notification(HoneypotStatus(phase = HoneypotPhase.STARTING, profile = profile)),
        )
        val currentSessionId = sessionId
        val server = LocalHttpHoneypot(
            context = this,
            onStatus = { status ->
                HoneypotRuntime.update(status.copy(profile = profile))
                updateNotification(status)
                if (status.phase == HoneypotPhase.ERROR) {
                    getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ACTIVE, false).apply()
                    scope.launch {
                        persistSession("INTERRUPTED")
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            },
            onEvent = { event ->
                scope.launch {
                    val repository = HoneypotEventRepository(applicationContext)
                    repository.record(event.copy(sessionId = currentSessionId))
                    repository.syncPending()
                }
            },
            profile = profile,
        )
        listener = server
        server.start()
        monitorActiveNetwork()
        scope.launch { openRemoteSession() }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterNetworkMonitor()
        listener?.stop()
        listener = null
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun openRemoteSession() = sessionMutex.withLock {
        try {
            val reviews = RoomReviewRepository(applicationContext)
            reviews.registerDevice()
            reviews.saveHoneypotSession(sessionPayload("RUNNING", null))
            runCatching { HoneypotEventRepository(applicationContext).syncPending() }
        } catch (exception: Exception) {
            val detail = exception.message?.takeIf(String::isNotBlank)
            val current = HoneypotRuntime.status.value
            if (current.phase == HoneypotPhase.LISTENING) {
                val warning = "El señuelo sigue activo en este teléfono, pero no se pudo guardar la sesión en la API${detail?.let { ": $it" }.orEmpty()}"
                HoneypotRuntime.update(current.copy(message = warning))
                updateNotification(current.copy(message = warning))
            }
        }
    }

    private fun stopListener(finalState: String) {
        if (stopping) return
        stopping = true
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ACTIVE, false).apply()
        unregisterNetworkMonitor()
        listener?.stop()
        listener = null
        HoneypotRuntime.update(HoneypotStatus(profile = profile))
        scope.launch {
            persistSession(finalState)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun monitorActiveNetwork() {
        val manager=getSystemService(ConnectivityManager::class.java)
        val callback=object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if(!networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !hasWifiNetwork()) stopListener("INTERRUPTED")
            }
            override fun onLost(network: Network) { if(!hasWifiNetwork()) stopListener("INTERRUPTED") }
        }
        wifiMonitor=callback
        runCatching { manager.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),callback) }
            .onFailure { stopListener("INTERRUPTED") }
    }

    private fun hasWifiNetwork():Boolean = getSystemService(ConnectivityManager::class.java).allNetworks.any {
        getSystemService(ConnectivityManager::class.java).getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true
    }

    private fun unregisterNetworkMonitor() {
        val callback=wifiMonitor ?: return
        wifiMonitor=null
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(callback) }
    }

    private suspend fun persistSession(finalState: String) = sessionMutex.withLock {
        if (sessionId.isBlank()) return@withLock
        val saved = runCatching {
            RoomReviewRepository(applicationContext).saveHoneypotSession(sessionPayload(finalState, Instant.now().toString()))
        }.isSuccess
        if(saved) runCatching { HoneypotEventRepository(applicationContext).syncPending() }
    }

    private fun sessionPayload(state: String, endedAt: String?): JSONObject = JSONObject()
        .put("session_id", sessionId)
        .put("installation_id", RoomReviewRepository(applicationContext).installationId())
        .put("source", "LIVE")
        .put("listen_port", LocalHttpHoneypot.PORT)
        .put("started_at", startedAt)
        .put("ended_at", endedAt ?: JSONObject.NULL)
        .put("state", state)

    private fun updateNotification(status: HoneypotStatus) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(status))
    }

    private fun notification(status: HoneypotStatus): Notification {
        val address = status.address?.removePrefix("http://")?.removePrefix("https://")?.trimEnd('/')
        val title = when (status.phase) {
            HoneypotPhase.LISTENING -> "Señuelo activo"
            HoneypotPhase.STARTING -> "Iniciando señuelo"
            HoneypotPhase.ERROR -> "Señuelo requiere atención"
            HoneypotPhase.STOPPED -> "Señuelo detenido"
        }
        val content = when (status.phase) {
            HoneypotPhase.LISTENING -> address?.let { "Escuchando en $it" }
                ?: "Escuchando en la red Wi-Fi local"
            HoneypotPhase.STARTING -> "Preparando el listener local"
            HoneypotPhase.ERROR -> status.message ?: "No se pudo iniciar el listener local"
            HoneypotPhase.STOPPED -> "El listener local está detenido"
        }
        val expandedContent = when (status.phase) {
            HoneypotPhase.LISTENING -> address?.let { "Escuchando en $it. Puedes detener el señuelo desde esta notificación." }
                ?: "El señuelo está activo en esta red Wi-Fi. Puedes detenerlo desde esta notificación."
            HoneypotPhase.STARTING -> "Preparando el señuelo en la red Wi-Fi local."
            HoneypotPhase.ERROR -> status.message ?: "No se pudo iniciar el listener local. Abre HoneyPhone para revisar el estado."
            HoneypotPhase.STOPPED -> "El señuelo local dejó de escuchar."
        }
        val openIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 2, Intent(this, HoneypotBackgroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_honeyphone)
            .setColor(android.graphics.Color.rgb(245, 183, 0))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentTitle(title)
            .setContentText(content)
            .setSubText("WI-FI LOCAL")
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedContent))
            .setContentIntent(openIntent)
            .setShowWhen(false)
            .setOngoing(status.phase != HoneypotPhase.STOPPED)
            .setOnlyAlertOnce(true)
            .addAction(0, "Detener", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Listener HoneyPhone", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "dev.honeyphone.app.honeypot.START"
        const val ACTION_STOP = "dev.honeyphone.app.honeypot.STOP"
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_STARTED_AT = "started_at"
        private const val PREFS = "honeypot_runtime"
        private const val KEY_ACTIVE = "active"
        private const val KEY_PROFILE = "profile"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_STARTED_AT = "started_at"
        private const val CHANNEL_ID = "honeypot_listener"
        private const val NOTIFICATION_ID = 8080
        const val PORT = 8080

        fun start(context: Context, profile: HoneypotProfile, sessionId: String, startedAt: String) {
            val intent = Intent(context, HoneypotBackgroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PROFILE, profile.name)
                .putExtra(EXTRA_SESSION_ID, sessionId)
                .putExtra(EXTRA_STARTED_AT, startedAt)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, HoneypotBackgroundService::class.java).setAction(ACTION_STOP))
        }
    }
}
