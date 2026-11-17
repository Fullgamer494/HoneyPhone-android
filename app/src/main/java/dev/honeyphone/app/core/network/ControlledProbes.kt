package dev.honeyphone.app.core.network

import android.net.DnsResolver
import android.net.Network
import android.os.CancellationSignal
import dev.honeyphone.app.BuildConfig
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URI
import java.net.SocketTimeoutException
import java.util.concurrent.Executor
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ProbeConfig(val dnsName:String,val expectedTxt:String,val dohUrl:String,val httpsUrl:String,val version:String) {
    init {
        require(DnsTxtWire.validName(dnsName))
        require(expectedTxt.matches(Regex("[A-Za-z0-9._:-]{1,128}")))
        require(version.matches(Regex("[A-Za-z0-9._-]{1,32}")))
        require(validUrl(dohUrl)&&validUrl(httpsUrl))
    }
    companion object {
        fun validUrl(value:String):Boolean = runCatching {
            val u=URI(value)
            u.scheme=="https" && !u.host.isNullOrBlank() && u.host.contains('.') &&
                u.host.any{it.isLetter()} && !u.host.endsWith(".localhost") &&
                u.host!="localhost" && u.port in listOf(-1,443) &&
                u.userInfo==null && u.rawQuery==null && u.rawFragment==null
        }.getOrDefault(false)
        fun load():ProbeConfig?=runCatching { ProbeConfig(BuildConfig.PROBE_DNS_NAME,BuildConfig.PROBE_EXPECTED_TXT,
            BuildConfig.PROBE_DOH_URL,BuildConfig.PROBE_HTTPS_URL,BuildConfig.PROBE_CONFIG_VERSION) }.getOrNull()
    }
}

class ControlledProbes(private val config:ProbeConfig?=ProbeConfig.load()) {
    private val executor=Executor { command->command.run() }
    private fun signal(kind:String,outcome:String,reason:String,started:Long,validated:Boolean?=null)=SignalPayload(
        CheckPayload.freshId(),kind,outcome,reason,JSONObject().apply{
            config?.let{put("probe_config_version",it.version)}
            validated?.let{put("validated",it)}
            if(reason=="NOT_CONFIGURED")put("reason",reason)
        },((System.nanoTime()-started)/1_000_000).toInt().coerceIn(0,300000))

    suspend fun read(network:Network?):List<SignalPayload> = coroutineScope {
        if(config==null)return@coroutineScope listOf("DNS","HTTPS").map{signal(it,"UNKNOWN","NOT_CONFIGURED",System.nanoTime())}
        if(network==null)return@coroutineScope listOf("DNS","HTTPS").map{signal(it,"UNKNOWN","NETWORK_ERROR",System.nanoTime())}
        val dns=async{dns(network)}
        val https=async(Dispatchers.IO){https(network)}
        listOf(dns.await(),https.await())
    }

    private data class Attempt(val bytes:ByteArray?=null,val error:String?=null)
    private suspend fun dns(network:Network):SignalPayload = coroutineScope {
        val start=System.nanoTime();val c=config!!
        val query=DnsTxtWire.query(c.dnsName)
        val system=async {
            try { Attempt(withTimeout(5000){systemDns(network,query)}) }
            catch(e:TimeoutCancellationException){Attempt(error="TIMEOUT")}
            catch(e:CancellationException){throw e}
            catch(e:Exception){Attempt(error="NETWORK_ERROR")}
        }
        val reference=async(Dispatchers.IO) {
            try { doh(network,query) } catch(e:CancellationException){throw e} catch(e:Exception){null}
        }
        val expected=reference.await()?.let{runCatching{DnsTxtWire.parse(it,query)}.getOrNull()}
        if(expected==null || expected!=setOf(c.expectedTxt)) {
            system.cancel()
            return@coroutineScope signal("DNS","UNKNOWN","REFERENCE_UNAVAILABLE",start)
        }
        val actual=system.await()
        if(actual.bytes==null)return@coroutineScope signal("DNS","UNKNOWN",actual.error ?: "NETWORK_ERROR",start)
        val observed=runCatching{DnsTxtWire.parse(actual.bytes,query)}.getOrNull()
            ?: return@coroutineScope signal("DNS","UNKNOWN","NETWORK_ERROR",start)
        val match=observed==expected
        signal("DNS",if(match)"PASS" else "ATTENTION",if(match)"DNS_MATCH" else "DNS_MISMATCH",start,match)
    }
    private suspend fun systemDns(network:Network,query:ByteArray):ByteArray = suspendCancellableCoroutine { cont->
        val cancel=CancellationSignal()
        cont.invokeOnCancellation { cancel.cancel() }
        try {
            DnsResolver.getInstance().rawQuery(network,query,DnsResolver.FLAG_NO_CACHE_LOOKUP or DnsResolver.FLAG_NO_CACHE_STORE,
                executor,cancel,object:DnsResolver.Callback<ByteArray> {
                    override fun onAnswer(answer:ByteArray,rcode:Int){if(cont.isActive)cont.resume(answer)}
                    override fun onError(error:DnsResolver.DnsException){if(cont.isActive)cont.resumeWithException(error)}
                })
        }catch(e:Exception){if(cont.isActive)cont.resumeWithException(e)}
    }
    private fun connection(network:Network,url:String)=(network.openConnection(java.net.URL(url)) as HttpsURLConnection).apply{
        connectTimeout=4000;readTimeout=4000;instanceFollowRedirects=false;useCaches=false
        setRequestProperty("User-Agent","HoneyPhone-Probe/"+BuildConfig.VERSION_NAME)
    }
    private fun doh(network:Network,query:ByteArray):ByteArray? {
        val connection=connection(network,config!!.dohUrl)
        return try {
            connection.requestMethod="POST";connection.doOutput=true
            connection.setRequestProperty("Content-Type","application/dns-message")
            connection.setRequestProperty("Accept","application/dns-message")
            connection.setFixedLengthStreamingMode(query.size)
            connection.outputStream.use{it.write(query)}
            if(connection.responseCode!=200 || !connection.contentType.orEmpty().substringBefore(';').equals("application/dns-message",true))null
            else connection.inputStream.use { input->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(1024)
                while(out.size()<=8192){val n=input.read(buffer,0,minOf(buffer.size,8193-out.size()));if(n<0)break;out.write(buffer,0,n)}
                out.toByteArray().takeIf{it.size<=8192}
            }
        }finally{connection.disconnect()}
    }
    private fun https(network:Network):SignalPayload {
        val start=System.nanoTime()
        var conn:HttpsURLConnection?=null
        return try {
            conn=connection(network,config!!.httpsUrl);conn.requestMethod="GET"
            // TLS and hostname validation remain the platform defaults. No response body is retained.
            val status=conn.responseCode
            if(status in 200..299)signal("HTTPS","PASS","TLS_VALID",start,true)
            else signal("HTTPS","UNKNOWN","NETWORK_ERROR",start)
        }catch(e:SocketTimeoutException){signal("HTTPS","UNKNOWN","TIMEOUT",start)}
        catch(e:SSLHandshakeException){signal("HTTPS","ATTENTION","TLS_INVALID",start,false)}
        catch(e:SSLPeerUnverifiedException){signal("HTTPS","ATTENTION","TLS_INVALID",start,false)}
        catch(e:Exception){signal("HTTPS","UNKNOWN","NETWORK_ERROR",start)}
        finally{conn?.disconnect()}
    }
}
