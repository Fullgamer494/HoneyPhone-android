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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.honeyphone.app.model.*
import dev.honeyphone.app.R
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoneyPhoneApp(viewModel: HoneyPhoneViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showQuickGuide by remember { mutableStateOf(false) }
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
    BackHandler(enabled=canGoBack) { viewModel.goBack() }
    Box(Modifier.fillMaxSize()) {
        TechBackground()
        Scaffold(
            containerColor=Color.Transparent, contentColor=HoneyStyle.Ink,
            topBar={
                TopAppBar(
                    title={
                        Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Image(painterResource(R.drawable.ic_honeyphone),contentDescription=null,modifier=Modifier.size(36.dp))
                            Text("HoneyPhone", style=MaterialTheme.typography.titleMedium)
                        }
                    },
                    navigationIcon={
                        if(canGoBack) IconButton(onClick=viewModel::goBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription="Volver")
                        }
                    },
                    actions={
                        IconButton(onClick={showQuickGuide=true}) { Icon(Icons.Default.Info,contentDescription="Guía y privacidad",tint=HoneyStyle.Muted) }
                        Spacer(Modifier.width(8.dp))
                    },
                    colors=TopAppBarDefaults.topAppBarColors(containerColor=HoneyStyle.Background.copy(alpha=0.96f)),
                )
            },
            bottomBar={
                NavigationBar(containerColor=HoneyStyle.Background.copy(alpha=0.98f), tonalElevation=0.dp,
                    modifier=Modifier.border(BorderStroke(1.dp,HoneyStyle.Line.copy(alpha=0.5f)))) {
                    listOf(Triple("Señuelo",Icons.Default.Security,AppDestination.HONEYPOT),
                        Triple("Red",Icons.Default.Wifi,AppDestination.HOME),
                        Triple("Actividad",Icons.Default.History,AppDestination.HISTORY),
                        Triple("Sistema",Icons.Default.Settings,AppDestination.SETTINGS)).forEach { (label,icon,destination) ->
                        val selected=state.activeTab==destination
                        NavigationBarItem(selected=selected,onClick={
                            when(destination) {
                                AppDestination.HISTORY -> viewModel.openHistory()
                                AppDestination.HONEYPOT -> viewModel.openHoneypot()
                                AppDestination.SETTINGS -> viewModel.openSettings()
                                else -> viewModel.openHome()
                            }
                        },icon={Icon(icon,contentDescription=null)},label={Text(label,maxLines=1,fontSize=10.sp)},
                            colors=NavigationBarItemDefaults.colors(selectedIconColor=HoneyStyle.Lime,
                                selectedTextColor=HoneyStyle.Lime, indicatorColor=HoneyStyle.Lime.copy(alpha=0.1f),
                                unselectedIconColor=HoneyStyle.Muted,unselectedTextColor=HoneyStyle.Muted))
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when(state.destination) {
                    AppDestination.HOME -> HomeScreen(state.networkLabelDraft,state.history,state.review,viewModel::setNetworkLabel,viewModel::openLivePreview,viewModel::openDemoGallery,viewModel::openHistoryReview,viewModel::syncCurrent,viewModel::exportCurrent,state.message)
                    AppDestination.LIVE_REVIEW -> LiveScreen(state.working,state.message,viewModel::cancelReview)
                    AppDestination.DEMO_GALLERY -> DemoScreen(state.scenarios,viewModel::openDemo)
                    AppDestination.RESULT -> state.review?.let {
                        ResultScreen(it,state.localSaved,state.working,state.message,
                            state.history.find { row->row.checkId==it.checkId }?.syncStatus ?: "pending",
                            viewModel::openDetail,viewModel::saveDemo,viewModel::syncCurrent,viewModel::exportCurrent,viewModel::deleteCurrent)
                    }
                    AppDestination.DETAIL -> {
                        state.review?.let { review ->
                            ResultScreen(review,state.localSaved,state.working,state.message,
                                state.history.find { row->row.checkId==review.checkId }?.syncStatus ?: "pending",
                                viewModel::openDetail,viewModel::saveDemo,viewModel::syncCurrent,viewModel::exportCurrent,viewModel::deleteCurrent)
                        }
                        state.selectedSignal?.let { signal -> ModalBottomSheet(onDismissRequest=viewModel::goBack,containerColor=HoneyStyle.Surface) {
                            SignalDetailsSheet(signal,Modifier.navigationBarsPadding())
                        } }
                    }
                    AppDestination.HISTORY -> ActivityScreen(state.history,state.honeypotEvents,state.honeypotSessions,state.remoteHoneypotEvents,state.selectedHoneypotSessionId,state.honeypotRemoteMessage,state.working,state.message,viewModel::refreshHistory,viewModel::refreshHoneypotSessions,viewModel::clearHoneypotEvents,viewModel::openHistoryReview)
                    AppDestination.HONEYPOT -> HoneypotHomeScreen(state.honeypotStatus,state.honeypotEvents,
                        viewModel::startHoneypot,viewModel::stopHoneypot,viewModel::clearHoneypotEvents)
                    AppDestination.SETTINGS -> SystemScreen(state.devices,state.deviceNameDraft,state.deviceMessage,state.currentInstallationId,state.apiConnection,viewModel::setDeviceName,viewModel::saveDeviceName,viewModel::revokeDevice,viewModel::refreshDevices,viewModel::refreshApiStatus,viewModel::openDemoGallery)
                }
            }
        }
        if(showQuickGuide) ModalBottomSheet(onDismissRequest={showQuickGuide=false},containerColor=HoneyStyle.Surface) {
            QuickGuideSheet(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun TechBackground() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(HoneyStyle.Background)
        val step=32.dp.toPx()
        var x=0f
        while(x<size.width){drawLine(HoneyStyle.Line.copy(alpha=0.11f),Offset(x,0f),Offset(x,size.height),0.5.dp.toPx());x+=step}
        var y=0f
        while(y<size.height){drawLine(HoneyStyle.Line.copy(alpha=0.11f),Offset(0f,y),Offset(size.width,y),0.5.dp.toPx());y+=step}
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=18.dp),
        verticalArrangement=Arrangement.spacedBy(18.dp),content=content)
}

@Composable
private fun Glass(modifier: Modifier=Modifier, accent: Color=HoneyStyle.Line, glow: Boolean=false, content: @Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),
        color=HoneyStyle.Surface,contentColor=HoneyStyle.Ink,
        border=BorderStroke(1.dp,accent.copy(alpha=if(glow) 0.95f else 0.72f))) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(label:String,history:List<ReviewHistoryItem>,review:ReviewPresentation?,onLabel:(String)->Unit,onLive: ()->Unit,onDemo: ()->Unit,onOpenReview:(String)->Unit,onSync:()->Unit,onExport:()->Unit,message:String?) {
    var selectedSignal by remember { mutableStateOf<SignalPresentation?>(null) }
    val latest=history.filter { it.source==ReviewSource.LIVE }.maxByOrNull { it.createdAt }
    val metrics=latest?.wifiMetrics
    val latestReview=review?.takeIf { it.source==ReviewSource.LIVE && it.checkId==latest?.checkId }
    val signals=latestReview?.signals?.associateBy { it.kind }.orEmpty()
    Page {
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Eyebrow("Red / lectura real",HoneyStyle.Muted)
            Text("Red Wi-Fi",style=MaterialTheme.typography.headlineSmall)
        }
        Glass(accent=HoneyStyle.Line) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                Text(metrics?.rssiDbm?.let { "$it" } ?: "—",fontFamily=HoneyStyle.Dots,fontWeight=FontWeight.Bold,fontSize=58.sp,lineHeight=64.sp,color=HoneyStyle.Ink)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Eyebrow("Señal recibida",HoneyStyle.Muted)
                    Text(if(metrics?.rssiDbm!=null)"dBm" else "Aún sin revisar",style=MaterialTheme.typography.titleLarge)
                    Text(listOfNotNull(metrics?.band,metrics?.channel?.let { "Canal $it" },metrics?.standardLabel).joinToString(" · ").ifBlank { "Los datos aparecerán aquí" },style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                MetricChip("RX",metrics?.rxLinkSpeedMbps?.let { "$it Mbps" } ?: "—",Modifier.weight(1f))
                MetricChip("TX",metrics?.txLinkSpeedMbps?.let { "$it Mbps" } ?: metrics?.linkSpeedMbps?.let { "$it Mbps" } ?: "—",Modifier.weight(1f))
                MetricChip("INTERNET",when(metrics?.internetValidated){true->"OK";false->"SIN VALIDAR";null->"—"},Modifier.weight(1.2f))
            }
            OutlinedTextField(value=label,onValueChange=onLabel,modifier=Modifier.fillMaxWidth(),singleLine=true,
                label={Text("Etiqueta opcional")},placeholder={Text("Ej. Sala A")},isError=!validManualLabel(label))
            PrimaryAction("Escanear red",enabled=validManualLabel(label),onClick=onLive)
        }
        Eyebrow("Última revisión",HoneyStyle.Muted)
        latest?.let { TextButton(onClick={onOpenReview(it.checkId)}) { Text("Ver informe") } }
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                SignalGridCell(SignalKind.WIFI_SECURITY,signals[SignalKind.WIFI_SECURITY],Modifier.weight(1f)){ signals[SignalKind.WIFI_SECURITY]?.let { selectedSignal=it } ?: latest?.let { onOpenReview(it.checkId) } }
                SignalGridCell(SignalKind.CAPTIVE_PORTAL,signals[SignalKind.CAPTIVE_PORTAL],Modifier.weight(1f)){ signals[SignalKind.CAPTIVE_PORTAL]?.let { selectedSignal=it } ?: latest?.let { onOpenReview(it.checkId) } }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                SignalGridCell(SignalKind.DNS,signals[SignalKind.DNS],Modifier.weight(1f)){ signals[SignalKind.DNS]?.let { selectedSignal=it } ?: latest?.let { onOpenReview(it.checkId) } }
                SignalGridCell(SignalKind.HTTPS,signals[SignalKind.HTTPS],Modifier.weight(1f)){ signals[SignalKind.HTTPS]?.let { selectedSignal=it } ?: latest?.let { onOpenReview(it.checkId) } }
            }
        }
        Text("${history.count { it.source==ReviewSource.LIVE }} lecturas guardadas",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
        if(latestReview!=null) Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick=onSync,modifier=Modifier.weight(1f)){Icon(Icons.Default.CloudUpload,null);Spacer(Modifier.width(6.dp));Text("Enviar",maxLines=1)}
            OutlinedButton(onClick=onExport,modifier=Modifier.weight(1f)){Icon(Icons.Default.Code,null);Spacer(Modifier.width(6.dp));Text("JSON")}
        }
        message?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted) }
        TextButton(onClick=onDemo){Text("Explorar ejemplos DEMO")}
    }
    selectedSignal?.let { signal -> ModalBottomSheet(onDismissRequest={selectedSignal=null},containerColor=HoneyStyle.Surface) {
        SignalDetailsSheet(signal,Modifier.navigationBarsPadding())
    } }
}

