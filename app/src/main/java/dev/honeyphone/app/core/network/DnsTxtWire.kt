package dev.honeyphone.app.core.network

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.nio.charset.StandardCharsets

/** Bounded TXT-only DNS wire codec. Raw responses never enter persistence. */
object DnsTxtWire {
    private val random=SecureRandom()
    fun query(name:String):ByteArray {
        require(validName(name))
        val out=ByteArrayOutputStream()
        fun word(n:Int){out.write(n shr 8);out.write(n and 255)}
        word(random.nextInt(65536));word(0x0100);word(1);word(0);word(0);word(0)
        name.trimEnd('.').split('.').forEach { val b=it.toByteArray(StandardCharsets.US_ASCII);out.write(b.size);out.write(b) }
        out.write(0);word(16);word(1)
        return out.toByteArray()
    }
    fun validName(name:String)=name.trimEnd('.').length in 1..253 &&
        name.trimEnd('.').split('.').all { it.length in 1..63 && it.all { c->c in 'a'..'z'||c in 'A'..'Z'||c in '0'..'9'||c=='_'||c=='-' } }

    fun parse(response:ByteArray,query:ByteArray):Set<String> {
        require(response.size in 12..8192 && query.size>=17)
        fun u16(data:ByteArray,p:Int):Int { require(p>=0&&p+1<data.size);return ((data[p].toInt()and 255)shl 8)or(data[p+1].toInt()and 255) }
        require(u16(response,0)==u16(query,0))
        val flags=u16(response,2)
        require(flags and 0x8000!=0 && flags and 0x7800==0 && flags and 0x0200==0)
        val rcode=flags and 15
        require(rcode==0 || rcode==3)
        require(u16(response,4)==1)
        val expected=name(query,12)
        val question=name(response,12)
        require(question.first.equals(expected.first,true))
        require(u16(response,question.second)==16 && u16(response,question.second+2)==1)
        var pos=question.second+4
        val count=u16(response,6);require(count<=32)
        val texts=mutableSetOf<String>()
        repeat(count) {
            val owner=name(response,pos);pos=owner.second
            require(pos+10<=response.size)
            val type=u16(response,pos);val cls=u16(response,pos+2);val len=u16(response,pos+8)
            pos+=10;val end=pos+len;require(end<=response.size)
            if(type==16 && cls==1 && owner.first.equals(expected.first,true)) {
                val bytes=ByteArrayOutputStream()
                while(pos<end){val n=response[pos++].toInt()and 255;require(pos+n<=end);bytes.write(response,pos,n);pos+=n}
                require(bytes.size()<=512)
                texts+=bytes.toString("UTF-8");require(texts.size<=16)
            }
            pos=end
        }
        return if(rcode==3)emptySet() else texts
    }
    private fun name(data:ByteArray,start:Int):Pair<String,Int> {
        var pos=start;var next=-1;val labels=mutableListOf<String>();val visited=mutableSetOf<Int>()
        while(true) {
            require(pos in data.indices && visited.add(pos) && visited.size<=128)
            val n=data[pos].toInt()and 255
            if(n==0){if(next<0)next=pos+1;break}
            if(n and 0xC0==0xC0){
                require(pos+1<data.size)
                if(next<0)next=pos+2
                pos=((n and 63)shl 8)or(data[pos+1].toInt()and 255)
            } else {
                require(n in 1..63 && pos+1+n<=data.size)
                val bytes=data.copyOfRange(pos+1,pos+1+n);require(bytes.all{it.toInt() in 33..126})
                labels+=String(bytes,StandardCharsets.US_ASCII);require(labels.sumOf{it.length+1}<=254)
                pos+=n+1
            }
        }
        return labels.joinToString(".") to next
    }
}
