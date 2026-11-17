package dev.honeyphone.app.data

import android.content.Context
import dev.honeyphone.app.model.DemoScenario
import dev.honeyphone.app.model.ReviewPresentation
import dev.honeyphone.app.model.ReviewSource
import dev.honeyphone.app.model.ReviewVerdict
import dev.honeyphone.app.model.SignalKind
import dev.honeyphone.app.model.SignalOutcome
import dev.honeyphone.app.model.SignalPresentation
import org.json.JSONObject

class AssetDemoReviewRepository(context: Context) : ReviewRepository {
    private val appContext = context.applicationContext
    private val files = listOf("demo-normal.json", "demo-discrepancia.json", "demo-incompleto.json")

    override fun scenarios(): List<DemoScenario> = files.map { file ->
        val json = read(file)
        require(json.optString("source") == "DEMO") { "$file must be labelled DEMO" }
        DemoScenario(
            checkId = json.getString("check_id"),
            title = when (file) {
                "demo-normal.json" -> "Sin discrepancias"
                "demo-discrepancia.json" -> "Diferencia observada"
                else -> "Revisión incompleta"
            },
            description = when (file) {
                "demo-normal.json" -> "Ejemplo sintético con señales conocidas."
                "demo-discrepancia.json" -> "Muestra una diferencia DNS de demostración."
                else -> "Muestra resultados desconocidos y pruebas pendientes."
            },
        )
    }

    override fun loadDemo(checkId: String): ReviewPresentation {
        val json = files.asSequence().map(::read).first { it.getString("check_id") == checkId }
        require(json.optString("source") == "DEMO") { "LIVE templates are not measurements." }
        val items = json.getJSONArray("signals")
        val signals = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            val kind = SignalKind.valueOf(item.getString("kind"))
            val outcome = SignalOutcome.valueOf(item.getString("outcome"))
            val reason = item.getString("reason_code")
            SignalPresentation(kind, outcome, reason, titleFor(kind), statusFor(outcome), explanationFor(kind, outcome, reason))
        }
        val attention = signals.any { it.outcome == SignalOutcome.ATTENTION }
        val incomplete = json.optString("run_status") == "CANCELLED" || signals.any { it.outcome == SignalOutcome.UNKNOWN }
        val verdict = when {
            incomplete -> ReviewVerdict.INCOMPLETE
            attention -> ReviewVerdict.ATTENTION_REQUIRED
            else -> ReviewVerdict.NO_DISCREPANCIES
        }
        val summary = when (verdict) {
            ReviewVerdict.NO_DISCREPANCIES -> "En este ejemplo no aparecen diferencias en las señales representadas. No es una garantía de seguridad."
            ReviewVerdict.ATTENTION_REQUIRED -> "El ejemplo contiene una señal que requiere atención. Una discrepancia no confirma por sí sola un ataque."
            ReviewVerdict.INCOMPLETE -> "Faltan comprobaciones para completar el ejemplo. Los avisos observados se conservan."
        }
        return ReviewPresentation(json.getString("check_id"), ReviewSource.DEMO, verdict, attention, summary, signals)
    }

    private fun read(file: String): JSONObject = appContext.assets.open(file).bufferedReader().use { JSONObject(it.readText()) }

    private fun titleFor(kind: SignalKind) = when (kind) {
        SignalKind.WIFI_SECURITY -> "Seguridad Wi-Fi"
        SignalKind.CAPTIVE_PORTAL -> "Portal cautivo"
        SignalKind.DNS -> "Resolución DNS"
        SignalKind.HTTPS -> "Conexión HTTPS"
    }

    private fun statusFor(outcome: SignalOutcome) = when (outcome) {
        SignalOutcome.PASS -> "Sin diferencia observada"
        SignalOutcome.ATTENTION -> "Revisar indicador"
        SignalOutcome.UNKNOWN -> "Prueba pendiente"
        SignalOutcome.NOT_APPLICABLE -> "No aplica"
    }

    private fun explanationFor(kind: SignalKind, outcome: SignalOutcome, reason: String): String = when (reason) {
        "OS_SECURITY_REPORTED" -> "Android informó el tipo de seguridad en este escenario sintético."
        "OS_PORTAL_NOT_DETECTED" -> "Android no reportó un portal cautivo en este escenario."
        "DNS_MISMATCH" -> "El resultado DNS de ejemplo no coincide con la referencia sintética. Esto no confirma la causa ni un ataque."
        "DNS_MATCH" -> "La respuesta coincide con la referencia de este escenario sintético."
        "TLS_VALID" -> "El ejemplo muestra una conexión TLS válida al destino configurado."
        "NOT_CONFIGURED" -> "Esta comprobación requiere configuración propia y permanece pendiente."
        "FIELD_UNAVAILABLE" -> "El dato no está disponible en este escenario. No se sustituye por una medición inventada."
        else -> when (outcome) {
            SignalOutcome.PASS -> "El escenario muestra una observación sin diferencias para " + kind.name.lowercase() + "."
            SignalOutcome.ATTENTION -> "El escenario muestra un indicador que requiere revisión."
            SignalOutcome.UNKNOWN -> "No hay datos suficientes para emitir una conclusión."
            SignalOutcome.NOT_APPLICABLE -> "El escenario marca esta comprobación como no aplicable."
        }
    }
}