@Composable
private fun MetricChip(label:String,value:String,modifier:Modifier=Modifier) {
    Surface(modifier,shape=RoundedCornerShape(8.dp),color=HoneyStyle.Background,border=BorderStroke(1.dp,HoneyStyle.Line)) {
        Column(Modifier.padding(horizontal=10.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(label,fontFamily=HoneyStyle.Mono,style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
            Text(value,style=MaterialTheme.typography.labelLarge,color=HoneyStyle.Ink)
        }
    }
}

@Composable
private fun SignalGridCell(kind:SignalKind,signal:SignalPresentation?,modifier:Modifier=Modifier,onClick:()->Unit) {
    val color=signal?.let { outcomeColor(it.outcome) } ?: HoneyStyle.Muted
    Surface(modifier.clickable(role=Role.Button,onClick=onClick),shape=RoundedCornerShape(10.dp),color=HoneyStyle.Surface,border=BorderStroke(1.dp,HoneyStyle.Line)) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(signalTitle(kind).uppercase(),fontFamily=HoneyStyle.Mono,style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
            Text(signal?.statusLabel ?: "Pendiente",style=MaterialTheme.typography.titleSmall,color=color)
            Text(signal?.reasonCode ?: "Toca para ver el último informe",fontFamily=HoneyStyle.Mono,style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted,maxLines=1)
        }
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
private fun SignalDetailsSheet(signal:SignalPresentation,modifier:Modifier=Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=26.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Eyebrow("Detalle del indicador",HoneyStyle.Amber)
        Text(signalTitle(signal.kind),style=MaterialTheme.typography.headlineSmall)
        StatusPill(signalStatus(signal),outcomeColor(signal.outcome))
        Text(signalPurpose(signal.kind),style=MaterialTheme.typography.bodyMedium,color=HoneyStyle.Muted)
        Text(signal.explanation,style=MaterialTheme.typography.bodyMedium)
        Text("CÓDIGO",fontFamily=HoneyStyle.Mono,style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
        Text(signal.reasonCode,fontFamily=HoneyStyle.Mono,style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Ink)
        Text("Una señal aislada no confirma una intrusión ni garantiza seguridad.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
    }
}

@Composable
private fun ActivityScreen(
    wifiItems:List<ReviewHistoryItem>,localEvents:List<HoneypotEvent>,sessions:List<HoneypotSessionInfo>,remoteEvents:List<HoneypotRemoteEvent>,selectedSession:String?,remoteMessage:String?,working:Boolean,message:String?,
    onRefreshWifi:()->Unit,onRefreshHttp:(String?)->Unit,onClearHttp:()->Unit,onOpenWifi:(String)->Unit,
) {
    var section by remember { mutableStateOf("WIFI") }
    var labelFilter by remember { mutableStateOf<String?>(null) }
    var sourceFilter by remember { mutableStateOf("LIVE") }
    val liveItems=wifiItems.filter { it.source==ReviewSource.LIVE }.sortedByDescending { it.createdAt }
    val latest=when(sourceFilter){"DEMO"->wifiItems.filter { it.source==ReviewSource.DEMO }.sortedByDescending { it.createdAt };"ALL"->wifiItems.sortedByDescending { it.createdAt };else->liveItems}
    val filtered=latest.filter { labelFilter==null || it.networkLabel.orEmpty()==labelFilter }
    Page {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Column { Eyebrow("Historial de actividad",HoneyStyle.Muted); Text("Actividad",style=MaterialTheme.typography.headlineMedium) }
            Text("${wifiItems.size+localEvents.size}",fontFamily=HoneyStyle.Dots,style=MaterialTheme.typography.headlineSmall,color=HoneyStyle.Ink)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=section=="WIFI",onClick={section="WIFI"},label={Text("Señal Wi-Fi")})
            FilterChip(selected=section=="HTTP",onClick={section="HTTP"},label={Text("Sesiones :8080")})
        }
        if(section=="WIFI") {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("LIVE" to "Real","DEMO" to "DEMO","ALL" to "Todas").forEach { (value,label)->
                    FilterChip(selected=sourceFilter==value,onClick={sourceFilter=value},label={Text(label)})
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text("${filtered.size} lecturas",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                TextButton(enabled=!working,onClick=onRefreshWifi){Text(if(working)"Actualizando…" else "Actualizar")}
            }
            if(working) LinearProgressIndicator(Modifier.fillMaxWidth(),color=HoneyStyle.Ink,trackColor=HoneyStyle.Line)
            message?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted) }
            if(latest.mapNotNull { it.networkLabel }.distinct().isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=labelFilter==null,onClick={labelFilter=null},label={Text("Todas")})
                    latest.mapNotNull { it.networkLabel }.distinct().forEach { label -> FilterChip(selected=labelFilter==label,onClick={labelFilter=label},label={Text(label,maxLines=1)}) }
                }
            }
            if(sourceFilter!="DEMO"&&liveItems.size>1) {
                val newest=liveItems[0].wifiMetrics?.rssiDbm; val previous=liveItems[1].wifiMetrics?.rssiDbm
                val delta=if(newest!=null&&previous!=null) newest-previous else null
                Glass {
                    Text("CAMBIO RECIENTE",fontFamily=HoneyStyle.Mono,style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
                    Text(delta?.let { if(it>0)"+$it dB" else "$it dB" } ?: "Sin comparación",fontFamily=HoneyStyle.Dots,style=MaterialTheme.typography.headlineSmall)
                    SignalSparkline(liveItems.take(12).mapNotNull { it.wifiMetrics?.rssiDbm })
                }
            }
            if(filtered.isEmpty()) Text("Las comprobaciones aparecerán aquí cuando escanees la red.",style=MaterialTheme.typography.bodyMedium,color=HoneyStyle.Muted)
            filtered.forEach { item ->
                Surface(Modifier.fillMaxWidth().clickable { onOpenWifi(item.checkId) },shape=RoundedCornerShape(8.dp),color=HoneyStyle.Surface,border=BorderStroke(1.dp,HoneyStyle.Line)) {
                    Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(item.wifiMetrics?.rssiDbm?.let { "$it dBm" } ?: "Señal sin dato",fontFamily=HoneyStyle.Dots,style=MaterialTheme.typography.titleLarge)
                            Text("${item.wifiMetrics?.band ?: "Banda desconocida"} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(item.createdAt))}",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                            Text("RX ${item.wifiMetrics?.rxLinkSpeedMbps?.let { "$it Mbps" } ?: "—"} · TX ${item.wifiMetrics?.txLinkSpeedMbps?.let { "$it Mbps" } ?: item.wifiMetrics?.linkSpeedMbps?.let { "$it Mbps" } ?: "—"}",fontFamily=HoneyStyle.Mono,style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
                        }
                        StatusPill(syncLabel(item.syncStatus),syncColor(item.syncStatus))
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text("${sessions.size} sesiones · ${localEvents.size} eventos",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                Row {
                    if(localEvents.isNotEmpty()) TextButton(onClick=onClearHttp){Text("Borrar")}
                    TextButton(onClick={onRefreshHttp(selectedSession)}){Text("Actualizar")}
                }
            }
            remoteMessage?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted) }
            if(sessions.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    sessions.forEach { session ->
                        FilterChip(selected=session.sessionId==selectedSession,onClick={onRefreshHttp(session.sessionId)},label={Text("${session.state} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(session.startedAt))}")})
                    }
                }
            }
            localEvents.forEach { event ->
                ActivityEventRow("${event.method} ${event.path}",event.sourceAddress,event.userAgent,Date(event.occurredAt),event.responseCode,"LOCAL")
            }
            remoteEvents.forEach { event ->
                ActivityEventRow("${event.method} ${event.path}",event.sourceIpMasked,event.userAgentFamily ?: "Desconocido",Date(event.occurredAt),event.responseCode,"REMOTO")
            }
            if(localEvents.isEmpty()&&remoteEvents.isEmpty()) Text("Las peticiones del señuelo aparecerán aquí cuando inicies una sesión.",style=MaterialTheme.typography.bodyMedium,color=HoneyStyle.Muted)
        }
    }
}

