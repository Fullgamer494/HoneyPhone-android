package dev.honeyphone.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.ScanResult
import dev.honeyphone.app.model.WifiMetrics
import android.os.Build
import androidx.core.content.getSystemService
import org.json.JSONObject
import java.util.UUID

data class WifiReading(val signals:List<SignalPayload>,val metrics:WifiMetrics?,val networkChanged:Boolean=false)

class AndroidSignalReader(context: Context) {
    private val connectivity = context.getSystemService<ConnectivityManager>()

    suspend fun read(): WifiReading {
        val started=System.nanoTime()
        val before=connectivity?.activeNetwork
        val caps=before?.let{connectivity?.getNetworkCapabilities(it)}
        val vpn=caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true
        val hasWifi=caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true && !vpn
        fun guarded(kind:String,body:()->SignalPayload)=try{body()}catch(e:SecurityException){unknown(kind,"PERMISSION_DENIED",elapsed(started))}
        val security=if(vpn)unknown("WIFI_SECURITY","FIELD_UNAVAILABLE",elapsed(started))
            else guarded("WIFI_SECURITY"){securitySignal(caps,hasWifi,elapsed(started))}
        val portal=if(vpn)unknown("CAPTIVE_PORTAL","FIELD_UNAVAILABLE",elapsed(started))
            else guarded("CAPTIVE_PORTAL"){portalSignal(caps,hasWifi,elapsed(started))}
        val snapshot=if(hasWifi)metrics(caps) else null
        val probes=ControlledProbes().read(if(hasWifi)before else null)
        val changed=before!=connectivity?.activeNetwork
        val signals=listOf(security,portal)+probes
        return if(changed)WifiReading(signals.map { it.copy(outcome="UNKNOWN",reasonCode="NETWORK_CHANGED",evidence=JSONObject().put("reason","NETWORK_CHANGED")) },null,true)
            else WifiReading(signals,snapshot)
    }

    private fun metrics(caps:NetworkCapabilities?):WifiMetrics {
        val info=caps?.transportInfo as? WifiInfo
        fun number(min:Int,max:Int,read:()->Int)=runCatching{read()}.getOrNull()?.takeIf{it in min..max}
        val standard=if(info!=null&&Build.VERSION.SDK_INT>=30)runCatching{
            when(info.wifiStandard){
                ScanResult.WIFI_STANDARD_LEGACY->"LEGACY"
                ScanResult.WIFI_STANDARD_11N->"WIFI_4"
                ScanResult.WIFI_STANDARD_11AC->"WIFI_5"
                ScanResult.WIFI_STANDARD_11AX->"WIFI_6"
                ScanResult.WIFI_STANDARD_11BE->"WIFI_7"
                ScanResult.WIFI_STANDARD_11AD->"WIGIG"
                else->"UNKNOWN"
            }
        }.getOrNull() else null
        return WifiMetrics(
            info?.let{number(-126,-1){it.rssi}},
            info?.let{number(2400,71000){it.frequency}},
            standard,
            info?.let{number(1,100000){it.linkSpeed}},
            info?.let{number(1,100000){it.txLinkSpeedMbps}},
            info?.let{number(1,100000){it.rxLinkSpeedMbps}},
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
    }

    private fun securitySignal(caps: NetworkCapabilities?, hasWifi: Boolean, ms: Int): SignalPayload {
        if (!hasWifi) return unknown("WIFI_SECURITY", "NO_WIFI", ms)
        if (Build.VERSION.SDK_INT < 31) return unknown("WIFI_SECURITY", "FIELD_UNAVAILABLE", ms)
        val info = caps?.transportInfo as? WifiInfo ?: return unknown("WIFI_SECURITY", "FIELD_UNAVAILABLE", ms)
        val type = info.currentSecurityType
        val name = when(type) {
            WifiInfo.SECURITY_TYPE_OPEN -> "OPEN"
            WifiInfo.SECURITY_TYPE_WEP -> "WEP"
            WifiInfo.SECURITY_TYPE_PSK -> "PSK_UNSPECIFIED"
            WifiInfo.SECURITY_TYPE_SAE -> "SAE"
            WifiInfo.SECURITY_TYPE_OWE -> "OWE"
            WifiInfo.SECURITY_TYPE_EAP -> "EAP"
            WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE -> "EAP_WPA3"
            WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT -> "EAP_WPA3"
            WifiInfo.SECURITY_TYPE_WAPI_PSK, WifiInfo.SECURITY_TYPE_WAPI_CERT -> "UNKNOWN"
            WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2, WifiInfo.SECURITY_TYPE_PASSPOINT_R3 -> "PASSPOINT"
            else -> "UNKNOWN"
        }
        val (outcome, reason) = when(name) {
            "OPEN" -> "ATTENTION" to "OPEN_NETWORK"
            "WEP" -> "ATTENTION" to "LEGACY_SECURITY"
            "UNKNOWN" -> "UNKNOWN" to "FIELD_UNAVAILABLE"
            else -> "PASS" to "OS_SECURITY_REPORTED"
        }
        val e = JSONObject().put("security_type", name)
        if (outcome == "UNKNOWN") e.put("reason", reason) else e.put("reported_by_os", true)
        return SignalPayload(UUID.randomUUID().toString(), "WIFI_SECURITY", outcome, reason, e, ms)
    }

    private fun portalSignal(caps: NetworkCapabilities?, hasWifi: Boolean, ms: Int): SignalPayload {
        if (!hasWifi) return unknown("CAPTIVE_PORTAL", "NO_WIFI", ms)
        val reported = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
            ?: return unknown("CAPTIVE_PORTAL", "FIELD_UNAVAILABLE", ms)
        return if (reported) SignalPayload(UUID.randomUUID().toString(), "CAPTIVE_PORTAL", "UNKNOWN", "PORTAL_DETECTED", JSONObject().put("reported_by_os", true).put("validated", caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)), ms)
        else SignalPayload(UUID.randomUUID().toString(), "CAPTIVE_PORTAL", "PASS", "OS_PORTAL_NOT_DETECTED", JSONObject().put("reported_by_os", true).put("validated", caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)), ms)
    }

    private fun unknown(kind: String, reason: String, ms: Int) = SignalPayload(
        UUID.randomUUID().toString(), kind, "UNKNOWN", reason, JSONObject().put("reason", reason), ms,
    )
    private fun elapsed(start: Long) = ((System.nanoTime() - start) / 1_000_000L).toInt().coerceAtLeast(0)
}
