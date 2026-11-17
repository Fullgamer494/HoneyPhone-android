package dev.honeyphone.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.honeyphone.app.model.*
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil
import kotlin.math.floor

@Composable
internal fun WifiTelemetryPanel(metrics:WifiMetrics?,label:String?) {
    Surface(shape=RoundedCornerShape(24.dp),color=HoneyStyle.Surface.copy(alpha=0.9f),
        border=BorderStroke(1.dp,HoneyStyle.Line),contentColor=HoneyStyle.Ink) {
        Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("DATOS DE LA CONEXIÓN",style=MaterialTheme.typography.labelMedium,color=HoneyStyle.Lime)
            if(label!=null) {
                Text(label,style=MaterialTheme.typography.titleLarge)
                Text("Etiqueta manual de esta prueba",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            }
            if(metrics==null) {
                Text("Este registro no contiene métricas Wi-Fi. Las revisiones antiguas o sin Wi-Fi no las incluyen.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            } else {
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MetricTile("Señal",metrics.rssiDbm?.let{it.toString()+" dBm"},"Potencia recibida",Modifier.weight(1f))
                    MetricTile("Banda",metrics.band,metrics.frequencyMhz?.let{it.toString()+" MHz"} ?: "Frecuencia no disponible",Modifier.weight(1f))
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MetricTile("Recepción / Rx",metrics.rxLinkSpeedMbps?.let{it.toString()+" Mbps"},"Enlace con el router",Modifier.weight(1f))
                    MetricTile("Transmisión / Tx",metrics.txLinkSpeedMbps?.let{it.toString()+" Mbps"},"Enlace con el router",Modifier.weight(1f))
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MetricTile("Canal",metrics.channel?.toString(),"Derivado de frecuencia",Modifier.weight(1f))
                    MetricTile("Enlace",metrics.linkSpeedMbps?.let{it.toString()+" Mbps"},"Valor reportado",Modifier.weight(1f))
                }
                HorizontalDivider(color=HoneyStyle.Line)
                Text(metrics.standardLabel ?: "Estándar Wi-Fi no disponible",style=MaterialTheme.typography.titleMedium)
                Text(when(metrics.internetValidated){true->"Internet validado por Android";false->"Internet no validado por Android";null->"Validación de Internet no disponible"},
                    style=MaterialTheme.typography.bodyMedium,color=if(metrics.internetValidated==true)HoneyStyle.Aqua else HoneyStyle.Muted)
                Text("dBm: más cerca de 0 indica mayor potencia recibida. Mbps: velocidades del enlace Wi-Fi, no una prueba de velocidad de Internet. Un dato ausente no se sustituye por cero.",
                    style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            }
        }
    }
}

@Composable
private fun MetricTile(title:String,value:String?,caption:String,modifier:Modifier) {
    Column(modifier.background(Color.White.copy(alpha=0.025f),RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(title,style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
        Text(value ?: "Sin dato",style=MaterialTheme.typography.titleMedium,color=if(value==null)HoneyStyle.Muted else HoneyStyle.Ink)
        Text(caption,style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
    }
}

@Composable
internal fun HistoryLabelFilter(items:List<ReviewHistoryItem>,selected:String?,onSelect:(String?)->Unit) {
    var expanded by remember{mutableStateOf(false)}
    val labels=items.mapNotNull{it.networkLabel}.distinct().sorted()
    Box {
        OutlinedButton(onClick={expanded=true},shape=RoundedCornerShape(12.dp)) {
            Text("Etiqueta: "+(selected?.ifBlank{"Sin etiqueta"} ?: "Todas")+" ▾")
        }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            DropdownMenuItem(text={Text("Todas las etiquetas")},onClick={onSelect(null);expanded=false})
            DropdownMenuItem(text={Text("Sin etiqueta")},onClick={onSelect("");expanded=false})
            labels.forEach{label->DropdownMenuItem(text={Text(label)},onClick={onSelect(label);expanded=false})}
        }
    }
}

@Composable
internal fun HistoryComparison(items:List<ReviewHistoryItem>,filter:String?) {
    val live=items.filter{it.source==ReviewSource.LIVE&&it.wifiMetrics?.rssiDbm!=null}.sortedBy{it.createdAt}.takeLast(20)
    Surface(shape=RoundedCornerShape(24.dp),color=HoneyStyle.Surface.copy(alpha=0.85f),
        border=BorderStroke(1.dp,HoneyStyle.Line),contentColor=HoneyStyle.Ink) {
        Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("EVOLUCIÓN DE LA SEÑAL",style=MaterialTheme.typography.labelMedium,color=HoneyStyle.Lime)
            Text("Solo lecturas reales / LIVE",style=MaterialTheme.typography.titleMedium)
            Text(if(filter==null)"Se muestran todas las etiquetas; pueden ser conexiones distintas. Filtra una etiqueta manual para acotar la comparación."
                else "Etiqueta manual: "+filter.ifBlank{"sin etiqueta"}+". La app no verifica que sea la misma red.",
                style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            if(live.isEmpty()) {
                Text("Aún no hay lecturas LIVE con potencia de señal para comparar. Conecta Wi-Fi y guarda una revisión.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            } else {
                SignalChart(live)
                Text("Últimas "+live.size+" lecturas con señal · orden cronológico por revisión",style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
                if(live.size==1)Text("Guarda otra lectura para observar un cambio.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                else {
                    val prior=live[live.lastIndex-1];val recent=live.last()
                    val delta=recent.wifiMetrics!!.rssiDbm!!-prior.wifiMetrics!!.rssiDbm!!
                    Text("Cambio: "+(if(delta>0)"+" else "")+delta+" dB",style=MaterialTheme.typography.titleLarge,color=HoneyStyle.Lime)
                    Text(when {delta>0->"Mayor potencia recibida que en la lectura anterior.";delta<0->"Menor potencia recibida que en la lectura anterior.";else->"La potencia recibida no cambió."},
                        style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        CompareReading("Anterior",prior,Modifier.weight(1f))
                        CompareReading("Reciente",recent,Modifier.weight(1f))
                    }
                }
                Text("La señal y la velocidad de enlace no califican la seguridad de la red.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            }
        }
    }
}

@Composable
private fun CompareReading(title:String,item:ReviewHistoryItem,modifier:Modifier) {
    val m=item.wifiMetrics!!
    Column(modifier,verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(title,style=MaterialTheme.typography.labelMedium,color=HoneyStyle.Aqua)
        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(item.createdAt)),style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
        Text(m.rssiDbm.toString()+" dBm",style=MaterialTheme.typography.titleMedium)
        Text((m.band ?: "Banda sin dato")+" · canal "+(m.channel?.toString() ?: "—"),style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
        Text("Rx "+(m.rxLinkSpeedMbps?.let{it.toString()+" Mbps"} ?: "sin dato"),style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
        Text("Tx "+(m.txLinkSpeedMbps?.let{it.toString()+" Mbps"} ?: "sin dato"),style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
    }
}

@Composable
private fun SignalChart(items:List<ReviewHistoryItem>) {
    val values=items.map{it.wifiMetrics!!.rssiDbm!!}
    val low=(floor((values.min()-5)/10.0)*10).toInt().coerceAtLeast(-130)
    val high=(ceil((values.max()+5)/10.0)*10).toInt().coerceAtMost(0).coerceAtLeast(low+10)
    Row(Modifier.fillMaxWidth().height(154.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxHeight(),verticalArrangement=Arrangement.SpaceBetween) {
            listOf(high,(high+low)/2,low).forEach{Text(it.toString(),fontFamily=HoneyStyle.Mono,fontSize=10.sp,color=HoneyStyle.Muted)}
        }
        Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical=7.dp)) {
            listOf(0f,0.5f,1f).forEach{fraction->drawLine(HoneyStyle.Line,Offset(0f,size.height*fraction),Offset(size.width,size.height*fraction),1.dp.toPx())}
            val points=values.mapIndexed { i,v->Offset(if(values.size==1)size.width/2 else size.width*i/(values.size-1),size.height*(high-v)/(high-low)) }
            points.zipWithNext().forEach{(a,b)->drawLine(HoneyStyle.Lime,a,b,2.dp.toPx())}
            points.forEach{drawCircle(HoneyStyle.Background,5.dp.toPx(),it);drawCircle(HoneyStyle.Lime,3.dp.toPx(),it)}
        }
    }
    Text("Potencia recibida (dBm)",style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
}