@Composable
private fun ActivityEventRow(title:String,origin:String,agent:String,date:Date,response:Int,source:String) {
    Surface(shape=RoundedCornerShape(8.dp),color=HoneyStyle.Surface,border=BorderStroke(1.dp,HoneyStyle.Line)) {
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(title,fontFamily=HoneyStyle.Mono,style=MaterialTheme.typography.titleSmall,color=HoneyStyle.Ink)
                StatusPill(source,HoneyStyle.Muted)
            }
            Text("$origin · $agent",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted,maxLines=2)
            Text("${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(date)} · HTTP $response",fontFamily=HoneyStyle.Mono,style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
        }
    }
}

@Composable
private fun SignalSparkline(values:List<Int>) {
    Canvas(Modifier.fillMaxWidth().height(72.dp)) {
        if(values.size<2) return@Canvas
        val min=values.min().toFloat(); val max=values.max().toFloat().let { if(it==min)it+1 else it }
        val points=values.reversed().mapIndexed { i,value -> Offset(size.width*i/(values.size-1),size.height*(1f-(value-min)/(max-min))) }
        for(i in 0 until points.lastIndex) drawLine(HoneyStyle.Ink,points[i],points[i+1],2.dp.toPx(),cap=StrokeCap.Round)
        points.forEach { drawCircle(HoneyStyle.Amber,3.dp.toPx(),it) }
    }
}

