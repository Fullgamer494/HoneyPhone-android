package dev.honeyphone.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.honeyphone.app.model.*
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoneyPhoneApp(viewModel: HoneyPhoneViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.exportJson) {
        val json = state.exportJson ?: return@LaunchedEffect
        val file = File(context.cacheDir, "exports/revision-" + (state.review?.checkId ?: "honeyphone") + ".json")
        file.parentFile?.mkdirs()
        file.writeText(json)
        val uri = FileProvider.getUriForFile(context, "dev.honeyphone.app.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"; putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartir revisión HoneyPhone"))
        viewModel.clearMessage()
    }
    val canGoBack = state.destination in setOf(AppDestination.LIVE_REVIEW, AppDestination.DEMO_GALLERY, AppDestination.RESULT, AppDestination.DETAIL)
    val apiIndicator = when(state.apiConnection) {
        ApiConnectionState.CHECKING -> "API · ?" to HoneyStyle.Muted
        ApiConnectionState.ONLINE -> "API · OK" to HoneyStyle.Lime
        ApiConnectionState.OFFLINE -> "API · OFFLINE" to HoneyStyle.Amber
    }
    BackHandler(enabled=canGoBack) { viewModel.goBack() }
    Box(Modifier.fillMaxSize()) {
        TechBackground()
        Scaffold(
            containerColor=Color.Transparent, contentColor=HoneyStyle.Ink,
            topBar={
                TopAppBar(
                    title={
                        Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            PixelMark(Modifier.size(28.dp))
                            Column {
                                Text("honeyphone", style=MaterialTheme.typography.titleMedium)
                                Text("INDICADORES WI-FI", style=MaterialTheme.typography.labelSmall, color=HoneyStyle.Muted, letterSpacing=1.sp)
                            }
                        }
                    },
                    navigationIcon={
                        if(canGoBack) IconButton(onClick=viewModel::goBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription="Volver")
                        }
                    },
                    actions={ StatusPill(apiIndicator.first, apiIndicator.second); Spacer(Modifier.width(16.dp)) },
                    colors=TopAppBarDefaults.topAppBarColors(containerColor=HoneyStyle.Background.copy(alpha=0.96f)),
                )
            },
            bottomBar={
                NavigationBar(containerColor=HoneyStyle.Background.copy(alpha=0.98f), tonalElevation=0.dp,
                    modifier=Modifier.border(BorderStroke(1.dp,HoneyStyle.Line.copy(alpha=0.5f)))) {
                    listOf(Triple("Revisar",Icons.Default.Wifi,AppDestination.HOME),
                        Triple("Señuelo",Icons.Default.Security,AppDestination.HONEYPOT),
                        Triple("Historial",Icons.Default.History,AppDestination.HISTORY),
                        Triple("Guía",Icons.Default.Info,AppDestination.SETTINGS)).forEach { (label,icon,destination) ->
                        val selected=if(destination==AppDestination.HOME) state.destination !in setOf(AppDestination.HISTORY,AppDestination.SETTINGS,AppDestination.HONEYPOT) else state.destination==destination
                        NavigationBarItem(selected=selected,onClick={
                            when(destination) {
                                AppDestination.HISTORY -> viewModel.openHistory()
                                AppDestination.HONEYPOT -> viewModel.openHoneypot()
                                AppDestination.SETTINGS -> viewModel.openSettings()
                                else -> viewModel.openHome()
                            }
                        },icon={Icon(icon,contentDescription=null)},label={Text(label)},
                            colors=NavigationBarItemDefaults.colors(selectedIconColor=HoneyStyle.Lime,
                                selectedTextColor=HoneyStyle.Lime, indicatorColor=HoneyStyle.Lime.copy(alpha=0.1f),
                                unselectedIconColor=HoneyStyle.Muted,unselectedTextColor=HoneyStyle.Muted))
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when(state.destination) {
                    AppDestination.HOME -> HomeScreen(state.networkLabelDraft,state.apiConnection,state.history,viewModel::setNetworkLabel,viewModel::openLivePreview,viewModel::openDemoGallery,viewModel::refreshApiStatus)
                    AppDestination.LIVE_REVIEW -> LiveScreen(state.working,state.message,viewModel::cancelReview)
                    AppDestination.DEMO_GALLERY -> DemoScreen(state.scenarios,viewModel::openDemo)
                    AppDestination.RESULT -> state.review?.let {
                        ResultScreen(it,state.localSaved,state.working,state.message,
                            state.history.find { row->row.checkId==it.checkId }?.syncStatus ?: "pending",
                            viewModel::openDetail,viewModel::saveDemo,viewModel::syncCurrent,viewModel::exportCurrent,viewModel::deleteCurrent)
                    }
                    AppDestination.DETAIL -> state.selectedSignal?.let { DetailScreen(it,state.review?.source ?: ReviewSource.LIVE) }
                    AppDestination.HISTORY -> HistoryScreen(state.history,state.working,state.message,viewModel::refreshHistory,viewModel::openHistoryReview,viewModel::openDemoGallery)
                    AppDestination.HONEYPOT -> HoneypotScreen(state.honeypotStatus,state.honeypotEvents,state.honeypotSessions,state.remoteHoneypotEvents,state.selectedHoneypotSessionId,state.honeypotRemoteMessage,
                        viewModel::startHoneypot,viewModel::stopHoneypot,viewModel::clearHoneypotEvents,viewModel::refreshHoneypotSessions)
                    AppDestination.SETTINGS -> GuideScreen(state.devices,state.deviceNameDraft,state.deviceMessage,state.currentInstallationId,viewModel::setDeviceName,viewModel::saveDeviceName,viewModel::revokeDevice,viewModel::refreshDevices,viewModel::openDemoGallery)
                }
            }
        }
    }
}

