package dev.honeyphone.app.model

import org.json.JSONObject

data class WifiMetrics(
    val rssiDbm: Int? = null,
    val frequencyMhz: Int? = null,
    val wifiStandard: String? = null,
    val linkSpeedMbps: Int? = null,
    val txLinkSpeedMbps: Int? = null,
    val rxLinkSpeedMbps: Int? = null,
    val internetValidated: Boolean? = null,
) {
    val band: String? get() = when(frequencyMhz) {
        in 2400..2500 -> "2.4 GHz"
        in 4900..5924 -> "5 GHz"
        in 5925..7125 -> "6 GHz"
        in 57000..71000 -> "60 GHz"
        else -> null
    }
    val channel: Int? get() {
        val f=frequencyMhz ?: return null
        return when {
            f==2484 -> 14
            f in 2412..2472 && (f-2407)%5==0 -> (f-2407)/5
            f in 4910..4980 && (f-4000)%5==0 -> (f-4000)/5
            f in 5000..5895 && f%5==0 -> (f-5000)/5
            f==5935 -> 2
            f in 5955..7115 && (f-5950)%5==0 -> (f-5950)/5
            f in 58320..69120 && (f-56160)%2160==0 -> (f-56160)/2160
            else -> null
        }
    }
    val standardLabel: String? get() = when(wifiStandard) {
        "WIFI_4" -> "Wi-Fi 4 / 802.11n"
        "WIFI_5" -> "Wi-Fi 5 / 802.11ac"
        "WIFI_6" -> "Wi-Fi 6 / 802.11ax"
        "WIFI_7" -> "Wi-Fi 7 / 802.11be"
        "WIGIG" -> "WiGig / 802.11ad"
        "LEGACY" -> "802.11a/b/g"
        else -> null
    }
    fun json() = JSONObject().apply {
        rssiDbm?.let{put("rssi_dbm",it)};frequencyMhz?.let{put("frequency_mhz",it)}
        wifiStandard?.let{put("wifi_standard",it)};linkSpeedMbps?.let{put("link_speed_mbps",it)}
        txLinkSpeedMbps?.let{put("tx_link_speed_mbps",it)};rxLinkSpeedMbps?.let{put("rx_link_speed_mbps",it)}
        internetValidated?.let{put("internet_validated",it)}
    }
    companion object {
        fun fromJson(obj: JSONObject?): WifiMetrics? {
            if(obj==null)return null
            fun int(key:String)=if(obj.has(key)&&!obj.isNull(key))obj.getInt(key) else null
            return WifiMetrics(int("rssi_dbm"),int("frequency_mhz"),
                if(obj.has("wifi_standard")&&!obj.isNull("wifi_standard"))obj.getString("wifi_standard") else null,
                int("link_speed_mbps"),int("tx_link_speed_mbps"),int("rx_link_speed_mbps"),
                if(obj.has("internet_validated")&&!obj.isNull("internet_validated"))obj.getBoolean("internet_validated") else null)
        }
    }
}

fun validManualLabel(value: String) = value.length<=40 && value.all { it.isLetterOrDigit() || it in " ._-" }
