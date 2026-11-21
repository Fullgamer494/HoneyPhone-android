package dev.honeyphone.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import dev.honeyphone.app.model.*
import dev.honeyphone.app.R
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.time.Instant
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoneyPhoneApp(viewModel: HoneyPhoneViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val screenStateHolder=rememberSaveableStateHolder()
    var showProductTour by rememberSaveable { mutableStateOf(false) }
    var productTourStep by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        if (!context.getSharedPreferences("honeyphone_ui", Context.MODE_PRIVATE)
                .getBoolean("product_tour_completed", false)) showProductTour = true
    }
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
                        IconButton(onClick={productTourStep=0;showProductTour=true}) { Icon(Icons.Default.Info,contentDescription="Recorrido de HoneyPhone",tint=HoneyStyle.Muted) }
                        Spacer(Modifier.width(8.dp))
                    },
                    colors=TopAppBarDefaults.topAppBarColors(containerColor=HoneyStyle.Background.copy(alpha=0.96f)),
                )
            },
            bottomBar={
                NavigationBar(containerColor=HoneyStyle.Background.copy(alpha=0.98f), tonalElevation=0.dp,
                    modifier=Modifier.border(BorderStroke(1.dp,HoneyStyle.Line.copy(alpha=0.5f)))) {
                    listOf(Triple("Red Wi-Fi",Icons.Default.Wifi,AppDestination.HOME),
                        Triple("Señuelo",Icons.Default.Security,AppDestination.HONEYPOT),
                        Triple("Actividad",Icons.Default.History,AppDestination.HISTORY),
                        Triple("Ajustes",Icons.Default.Settings,AppDestination.SETTINGS)).forEach { (label,icon,destination) ->
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
                screenStateHolder.SaveableStateProvider(state.destination.name) { when(state.destination) {
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
                    AppDestination.HONEYPOT -> HoneypotHomeScreen(state.honeypotStatus,state.honeypotEvents,state.wifiConnected,
                        state.selectedHoneypotProfile,viewModel::selectHoneypotProfile,
                        viewModel::startHoneypot,viewModel::stopHoneypot,viewModel::clearHoneypotEvents)
                    AppDestination.SETTINGS -> SystemScreen(state.devices,state.deviceNameDraft,state.deviceMessage,state.currentInstallationId,state.apiConnection,viewModel::setDeviceName,viewModel::saveDeviceName,viewModel::revokeDevice,viewModel::refreshDevices,viewModel::refreshApiStatus,viewModel::openDemoGallery)
                } }
            }
        }
        if(showProductTour) ModalBottomSheet(onDismissRequest={
            showProductTour=false
            context.getSharedPreferences("honeyphone_ui",Context.MODE_PRIVATE).edit().putBoolean("product_tour_completed",true).apply()
        },containerColor=HoneyStyle.Surface) {
            ProductTourSheet(
                step=productTourStep,
                onPrevious={productTourStep=(productTourStep-1).coerceAtLeast(0)},
                onNext={productTourStep=(productTourStep+1).coerceAtMost(PRODUCT_TOUR_STEPS.lastIndex)},
                onFinish={
                    showProductTour=false
                    context.getSharedPreferences("honeyphone_ui",Context.MODE_PRIVATE).edit().putBoolean("product_tour_completed",true).apply()
                },
                modifier=Modifier.navigationBarsPadding(),
            )
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
    Text(text,style=MaterialTheme.typography.labelMedium,color=color,letterSpacing=0.5.sp)
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
    var showIndicators by rememberSaveable { mutableStateOf(false) }
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
                MetricChip("Recibido",metrics?.rxLinkSpeedMbps?.let { "$it Mbps" } ?: "—",Modifier.weight(1f))
                MetricChip("Enviado",metrics?.txLinkSpeedMbps?.let { "$it Mbps" } ?: metrics?.linkSpeedMbps?.let { "$it Mbps" } ?: "—",Modifier.weight(1f))
                MetricChip("Internet",when(metrics?.internetValidated){true->"Disponible";false->"Sin validar";null->"Sin dato"},Modifier.weight(1.2f))
            }
            OutlinedTextField(value=label,onValueChange=onLabel,modifier=Modifier.fillMaxWidth(),singleLine=true,
                label={Text("Etiqueta opcional")},placeholder={Text("Ej. Sala A")},isError=!validManualLabel(label))
            PrimaryAction("Escanear red",enabled=validManualLabel(label),onClick=onLive)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Eyebrow("Última revisión",HoneyStyle.Muted)
            Row(verticalAlignment=Alignment.CenterVertically) {
                latest?.let { TextButton(onClick={onOpenReview(it.checkId)}) { Text("Ver informe") } }
                TextButton(onClick={showIndicators=!showIndicators}) { Text(if(showIndicators)"Ocultar señales" else "Ver señales") }
            }
        }
        if(showIndicators) Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                SignalGridCell(SignalKind.WIFI_SECURITY,signals[SignalKind.WIFI_SECURITY],Modifier.weight(1f)){ signals[SignalKind.WIFI_SECURITY]?.let { selectedSignal=it } ?: latest?.let { onOpenReview(it.checkId) } }
                SignalGridCell(SignalKind.CAPTIVE_PORTAL,signals[SignalKind.CAPTIVE_PORTAL],Modifier.weight(1f)){ signals[SignalKind.CAPTIVE_PORTAL]?.let { selectedSignal=it } ?: latest?.let { onOpenReview(it.checkId) } }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                SignalGridCell(SignalKind.DNS,signals[SignalKind.DNS],Modifier.weight(1f)){ signals[SignalKind.DNS]?.let { selectedSignal=it } ?: latest?.let { onOpenReview(it.checkId) } }
                SignalGridCell(SignalKind.HTTPS,signals[SignalKind.HTTPS],Modifier.weight(1f)){ signals[SignalKind.HTTPS]?.let { selectedSignal=it } ?: latest?.let { onOpenReview(it.checkId) } }
            }
        }
        Text("${history.count { it.source==ReviewSource.LIVE }} lecturas reales guardadas",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
        if(latestReview!=null) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick=onSync,modifier=Modifier.weight(1f)){Icon(Icons.Default.CloudUpload,null);Spacer(Modifier.width(6.dp));Text("Enviar",maxLines=1)}
            OutlinedButton(onClick=onExport,modifier=Modifier.weight(1f)){Icon(Icons.Default.Share,null);Spacer(Modifier.width(6.dp));Text("Compartir archivo")}
        }
        message?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted) }
        TextButton(onClick=onDemo){Text("Ver ejemplos")}
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
    val missingProjectTarget=signal==null && kind in setOf(SignalKind.DNS,SignalKind.HTTPS) && dev.honeyphone.app.core.network.ProbeConfig.load()==null
    Surface(modifier.clickable(role=Role.Button,onClick=onClick),shape=RoundedCornerShape(10.dp),color=HoneyStyle.Surface,border=BorderStroke(1.dp,HoneyStyle.Line)) {
        Column(Modifier.fillMaxWidth().heightIn(min=112.dp).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(signalTitle(kind),style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted,maxLines=2)
            Text(signal?.let(::signalStatus) ?: if(missingProjectTarget)"No configurada" else "Pendiente de lectura",style=MaterialTheme.typography.titleSmall,color=color,maxLines=2)
            Text(signal?.explanation ?: if(missingProjectTarget)"Falta definir un destino de prueba del proyecto." else "Escanea la red para consultar este indicador.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted,maxLines=2)
        }
    }
}

