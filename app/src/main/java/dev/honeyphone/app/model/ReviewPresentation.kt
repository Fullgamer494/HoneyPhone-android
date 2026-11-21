package dev.honeyphone.app.model

enum class ReviewSource { LIVE, DEMO }
enum class SignalKind { WIFI_SECURITY, CAPTIVE_PORTAL, DNS, HTTPS }
enum class SignalOutcome { PASS, ATTENTION, UNKNOWN, NOT_APPLICABLE }
enum class ReviewVerdict { NO_DISCREPANCIES, ATTENTION_REQUIRED, INCOMPLETE }
enum class AppDestination { HOME, LIVE_REVIEW, DEMO_GALLERY, RESULT, DETAIL, HISTORY, HONEYPOT, SETTINGS }
enum class HoneypotPhase { STOPPED, STARTING, LISTENING, ERROR }
enum class ApiConnectionState { CHECKING, ONLINE, OFFLINE }

data class HoneypotStatus(
    val phase: HoneypotPhase = HoneypotPhase.STOPPED,
    val address: String? = null,
    val message: String? = null,
    val serviceName: String? = null,
    val serviceDiscoveryReady: Boolean? = null,
)

data class HoneypotEvent(
    val id: String,
    val occurredAt: Long,
    val sourceAddress: String,
    val method: String,
    val path: String,
    val userAgent: String,
    val sessionId: String? = null,
    val responseCode: Int = 404,
)
data class DeviceInfo(val installationId: String, val deviceName: String?, val appVersion: String, val active: Boolean, val lastSeenAt: String)
data class HoneypotSessionInfo(val sessionId: String, val startedAt: Long, val endedAt: Long?, val state: String, val listenPort: Int)
data class HoneypotRemoteEvent(val eventId: String, val occurredAt: Long, val sourceIpMasked: String, val method: String, val path: String, val userAgentFamily: String?, val responseCode: Int)

data class SignalPresentation(
    val kind: SignalKind,
    val outcome: SignalOutcome,
    val reasonCode: String,
    val title: String,
    val statusLabel: String,
    val explanation: String,
)
data class ReviewPresentation(
    val checkId: String,
    val source: ReviewSource,
    val verdict: ReviewVerdict,
    val hasAttention: Boolean,
    val summary: String,
    val signals: List<SignalPresentation>,
    val networkLabel: String? = null,
    val wifiMetrics: WifiMetrics? = null,
)
data class DemoScenario(val checkId: String, val title: String, val description: String)
data class ReviewHistoryItem(val checkId: String, val source: ReviewSource, val verdict: ReviewVerdict, val createdAt: Long, val syncStatus: String, val origin: String, val networkLabel: String? = null, val wifiMetrics: WifiMetrics? = null)
data class HoneyPhoneUiState(
    val destination: AppDestination = AppDestination.HOME,
    val activeTab: AppDestination = AppDestination.HOME,
    val scenarios: List<DemoScenario> = emptyList(),
    val review: ReviewPresentation? = null,
    val selectedSignal: SignalPresentation? = null,
    val history: List<ReviewHistoryItem> = emptyList(),
    val working: Boolean = false,
    val message: String? = null,
    val exportJson: String? = null,
    val localSaved: Boolean = false,
    val networkLabelDraft: String = "",
    val honeypotStatus: HoneypotStatus = HoneypotStatus(),
    val honeypotEvents: List<HoneypotEvent> = emptyList(),
    val devices: List<DeviceInfo> = emptyList(),
    val deviceNameDraft: String = "",
    val deviceMessage: String? = null,
    val currentInstallationId: String = "",
    val honeypotSessions: List<HoneypotSessionInfo> = emptyList(),
    val remoteHoneypotEvents: List<HoneypotRemoteEvent> = emptyList(),
    val selectedHoneypotSessionId: String? = null,
    val honeypotRemoteMessage: String? = null,
    val apiConnection: ApiConnectionState = ApiConnectionState.CHECKING,
)
