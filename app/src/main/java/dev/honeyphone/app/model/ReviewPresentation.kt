package dev.honeyphone.app.model

enum class ReviewSource { LIVE, DEMO }
enum class SignalKind { WIFI_SECURITY, CAPTIVE_PORTAL, DNS, HTTPS }
enum class SignalOutcome { PASS, ATTENTION, UNKNOWN, NOT_APPLICABLE }
enum class ReviewVerdict { NO_DISCREPANCIES, ATTENTION_REQUIRED, INCOMPLETE }
enum class AppDestination { HOME, LIVE_REVIEW, DEMO_GALLERY, RESULT, DETAIL, HISTORY, SETTINGS }

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
    val scenarios: List<DemoScenario> = emptyList(),
    val review: ReviewPresentation? = null,
    val selectedSignal: SignalPresentation? = null,
    val history: List<ReviewHistoryItem> = emptyList(),
    val working: Boolean = false,
    val message: String? = null,
    val exportJson: String? = null,
    val localSaved: Boolean = false,
    val networkLabelDraft: String = "",
)