@Composable
private fun TechBackground() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(HoneyStyle.Background)
        drawRect(Brush.radialGradient(listOf(HoneyStyle.Lime.copy(alpha=0.065f),Color.Transparent),
            center=Offset(size.width*0.95f,size.height*0.18f),radius=size.width*0.9f))
        drawRect(Brush.radialGradient(listOf(HoneyStyle.Aqua.copy(alpha=0.035f),Color.Transparent),
            center=Offset(0f,size.height*0.8f),radius=size.width))
        val step=32.dp.toPx()
        var x=0f
        while(x<size.width){drawLine(HoneyStyle.Line.copy(alpha=0.12f),Offset(x,0f),Offset(x,size.height),0.5.dp.toPx());x+=step}
        var y=0f
        while(y<size.height){drawLine(HoneyStyle.Line.copy(alpha=0.12f),Offset(0f,y),Offset(size.width,y),0.5.dp.toPx());y+=step}
    }
}

@Composable
private fun PixelMark(modifier: Modifier=Modifier) {
    Canvas(modifier) {
        val pattern=listOf("1100011","1100011","1100011","1111111","1100011","1100011","1100011")
        val unit=size.width/8f
        pattern.forEachIndexed { y,row-> row.forEachIndexed { x,c->if(c=='1') drawRect(HoneyStyle.Lime,Offset(x*unit,y*unit),Size(unit*0.72f,unit*0.72f)) } }
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=18.dp),
        verticalArrangement=Arrangement.spacedBy(18.dp),content=content)
}

@Composable
private fun Glass(modifier: Modifier=Modifier, accent: Color=HoneyStyle.Line, glow: Boolean=false, content: @Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),
        color=HoneyStyle.Surface.copy(alpha=0.82f),contentColor=HoneyStyle.Ink,
        border=BorderStroke(1.dp,accent.copy(alpha=0.65f))) {
        Column(Modifier.then(if(glow) Modifier.background(Brush.verticalGradient(listOf(Color.Transparent,Color.Transparent,HoneyStyle.Lime.copy(alpha=0.13f)))) else Modifier).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}

@Composable
private fun Eyebrow(text: String,color: Color=HoneyStyle.Lime) {
    Text(text.uppercase(),style=MaterialTheme.typography.labelMedium,color=color,letterSpacing=1.5.sp)
}
@Composable
private fun Heading(label: String,title: String) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Eyebrow(label);Text(title,style=MaterialTheme.typography.headlineMedium)
    }
}
@Composable
private fun PrimaryAction(text: String,enabled: Boolean=true,onClick: ()->Unit) {
    Button(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),
        shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(horizontal=18.dp,vertical=14.dp)) {
        Text(text,modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(8.dp));Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(20.dp))
    }
}
@Composable
private fun SecondaryAction(text: String,enabled: Boolean=true,icon: ImageVector?=null,onClick: ()->Unit) {
    OutlinedButton(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),
        shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,HoneyStyle.Line),
        colors=ButtonDefaults.outlinedButtonColors(contentColor=HoneyStyle.Ink),
        contentPadding=PaddingValues(16.dp)) {
        if(icon!=null){Icon(icon,null,Modifier.size(18.dp));Spacer(Modifier.width(10.dp))}
        Text(text,style=MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun HomeScreen(label:String,apiConnection:ApiConnectionState,history:List<ReviewHistoryItem>,onLabel:(String)->Unit,onLive: ()->Unit,onDemo: ()->Unit,onRefreshApi:()->Unit) {
    Page {
        Heading("Conoce tu conexión","Tu Wi-Fi,\nmás claro.")
        Text("Observa la señal, frecuencia y enlace de tu Wi-Fi. Guarda lecturas y compara cómo cambian.",color=HoneyStyle.Muted,style=MaterialTheme.typography.bodyLarge)
        val pending=history.count { it.source==ReviewSource.LIVE && it.syncStatus.substringBefore(":")!="synced" }
        val latest=history.filter { it.source==ReviewSource.LIVE }.maxByOrNull { it.createdAt }
        Glass(accent=if(apiConnection==ApiConnectionState.ONLINE)HoneyStyle.Lime else HoneyStyle.Line) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Eyebrow("Estado del servicio",HoneyStyle.Muted)
                    Text(when(apiConnection){ApiConnectionState.CHECKING->"Comprobando API…";ApiConnectionState.ONLINE->"API conectada";ApiConnectionState.OFFLINE->"API sin conexión"},style=MaterialTheme.typography.titleMedium,color=if(apiConnection==ApiConnectionState.ONLINE)HoneyStyle.Lime else HoneyStyle.Amber)
                }
                TextButton(onClick=onRefreshApi){Text("Revisar")}
            }
            Text("Última lectura: ${latest?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(it.createdAt)) } ?: "ninguna"} · pendientes: $pending",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
        }
        Glass(accent=HoneyStyle.Lime.copy(alpha=0.35f),glow=true) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Eyebrow("Lectura del teléfono",HoneyStyle.Muted)
                Icon(Icons.Default.Wifi,null,tint=HoneyStyle.Lime,modifier=Modifier.size(22.dp))
            }
            WifiArtwork()
            Text("Datos reales de tu conexión.",style=MaterialTheme.typography.titleLarge)
            Text("Señal, banda, estándar y velocidades del enlace reportados por Android. Las pruebas externas requieren configuración.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            OutlinedTextField(value=label,onValueChange=onLabel,modifier=Modifier.fillMaxWidth(),singleLine=true,
                label={Text("Etiqueta de prueba (opcional)")},placeholder={Text("Ej. Sala A")},
                isError=!validManualLabel(label),supportingText={Text(if(validManualLabel(label))"Escríbela tú. No se lee ni identifica el nombre real de la red." else "Usa hasta 40 letras, números, espacios, punto o guion.")})
            PrimaryAction("Revisar mi Wi-Fi",enabled=validManualLabel(label),onClick=onLive)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            MiniNote("01 / GUARDA","En tu teléfono",Icons.Default.PhoneAndroid,Modifier.weight(1f))
            MiniNote("02 / COMPARTE","Tú decides",Icons.Default.CloudUpload,Modifier.weight(1f))
        }
        SecondaryAction("Ver ejemplos de resultados",icon=Icons.Default.Science,onClick=onDemo)
        Text("Son indicadores limitados: no garantizan que la red sea segura ni confirman un ataque.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
    }
}

