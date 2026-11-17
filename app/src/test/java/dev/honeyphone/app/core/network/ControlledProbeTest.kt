package dev.honeyphone.app.core.network

import org.junit.Assert.*
import org.junit.Test
import dev.honeyphone.app.model.WifiMetrics

class ControlledProbeTest {
    private fun response(query:ByteArray,text:String):ByteArray {
        val data=text.toByteArray(Charsets.UTF_8)
        val header=query.copyOfRange(0,12).apply{this[2]=0x81.toByte();this[3]=0x80.toByte();this[7]=1}
        return header+query.copyOfRange(12,query.size)+byteArrayOf(0xc0.toByte(),12,0,16,0,1,0,0,0,60,0,(data.size+1).toByte(),data.size.toByte())+data
    }
    @Test fun txtAcceptsCompressedAnswerAndConcatenatesSegments() {
        val q=DnsTxtWire.query("_hp.example.test")
        assertEquals(setOf("hp-v1"),DnsTxtWire.parse(response(q,"hp-v1"),q))
        val split=response(q,"hp-v1").dropLast(6).toByteArray()+byteArrayOf(2,'h'.code.toByte(),'p'.code.toByte(),3,'-'.code.toByte(),'v'.code.toByte(),'1'.code.toByte())
        split[split.size-8]=7 // RDLENGTH low byte: two strings with 2+3 bytes.
        assertEquals(setOf("hp-v1"),DnsTxtWire.parse(split,q))
    }
    @Test fun malformedDnsDoesNotBecomeAMismatch() {
        val q=DnsTxtWire.query("_hp.example.test")
        assertThrows(IllegalArgumentException::class.java){DnsTxtWire.parse(ByteArray(5),q)}
        val truncated=response(q,"hp-v1").dropLast(1).toByteArray()
        assertThrows(IllegalArgumentException::class.java){DnsTxtWire.parse(truncated,q)}
        val loop=response(q,"hp-v1");loop[12]=0xc0.toByte();loop[13]=12
        assertThrows(IllegalArgumentException::class.java){DnsTxtWire.parse(loop,q)}
        val wrongId=response(q,"hp-v1");wrongId[0]=(wrongId[0].toInt()xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java){DnsTxtWire.parse(wrongId,q)}
    }
    @Test fun onlyExplicitHttpsTargetsWithoutCredentialsAreAccepted() {
        assertTrue(ProbeConfig.validUrl("https://probe.example.test/health"))
        for(url in listOf("http://probe.example.test/health","https://user:pass@probe.example.test/","https://127.0.0.1/","https://probe.example.test/?secret=value","https://probe.example.test/#secret","https://probe.example.test:8443/"))assertFalse(url,ProbeConfig.validUrl(url))
        assertThrows(IllegalArgumentException::class.java){ProbeConfig("","token","https://doh.example.test/dns-query","https://probe.example.test/health","v1")}
    }
    @Test fun frequencyConversionsIncludeChannel14AndSixGhzExceptions() {
        assertEquals("2.4 GHz",WifiMetrics(frequencyMhz=2484).band)
        assertEquals(14,WifiMetrics(frequencyMhz=2484).channel)
        assertEquals(36,WifiMetrics(frequencyMhz=5180).channel)
        assertEquals(2,WifiMetrics(frequencyMhz=5935).channel)
        assertEquals(1,WifiMetrics(frequencyMhz=5955).channel)
        assertNull(WifiMetrics().channel)
        assertNull(WifiMetrics(frequencyMhz=2501).band)
    }
}