@Composable
private fun LiveScreen(working: Boolean,message: String?,onCancel:()->Unit) {
    Page {
        Heading("Lectura de tu red",if(working)"Consultando la conexión…" else "No se completó la lectura")
        if(working) LinearProgressIndicator(Modifier.fillMaxWidth(),color=HoneyStyle.Lime,trackColor=HoneyStyle.Line)
        InfoStrip("Qué estamos leyendo",message ?: "Datos disponibles del Wi-Fi activo. La revisión se guardará en este teléfono.")
        InfoStrip("Pruebas de referencia",if(dev.honeyphone.app.core.network.ProbeConfig.load()==null)"Las pruebas de resolución de nombres y conexión segura todavía no están activas. El equipo del proyecto debe definir un destino propio; no indica un problema de tu Wi-Fi." else "Las pruebas de resolución de nombres y conexión segura consultan destinos del proyecto con límites de tiempo. Si cambia la red, se descartan los datos.",HoneyStyle.Muted)
        if(working) SecondaryAction("Cancelar revisión",onClick=onCancel)
    }
}

@Composable
private fun DemoScreen(scenarios: List<DemoScenario>,onOpen: (String)->Unit) {
    Page {
        Heading("Ejemplos guiados","Aprende a leer los resultados")
        Text("Son ejemplos simulados. No describen tu conexión ni se guardan hasta que tú lo pidas.",color=HoneyStyle.Muted,style=MaterialTheme.typography.bodyMedium)
        scenarios.forEachIndexed { i,scenario ->
            Glass(Modifier.clickable(role=Role.Button){onOpen(scenario.checkId)}) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("0"+(i+1),fontFamily=HoneyStyle.Dots,fontSize=36.sp,color=HoneyStyle.Lime)
                    StatusPill("Simulación",HoneyStyle.Amber)
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
        Eyebrow("Indicadores · toca para entender",HoneyStyle.Muted)
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
                SecondaryAction("Compartir archivo de revisión",!working,Icons.Default.Share,onExport)
                Text("El archivo incluye esta revisión y los resultados de sus indicadores.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
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
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(outcomeColor(signal.outcome).copy(alpha=0.09f)),contentAlignment=Alignment.Center) {
                Icon(signalIcon(signal.kind),null,tint=outcomeColor(signal.outcome),modifier=Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(signalTitle(signal.kind),style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(signalStatus(signal),style=MaterialTheme.typography.bodySmall,color=outcomeColor(signal.outcome),maxLines=2,overflow=TextOverflow.Ellipsis)
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
            Column(Modifier.weight(1f)) { Eyebrow("Historial de actividad",HoneyStyle.Muted); Text("Actividad",style=MaterialTheme.typography.headlineMedium) }
            Text("${wifiItems.size+sessions.size+localEvents.size+remoteEvents.size}",fontFamily=HoneyStyle.Dots,style=MaterialTheme.typography.headlineSmall,color=HoneyStyle.Ink)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=section=="WIFI",onClick={section="WIFI"},label={Text("Señal Wi-Fi")})
            FilterChip(selected=section=="HTTP",onClick={section="HTTP"},label={Text("Sesiones del señuelo")})
        }
        if(section=="WIFI") {
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("LIVE" to "Lecturas reales","DEMO" to "Simulaciones","ALL" to "Todas").forEach { (value,label)->
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
                    Text("Cambio reciente",style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
                    Text(delta?.let { if(it>0)"+$it dB" else "$it dB" } ?: "Sin comparación",fontFamily=HoneyStyle.Dots,style=MaterialTheme.typography.headlineSmall)
                    SignalSparkline(liveItems.take(12).mapNotNull { it.wifiMetrics?.rssiDbm })
                }
            }
            if(filtered.isEmpty()) Text("Las comprobaciones aparecerán aquí cuando escanees la red.",style=MaterialTheme.typography.bodyMedium,color=HoneyStyle.Muted)
            filtered.forEach { item ->
                Surface(Modifier.fillMaxWidth().clickable { onOpenWifi(item.checkId) },shape=RoundedCornerShape(8.dp),color=HoneyStyle.Surface,border=BorderStroke(1.dp,HoneyStyle.Line)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(item.wifiMetrics?.rssiDbm?.let { "$it dBm" } ?: "Señal sin dato",fontFamily=HoneyStyle.Dots,style=MaterialTheme.typography.titleLarge)
                            Text("${item.wifiMetrics?.band ?: "Banda desconocida"} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(item.createdAt))}",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                            Text("Recibido ${item.wifiMetrics?.rxLinkSpeedMbps?.let { "$it Mbps" } ?: "—"} · enviado ${item.wifiMetrics?.txLinkSpeedMbps?.let { "$it Mbps" } ?: item.wifiMetrics?.linkSpeedMbps?.let { "$it Mbps" } ?: "—"}",style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
                        }
                        StatusPill(syncLabel(item.syncStatus),syncColor(item.syncStatus))
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text("${sessions.size} sesiones · ${localEvents.size} solicitudes en el teléfono",modifier=Modifier.weight(1f).padding(end=8.dp),style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted,maxLines=2,overflow=TextOverflow.Ellipsis)
                Row {
                    if(localEvents.isNotEmpty()) TextButton(onClick=onClearHttp){Text("Borrar")}
                    TextButton(onClick={onRefreshHttp(selectedSession)}){Text("Actualizar")}
                }
            }
            remoteMessage?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted) }
            if(sessions.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    sessions.forEach { session ->
                        FilterChip(selected=session.sessionId==selectedSession,onClick={onRefreshHttp(session.sessionId)},label={Text("${honeypotSessionLabel(session.state)} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(session.startedAt))}")})
                    }
                }
            }
            localEvents.forEach { event ->
                ActivityEventRow(requestDescription(event.method,event.path),event.sourceAddress,userAgentLabel(event.userAgent),Date(event.occurredAt),event.responseCode,"En el teléfono",event.requestDetails,event.userAgent)
            }
            remoteEvents.forEach { event ->
                ActivityEventRow(requestDescription(event.method,event.path),event.sourceIpMasked,event.userAgentFamily?.let(::userAgentLabel) ?: "Desconocido",Date(event.occurredAt),event.responseCode,"En el servidor")
            }
            if(localEvents.isEmpty()&&remoteEvents.isEmpty()) Text("Las peticiones del señuelo aparecerán aquí cuando inicies una sesión.",style=MaterialTheme.typography.bodyMedium,color=HoneyStyle.Muted)
        }
    }
}

@Composable
private fun ActivityEventRow(title:String,origin:String,agent:String,date:Date,response:Int,source:String,requestDetails:String="{}",rawAgent:String="") {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val metadata=remember(requestDetails) { runCatching { JSONObject(requestDetails) }.getOrDefault(JSONObject()) }
    Surface(Modifier.fillMaxWidth().clickable { expanded=!expanded },shape=RoundedCornerShape(8.dp),color=HoneyStyle.Surface,border=BorderStroke(1.dp,HoneyStyle.Line)) {
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Top) {
                Text(title,modifier=Modifier.weight(1f),fontFamily=HoneyStyle.Mono,style=MaterialTheme.typography.titleSmall,color=HoneyStyle.Ink,maxLines=2,overflow=TextOverflow.Ellipsis)
                StatusPill(source,HoneyStyle.Muted)
            }
            Text("Origen: ${origin.ifBlank { "No disponible" }} · cliente: ${agent.ifBlank { "No identificado" }}",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted,maxLines=2)
            Text("${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(date)} · respuesta $response · ${if(expanded)"ocultar detalles" else "ver detalles"}",style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
            if(expanded) {
                if(rawAgent.isNotBlank()) Text("Agente de usuario declarado: $rawAgent",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                listOf("Puerto de origen" to "source_port","Versión HTTP" to "http_version","Host" to "host","Origen declarado" to "origin","Página de procedencia" to "referer","Idioma" to "accept_language","Tipos aceptados" to "accept","Tipo enviado" to "content_type","Tamaño declarado" to "content_length","Conexión" to "connection").forEach { (label,key) ->
                    metadata.optString(key).takeIf(String::isNotBlank)?.let { Text("$label: $it",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted) }
                }
                if(source=="En el servidor") Text("El servidor conserva una vista resumida de este evento.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            }
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
    val ownDevice=devices.firstOrNull { it.installationId==currentInstallationId }
    var confirmDisable by remember { mutableStateOf(false) }
    var showOtherDevices by rememberSaveable { mutableStateOf(false) }
    var showAutomaticReviews by rememberSaveable { mutableStateOf(false) }
    var showProbeDetails by rememberSaveable { mutableStateOf(false) }
    var showApiDetails by rememberSaveable { mutableStateOf(false) }
    if(confirmDisable) AlertDialog(onDismissRequest={confirmDisable=false},
        title={Text("¿Desactivar este teléfono?")},
        text={Text("Dejará de enviar revisiones y solicitudes nuevas. La información ya guardada no se elimina.")},
        confirmButton={TextButton(onClick={confirmDisable=false;onRevokeDevice()}){Text("Desactivar",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton(onClick={confirmDisable=false}){Text("Cancelar")}})
    Page {
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Eyebrow("Tu instalación y sus ajustes",HoneyStyle.Muted)
            Text("Ajustes",style=MaterialTheme.typography.headlineMedium)
        }
        Glass {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Eyebrow("Este dispositivo",HoneyStyle.Muted)
                StatusPill(when(ownDevice?.active){true->"Disponible";false->"Desactivado";null->"Registrando"},if(ownDevice?.active==true)HoneyStyle.Aqua else HoneyStyle.Amber)
            }
            Text(deviceName.ifBlank { "HoneyPhone" },style=MaterialTheme.typography.titleLarge)
            Text("Nombre que identifica este teléfono en el servidor.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            if(ownDevice?.active!=false) {
                OutlinedTextField(value=deviceName,onValueChange=onDeviceName,label={Text("Nombre del teléfono")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Button(onClick=onSaveDevice,modifier=Modifier.fillMaxWidth()){Text("Guardar nombre",maxLines=1)}
                TextButton(onClick={confirmDisable=true}){Text("Desactivar este teléfono",color=HoneyStyle.Amber)}
            } else {
                Text("El servidor está disponible, pero esta instalación no puede enviar revisiones. Pide al administrador que la habilite.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Amber)
            }
            deviceMessage?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=if(ownDevice?.active==false)HoneyStyle.Amber else HoneyStyle.Muted) }
            OutlinedButton(onClick=onRefreshDevices,modifier=Modifier.fillMaxWidth()){Text("Actualizar estado del teléfono")}
        }
        SettingsDisclosure("Otros teléfonos registrados","${devices.count { it.installationId!=currentInstallationId }} dispositivos",showOtherDevices,{showOtherDevices=!showOtherDevices}) {
            devices.filter { it.installationId!=currentInstallationId }.forEach { device ->
                Surface(shape=RoundedCornerShape(8.dp),color=HoneyStyle.Surface,border=BorderStroke(1.dp,HoneyStyle.Line)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(device.deviceName ?: "HoneyPhone",style=MaterialTheme.typography.titleMedium)
                        Text(if(device.active)"Disponible" else "Desactivado",style=MaterialTheme.typography.labelSmall,color=if(device.active)HoneyStyle.Aqua else HoneyStyle.Muted)
                        Text("Último contacto: ${formatServerTimestamp(device.lastSeenAt)}",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                    }
                }
            }
        }
        SettingsDisclosure("Lecturas automáticas","Cada 15 minutos · pueden aplazarse para ahorrar batería",showAutomaticReviews,{showAutomaticReviews=!showAutomaticReviews}) {
            Text("HoneyPhone intenta guardar una lectura cada 15 minutos. Android puede aplazarla para ahorrar batería; cuando no hay conexión, conserva la lectura en el teléfono y la envía al volver a conectarse.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
        }
        SettingsDisclosure("Pruebas con sitios de referencia",if(dev.honeyphone.app.core.network.ProbeConfig.load()==null)"Aún no están configuradas" else "Activas para los destinos del proyecto",showProbeDetails,{showProbeDetails=!showProbeDetails}) {
            if(dev.honeyphone.app.core.network.ProbeConfig.load()==null) {
                Text("Resolución de nombres compara la respuesta DNS de un dominio de prueba con una referencia independiente. Conexión segura intenta abrir un sitio de prueba y valida su certificado HTTPS.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                Text("La API ya se comunica por HTTPS. Estas comprobaciones de la red son independientes y esperan destinos de prueba del proyecto; no indican un fallo de tu Wi-Fi.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            } else {
                Text("En cada revisión se compara la respuesta DNS del dominio de prueba con una referencia independiente y se valida el certificado HTTPS del sitio configurado.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            }
        }
        SettingsDisclosure("Conexión con el servidor",when(apiConnection){ApiConnectionState.CHECKING->"Comprobando conexión";ApiConnectionState.ONLINE->"Conectado y con base de datos disponible";ApiConnectionState.OFFLINE->"No responde ahora"},showApiDetails,{showApiDetails=!showApiDetails}) {
            Text(when(apiConnection){ApiConnectionState.CHECKING->"Comprobando…";ApiConnectionState.ONLINE->"Conectado y con base de datos disponible";ApiConnectionState.OFFLINE->"No responde ahora"},style=MaterialTheme.typography.titleMedium)
            Text("Esto comprueba el servidor. El estado de este teléfono se muestra por separado arriba.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            TextButton(onClick=onRefreshApi){Text("Comprobar ahora")}
        }
        SecondaryAction("Ver ejemplos",icon=Icons.Default.Science,onClick=onDemos)
    }
}

@Composable
private fun SettingsDisclosure(title:String,summary:String,expanded:Boolean,onToggle:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(12.dp),color=HoneyStyle.Surface,border=BorderStroke(1.dp,HoneyStyle.Line)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick=onToggle),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(title,style=MaterialTheme.typography.titleMedium)
                    Text(summary,style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                }
                Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,contentDescription=if(expanded)"Contraer" else "Ampliar",tint=HoneyStyle.Muted)
            }
            if(expanded) Column(verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
        }
    }
}

private data class ProductTourStep(val icon:ImageVector,val title:String,val purpose:String,val howTo:String,val note:String)

private val PRODUCT_TOUR_STEPS = listOf(
    ProductTourStep(Icons.Default.Wifi,"Revisa tu red Wi-Fi","HoneyPhone lee los datos que Android comparte sobre la conexión: intensidad de señal, banda, canal y velocidades del enlace.","Pulsa «Escanear red». Puedes añadir una etiqueta como «Casa» o «Trabajo» para reconocer la lectura en Actividad.","La velocidad indicada es la del enlace Wi-Fi, no una prueba de velocidad de Internet ni una auditoría completa de seguridad."),
    ProductTourStep(Icons.Default.Science,"Aprende con ejemplos","Los ejemplos muestran situaciones inventadas para explicar cómo leer los indicadores.","Abre «Ver ejemplos» desde Red Wi-Fi. Los ejemplos siempre aparecen identificados y no describen tu red.","No se envían ni se guardan hasta que eliges guardarlos."),
    ProductTourStep(Icons.Default.Security,"Activa el señuelo","El teléfono abre un pequeño servidor web local para registrar solicitudes recibidas mientras está activo.","Pulsa «Activar señuelo» y comparte la dirección mostrada con un equipo de prueba conectado a la misma Wi-Fi. Mantén HoneyPhone en primer plano.","Guarda la hora, la ruta y la dirección de origen en el teléfono. Al sincronizar, envía solo una red aproximada; no conserva el contenido enviado. El router puede impedir conexiones entre equipos."),
    ProductTourStep(Icons.Default.History,"Consulta la actividad","Aquí vuelves a las revisiones, sesiones y solicitudes que quedaron guardadas en el teléfono o sincronizadas con el servidor.","Usa las pestañas «Señal Wi-Fi» y «Sesiones del señuelo». Pulsa Actualizar para traer datos remotos.","Una solicitud aparece aquí solo si alguien abrió la dirección local mientras el señuelo estaba escuchando."),
    ProductTourStep(Icons.Default.Settings,"Revisa el dispositivo y los ajustes","Puedes cambiar el nombre de esta instalación, consultar la conexión con el servidor y revisar las lecturas automáticas.","Las lecturas se intentan cada 15 minutos; Android puede aplazarlas por batería o conectividad.","La API ya usa HTTPS. Las comprobaciones DNS y HTTPS de la red son independientes y esperan destinos de prueba definidos por el proyecto."),
)

@Composable
private fun ProductTourSheet(step:Int,onPrevious:()->Unit,onNext:()->Unit,onFinish:()->Unit,modifier:Modifier=Modifier) {
    val current=PRODUCT_TOUR_STEPS[step.coerceIn(PRODUCT_TOUR_STEPS.indices)]
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=22.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Eyebrow("Recorrido · ${step+1} de ${PRODUCT_TOUR_STEPS.size}",HoneyStyle.Amber)
            TextButton(onClick=onFinish){Text("Omitir")}
        }
        LinearProgressIndicator(progress={(step+1f)/PRODUCT_TOUR_STEPS.size},modifier=Modifier.fillMaxWidth(),color=HoneyStyle.Lime,trackColor=HoneyStyle.Line)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(current.icon,null,tint=HoneyStyle.Lime,modifier=Modifier.size(28.dp))
            Text(current.title,style=MaterialTheme.typography.headlineSmall)
        }
        Text(current.purpose,style=MaterialTheme.typography.bodyLarge,color=HoneyStyle.Ink)
        InfoStrip("Cómo usarlo",current.howTo,HoneyStyle.Aqua)
        InfoStrip("Qué debes saber",current.note,HoneyStyle.Muted)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick=onPrevious,enabled=step>0,modifier=Modifier.weight(1f).heightIn(min=50.dp)) { Text("Anterior") }
            Button(onClick=if(step==PRODUCT_TOUR_STEPS.lastIndex)onFinish else onNext,modifier=Modifier.weight(1f).heightIn(min=50.dp)) {
                Text(if(step==PRODUCT_TOUR_STEPS.lastIndex)"Terminar" else "Siguiente")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HoneypotHomeScreen(status:HoneypotStatus,events:List<HoneypotEvent>,wifiConnected:Boolean,selectedProfile:HoneypotProfile,onSelectProfile:(HoneypotProfile)->Unit,onStart:()->Unit,onStop:()->Unit,onClear:()->Unit) {
    var selectedEvent by remember { mutableStateOf<HoneypotEvent?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val context=LocalContext.current
    val haptics=androidx.compose.ui.platform.LocalHapticFeedback.current
    var notificationNotice by remember { mutableStateOf<String?>(null) }
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) { notificationNotice=null; onStart() }
        else notificationNotice="Para mantener el señuelo visible en segundo plano, permite las notificaciones de HoneyPhone en Ajustes."
    }
    val startWithVisibleNotification={
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else onStart()
    }
    var lastAnimatedEvent by remember { mutableStateOf(events.firstOrNull()?.id) }
    val currentEventId=events.firstOrNull()?.id
    LaunchedEffect(currentEventId,status.phase) {
        if(status.phase==HoneypotPhase.LISTENING && currentEventId!=null && currentEventId!=lastAnimatedEvent) {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            lastAnimatedEvent=currentEventId
        }
    }
    if(confirmClear) AlertDialog(onDismissRequest={confirmClear=false},title={Text("¿Borrar solicitudes guardadas?")},
        text={Text("Esto elimina del teléfono las solicitudes recibidas y guardadas localmente.")},
        confirmButton={TextButton(onClick={onClear();confirmClear=false}){Text("Borrar")}},
        dismissButton={TextButton(onClick={confirmClear=false}){Text("Cancelar")}})
    Page {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Eyebrow("Servidor señuelo local · puerto 8080",HoneyStyle.Muted)
                Text("Señuelo",style=MaterialTheme.typography.headlineMedium)
            }
            StatusPill(when(status.phase){HoneypotPhase.STARTING->"Activando";HoneypotPhase.LISTENING->"Escuchando";HoneypotPhase.ERROR->"No se inició";HoneypotPhase.STOPPED->"Detenido"},
                when(status.phase){HoneypotPhase.LISTENING->HoneyStyle.Lime;HoneypotPhase.ERROR->HoneyStyle.Amber;else->HoneyStyle.Muted})
        }
        if(!wifiConnected && status.phase!=HoneypotPhase.LISTENING) InfoStrip("Conecta a una red Wi‑Fi","El señuelo solo puede aceptar solicitudes desde una red Wi‑Fi activa.",HoneyStyle.Amber)
        Box(Modifier.fillMaxWidth().height(210.dp),contentAlignment=Alignment.Center) {
            HoneypotFlytrap(status.phase==HoneypotPhase.LISTENING,currentEventId,size=210.dp)
        }
        Glass {
            Eyebrow("Escenario visual",HoneyStyle.Muted)
            Text(honeypotProfileTitle(selectedProfile),style=MaterialTheme.typography.titleMedium)
            Text(honeypotProfileDescription(selectedProfile),style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                HoneypotProfile.entries.forEach { profile ->
                    FilterChip(selected=selectedProfile==profile,onClick={onSelectProfile(profile)},enabled=status.phase !in setOf(HoneypotPhase.STARTING,HoneypotPhase.LISTENING),label={Text(honeypotProfileShortName(profile),maxLines=1)})
                }
            }
            Text("Los formularios son ficticios. HoneyPhone registra la ruta y el intento, pero no lee ni guarda los campos escritos. Usa solo esta red de prueba o una red con autorización.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end=12.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(if(status.phase==HoneypotPhase.LISTENING)"Esperando solicitudes" else "Servidor detenido",style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(if(status.phase==HoneypotPhase.LISTENING)"Servidor web local · puerto 8080" else "Actívalo para aceptar solicitudes desde esta Wi-Fi",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            }
            Text("${events.size}",fontFamily=HoneyStyle.Dots,fontSize=28.sp,color=HoneyStyle.Ink)
        }
        if(status.phase==HoneypotPhase.LISTENING && status.address!=null) {
            val url="${status.address}${status.profile.entryPath}"
            Surface(Modifier.fillMaxWidth().clickable {
                val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("HoneyPhone URL",url))
                Toast.makeText(context,"Enlace copiado",Toast.LENGTH_SHORT).show()
            },shape=RoundedCornerShape(8.dp),color=HoneyStyle.Surface,border=BorderStroke(1.dp,HoneyStyle.Line)) {
                Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(status.serviceName?.let { "$it · nombre local" } ?: "Dirección de acceso",fontFamily=HoneyStyle.Mono,color=HoneyStyle.Ink,style=MaterialTheme.typography.titleSmall)
                        Text(url,fontFamily=HoneyStyle.Mono,color=HoneyStyle.Muted,style=MaterialTheme.typography.labelSmall)
                    }
                    Icon(Icons.Default.ContentCopy,contentDescription="Copiar enlace",tint=HoneyStyle.Muted)
                }
            }
            status.message?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Amber) }
        }
        if(status.phase==HoneypotPhase.ERROR) status.message?.let { InfoStrip("No se pudo iniciar",it,HoneyStyle.Amber) }
        PrimaryAction(if(status.phase==HoneypotPhase.LISTENING)"Detener señuelo" else if(status.phase==HoneypotPhase.STARTING)"Activando…" else "Activar señuelo",
            enabled=status.phase!=HoneypotPhase.STARTING && (wifiConnected || status.phase==HoneypotPhase.LISTENING),onClick=if(status.phase==HoneypotPhase.LISTENING)onStop else startWithVisibleNotification)
        notificationNotice?.let { InfoStrip("Notificación necesaria",it,HoneyStyle.Amber) }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Eyebrow("Solicitudes recibidas",HoneyStyle.Muted)
            if(events.isNotEmpty()) TextButton(onClick={confirmClear=true}) { Text("Borrar") }
        }
        if(events.isEmpty()) Text("Las solicitudes aparecerán aquí cuando otro equipo abra la dirección mientras el señuelo esté activo.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
        events.take(4).forEach { event ->
            Surface(Modifier.fillMaxWidth().clickable { selectedEvent=event },shape=RoundedCornerShape(8.dp),color=HoneyStyle.Surface,border=BorderStroke(1.dp,HoneyStyle.Line)) {
                Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(requestDescription(event.method,event.path),fontFamily=HoneyStyle.Mono,color=HoneyStyle.Ink,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text("${event.sourceAddress} · ${userAgentLabel(event.userAgent)}",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted,maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                    Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(event.occurredAt)),fontFamily=HoneyStyle.Mono,style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
                }
            }
        }
    }
    selectedEvent?.let { event ->
        ModalBottomSheet(onDismissRequest={selectedEvent=null},containerColor=HoneyStyle.Surface) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Eyebrow("Solicitud recibida",HoneyStyle.Amber)
                Text(requestDescription(event.method,event.path),style=MaterialTheme.typography.headlineSmall)
                Text("${DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(event.occurredAt))} · respuesta ${event.responseCode}",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                MetricChip("Dirección de origen",event.sourceAddress,Modifier.fillMaxWidth())
                MetricChip("Navegador o herramienta",userAgentLabel(event.userAgent),Modifier.fillMaxWidth())
                MetricChip("Sistema declarado por el cliente",clientOsLabel(event.userAgent),Modifier.fillMaxWidth())
                val metadata=remember(event.requestDetails) { runCatching { JSONObject(event.requestDetails) }.getOrDefault(JSONObject()) }
                val detailFields=listOf(
                    "Puerto de origen" to "source_port",
                    "Versión HTTP" to "http_version",
                    "Host solicitado" to "host",
                    "Origen declarado" to "origin",
                    "Página de procedencia" to "referer",
                    "Idioma declarado" to "accept_language",
                    "Tipos de contenido aceptados" to "accept",
                    "Tipo de contenido enviado" to "content_type",
                    "Tamaño declarado del cuerpo" to "content_length",
                    "Conexión" to "connection",
                )
                detailFields.forEach { (label,key) -> metadata.optString(key).takeIf(String::isNotBlank)?.let { MetricChip(label,it,Modifier.fillMaxWidth()) } }
                if(event.userAgent.isNotBlank()) MetricChip("Identificación declarada por el cliente",event.userAgent,Modifier.fillMaxWidth())
                Text("La dirección es solo la IP visible en esta red. El navegador no revela la MAC ni la ubicación precisa. Se guardan encabezados permitidos; nunca el cuerpo del formulario, cookies ni credenciales. Al sincronizar, la IP se reduce a una red aproximada.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
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
    StatusPill(if(source==ReviewSource.DEMO)"Ejemplo simulado" else if(compact)"Lectura real" else "Lectura real de tu red",
        if(source==ReviewSource.DEMO)HoneyStyle.Amber else HoneyStyle.Aqua)
}
@Composable
private fun StatusPill(text: String,color: Color) {
    Surface(color=color.copy(alpha=0.08f),contentColor=color,shape=RoundedCornerShape(8.dp),
        border=BorderStroke(1.dp,color.copy(alpha=0.18f))) {
        Text(text,Modifier.padding(horizontal=10.dp,vertical=6.dp),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}
private fun honeypotSessionLabel(state:String)=when(state.uppercase()) {
    "RUNNING"->"En curso"
    "STOPPED"->"Finalizada"
    "INTERRUPTED"->"Interrumpida"
    else->"Estado desconocido"
}
private fun requestDescription(method:String,path:String)="${when(method.uppercase()){"GET"->"Consulta";"HEAD"->"Comprobación";"POST"->"Envío";else->"Solicitud"}} · ${path.ifBlank { "ruta no disponible" }}"
private fun honeypotProfileTitle(profile:HoneypotProfile)=when(profile) {
    HoneypotProfile.LOGIN_PORTAL->"Inicio de sesión"
    HoneypotProfile.ADMIN_CONSOLE->"Consola de administración"
    HoneypotProfile.COMPROMISED_SITE->"Sitio intervenido"
}
private fun honeypotProfileShortName(profile:HoneypotProfile)=when(profile) {
    HoneypotProfile.LOGIN_PORTAL->"Acceso"
    HoneypotProfile.ADMIN_CONSOLE->"Consola"
    HoneypotProfile.COMPROMISED_SITE->"Intervenido"
}
private fun honeypotProfileDescription(profile:HoneypotProfile)=when(profile) {
    HoneypotProfile.LOGIN_PORTAL->"Muestra un formulario de acceso ficticio. Al enviarlo se registra el intento en /login; los campos no se leen ni se guardan."
    HoneypotProfile.ADMIN_CONSOLE->"Presenta un panel con datos inventados y enlaces señuelo para registrar qué secciones intenta abrir el visitante."
    HoneypotProfile.COMPROMISED_SITE->"Muestra una página ficticia que parece intervenida. Registra la visita y el acceso a la ruta del panel."
}
private fun userAgentLabel(value:String)=when(value.uppercase()) {
    "BROWSER"->"Navegador"
    "SCANNER"->"Herramienta de análisis"
    "OTHER"->"Otro cliente"
    "UNKNOWN"->"No identificado"
    else->when {
        value.contains("curl",true)||value.contains("python-requests",true)||value.contains("nmap",true)->"Herramienta de análisis"
        value.contains("mozilla",true)||value.contains("chrome",true)||value.contains("safari",true)||value.contains("firefox",true)->"Navegador web"
        value.isBlank()->"No identificado"
        else->"Otro cliente web"
    }
}
private fun clientOsLabel(value:String)=when {
    value.contains("Android",true)->"Android (declarado por el navegador)"
    value.contains("iPhone",true)||value.contains("iPad",true)||value.contains("iOS",true)->"iOS / iPadOS (declarado por el navegador)"
    value.contains("Windows",true)->"Windows (declarado por el navegador)"
    value.contains("Mac OS",true)||value.contains("Macintosh",true)->"macOS (declarado por el navegador)"
    value.contains("Linux",true)->"Linux (declarado por el navegador)"
    else->"No identificado"
}
private fun formatServerTimestamp(value:String):String=runCatching {
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(Instant.parse(value).toEpochMilli()))
}.getOrDefault("Sin fecha disponible")
private fun signalTitle(kind: SignalKind)=when(kind) {
    SignalKind.WIFI_SECURITY->"Tipo de seguridad Wi-Fi"
    SignalKind.CAPTIVE_PORTAL->"Acceso a la red"
    SignalKind.DNS->"Resolución DNS"
    SignalKind.HTTPS->"Conexión HTTPS"
}
private fun signalPurpose(kind: SignalKind)=when(kind) {
    SignalKind.WIFI_SECURITY->"Muestra el tipo de cifrado que Android reporta para esta red. HoneyPhone lo consulta, pero no cambia ni refuerza la protección del router."
    SignalKind.CAPTIVE_PORTAL->"Indica si Android detectó una página de acceso, como la que pide iniciar sesión o aceptar condiciones en un hotel."
    SignalKind.DNS->"Compara la respuesta DNS de un dominio de prueba con una referencia independiente del proyecto."
    SignalKind.HTTPS->"Conecta con un sitio de prueba y valida que su certificado HTTPS sea confiable."
}
private fun signalIcon(kind: SignalKind)=when(kind) {
    SignalKind.WIFI_SECURITY->Icons.Default.Wifi
    SignalKind.CAPTIVE_PORTAL->Icons.Default.Language
    SignalKind.DNS->Icons.Default.Dns
    SignalKind.HTTPS->Icons.Default.Lock
}
private fun signalStatus(signal: SignalPresentation)=when {
    signal.reasonCode=="NOT_CONFIGURED"->"Aún no disponible"
    signal.reasonCode=="NO_WIFI"->"Conecta una red Wi-Fi"
    signal.reasonCode=="NETWORK_CHANGED"->"La red cambió durante la lectura"
    signal.reasonCode=="PERMISSION_DENIED"->"Android no permitió leer el dato"
    signal.outcome==SignalOutcome.ATTENTION->"Requiere revisión"
    signal.outcome==SignalOutcome.UNKNOWN->"No se pudo comprobar"
    signal.outcome==SignalOutcome.NOT_APPLICABLE->"No aplica en esta lectura"
    signal.reasonCode=="OS_PORTAL_NOT_DETECTED"->"Android no detectó una página de acceso"
    signal.reasonCode=="OS_SECURITY_REPORTED"->"Android informó la protección de la red"
    else->"Sin diferencia observada"
}
private fun syncLabel(status: String)=when(status.substringBefore(":").lowercase()) {
    "synced"->"Guardada en el servidor"
    "syncing"->"Enviando al servidor"
    "failed"->"Pendiente · reintentar envío"
    else->"Guardada solo en este teléfono"
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