@Composable
private fun WifiArtwork() {
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        val cx=size.width/2f
        val cy=size.height*0.88f
        drawRect(Brush.radialGradient(listOf(HoneyStyle.Lime.copy(alpha=0.15f),Color.Transparent),Offset(cx,cy),size.width*0.55f))
        for(i in 1..3) {
            val radius=(i*24).dp.toPx()
            drawArc(if(i==3) HoneyStyle.Lime.copy(alpha=0.3f) else HoneyStyle.Lime,
                220f,100f,false,Offset(cx-radius,cy-radius),Size(radius*2,radius*2),style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
        }
        drawRect(HoneyStyle.Lime,Offset(cx-3.dp.toPx(),cy-3.dp.toPx()),Size(6.dp.toPx(),6.dp.toPx()))
        for(i in 0..15)drawCircle(HoneyStyle.Lime.copy(alpha=0.2f),1.dp.toPx(),Offset(size.width*i/15f,size.height-2.dp.toPx()))
    }
}
@Composable
private fun MiniNote(label: String,title: String,icon: ImageVector,modifier: Modifier) {
    Glass(modifier) {
        Icon(icon,null,tint=HoneyStyle.Aqua,modifier=Modifier.size(20.dp))
        Text(label,style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
        Text(title,style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.SemiBold)
    }
}

@Composable
private fun LiveScreen(working: Boolean,message: String?,onCancel:()->Unit) {
    Page {
        Heading("Lectura real / LIVE",if(working)"Consultando Android…" else "No se completó la lectura")
        if(working) LinearProgressIndicator(Modifier.fillMaxWidth(),color=HoneyStyle.Lime,trackColor=HoneyStyle.Line)
        InfoStrip("Qué estamos leyendo",message ?: "Datos disponibles del Wi-Fi activo. La revisión se guardará en este teléfono.")
        InfoStrip("Pruebas externas",if(dev.honeyphone.app.core.network.ProbeConfig.load()==null)"DNS y HTTPS no tienen un destino controlado. Se mostrarán como pendientes." else "DNS y HTTPS se consultan por esta Wi-Fi con límites de tiempo. Si cambia la red, se descartan los datos.",HoneyStyle.Muted)
        if(working) SecondaryAction("Cancelar revisión",onClick=onCancel)
    }
}

@Composable
private fun DemoScreen(scenarios: List<DemoScenario>,onOpen: (String)->Unit) {
    Page {
        Heading("Modo DEMO","Aprende a leer los resultados")
        Text("Son ejemplos simulados. No describen tu conexión ni se guardan hasta que tú lo pidas.",color=HoneyStyle.Muted,style=MaterialTheme.typography.bodyMedium)
        scenarios.forEachIndexed { i,scenario ->
            Glass(Modifier.clickable(role=Role.Button){onOpen(scenario.checkId)}) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("0"+(i+1),fontFamily=HoneyStyle.Dots,fontSize=36.sp,color=HoneyStyle.Lime)
                    StatusPill("DEMO",HoneyStyle.Amber)
                }
                Text(scenario.title,style=MaterialTheme.typography.titleLarge)
                Text(scenario.description,style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Abrir ejemplo",color=HoneyStyle.Lime,style=MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f));Icon(Icons.AutoMirrored.Filled.ArrowForward,null,tint=HoneyStyle.Lime)
                }
            }
        }
    }
}