@Composable
private fun SystemScreen(devices:List<DeviceInfo>,deviceName:String,deviceMessage:String?,currentInstallationId:String,apiConnection:ApiConnectionState,onDeviceName:(String)->Unit,onSaveDevice:()->Unit,onRevokeDevice:()->Unit,onRefreshDevices:()->Unit,onRefreshApi:()->Unit,onDemos:()->Unit) {
    Page {
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Eyebrow("Dispositivo + configuración",HoneyStyle.Muted)
            Text("Sistema",style=MaterialTheme.typography.headlineMedium)
        }
        Glass {
            Eyebrow("Este dispositivo",HoneyStyle.Muted)
            Text(deviceName.ifBlank { "HoneyPhone" },style=MaterialTheme.typography.titleLarge)
            Text("UUID · ${currentInstallationId.ifBlank { "registrando…" }}",fontFamily=HoneyStyle.Mono,style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            OutlinedTextField(value=deviceName,onValueChange=onDeviceName,label={Text("Alias")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Button(onClick=onSaveDevice,modifier=Modifier.weight(1f)){Text("Guardar",maxLines=1)}
                OutlinedButton(onClick=onRefreshDevices,modifier=Modifier.weight(1f)){Text("Actualizar",maxLines=1)}
            }
            deviceMessage?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted) }
            if(devices.any { it.installationId==currentInstallationId && it.active }) TextButton(onClick=onRevokeDevice){Text("Revocar esta instalación",color=HoneyStyle.Amber)}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Eyebrow("Instalaciones registradas",HoneyStyle.Muted)
            Text("${devices.size}",fontFamily=HoneyStyle.Dots,color=HoneyStyle.Ink)
        }
        devices.filter { it.installationId!=currentInstallationId }.forEach { device ->
            Surface(shape=RoundedCornerShape(8.dp),color=HoneyStyle.Surface,border=BorderStroke(1.dp,HoneyStyle.Line)) {
                Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(device.deviceName ?: "HoneyPhone · ${device.installationId.take(8)}",style=MaterialTheme.typography.titleMedium)
                    Text("${if(device.active)"ACTIVO" else "REVOCADO"} · app ${device.appVersion}",fontFamily=HoneyStyle.Mono,style=MaterialTheme.typography.labelSmall,color=if(device.active)HoneyStyle.Ink else HoneyStyle.Muted)
                    Text("UUID ${device.installationId} · Última actividad ${device.lastSeenAt}",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                }
            }
        }
        Glass {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Eyebrow("Lecturas automáticas",HoneyStyle.Muted)
                    Text("Cada 15 minutos",style=MaterialTheme.typography.titleMedium)
                }
                StatusPill("ON",HoneyStyle.Ink)
            }
            Text("Android puede aplazarlas para ahorrar batería. Se guardan en este teléfono y se sincronizan cuando hay conexión.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
        }
        Glass {
            Eyebrow("Sondas DNS / HTTPS",HoneyStyle.Muted)
            Text(if(dev.honeyphone.app.core.network.ProbeConfig.load()==null)"Destino controlado pendiente" else "Destino de prueba configurado",style=MaterialTheme.typography.titleMedium)
            Text("DNS y HTTPS solo se comprueban contra destinos configurados por el proyecto.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
        }
        Glass {
            Eyebrow("Conexión con el servidor",HoneyStyle.Muted)
            Text(when(apiConnection){ApiConnectionState.CHECKING->"Comprobando conexión";ApiConnectionState.ONLINE->"Servidor disponible";ApiConnectionState.OFFLINE->"Servidor no disponible"},style=MaterialTheme.typography.titleMedium)
            TextButton(onClick=onRefreshApi){Text("Comprobar conexión")}
        }
        SecondaryAction("Ejemplos DEMO",icon=Icons.Default.Science,onClick=onDemos)
    }
}

