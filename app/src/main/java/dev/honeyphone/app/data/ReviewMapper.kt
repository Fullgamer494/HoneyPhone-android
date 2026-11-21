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
        SignalKind.WIFI_SECURITY -> "Tipo de seguridad Wi-Fi"
        SignalKind.CAPTIVE_PORTAL -> "Acceso a la red"
        SignalKind.DNS -> "Resolución DNS"
        SignalKind.HTTPS -> "Conexión HTTPS"
    }
    private fun label(outcome: SignalOutcome) = when(outcome) {
        SignalOutcome.PASS -> "Disponible"
        SignalOutcome.ATTENTION -> "Revisar indicador"
        SignalOutcome.UNKNOWN -> "Sin resultado"
        SignalOutcome.NOT_APPLICABLE -> "No aplica"
    }
    private fun summary(v: ReviewVerdict) = when(v) {
        ReviewVerdict.NO_DISCREPANCIES -> "No se observaron diferencias en los indicadores disponibles."
        ReviewVerdict.ATTENTION_REQUIRED -> "Hay al menos un indicador que conviene revisar."
        ReviewVerdict.INCOMPLETE -> "Algunas comprobaciones están pendientes o no disponibles."
    }
    private fun explain(code: String, e: JSONObject) = when(code) {
        "OS_SECURITY_REPORTED" -> "Android informó el tipo de cifrado de esta red. HoneyPhone solo lo consulta; no modifica la protección del router."
        "OPEN_NETWORK" -> "Android informó que esta red Wi-Fi está abierta."
        "LEGACY_SECURITY" -> "Android informó un tipo de seguridad heredado."
        "OS_PORTAL_NOT_DETECTED" -> "Android no detectó que esta red requiera una página de inicio de sesión."
        "PORTAL_DETECTED" -> "Android detectó que la red requiere una página de acceso. Completa el inicio de sesión desde los ajustes del sistema."
        "NO_WIFI" -> "No hay una conexión Wi-Fi activa disponible para esta lectura."
        "NOT_CONFIGURED" -> "Esta comprobación todavía no está preparada. El equipo del proyecto debe elegir un sitio de prueba y definir qué respuesta esperar. No indica un problema de tu Wi-Fi."
        "DNS_MATCH" -> "La respuesta para el nombre de prueba coincide con la referencia independiente configurada por el proyecto."
        "DNS_MISMATCH" -> "La respuesta para el nombre de prueba no coincide con la referencia del proyecto. Es una diferencia para revisar, no una prueba de ataque."
        "REFERENCE_UNAVAILABLE" -> "No se pudo consultar el servidor de referencia. Por eso no hay un resultado para la resolución de nombres."
        "TLS_VALID" -> "El sitio de prueba presentó un certificado válido para su nombre y se pudo establecer una conexión segura."
        "TLS_INVALID" -> "No se pudo validar el certificado o establecer la conexión segura con el sitio de prueba. Esto no confirma un ataque."
        "TIMEOUT" -> "La comprobación tardó más de lo permitido. Inténtalo de nuevo cuando la conexión sea estable."
        "NETWORK_ERROR" -> "No se pudo completar la comprobación por un problema de conexión."
        "NETWORK_CHANGED" -> "La red activa cambió durante la lectura; los datos se descartaron."
        "PERMISSION_DENIED" -> "Android no permitió leer este indicador."
        else -> "Android no proporcionó este campo. No se guardan SSID, BSSID ni ubicación."
    }
}
