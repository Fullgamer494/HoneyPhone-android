package dev.honeyphone.app.core.network

import dev.honeyphone.app.core.database.OutboxEntity
import dev.honeyphone.app.core.database.ReviewEntity
import dev.honeyphone.app.core.database.SignalEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import dev.honeyphone.app.model.WifiMetrics

data class SignalPayload(
    val signalId: String,
    val kind: String,
    val outcome: String,
    val reasonCode: String,
    val evidence: JSONObject,
    val elapsedMs: Int? = null,
)

data class CheckPayload(
    val checkId: String,
    val installationId: String,
    val source: String,
    val appVersion: String,
    val androidApiLevel: Int,
    val startedAt: String,
    val finishedAt: String,
    val runStatus: String,
    val signals: List<SignalPayload>,
    val networkLabel: String? = null,
    val wifiMetrics: WifiMetrics? = null,
) {
    fun json(): String = JSONObject().apply {
        put("check_id", checkId); put("installation_id", installationId); put("source", source)
        put("app_version", appVersion); put("android_api_level", androidApiLevel)
        put("started_at", startedAt); put("finished_at", finishedAt); put("run_status", runStatus)
        networkLabel?.let { put("network_label", it) }
        wifiMetrics?.let { put("wifi_metrics", it.json()) }
        put("signals", JSONArray().apply { signals.forEach { s ->
            put(JSONObject().apply {
                put("signal_id", s.signalId); put("kind", s.kind); put("outcome", s.outcome)
                put("reason_code", s.reasonCode); put("evidence", s.evidence)
                if (s.elapsedMs != null) put("elapsed_ms", s.elapsedMs)
            })
        } })
    }.toString()

    fun reviewEntity(verdict: String? = null, hasAttention: Boolean? = null, origin: String = "LOCAL_PENDING") = ReviewEntity(
        checkId, installationId, source, verdict ?: computedVerdict(), hasAttention ?: signals.any { it.outcome == "ATTENTION" },
        runStatus, startedAt, finishedAt, runCatching { Instant.parse(startedAt).toEpochMilli() }.getOrDefault(System.currentTimeMillis()), json(), origin, networkLabel, wifiMetrics?.json()?.toString(),
    )
    fun signalEntities() = signals.map { SignalEntity(checkId, it.signalId, it.kind, it.outcome, it.reasonCode, it.evidence.toString(), it.elapsedMs) }
    fun outboxEntity() = OutboxEntity(checkId, "pending", System.currentTimeMillis())

    private fun computedVerdict(): String = when {
        runStatus == "CANCELLED" || signals.any { it.outcome == "UNKNOWN" } -> "INCOMPLETE"
        signals.any { it.outcome == "ATTENTION" } -> "ATTENTION_REQUIRED"
        else -> "NO_DISCREPANCIES"
    }

    companion object {
        fun fromResponse(json: JSONObject): CheckPayload {
            val a = json.getJSONArray("signals")
            val signals = (0 until a.length()).map { i -> a.getJSONObject(i).let { s ->
                SignalPayload(s.getString("signal_id"), s.getString("kind"), s.getString("outcome"), s.getString("reason_code"), s.optJSONObject("evidence") ?: JSONObject(), s.optInt("elapsed_ms").takeIf { s.has("elapsed_ms") && !s.isNull("elapsed_ms") })
            } }
            return CheckPayload(json.getString("check_id"), json.getString("installation_id"), json.getString("source"),
                json.getString("app_version"), json.getInt("android_api_level"), json.getString("started_at"),
                json.getString("finished_at"), json.getString("run_status"), signals,
                if(json.has("network_label")&&!json.isNull("network_label"))json.getString("network_label") else null,
                WifiMetrics.fromJson(json.optJSONObject("wifi_metrics")))
        }

        fun fromStored(json: String): CheckPayload = fromResponse(JSONObject(json))
        fun freshId(): String = UUID.randomUUID().toString()
        fun now(): String = Instant.now().toString()
    }
}