@Composable
private fun QuickGuideSheet(modifier:Modifier=Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=22.dp).padding(bottom=30.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Eyebrow("Ayuda rápida",HoneyStyle.Amber)
        Text("Qué muestra HoneyPhone",style=MaterialTheme.typography.headlineSmall)
        Text("Señuelo · inicia un listener HTTP real en el puerto 8080. Las peticiones recibidas muestran hora, IP de origen, método, ruta y User-Agent.",style=MaterialTheme.typography.bodyMedium,color=HoneyStyle.Muted)
        Text("Red · lee métricas Wi-Fi informadas por Android. La señal y velocidad del enlace no equivalen a la velocidad de Internet ni certifican seguridad.",style=MaterialTheme.typography.bodyMedium,color=HoneyStyle.Muted)
        Text("Actividad · reúne lecturas, sesiones del listener y eventos guardados localmente o en la API.",style=MaterialTheme.typography.bodyMedium,color=HoneyStyle.Muted)
        Text("Privacidad · la app no guarda SSID, BSSID, ubicación, contraseñas ni el cuerpo de las peticiones. Los ejemplos DEMO son datos simulados y están identificados.",style=MaterialTheme.typography.bodyMedium,color=HoneyStyle.Muted)
        Text("Prueba local · conecta ambos dispositivos a una Wi-Fi que permita tráfico entre clientes y abre el enlace mostrado en Señuelo.",style=MaterialTheme.typography.bodyMedium,color=HoneyStyle.Muted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HoneypotHomeScreen(status:HoneypotStatus,events:List<HoneypotEvent>,onStart:()->Unit,onStop:()->Unit,onClear:()->Unit) {
    var selectedEvent by remember { mutableStateOf<HoneypotEvent?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val context=LocalContext.current
    val haptics=androidx.compose.ui.platform.LocalHapticFeedback.current
    var lastAnimatedEvent by remember { mutableStateOf(events.firstOrNull()?.id) }
    val currentEventId=events.firstOrNull()?.id
    LaunchedEffect(currentEventId,status.phase) {
        if(status.phase==HoneypotPhase.LISTENING && currentEventId!=null && currentEventId!=lastAnimatedEvent) {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            lastAnimatedEvent=currentEventId
        }
    }
    if(confirmClear) AlertDialog(onDismissRequest={confirmClear=false},title={Text("Borrar eventos locales?")},
        text={Text("Esto elimina del teléfono los eventos HTTP guardados localmente.")},
        confirmButton={TextButton(onClick={onClear();confirmClear=false}){Text("Borrar")}},
        dismissButton={TextButton(onClick={confirmClear=false}){Text("Cancelar")}})
    Page {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Eyebrow("HoneyPhone / :8080",HoneyStyle.Muted)
                Text("Señuelo",style=MaterialTheme.typography.headlineMedium)
            }
            StatusPill(when(status.phase){HoneypotPhase.STARTING->"INICIANDO";HoneypotPhase.LISTENING->"LIVE";HoneypotPhase.ERROR->"ERROR";HoneypotPhase.STOPPED->"DETENIDO"},
                when(status.phase){HoneypotPhase.LISTENING->HoneyStyle.Lime;HoneypotPhase.ERROR->HoneyStyle.Amber;else->HoneyStyle.Muted})
        }
        Box(Modifier.fillMaxWidth().height(210.dp),contentAlignment=Alignment.Center) {
            HoneypotFlytrap(status.phase==HoneypotPhase.LISTENING,currentEventId,size=210.dp)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(if(status.phase==HoneypotPhase.LISTENING)"Escuchando peticiones" else "Listener detenido",style=MaterialTheme.typography.titleLarge)
                Text(if(status.phase==HoneypotPhase.LISTENING)"HTTP · puerto 8080" else "Actívalo para recibir tráfico HTTP en esta Wi-Fi",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            }
            Text("${events.size}",fontFamily=HoneyStyle.Dots,fontSize=28.sp,color=HoneyStyle.Ink)
        }
        if(status.phase==HoneypotPhase.LISTENING && status.address!=null) {
            val url="${status.address}/admin"
            Surface(Modifier.fillMaxWidth().clickable {
                val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("HoneyPhone URL",url))
                Toast.makeText(context,"Enlace copiado",Toast.LENGTH_SHORT).show()
            },shape=RoundedCornerShape(8.dp),color=HoneyStyle.Surface,border=BorderStroke(1.dp,HoneyStyle.Line)) {
                Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(status.serviceName?.let { "$it · mDNS" } ?: "Servicio local",fontFamily=HoneyStyle.Mono,color=HoneyStyle.Ink,style=MaterialTheme.typography.titleSmall)
                        Text(url,fontFamily=HoneyStyle.Mono,color=HoneyStyle.Muted,style=MaterialTheme.typography.labelSmall)
                    }
                    Icon(Icons.Default.ContentCopy,contentDescription="Copiar enlace",tint=HoneyStyle.Muted)
                }
            }
            status.message?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Amber) }
        }
        if(status.phase==HoneypotPhase.ERROR) status.message?.let { InfoStrip("No se pudo iniciar",it,HoneyStyle.Amber) }
        PrimaryAction(if(status.phase==HoneypotPhase.LISTENING)"Detener señuelo" else if(status.phase==HoneypotPhase.STARTING)"Iniciando…" else "Activar señuelo :8080",
            enabled=status.phase!=HoneypotPhase.STARTING,onClick=if(status.phase==HoneypotPhase.LISTENING)onStop else onStart)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Eyebrow("Actividad HTTP",HoneyStyle.Muted)
            if(events.isNotEmpty()) TextButton(onClick={confirmClear=true}) { Text("Borrar") }
        }
        if(events.isEmpty()) Text("Las peticiones recibidas aparecerán aquí.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
        events.take(4).forEach { event ->
            Surface(Modifier.fillMaxWidth().clickable { selectedEvent=event },shape=RoundedCornerShape(8.dp),color=HoneyStyle.Surface,border=BorderStroke(1.dp,HoneyStyle.Line)) {
                Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text("${event.method}  ${event.path}",fontFamily=HoneyStyle.Mono,color=HoneyStyle.Ink,style=MaterialTheme.typography.titleSmall,maxLines=1)
                        Text("${event.sourceAddress} · ${event.userAgent.ifBlank { "cliente desconocido" }}",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted,maxLines=1)
                    }
                    Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(event.occurredAt)),fontFamily=HoneyStyle.Mono,style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
                }
            }
        }
    }
    selectedEvent?.let { event ->
        ModalBottomSheet(onDismissRequest={selectedEvent=null},containerColor=HoneyStyle.Surface) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Eyebrow("Evento HTTP",HoneyStyle.Amber)
                Text("${event.method} ${event.path}",style=MaterialTheme.typography.headlineSmall)
                Text("${DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(event.occurredAt))} · respuesta ${event.responseCode}",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                MetricChip("IP DE ORIGEN",event.sourceAddress,Modifier.fillMaxWidth())
                MetricChip("USER-AGENT",event.userAgent.ifBlank { "No informado" },Modifier.fillMaxWidth())
                Text("No se guardó el cuerpo de la petición.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
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
