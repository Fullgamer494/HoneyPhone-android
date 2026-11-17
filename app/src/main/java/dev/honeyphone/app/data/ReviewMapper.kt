package dev.honeyphone.app.data

import dev.honeyphone.app.core.database.ReviewEntity
import dev.honeyphone.app.core.database.SignalEntity
import dev.honeyphone.app.model.ReviewPresentation
import dev.honeyphone.app.model.ReviewSource
import dev.honeyphone.app.model.ReviewVerdict
import dev.honeyphone.app.model.SignalKind
import dev.honeyphone.app.model.SignalOutcome
import dev.honeyphone.app.model.SignalPresentation
import org.json.JSONObject
import dev.honeyphone.app.model.WifiMetrics

object ReviewMapper {
    fun toPresentation(review: ReviewEntity, signals: List<SignalEntity>): ReviewPresentation {
        val mapped = signals.map { s ->
            val kind = runCatching { SignalKind.valueOf(s.kind) }.getOrDefault(SignalKind.WIFI_SECURITY)
            val outcome = runCatching { SignalOutcome.valueOf(s.outcome) }.getOrDefault(SignalOutcome.UNKNOWN)
            SignalPresentation(kind, outcome, s.reasonCode, title(kind), label(outcome), explain(s.reasonCode, JSONObject(s.evidenceJson)))
        }
        val verdict = if (review.runStatus == "CANCELLED" || mapped.any { it.outcome == SignalOutcome.UNKNOWN }) ReviewVerdict.INCOMPLETE
            else runCatching { ReviewVerdict.valueOf(review.verdict) }.getOrDefault(ReviewVerdict.INCOMPLETE)
        return ReviewPresentation(review.checkId, if (review.source == "DEMO") ReviewSource.DEMO else ReviewSource.LIVE,
            verdict, review.hasAttention, summary(verdict), mapped, review.networkLabel, WifiMetrics.fromJson(review.metricsJson?.let(::JSONObject)))
    }

    private fun title(kind: SignalKind) = when(kind) {
        SignalKind.WIFI_SECURITY -> "Seguridad Wi-Fi"
        SignalKind.CAPTIVE_PORTAL -> "Portal cautivo"
        SignalKind.DNS -> "Resolución DNS"
        SignalKind.HTTPS -> "Conexión HTTPS"
    }
    private fun label(outcome: SignalOutcome) = when(outcome) {
        SignalOutcome.PASS -> "Disponible"
        SignalOutcome.ATTENTION -> "Revisar indicador"
        SignalOutcome.UNKNOWN -> "Pendiente"
        SignalOutcome.NOT_APPLICABLE -> "No aplica"
    }
    private fun summary(v: ReviewVerdict) = when(v) {
        ReviewVerdict.NO_DISCREPANCIES -> "No se observaron diferencias en los indicadores disponibles."
        ReviewVerdict.ATTENTION_REQUIRED -> "Hay al menos un indicador que conviene revisar."
        ReviewVerdict.INCOMPLETE -> "Algunas comprobaciones están pendientes o no disponibles."
    }
    private fun explain(code: String, e: JSONObject) = when(code) {
        "OS_SECURITY_REPORTED" -> "Android informó un tipo de seguridad para la conexión Wi-Fi actual."
        "OPEN_NETWORK" -> "Android informó que esta red Wi-Fi está abierta."
        "LEGACY_SECURITY" -> "Android informó un tipo de seguridad heredado."
        "OS_PORTAL_NOT_DETECTED" -> "Android no marcó la red activa como portal cautivo."
        "PORTAL_DETECTED" -> "Android marcó la red activa como portal cautivo. Completa el acceso desde los ajustes del sistema."
        "NO_WIFI" -> "No hay una conexión Wi-Fi activa disponible para esta lectura."
        "NOT_CONFIGURED" -> "Prueba pendiente de configuración del dominio y destino controlado."
        "DNS_MATCH" -> "La respuesta TXT recibida por el DNS de esta Wi-Fi coincide con la referencia DoH y el valor esperado del proyecto."
        "DNS_MISMATCH" -> "La respuesta TXT de esta Wi-Fi difiere de la referencia verificada. Es una discrepancia; no confirma su causa ni un ataque."
        "REFERENCE_UNAVAILABLE" -> "No pudimos validar la referencia DoH. No se emite una conclusión sobre el DNS de esta Wi-Fi."
        "TLS_VALID" -> "Se completó una conexión HTTPS al destino del proyecto con la validación normal de certificados y nombre de servidor."
        "TLS_INVALID" -> "Falló la validación o negociación TLS con el destino controlado. Esto no confirma un ataque."
        "TIMEOUT" -> "La prueba alcanzó su tiempo límite; no hay una conclusión disponible."
        "NETWORK_ERROR" -> "No se pudo completar la prueba por un error de conexión."
        "NETWORK_CHANGED" -> "La red activa cambió durante la lectura; los datos se descartaron."
        "PERMISSION_DENIED" -> "Android no permitió leer este indicador."
        else -> "Android no proporcionó este campo. No se guardan SSID, BSSID ni ubicación."
    }
}