@Composable
private fun ResultScreen(review: ReviewPresentation,localSaved: Boolean,working: Boolean,message: String?,syncStatus: String,
    onSignal:(SignalPresentation)->Unit,onSave:()->Unit,onSync:()->Unit,onExport:()->Unit,onDelete:()->Unit) {
    var confirmDelete by remember(review.checkId){mutableStateOf(false)}
    val demo=review.source==ReviewSource.DEMO
    val available=review.signals.count{it.outcome==SignalOutcome.PASS || it.outcome==SignalOutcome.ATTENTION}
    val rssi=if(!demo)review.wifiMetrics?.rssiDbm else null
    val synced=syncStatus.substringBefore(":").equals("synced",true)
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},
        title={Text("¿Eliminar esta revisión?")},
        text={Text("Se borrará del teléfono y, si fue enviada, también del servidor. Esta acción no se puede deshacer.")},
        confirmButton={TextButton(onClick={confirmDelete=false;onDelete()}){Text("Eliminar revisión",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton(onClick={confirmDelete=false}){Text("Conservar")}})
    Page {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            SourcePill(review.source)
            StatusPill(verdictLabel(review.verdict),verdictColor(review.verdict))
        }
        Glass(accent=verdictColor(review.verdict).copy(alpha=0.35f),glow=true) {
            Eyebrow(if(demo)"Resultado simulado" else "Resultado de tu revisión",HoneyStyle.Muted)
            Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Text(rssi?.toString() ?: String.format("%02d",available),fontFamily=HoneyStyle.Dots,fontWeight=FontWeight.Bold,fontSize=if(rssi==null)70.sp else 56.sp,lineHeight=76.sp,color=HoneyStyle.Lime)
                Column(Modifier.padding(bottom=10.dp)) {
                    Text(if(rssi!=null)"dBm" else "/ "+String.format("%02d",review.signals.size),fontFamily=HoneyStyle.Mono,color=HoneyStyle.Muted)
                    Text(if(rssi!=null)"señal recibida" else "señales con datos",style=MaterialTheme.typography.bodySmall)
                }
            }
            Text(verdictTitle(review.verdict),style=MaterialTheme.typography.titleLarge)
            Text(review.summary,style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            Text(available.toString()+"/"+review.signals.size+" indicadores con datos. No es una puntuación de seguridad.",style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
        }
        if(review.hasAttention) InfoStrip("Hay una señal que revisar","Abre los indicadores marcados para entender qué se observó y sus límites.",HoneyStyle.Amber)
        if(!demo && review.signals.any { it.reasonCode=="NO_WIFI" }) InfoStrip("No hay Wi-Fi activo","Conéctate a una red Wi-Fi y vuelve a revisar. La conexión móvil no sustituye esta lectura.",HoneyStyle.Muted)
        if(demo) InfoStrip("Datos de demostración","Este resultado es un ejemplo, no una lectura de tu Wi-Fi.",HoneyStyle.Amber)
        if(!demo) WifiTelemetryPanel(review.wifiMetrics,review.networkLabel)
        Eyebrow("Indicadores / toca para entender",HoneyStyle.Muted)
        review.signals.sortedBy { it.kind.ordinal }.forEach { signal->SignalCard(signal){onSignal(signal)} }
        Glass {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(if(localSaved)Icons.Default.CheckCircle else Icons.Default.Save,null,tint=HoneyStyle.Aqua,modifier=Modifier.size(20.dp))
                Text(if(localSaved)"Guardada en este teléfono" else "Ejemplo sin guardar",style=MaterialTheme.typography.titleMedium)
            }
            Text(if(localSaved)"Puedes volver a esta revisión desde Historial." else "Guárdalo si quieres probar el envío o exportarlo.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            if(!localSaved&&demo) PrimaryAction("Guardar este ejemplo",!working,onSave)
            else {
                StatusPill(syncLabel(syncStatus),syncColor(syncStatus))
                PrimaryAction(if(working)"Procesando…" else if(synced)"Volver a enviar al servidor" else "Enviar al servidor",!working,onSync)
                Text("Solo se envía esta revisión cuando pulsas el botón.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                SecondaryAction("Compartir archivo JSON",!working,Icons.Default.Share,onExport)
                Text("JSON es un archivo con las señales y sus resultados.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            }
        }
        if(message!=null) InfoStrip("Estado de la revisión",message)
        if(localSaved) TextButton(onClick={confirmDelete=true},enabled=!working,modifier=Modifier.fillMaxWidth()) {
            Icon(Icons.Default.DeleteOutline,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp));Text("Eliminar revisión",color=MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SignalCard(signal: SignalPresentation,onClick:()->Unit) {
    Glass(Modifier.clickable(role=Role.Button,onClick=onClick)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(outcomeColor(signal.outcome).copy(alpha=0.09f)),contentAlignment=Alignment.Center) {
                Icon(signalIcon(signal.kind),null,tint=outcomeColor(signal.outcome),modifier=Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(signalTitle(signal.kind),style=MaterialTheme.typography.titleMedium)
                Text(signalStatus(signal),style=MaterialTheme.typography.bodySmall,color=outcomeColor(signal.outcome))
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward,null,tint=HoneyStyle.Muted,modifier=Modifier.size(18.dp))
        }
        Text(signalPurpose(signal.kind),style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
    }
}

@Composable
private fun DetailScreen(signal: SignalPresentation,source: ReviewSource) {
    var showCode by remember(signal){mutableStateOf(false)}
    Page {
        SourcePill(source)
        Heading("Entiende la señal",signalTitle(signal.kind))
        StatusPill(signalStatus(signal),outcomeColor(signal.outcome))
        InfoStrip("Qué revisa",signalPurpose(signal.kind))
        InfoStrip(if(source==ReviewSource.DEMO)"Qué representa el ejemplo" else "Qué observó Android",signal.explanation)
        if(signal.reasonCode=="NOT_CONFIGURED") InfoStrip("Por qué está pendiente","Esta prueba necesita un destino controlado del proyecto. Todavía no está configurado; no se hizo una comprobación real.",HoneyStyle.Muted)
        Text("Una señal aislada no confirma una intrusión ni garantiza la seguridad de la red.",style=MaterialTheme.typography.bodyMedium,color=HoneyStyle.Muted)
        TextButton(onClick={showCode=!showCode}){Text(if(showCode)"Ocultar código técnico" else "Ver código técnico")}
        if(showCode) Text(signal.reasonCode,fontFamily=HoneyStyle.Mono,fontSize=12.sp,color=HoneyStyle.Muted)
    }
}

@Composable
private fun HistoryScreen(items: List<ReviewHistoryItem>,working: Boolean,message: String?,onRefresh:()->Unit,onOpen:(String)->Unit,onDemos:()->Unit) {
    var labelFilter by remember { mutableStateOf<String?>(null) }
    val filtered=items.filter { labelFilter==null || it.networkLabel.orEmpty()==labelFilter }
    Page {
        Heading("Tu registro","Historial de revisiones")
        Text("Tus revisiones se conservan en este teléfono. Puedes recuperar las enviadas al servidor.",style=MaterialTheme.typography.bodyMedium,color=HoneyStyle.Muted)
        SecondaryAction(if(working)"Consultando servidor…" else "Consultar servidor",!working,Icons.Default.CloudSync,onRefresh)
        if(working) LinearProgressIndicator(Modifier.fillMaxWidth(),color=HoneyStyle.Lime,trackColor=HoneyStyle.Line)
        if(message!=null) InfoStrip("Estado del historial",message)
        if(items.isNotEmpty()) {
            HistoryLabelFilter(items,labelFilter){labelFilter=it}
            HistoryComparison(filtered,labelFilter)
        }
        if(filtered.isEmpty()) {
            Glass {
                Icon(Icons.Default.History,null,tint=HoneyStyle.Lime,modifier=Modifier.size(32.dp))
                Text("Tu primera revisión empieza aquí",style=MaterialTheme.typography.titleLarge)
                Text("Pulsa Revisar en la barra inferior para leer tu Wi-Fi. También puedes explorar un ejemplo.",style=MaterialTheme.typography.bodyMedium,color=HoneyStyle.Muted)
                SecondaryAction("Explorar ejemplos DEMO",onClick=onDemos)
            }
        }
        filtered.forEach { item ->
            Glass(Modifier.clickable(role=Role.Button){onOpen(item.checkId)}) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    SourcePill(item.source,compact=true);StatusPill(syncLabel(item.syncStatus),syncColor(item.syncStatus))
                }
                item.networkLabel?.let{Text(it,style=MaterialTheme.typography.titleLarge)}
                Text(verdictTitle(item.verdict),style=MaterialTheme.typography.titleMedium)
                item.wifiMetrics?.let{m->Text((m.rssiDbm?.let{it.toString()+" dBm"} ?: "Señal sin dato")+" · "+(m.band ?: "Banda sin dato"),style=MaterialTheme.typography.bodyMedium,color=HoneyStyle.Aqua)}
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(item.createdAt)),style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Ver resultados",style=MaterialTheme.typography.labelLarge,color=HoneyStyle.Lime)
                    Spacer(Modifier.weight(1f));Icon(Icons.AutoMirrored.Filled.ArrowForward,null,tint=HoneyStyle.Lime,modifier=Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun GuideScreen(devices:List<DeviceInfo>,deviceName:String,deviceMessage:String?,currentInstallationId:String,onDeviceName:(String)->Unit,onSaveDevice:()->Unit,onRevokeDevice:()->Unit,onRefreshDevices:()->Unit,onDemos:()->Unit) {
    Page {
        Heading("Guía y privacidad","Qué hace HoneyPhone")
        InfoStrip("Comprobación periódica","HoneyPhone guarda una lectura LIVE automáticamente cada 15 minutos como mínimo. Android puede aplazarla para ahorrar batería. Las lecturas quedan en este teléfono y esperan conexión para sincronizarse.",HoneyStyle.Lime)
        Text("Guarda métricas reales de la Wi-Fi activa y compara tus pruebas por etiqueta manual. Cada lectura explica sus datos disponibles y sus límites.",style=MaterialTheme.typography.bodyLarge,color=HoneyStyle.Muted)
        listOf(
            Triple("01","Revisa tu Wi-Fi","Consulta señal en dBm, frecuencia, estándar y velocidad del enlace, además de seguridad y acceso reportados por Android."),
            Triple("02","Lee el resultado","Abre cada señal. En Historial puedes filtrar una etiqueta y comparar las últimas lecturas LIVE. Un dato ausente se muestra como pendiente."),
            Triple("03","Guarda y comparte","Escribe una etiqueta opcional antes de leer. La app no identifica automáticamente redes. El guardado es local; envío y exportación son acciones tuyas.")
        ).forEach { (number,title,body) ->
            Glass {
                Text(number,fontFamily=HoneyStyle.Dots,fontSize=32.sp,color=HoneyStyle.Lime)
                Text(title,style=MaterialTheme.typography.titleMedium)
                Text(body,style=MaterialTheme.typography.bodyMedium,color=HoneyStyle.Muted)
            }
        }
        InfoStrip("Tus datos","No guardamos el nombre de tu red (SSID), su identificador BSSID, ubicación ni credenciales. El envío al servidor es manual.")
        Heading("Dispositivos","Registro en la API")
        Text("La instalación actual usa un UUID aleatorio persistido en este teléfono.",color=HoneyStyle.Muted)
        OutlinedTextField(value=deviceName,onValueChange=onDeviceName,label={Text("Nombre para este dispositivo")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Button(onClick=onSaveDevice,modifier=Modifier.weight(1f)){Text("Guardar nombre")}
            OutlinedButton(onClick=onRefreshDevices,modifier=Modifier.weight(1f)){Text("Actualizar lista")}
        }
        if(deviceMessage!=null) Text(deviceMessage,color=HoneyStyle.Muted)
        devices.forEach { device ->
            Glass(accent=if(device.active)HoneyStyle.Lime else HoneyStyle.Amber) {
                Text(device.deviceName ?: "HoneyPhone · ${device.installationId.take(8)}",style=MaterialTheme.typography.titleMedium)
                Text("${if(device.active)"ACTIVO" else "REVOCADO"} · app ${device.appVersion}",color=if(device.active)HoneyStyle.Lime else HoneyStyle.Amber)
                Text("UUID ${device.installationId}\nÚltima actividad: ${device.lastSeenAt}",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                if(device.active && device.installationId==currentInstallationId) TextButton(onClick=onRevokeDevice){Text("Revocar esta instalación")}
            }
        }
        InfoStrip("Pruebas controladas",if(dev.honeyphone.app.core.network.ProbeConfig.load()==null)"Sondas DNS/DoH y HTTPS preparadas, sin destino configurado. No realizan pruebas externas ni se marcan exitosas." else "DNS TXT se contrasta con la referencia DoH configurada; HTTPS valida el destino con TLS normal. Los fallos de referencia se muestran como desconocidos.",HoneyStyle.Muted)
        InfoStrip("Cómo interpretar","La velocidad de enlace no es la de Internet. Un gráfico de señal no califica seguridad. HoneyPhone no certifica una red ni confirma ataques.",HoneyStyle.Amber)
        SecondaryAction("Explorar ejemplos DEMO",icon=Icons.Default.Science,onClick=onDemos)
        Text("Los ejemplos simulados siempre llevan la etiqueta DEMO.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
    }
}

@Composable
private fun HoneypotScreen(
    status: HoneypotStatus,
    events: List<HoneypotEvent>,
    sessions:List<HoneypotSessionInfo>,
    remoteEvents:List<HoneypotRemoteEvent>,
    selectedSessionId:String?,
    remoteMessage:String?,
    onStart: ()->Unit,
    onStop: ()->Unit,
    onClear: ()->Unit,
    onRefreshRemote:(String?)->Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    val context = LocalContext.current
    if (confirmClear) AlertDialog(
        onDismissRequest={confirmClear=false},
        title={Text("¿Borrar eventos del señuelo?")},
        text={Text("Se eliminarán los eventos almacenados localmente en este teléfono.")},
        confirmButton={TextButton(onClick={onClear();confirmClear=false}){Text("Borrar")}},
        dismissButton={TextButton(onClick={confirmClear=false}){Text("Cancelar")}},
    )
    Page {
        Heading("HoneyPhone / señuelo","Observa intentos HTTP en tu Wi-Fi")
        Text("Este es un listener HTTP real del teléfono. Inícialo manualmente y prueba desde otro dispositivo conectado a la misma Wi-Fi.",color=HoneyStyle.Muted,style=MaterialTheme.typography.bodyLarge)
        InfoStrip("Alcance","Solo acepta conexiones dirigidas a la IPv4 Wi-Fi del teléfono. Úsalo en una red propia o con autorización; la red debe permitir comunicación entre dispositivos.",HoneyStyle.Amber)
        Glass(accent=if(status.phase==HoneypotPhase.LISTENING) HoneyStyle.Lime else HoneyStyle.Line,glow=status.phase==HoneypotPhase.LISTENING) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Eyebrow("Estado del listener",HoneyStyle.Muted)
                StatusPill(when(status.phase) {
                    HoneypotPhase.STARTING -> "INICIANDO"
                    HoneypotPhase.LISTENING -> "ESCUCHANDO"
                    HoneypotPhase.ERROR -> "ERROR"
                    HoneypotPhase.STOPPED -> "DETENIDO"
                },if(status.phase==HoneypotPhase.LISTENING) HoneyStyle.Lime else if(status.phase==HoneypotPhase.ERROR) HoneyStyle.Amber else HoneyStyle.Muted)
            }
            if(status.phase==HoneypotPhase.LISTENING && status.address!=null) {
                val isEmulatorAddress = status.address.startsWith("http://10.0.2.")
                when (status.serviceDiscoveryReady) {
                    true -> Text(
                        "Nombre en la red: ${status.serviceName ?: "HoneyPhone"}",
                        style=MaterialTheme.typography.titleLarge,
                        color=HoneyStyle.Lime,
                    )
                    false -> Text("Nombre local no disponible",style=MaterialTheme.typography.titleMedium,color=HoneyStyle.Amber)
                    null -> Text("Anunciando HoneyPhone en la red…",style=MaterialTheme.typography.titleMedium,color=HoneyStyle.Muted)
                }
                Text(
                    if (isEmulatorAddress) {
                        "El emulador usa una IP virtual; prueba el listener desde el computador con ADB. Para recibir conexiones de la red, inicia HoneyPhone en un teléfono físico."
                    } else {
                        "Otros dispositivos compatibles pueden descubrir HoneyPhone por su nombre. Los navegadores pueden usar este enlace IP de respaldo: ${status.address}/admin."
                    },
                    style=MaterialTheme.typography.bodySmall,
                    color=HoneyStyle.Muted,
                )
                if (!isEmulatorAddress) SecondaryAction(
                    "Copiar enlace IP de respaldo",
                    icon=Icons.Default.ContentCopy,
                    onClick={
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Enlace HoneyPhone", "${status.address}/admin"))
                        Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
                    },
                )
            }
            if(status.message!=null) Text(status.message,style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Amber)
            Text("El listener se enlaza a la IPv4 de esta Wi-Fi y al puerto 8080. Se detiene al salir de la app o cambiar de sección.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            if(status.phase==HoneypotPhase.LISTENING) SecondaryAction("Detener listener",icon=Icons.Default.Stop,onClick=onStop)
            else PrimaryAction("Iniciar listener en esta Wi-Fi",enabled=status.phase!=HoneypotPhase.STARTING,onClick=onStart)
        }
        InfoStrip("Qué registra","Hora, IP de origen, método HTTP, ruta y User-Agent. No lee ni almacena cuerpos de petición, contraseñas, SSID ni BSSID.")
        InfoStrip("Nombre local","HoneyPhone anuncia su servicio HTTP por descubrimiento de red (NSD/mDNS). El acceso por nombre depende de que la Wi-Fi permita descubrimiento entre dispositivos; la IP copiada sirve como respaldo para el navegador.",HoneyStyle.Muted)
        InfoStrip("Almacenamiento","Los últimos 100 eventos quedan en el teléfono y se envían enmascarados a la API cuando hay conexión. Puedes borrar la copia local desde aquí.",HoneyStyle.Muted)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Heading("Actividad",if(events.isEmpty()) "Sin peticiones" else "${events.size} eventos recientes")
            if(events.isNotEmpty()) TextButton(onClick={confirmClear=true}) { Text("Borrar") }
        }
        if(events.isEmpty()) {
            Glass { Text("Aún no hay conexiones recibidas.",color=HoneyStyle.Muted) }
        } else {
            events.forEach { event ->
                Glass(accent=HoneyStyle.Aqua.copy(alpha=0.3f)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text("${event.method}  ${event.path}",style=MaterialTheme.typography.titleMedium,color=HoneyStyle.Lime)
                        Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(event.occurredAt)),style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
                    }
                    Text("Origen ${event.sourceAddress}",style=MaterialTheme.typography.bodySmall)
                    Text("User-Agent: ${event.userAgent.ifBlank { "no informado" }}",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Heading("Servidor","Sesiones y eventos sincronizados")
            TextButton(onClick={onRefreshRemote(selectedSessionId)}){Text("Actualizar")}
        }
        if(remoteMessage!=null) Text(remoteMessage,style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
        if(sessions.isEmpty()) Glass { Text("La API aún no tiene sesiones registradas.",color=HoneyStyle.Muted) }
        sessions.forEach { session ->
            Glass(accent=if(session.sessionId==selectedSessionId)HoneyStyle.Lime else HoneyStyle.Line) {
                TextButton(onClick={onRefreshRemote(session.sessionId)}) {
                    Text("${session.state} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(session.startedAt))}",color=HoneyStyle.Lime)
                }
                Text("Sesión ${session.sessionId.take(8)} · puerto ${session.listenPort}",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            }
        }
        remoteEvents.forEach { event ->
            Glass(accent=HoneyStyle.Aqua.copy(alpha=0.35f)) {
                Text("${event.method} ${event.path} · ${event.responseCode}",color=HoneyStyle.Lime,style=MaterialTheme.typography.titleSmall)
                Text("${event.sourceIpMasked} · ${event.userAgentFamily ?: "UNKNOWN"} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(event.occurredAt))}",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            }
        }
    }
}

@Composable
private fun InfoStrip(title: String,message: String,color: Color=HoneyStyle.Aqua) {
    Surface(shape=RoundedCornerShape(18.dp),contentColor=HoneyStyle.Ink,color=color.copy(alpha=0.055f),border=BorderStroke(1.dp,color.copy(alpha=0.16f))) {
        Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Info,null,Modifier.size(18.dp),tint=color)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium)
                Text(message,style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            }
        }
    }
}
@Composable
private fun SourcePill(source: ReviewSource,compact: Boolean=false) {
    StatusPill(if(source==ReviewSource.DEMO)"DEMO" else if(compact)"LIVE" else "LECTURA REAL · LIVE",
        if(source==ReviewSource.DEMO)HoneyStyle.Amber else HoneyStyle.Aqua)
}
@Composable
private fun StatusPill(text: String,color: Color) {
    Surface(color=color.copy(alpha=0.08f),contentColor=color,shape=RoundedCornerShape(8.dp),
        border=BorderStroke(1.dp,color.copy(alpha=0.18f))) {
        Text(text,Modifier.padding(horizontal=10.dp,vertical=6.dp),style=MaterialTheme.typography.labelSmall)
    }
}
private fun signalTitle(kind: SignalKind)=when(kind) {
    SignalKind.WIFI_SECURITY->"Seguridad Wi-Fi"
    SignalKind.CAPTIVE_PORTAL->"Acceso a la red"
    SignalKind.DNS->"Resolución DNS"
    SignalKind.HTTPS->"Conexión HTTPS"
}
private fun signalPurpose(kind: SignalKind)=when(kind) {
    SignalKind.WIFI_SECURITY->"El tipo de protección de la conexión que informa Android."
    SignalKind.CAPTIVE_PORTAL->"Si la red pide abrir una página para iniciar sesión o aceptar condiciones."
    SignalKind.DNS->"Cómo se resuelven los nombres de sitios. Requiere una referencia del proyecto."
    SignalKind.HTTPS->"La conexión cifrada a un destino controlado. Requiere configuración del proyecto."
}
private fun signalIcon(kind: SignalKind)=when(kind) {
    SignalKind.WIFI_SECURITY->Icons.Default.Wifi
    SignalKind.CAPTIVE_PORTAL->Icons.Default.Language
    SignalKind.DNS->Icons.Default.Dns
    SignalKind.HTTPS->Icons.Default.Lock
}
private fun signalStatus(signal: SignalPresentation)=when {
    signal.reasonCode=="NOT_CONFIGURED"->"Sin configurar"
    signal.reasonCode=="NO_WIFI"->"Sin conexión Wi-Fi"
    signal.reasonCode=="NETWORK_CHANGED"->"La red cambió durante la lectura"
    signal.reasonCode=="PERMISSION_DENIED"->"Android no permitió leer el dato"
    signal.outcome==SignalOutcome.ATTENTION->"Requiere revisión"
    signal.outcome==SignalOutcome.UNKNOWN->"Sin datos disponibles"
    signal.outcome==SignalOutcome.NOT_APPLICABLE->"No aplica en esta lectura"
    signal.reasonCode=="OS_PORTAL_NOT_DETECTED"->"Sin portal detectado"
    signal.reasonCode=="OS_SECURITY_REPORTED"->"Protección reportada por Android"
    else->"Sin diferencia observada"
}
private fun syncLabel(status: String)=when(status.substringBefore(":").lowercase()) {
    "synced"->"Enviada"
    "syncing"->"Enviando"
    "failed"->"Sin enviar · reintentar"
    else->"Solo en el teléfono"
}
private fun syncColor(status: String)=when(status.substringBefore(":").lowercase()) {
    "synced"->HoneyStyle.Aqua
    "failed"->HoneyStyle.Amber
    else->HoneyStyle.Muted
}
private fun verdictTitle(v: ReviewVerdict)=when(v) {
    ReviewVerdict.NO_DISCREPANCIES->"Sin diferencias observadas"
    ReviewVerdict.ATTENTION_REQUIRED->"Hay una señal que revisar"
    ReviewVerdict.INCOMPLETE->"Información parcial"
}
private fun verdictLabel(v: ReviewVerdict)=when(v) {
    ReviewVerdict.NO_DISCREPANCIES->"Sin diferencias"
    ReviewVerdict.ATTENTION_REQUIRED->"Atención"
    ReviewVerdict.INCOMPLETE->"Parcial"
}
private fun verdictColor(v: ReviewVerdict)=when(v) {
    ReviewVerdict.NO_DISCREPANCIES->HoneyStyle.Aqua
    ReviewVerdict.ATTENTION_REQUIRED->HoneyStyle.Amber
    ReviewVerdict.INCOMPLETE->HoneyStyle.Muted
}
private fun outcomeColor(v: SignalOutcome)=when(v) {
    SignalOutcome.PASS->HoneyStyle.Aqua
    SignalOutcome.ATTENTION->HoneyStyle.Amber
    SignalOutcome.UNKNOWN,SignalOutcome.NOT_APPLICABLE->HoneyStyle.Muted
}
