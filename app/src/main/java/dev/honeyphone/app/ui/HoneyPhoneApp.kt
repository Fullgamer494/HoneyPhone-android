package dev.honeyphone.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoneyPhoneApp(viewModel: HoneyPhoneViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val screenStateHolder=rememberSaveableStateHolder()
    var showStartupAnimation by remember { mutableStateOf(true) }
    var startupAnimationStarted by remember { mutableStateOf(false) }
    var showProductTour by rememberSaveable { mutableStateOf(false) }
    var productTourStep by rememberSaveable { mutableIntStateOf(0) }
    var productTourBounds by remember { mutableStateOf<Rect?>(null) }
    var transientMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(startupAnimationStarted) {
        if (startupAnimationStarted) {
            delay(4_800)
            showStartupAnimation = false
        }
    }
    LaunchedEffect(Unit) {
        delay(10_000)
        showStartupAnimation = false
    }
    LaunchedEffect(Unit) {
        if (!context.getSharedPreferences("honeyphone_ui", Context.MODE_PRIVATE)
                .getBoolean("product_tour_completed", false)) showProductTour = true
    }
    LaunchedEffect(transientMessage) {
        if(transientMessage!=null) { delay(2400);transientMessage=null }
    }
    LaunchedEffect(showProductTour,productTourStep,state.authenticated) {
        if(!showProductTour || !state.authenticated) return@LaunchedEffect
        productTourBounds=null
        when(PRODUCT_TOUR_STEPS[productTourStep.coerceIn(PRODUCT_TOUR_STEPS.indices)].destination) {
            AppDestination.HOME -> viewModel.openHome()
            AppDestination.DEMO_GALLERY -> viewModel.openDemoGallery()
            AppDestination.HONEYPOT -> viewModel.openHoneypot()
            AppDestination.HISTORY -> viewModel.openHistory()
            AppDestination.SETTINGS -> viewModel.openProfile()
            AppDestination.PROFILE -> viewModel.openProfile()
            else -> viewModel.openHome()
        }
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
    val canGoBack = state.destination in setOf(AppDestination.LIVE_REVIEW, AppDestination.DEMO_GALLERY, AppDestination.RESULT, AppDestination.DETAIL, AppDestination.PROFILE)
    BackHandler(enabled=canGoBack) { viewModel.goBack() }
    Box(Modifier.fillMaxSize()) {
        TechBackground()
        if (!state.authenticated) {
            AccountGate(
                busy=state.authBusy,
                message=state.authMessage,
                apiConfigured=dev.honeyphone.app.BuildConfig.API_BASE_URL.startsWith("https://") ||
                    (dev.honeyphone.app.BuildConfig.DEBUG && dev.honeyphone.app.BuildConfig.API_BASE_URL.startsWith("http://127.0.0.1")),
                googleConfigured=dev.honeyphone.app.BuildConfig.GOOGLE_SERVER_CLIENT_ID.isNotBlank(),
                onSubmit=viewModel::authenticate,
                onGoogleToken=viewModel::authenticateWithGoogle,
                onResendVerification=viewModel::resendEmailVerification,
                onPasswordRecovery=viewModel::requestPasswordRecovery,
            )
        } else {
        Scaffold(
            modifier=Modifier.fillMaxSize(),
            containerColor=Color.Transparent, contentColor=HoneyStyle.Ink,
            topBar={
                TopAppBar(
                    title={
                        Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Image(painterResource(R.drawable.logo_sin_fondo),contentDescription=null,modifier=Modifier.size(36.dp).padding(3.dp))
                            Text("HoneyPhone", style=MaterialTheme.typography.titleMedium)
                        }
                    },
                    navigationIcon={
                        if(canGoBack) IconButton(onClick=viewModel::goBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription="Volver")
                        }
                    },
                    colors=TopAppBarDefaults.topAppBarColors(containerColor=HoneyStyle.Background.copy(alpha=0.96f)),
                )
            },
            bottomBar={
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=14.dp,vertical=7.dp)) {
                    Surface(
                        modifier=Modifier.fillMaxWidth(),
                        shape=RoundedCornerShape(24.dp),
                        color=HoneyStyle.Surface.copy(alpha=0.98f),
                        border=BorderStroke(1.dp,HoneyStyle.Line),
                        shadowElevation=16.dp,
                    ) {
                        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal=4.dp,vertical=5.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            listOf("Wi-Fi" to AppDestination.HOME,"Señuelo" to AppDestination.HONEYPOT,
                                "Actividad" to AppDestination.HISTORY,"Cuenta" to AppDestination.PROFILE).forEach { (label,destination) ->
                                val selected=state.activeTab==destination
                                Column(
                                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                                        .clickable(role=Role.Tab) {
                                            when(destination) {
                                                AppDestination.HISTORY -> viewModel.openHistory()
                                                AppDestination.HONEYPOT -> viewModel.openHoneypot()
                                                AppDestination.SETTINGS -> viewModel.openProfile()
                                                AppDestination.PROFILE -> viewModel.openProfile()
                                                else -> viewModel.openHome()
                                            }
                                        }.padding(horizontal=2.dp,vertical=2.dp),
                                    horizontalAlignment=Alignment.CenterHorizontally,
                                    verticalArrangement=Arrangement.spacedBy(2.dp),
                                ) {
                                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(13.dp))
                                        .background(if(selected)Color(0xFF242424) else Color.Transparent),
                                        contentAlignment=Alignment.Center) {
                                        HoneyPhoneNavGlyph(destination,selected)
                                    }
                                    Text(label,maxLines=1,fontSize=9.sp,lineHeight=11.sp,
                                        color=if(selected)HoneyStyle.Ink else HoneyStyle.Muted,
                                        fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AnimatedContent(
                    targetState=state.destination,
                    transitionSpec={
                        (fadeIn(tween(220))+slideInHorizontally(tween(220)){it/12}) togetherWith
                            (fadeOut(tween(160))+slideOutHorizontally(tween(160)){-it/12})
                    },
                    label="honeyphone-screen-transition",
                ) { destination -> screenStateHolder.SaveableStateProvider(destination.name) { when(destination) {
                    AppDestination.HOME -> HomeScreen(state.networkLabelDraft,state.history,state.review,state.wifiConnected,viewModel::setNetworkLabel,viewModel::openLivePreview,viewModel::openDemoGallery,viewModel::openHistoryReview,viewModel::syncCurrent,viewModel::exportCurrent,state.message,
                        tourTarget=PRODUCT_TOUR_STEPS.getOrNull(productTourStep)?.target,onTourBounds={productTourBounds=it})
                    AppDestination.LIVE_REVIEW -> LiveScreen(state.working,state.message,viewModel::cancelReview)
                    AppDestination.DEMO_GALLERY -> DemoScreen(state.scenarios,viewModel::openDemo,
                        tourTarget=PRODUCT_TOUR_STEPS.getOrNull(productTourStep)?.target,onTourBounds={productTourBounds=it})
                    AppDestination.RESULT -> {
                        state.review?.let {
                            ResultScreen(it,state.localSaved,state.working,state.message,
                                state.history.find { row->row.checkId==it.checkId }?.syncStatus ?: "pending",
                                viewModel::openDetail,viewModel::saveDemo,viewModel::syncCurrent,viewModel::exportCurrent,viewModel::deleteCurrent)
                        }
                        state.selectedSignal?.let { signal -> ModalBottomSheet(onDismissRequest={viewModel.goBack()},containerColor=HoneyStyle.Surface) {
                            SignalDetailsSheet(signal,Modifier.navigationBarsPadding())
                        } }
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
                    AppDestination.HISTORY -> ActivityScreen(state.history,state.honeypotEvents,state.honeypotSessions,state.remoteHoneypotEvents,state.selectedHoneypotSessionId,state.honeypotRemoteMessage,state.working,state.message,viewModel::refreshHistory,viewModel::refreshHoneypotSessions,viewModel::clearHoneypotEvents,viewModel::openHistoryReview,viewModel::syncPendingReviews,viewModel::deletePendingLocalReviews,
                        tourTarget=PRODUCT_TOUR_STEPS.getOrNull(productTourStep)?.target,onTourBounds={productTourBounds=it})
                    AppDestination.HONEYPOT -> HoneypotHomeScreen(state.honeypotStatus,state.honeypotEvents,state.wifiConnected,
                        state.selectedHoneypotProfile,viewModel::selectHoneypotProfile,
                        viewModel::startHoneypot,viewModel::stopHoneypot,viewModel::clearHoneypotEvents,
                        onToast={transientMessage=it},tourTarget=PRODUCT_TOUR_STEPS.getOrNull(productTourStep)?.target,onTourBounds={productTourBounds=it})
                    AppDestination.SETTINGS, AppDestination.PROFILE -> ProfileScreen(
                        email=state.accountEmail,deleting=state.accountDeleteBusy,message=state.accountMessage,
                        devices=state.devices,deviceName=state.deviceNameDraft,deviceMessage=state.deviceMessage,
                        currentInstallationId=state.currentInstallationId,apiConnection=state.apiConnection,
                        automaticScansEnabled=state.automaticWifiScansEnabled,
                        onAutomaticScansChanged=viewModel::setAutomaticWifiScansEnabled,
                        onDeviceName=viewModel::setDeviceName,onSaveDevice=viewModel::saveDeviceName,onRevokeDevice=viewModel::revokeDevice,
                        onRefreshDevices=viewModel::refreshDevices,onRefreshApi=viewModel::refreshApiStatus,onDemos=viewModel::openDemoGallery,
                        onTour={productTourStep=0;showProductTour=true},onSignOut=viewModel::signOut,onDeleteAccount=viewModel::deleteAccount,
                        tourTarget=PRODUCT_TOUR_STEPS.getOrNull(productTourStep)?.target,onTourBounds={productTourBounds=it})
                } }
                }
            }
        }
        }
        if(showProductTour && state.authenticated) ProductTourOverlay(
            step=productTourStep,
            targetBounds=productTourBounds,
            onPrevious={productTourStep=(productTourStep-1).coerceAtLeast(0)},
            onNext={productTourStep=(productTourStep+1).coerceAtMost(PRODUCT_TOUR_STEPS.lastIndex)},
            onFinish={
                showProductTour=false
                context.getSharedPreferences("honeyphone_ui",Context.MODE_PRIVATE).edit().putBoolean("product_tour_completed",true).apply()
            },
        )
        AnimatedVisibility(
            visible=transientMessage!=null,
            modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=104.dp).padding(horizontal=20.dp),
            enter=fadeIn()+slideInVertically { it/3 },
            exit=fadeOut()+slideOutVertically { it/3 },
        ) {
            Surface(shape=RoundedCornerShape(18.dp),color=HoneyStyle.Surface,
                border=BorderStroke(1.dp,HoneyStyle.Line),shadowElevation=12.dp) {
                Row(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(HoneyStyle.Amber))
                    Text(transientMessage.orEmpty(),style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Ink)
                }
            }
        }
        AnimatedVisibility(
            visible=showStartupAnimation,
            modifier=Modifier.fillMaxSize(),
            enter=fadeIn(animationSpec=tween(120)),
            exit=fadeOut(animationSpec=tween(300)),
        ) {
            StartupAnimationOverlay(onAnimationStarted={startupAnimationStarted=true})
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
private fun Heading(title: String) {
    Text(title,style=MaterialTheme.typography.headlineMedium)
}
@Composable
private fun PrimaryAction(text: String,enabled: Boolean=true,modifier:Modifier=Modifier,onClick: ()->Unit) {
    Button(onClick=onClick,enabled=enabled,modifier=modifier.fillMaxWidth().heightIn(min=54.dp),
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
private fun HomeScreen(label:String,history:List<ReviewHistoryItem>,review:ReviewPresentation?,wifiConnected:Boolean,onLabel:(String)->Unit,onLive: ()->Unit,onDemo: ()->Unit,onOpenReview:(String)->Unit,onSync:()->Unit,onExport:()->Unit,message:String?,tourTarget:ProductTourTarget?,onTourBounds:(Rect)->Unit) {
    var selectedSignal by remember { mutableStateOf<SignalPresentation?>(null) }
    var showIndicators by rememberSaveable { mutableStateOf(false) }
    val latest=history.filter { it.source==ReviewSource.LIVE }.maxByOrNull { it.createdAt }
    val metrics=latest?.wifiMetrics
    val latestReview=review?.takeIf { it.source==ReviewSource.LIVE && it.checkId==latest?.checkId }
    val signals=latestReview?.signals?.associateBy { it.kind }.orEmpty()
    Page {
        Text("Red Wi-Fi",style=MaterialTheme.typography.headlineSmall)
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
                MetricChip("Velocidad de recepción",metrics?.rxLinkSpeedMbps?.let { "$it Mbps" } ?: "Sin dato",Modifier.weight(1f))
                MetricChip("Velocidad de envío",metrics?.txLinkSpeedMbps?.let { "$it Mbps" } ?: metrics?.linkSpeedMbps?.let { "$it Mbps" } ?: "Sin dato",Modifier.weight(1f))
            }
            MetricChip("Acceso a Internet",when(metrics?.internetValidated){true->"Disponible";false->"Sin validar";null->"Aún sin revisar"},Modifier.fillMaxWidth())
            OutlinedTextField(value=label,onValueChange=onLabel,modifier=Modifier.fillMaxWidth(),singleLine=true,
                label={Text("Etiqueta opcional")},placeholder={Text("Ej. Sala A")},isError=!validManualLabel(label))
            PrimaryAction("Escanear red",enabled=wifiConnected&&validManualLabel(label),onClick=onLive,
                modifier=productTourAnchor(tourTarget==ProductTourTarget.WIFI_SCAN,onTourBounds))
            Text(if(wifiConnected)"La revisión empieza cuando la solicitas." else "Conecta a una red Wi‑Fi para escanear; los datos móviles solos no cuentan como lectura.",
                style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
        }
        Eyebrow("Última revisión",HoneyStyle.Muted)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            latest?.let { item ->
                OutlinedButton(
                    onClick={onOpenReview(item.checkId)},
                    modifier=Modifier.weight(1f).heightIn(min=48.dp),
                    shape=RoundedCornerShape(14.dp),
                    border=BorderStroke(1.dp,HoneyStyle.Line),
                    colors=ButtonDefaults.outlinedButtonColors(contentColor=HoneyStyle.Ink),
                    contentPadding=PaddingValues(horizontal=10.dp,vertical=8.dp),
                ) {
                    Text("Ver informe",modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(16.dp))
                }
            }
            OutlinedButton(
                onClick={showIndicators=!showIndicators},
                enabled=latest!=null,
                modifier=Modifier.weight(1f).heightIn(min=48.dp),
                shape=RoundedCornerShape(14.dp),
                border=BorderStroke(1.dp,HoneyStyle.Line),
                colors=ButtonDefaults.outlinedButtonColors(contentColor=HoneyStyle.Ink),
                contentPadding=PaddingValues(horizontal=10.dp,vertical=8.dp),
            ) {
                Icon(if(showIndicators)Icons.Default.VisibilityOff else Icons.Default.Visibility,null,Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if(showIndicators)"Ocultar señales" else "Ver señales",modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
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
        Heading(if(working)"Consultando la conexión…" else "No se completó la lectura")
        if(working) LinearProgressIndicator(Modifier.fillMaxWidth(),color=HoneyStyle.Lime,trackColor=HoneyStyle.Line)
        InfoStrip("Qué estamos leyendo",message ?: "Datos disponibles del Wi-Fi activo. La revisión se guardará en este teléfono.")
        InfoStrip("Pruebas de referencia",if(dev.honeyphone.app.core.network.ProbeConfig.load()==null)"Las pruebas de resolución de nombres y conexión segura todavía no están activas. El equipo del proyecto debe definir un destino propio; no indica un problema de tu Wi-Fi." else "Las pruebas de resolución de nombres y conexión segura consultan destinos del proyecto con límites de tiempo. Si cambia la red, se descartan los datos.",HoneyStyle.Muted)
        if(working) SecondaryAction("Cancelar revisión",onClick=onCancel)
    }
}

@Composable
private fun DemoScreen(scenarios: List<DemoScenario>,onOpen: (String)->Unit,tourTarget:ProductTourTarget?,onTourBounds:(Rect)->Unit) {
    Page {
        Heading("Aprende a leer los resultados")
        Text("Son ejemplos simulados. No describen tu conexión ni se guardan hasta que tú lo pidas.",color=HoneyStyle.Muted,style=MaterialTheme.typography.bodyMedium)
        scenarios.forEachIndexed { i,scenario ->
            Glass(productTourAnchor(tourTarget==ProductTourTarget.DEMO_SAMPLE&&i==0,onTourBounds).clickable(role=Role.Button){onOpen(scenario.checkId)}) {
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
    if(confirmDelete) HoneyAlertDialog(onDismissRequest={confirmDelete=false},
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
            if(!localSaved&&demo) PrimaryAction("Guardar este ejemplo",enabled=!working,onClick=onSave)
            else {
                StatusPill(syncLabel(syncStatus),syncColor(syncStatus))
                PrimaryAction(if(working)"Procesando…" else if(synced)"Volver a enviar al servidor" else "Enviar al servidor",enabled=!working,onClick=onSync)
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
private fun activityFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor=HoneyStyle.Surface,
    selectedContainerColor=Color(0xFF292929),
    labelColor=HoneyStyle.Muted,
    selectedLabelColor=HoneyStyle.Ink,
)

private data class HoneypotMetricEvent(
    val id:String,
    val occurredAt:Long,
    val method:String,
    val responseCode:Int,
)

@Composable
private fun ActivityScreen(
    wifiItems:List<ReviewHistoryItem>,localEvents:List<HoneypotEvent>,sessions:List<HoneypotSessionInfo>,remoteEvents:List<HoneypotRemoteEvent>,selectedSession:String?,remoteMessage:String?,working:Boolean,message:String?,
    onRefreshWifi:()->Unit,onRefreshHttp:(String?)->Unit,onClearHttp:()->Unit,onOpenWifi:(String)->Unit,onSyncPending:(List<String>)->Unit,onDeletePending:(List<String>)->Unit,
    tourTarget:ProductTourTarget?,onTourBounds:(Rect)->Unit,
) {
    var section by remember { mutableStateOf("WIFI") }
    var labelFilter by remember { mutableStateOf<String?>(null) }
    var sourceFilter by remember { mutableStateOf("LIVE") }
    var confirmDeletePending by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteIds by remember { mutableStateOf(emptyList<String>()) }
    var pendingDeleteScope by rememberSaveable { mutableStateOf("revisiones") }
    val liveItems=wifiItems.filter { it.source==ReviewSource.LIVE }.sortedByDescending { it.createdAt }
    val latest=when(sourceFilter){"DEMO"->wifiItems.filter { it.source==ReviewSource.DEMO }.sortedByDescending { it.createdAt };"ALL"->wifiItems.sortedByDescending { it.createdAt };else->liveItems}
    val filtered=latest.filter { labelFilter==null || it.networkLabel.orEmpty()==labelFilter }
    val chartItems=filtered.filter { it.source==ReviewSource.LIVE }.sortedByDescending { it.createdAt }.take(12)
    val pendingItems=latest.filter { it.syncStatus.substringBefore(":").lowercase() in setOf("pending","failed") }
    val syncingCount=latest.count { it.syncStatus.substringBefore(":").equals("syncing",ignoreCase=true) }
    val pendingScope=when(sourceFilter) {
        "LIVE" -> "lecturas reales"
        "DEMO" -> "simulaciones"
        else -> "lecturas reales y simulaciones"
    }
    val visibleLocalEvents=remember(localEvents,selectedSession) {
        if(selectedSession==null) localEvents else localEvents.filter { it.sessionId==selectedSession }
    }
    val visibleRemoteEvents=remember(visibleLocalEvents,remoteEvents) {
        val localIds=visibleLocalEvents.mapTo(mutableSetOf()) { it.id }
        remoteEvents.filterNot { it.eventId in localIds }
    }
    val honeypotMetricEvents=remember(visibleLocalEvents,remoteEvents) {
        (visibleLocalEvents.map { HoneypotMetricEvent(it.id,it.occurredAt,it.method,it.responseCode) }+
            remoteEvents.map { HoneypotMetricEvent(it.eventId,it.occurredAt,it.method,it.responseCode) })
            .distinctBy { it.id }
            .sortedBy { it.occurredAt }
    }
    val allHoneypotEventCount=remember(localEvents,remoteEvents) {
        (localEvents.map { it.id }+remoteEvents.map { it.eventId }).distinct().size
    }
    val honeypotSourceNote=when {
        visibleLocalEvents.isNotEmpty()&&remoteEvents.isNotEmpty() -> "Datos del teléfono y del servidor; los eventos repetidos se cuentan una sola vez."
        visibleLocalEvents.isNotEmpty() -> "Eventos guardados localmente en este teléfono."
        remoteEvents.isNotEmpty() -> "Eventos consultados en el servidor."
        else -> ""
    }
    if(confirmDeletePending) HoneyAlertDialog(
        onDismissRequest={confirmDeletePending=false},
        title={Text("¿Eliminar estos pendientes?")},
        text={Text("Se borrarán ${pendingDeleteIds.size} ${pendingDeleteScope} guardadas solo en este teléfono, junto con sus señales. Lo que ya se envió al servidor se conserva. Esta acción no se puede deshacer.")},
        confirmButton={TextButton(onClick={confirmDeletePending=false;onDeletePending(pendingDeleteIds)}){Text("Eliminar del teléfono",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton(onClick={confirmDeletePending=false}){Text("Cancelar")}},
    )
    Page {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Actividad",style=MaterialTheme.typography.headlineMedium) }
            Text("${wifiItems.size+sessions.size+allHoneypotEventCount}",fontFamily=HoneyStyle.Dots,style=MaterialTheme.typography.headlineSmall,color=HoneyStyle.Ink)
        }
        Row(Modifier.then(productTourAnchor(tourTarget==ProductTourTarget.ACTIVITY_SECTIONS,onTourBounds)).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=section=="WIFI",onClick={section="WIFI"},label={Text("Señal Wi-Fi")},colors=activityFilterChipColors())
            FilterChip(selected=section=="HTTP",onClick={section="HTTP"},label={Text("Sesiones del señuelo")},colors=activityFilterChipColors())
        }
        if(section=="WIFI") {
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("LIVE" to "Lecturas reales","DEMO" to "Simulaciones","ALL" to "Todas").forEach { (value,label)->
                    FilterChip(selected=sourceFilter==value,onClick={sourceFilter=value},label={Text(label)},colors=activityFilterChipColors())
                }
            }
            if(sourceFilter=="DEMO") InfoStrip(
                "¿Para qué sirven las simulaciones?",
                "Son ejemplos ficticios que guardaste desde la galería. Sirven para practicar cómo interpretar los resultados; no son lecturas de este teléfono ni comprueban tu Wi-Fi."
            ,HoneyStyle.Amber)
            if(pendingItems.isNotEmpty()||syncingCount>0) {
                Surface(shape=RoundedCornerShape(12.dp),color=HoneyStyle.Surface,border=BorderStroke(1.dp,HoneyStyle.Line)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                Text(
                                    when(sourceFilter) {
                                        "LIVE" -> "Lecturas reales sin enviar"
                                        "DEMO" -> "Simulaciones sin enviar"
                                        else -> "Pendientes de ambas pestañas"
                                    },
                                    style=MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    if(pendingItems.isNotEmpty()) "${pendingItems.size} guardadas solo en este teléfono"
                                    else "No hay elementos en espera para enviar.",
                                    style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted
                                )
                            }
                            if(pendingItems.isNotEmpty()) StatusPill("${pendingItems.size} pendientes",HoneyStyle.Amber)
                        }
                        if(syncingCount>0) Text("$syncingCount ${if(syncingCount==1)"envío está" else "envíos están"} en curso.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                        if(pendingItems.isNotEmpty()) Text(
                            if(sourceFilter=="ALL") "Se aplicará a lecturas reales y simulaciones pendientes."
                            else "Se aplicará solo a las $pendingScope pendientes.",
                            style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted
                        )
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick={onSyncPending(pendingItems.map { it.checkId })},enabled=!working&&pendingItems.isNotEmpty(),modifier=Modifier.weight(1f),
                                contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp)
                            ) { Icon(Icons.Default.CloudUpload,null,Modifier.size(17.dp));Spacer(Modifier.width(5.dp));Text("Enviar estas",maxLines=1,overflow=TextOverflow.Ellipsis) }
                            OutlinedButton(
                                onClick={pendingDeleteIds=pendingItems.map { it.checkId };pendingDeleteScope=pendingScope;confirmDeletePending=true},enabled=!working&&pendingItems.isNotEmpty(),modifier=Modifier.weight(1f),
                                contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),
                                colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)
                            ) { Icon(Icons.Default.DeleteOutline,null,Modifier.size(17.dp));Spacer(Modifier.width(5.dp));Text("Eliminar estas",maxLines=1,overflow=TextOverflow.Ellipsis) }
                        }
                    }
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
                    FilterChip(selected=labelFilter==null,onClick={labelFilter=null},label={Text("Todas")},colors=activityFilterChipColors())
                    latest.mapNotNull { it.networkLabel }.distinct().forEach { label -> FilterChip(selected=labelFilter==label,onClick={labelFilter=label},label={Text(label,maxLines=1)},colors=activityFilterChipColors()) }
                }
            }
            val signalItems=chartItems.filter { it.wifiMetrics?.rssiDbm!=null }
            val signalReadings=signalItems.mapNotNull { it.wifiMetrics?.rssiDbm }
            if(sourceFilter!="DEMO"&&signalReadings.size>1) {
                val newest=signalItems.firstOrNull()?.wifiMetrics?.rssiDbm
                val previous=signalItems.getOrNull(1)?.wifiMetrics?.rssiDbm
                val delta=if(newest!=null&&previous!=null) newest-previous else null
                Glass {
                    Text("Cambio reciente",style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
                    Text(delta?.let { if(it>0)"+$it dB" else "$it dB" } ?: "Sin comparación",fontFamily=HoneyStyle.Dots,style=MaterialTheme.typography.headlineSmall)
                    SignalSparkline(signalReadings)
                }
            }
            if(filtered.isEmpty()) Text(
                if(sourceFilter=="DEMO") "Los ejemplos guardados desde la galería aparecerán aquí."
                else "Las comprobaciones aparecerán aquí cuando escanees la red.",
                style=MaterialTheme.typography.bodyMedium,color=HoneyStyle.Muted
            )
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
                Text("${sessions.size} sesiones · ${visibleLocalEvents.size} solicitudes en el teléfono",modifier=Modifier.weight(1f).padding(end=8.dp),style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted,maxLines=2,overflow=TextOverflow.Ellipsis)
                Row {
                    if(localEvents.isNotEmpty()) TextButton(onClick=onClearHttp){Text("Borrar")}
                    TextButton(onClick={onRefreshHttp(selectedSession)}){Text("Actualizar")}
                }
            }
            Text("Los eventos se intentan enviar automáticamente. Actualizar también reintenta los que sigan guardados en el teléfono.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            remoteMessage?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted) }
            if(sessions.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    sessions.forEach { session ->
                        FilterChip(selected=session.sessionId==selectedSession,onClick={onRefreshHttp(session.sessionId)},label={Text("${honeypotSessionLabel(session.state)} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(session.startedAt))}")},colors=activityFilterChipColors())
                    }
                }
            }
            if(honeypotMetricEvents.isNotEmpty()) HoneypotMetricsPanel(honeypotMetricEvents,honeypotSourceNote)
            visibleLocalEvents.forEach { event ->
                ActivityEventRow(requestDescription(event.method,event.path),event.sourceAddress,userAgentLabel(event.userAgent),Date(event.occurredAt),event.responseCode,"En el teléfono",event.requestDetails,event.userAgent)
            }
            visibleRemoteEvents.forEach { event ->
                ActivityEventRow(requestDescription(event.method,event.path),event.sourceIpMasked,event.userAgentFamily?.let(::userAgentLabel) ?: "Desconocido",Date(event.occurredAt),event.responseCode,"En el servidor")
            }
            if(visibleLocalEvents.isEmpty()&&visibleRemoteEvents.isEmpty()) Text("Las peticiones del señuelo aparecerán aquí cuando inicies una sesión.",style=MaterialTheme.typography.bodyMedium,color=HoneyStyle.Muted)
        }
    }
}

@Composable
private fun HoneypotMetricsPanel(events:List<HoneypotMetricEvent>,sourceNote:String) {
    if(events.isEmpty()) return
    val counts=remember(events) {
        val buckets=MutableList(8) { 0 }
        val first=events.first().occurredAt
        val span=(events.last().occurredAt-first).coerceAtLeast(1L)
        events.forEach { event ->
            val bucket=(((event.occurredAt-first)*8)/span).toInt().coerceIn(0,7)
            buckets[bucket]++
        }
        buckets
    }
    val responseCounts=remember(events) {
        listOf(
            "2xx" to events.count { it.responseCode in 200..299 },
            "3xx" to events.count { it.responseCode in 300..399 },
            "4xx" to events.count { it.responseCode in 400..499 },
            "5xx" to events.count { it.responseCode in 500..599 },
            "Otros" to events.count { it.responseCode !in 200..599 },
        )
    }
    Glass {
        Eyebrow("Métricas del honeypot",HoneyStyle.Aqua)
        Text("${events.size} ${if(events.size==1)"evento único" else "eventos únicos"}",fontFamily=HoneyStyle.Dots,style=MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            MetricChip("GET",events.count { it.method.equals("GET",true) }.toString(),Modifier.weight(1f))
            MetricChip("POST",events.count { it.method.equals("POST",true) }.toString(),Modifier.weight(1f))
            MetricChip("Otros",events.count { !it.method.equals("GET",true)&&!it.method.equals("POST",true) }.toString(),Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text("Solicitudes registradas",style=MaterialTheme.typography.titleSmall)
            HoneypotTimelineChart(counts)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Más antigua",style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
                Text("Más reciente",style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
            }
        }
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Text("Respuestas HTTP",style=MaterialTheme.typography.titleSmall)
            val maxCount=responseCounts.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
            responseCounts.forEach { (label,count) ->
                val color=when(label) {
                    "2xx" -> HoneyStyle.Aqua
                    "3xx" -> HoneyStyle.Lime
                    "4xx" -> HoneyStyle.Amber
                    "5xx" -> MaterialTheme.colorScheme.error
                    else -> HoneyStyle.Muted
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(label,modifier=Modifier.width(38.dp),style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
                    BoxWithConstraints(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(8.dp)).background(HoneyStyle.Line)) {
                        if(count>0) Box(Modifier.fillMaxWidth(count.toFloat()/maxCount).fillMaxHeight().background(color))
                    }
                    Text(count.toString(),modifier=Modifier.width(22.dp),fontFamily=HoneyStyle.Mono,style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Ink)
                }
            }
        }
        Text(sourceNote,style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
    }
}

@Composable
private fun HoneypotTimelineChart(counts:List<Int>) {
    Canvas(Modifier.fillMaxWidth().height(86.dp)) {
        if(counts.isEmpty()) return@Canvas
        val maxCount=counts.maxOrNull()?.coerceAtLeast(1) ?: 1
        val spacing=6.dp.toPx()
        val barWidth=((size.width-spacing*(counts.size-1))/counts.size).coerceAtLeast(1f)
        val minimumHeight=2.dp.toPx()
        drawLine(HoneyStyle.Line,Offset(0f,size.height),Offset(size.width,size.height),1.dp.toPx())
        counts.forEachIndexed { index,count ->
            val height=(size.height*count/maxCount).coerceAtLeast(minimumHeight)
            val left=index*(barWidth+spacing)
            drawRoundRect(
                color=HoneyStyle.Aqua.copy(alpha=if(count==0)0.16f else 0.9f),
                topLeft=Offset(left,size.height-height),
                size=Size(barWidth,height),
                cornerRadius=CornerRadius(5.dp.toPx(),5.dp.toPx()),
            )
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
private fun AccountSettingsContent(devices:List<DeviceInfo>,deviceName:String,deviceMessage:String?,currentInstallationId:String,apiConnection:ApiConnectionState,automaticScansEnabled:Boolean,onAutomaticScansChanged:(Boolean)->Unit,onDeviceName:(String)->Unit,onSaveDevice:()->Unit,onRevokeDevice:()->Unit,onRefreshDevices:()->Unit,onRefreshApi:()->Unit,onDemos:()->Unit,onTour:()->Unit,tourTarget:ProductTourTarget?,onTourBounds:(Rect)->Unit) {
    val ownDevice=devices.firstOrNull { it.installationId==currentInstallationId }
    var confirmDisable by remember { mutableStateOf(false) }
    var showOtherDevices by rememberSaveable { mutableStateOf(false) }
    var showProbeDetails by rememberSaveable { mutableStateOf(false) }
    var showApiDetails by rememberSaveable { mutableStateOf(false) }
    if(confirmDisable) HoneyAlertDialog(onDismissRequest={confirmDisable=false},
        title={Text("¿Desactivar este teléfono?")},
        text={Text("Dejará de enviar revisiones y solicitudes nuevas. La información ya guardada no se elimina.")},
        confirmButton={TextButton(onClick={confirmDisable=false;onRevokeDevice()}){Text("Desactivar",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton(onClick={confirmDisable=false}){Text("Cancelar")}})
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Ajustes del dispositivo",style=MaterialTheme.typography.titleLarge)
        }
        SecondaryAction("Recorrido interactivo",icon=Icons.Default.Explore,onClick=onTour)
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
        Surface(
            modifier=Modifier.then(productTourAnchor(tourTarget==ProductTourTarget.AUTOMATIC_SCANS,onTourBounds)).fillMaxWidth(),
            shape=RoundedCornerShape(12.dp),color=HoneyStyle.Surface,border=BorderStroke(1.dp,HoneyStyle.Line),
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.BatteryChargingFull,null,tint=HoneyStyle.Muted)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("Escaneos automáticos",style=MaterialTheme.typography.titleMedium)
                    Text(if(automaticScansEnabled)"Aprox. cada 15 min · Wi-Fi e Internet" else "Pausados · no se crean lecturas en segundo plano",
                        style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                    Text("Puede consumir batería. Los datos móviles solos no activan un escaneo.",style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
                }
                Switch(checked=automaticScansEnabled,onCheckedChange=onAutomaticScansChanged)
            }
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
private fun ProfileScreen(email:String?,deleting:Boolean,message:String?,devices:List<DeviceInfo>,deviceName:String,deviceMessage:String?,currentInstallationId:String,apiConnection:ApiConnectionState,automaticScansEnabled:Boolean,onAutomaticScansChanged:(Boolean)->Unit,onDeviceName:(String)->Unit,onSaveDevice:()->Unit,onRevokeDevice:()->Unit,onRefreshDevices:()->Unit,onRefreshApi:()->Unit,onDemos:()->Unit,onTour:()->Unit,onSignOut:()->Unit,onDeleteAccount:()->Unit,tourTarget:ProductTourTarget?,onTourBounds:(Rect)->Unit) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf("") }
    if(confirmDelete) HoneyAlertDialog(
        onDismissRequest={if(!deleting){confirmDelete=false;confirmation=""}},
        title={Text("Eliminar cuenta")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Se eliminarán tu cuenta, las instalaciones registradas y sus revisiones y eventos del servidor. También se borrará el historial local de esta cuenta en este teléfono. Esta acción no se puede deshacer.")
                Text("Escribe ELIMINAR para confirmar.",style=MaterialTheme.typography.labelMedium,color=HoneyStyle.Muted)
                OutlinedTextField(value=confirmation,onValueChange={confirmation=it.trim().uppercase()},singleLine=true,
                    label={Text("Confirmación")},modifier=Modifier.fillMaxWidth(),enabled=!deleting)
                if(deleting) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton={TextButton(enabled=confirmation=="ELIMINAR"&&!deleting,onClick={confirmDelete=false;onDeleteAccount()}) { Text("Eliminar definitivamente",color=MaterialTheme.colorScheme.error) }},
        dismissButton={TextButton(enabled=!deleting,onClick={confirmDelete=false;confirmation=""}) { Text("Cancelar") }},
    )
    Page {
        Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text("Cuenta",style=MaterialTheme.typography.headlineMedium)
        }
        Glass(Modifier.then(productTourAnchor(tourTarget==ProductTourTarget.ACCOUNT_PROFILE,onTourBounds)),accent=HoneyStyle.Line,glow=true) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF1E1E1E)),contentAlignment=Alignment.Center) {
                    HoneyPhoneNavGlyph(AppDestination.PROFILE,true,Modifier.size(44.dp))
                }
                Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("Tu espacio privado",style=MaterialTheme.typography.titleLarge)
                    Text(email?.takeIf(String::isNotBlank) ?: "Cuenta conectada",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                }
            }
            HorizontalDivider(color=HoneyStyle.Line)
            Text("Las revisiones y la actividad de HoneyPhone se guardan bajo esta cuenta para mantener tus datos separados de los de otras personas.",style=MaterialTheme.typography.bodyMedium,color=HoneyStyle.Muted)
        }
        message?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=if(it.startsWith("No se pudo"))MaterialTheme.colorScheme.error else HoneyStyle.Muted) }
        AccountSettingsContent(
            devices=devices,deviceName=deviceName,deviceMessage=deviceMessage,currentInstallationId=currentInstallationId,
            apiConnection=apiConnection,automaticScansEnabled=automaticScansEnabled,onAutomaticScansChanged=onAutomaticScansChanged,
            onDeviceName=onDeviceName,onSaveDevice=onSaveDevice,onRevokeDevice=onRevokeDevice,
            onRefreshDevices=onRefreshDevices,onRefreshApi=onRefreshApi,onDemos=onDemos,onTour=onTour,
            tourTarget=tourTarget,onTourBounds=onTourBounds,
        )
        SecondaryAction("Cerrar sesión",enabled=!deleting,icon=Icons.Default.Logout,onClick=onSignOut)
        Glass(accent=MaterialTheme.colorScheme.error.copy(alpha=0.4f)) {
            Eyebrow("Acción permanente",MaterialTheme.colorScheme.error)
            Text("Eliminar cuenta",style=MaterialTheme.typography.titleLarge)
            Text("Borra la cuenta y los datos asociados en el servidor y en este teléfono.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            if(deleting) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick={confirmation="";confirmDelete=true},enabled=!deleting,modifier=Modifier.fillMaxWidth(),
                border=BorderStroke(1.dp,MaterialTheme.colorScheme.error.copy(alpha=0.65f)),
                colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)) {
                Text(if(deleting)"Eliminando cuenta…" else "Eliminar mi cuenta",style=MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun HoneyAlertDialog(
    onDismissRequest:()->Unit,
    title:@Composable ()->Unit,
    text:@Composable ()->Unit,
    confirmButton:@Composable ()->Unit,
    dismissButton:@Composable ()->Unit,
) {
    AlertDialog(
        onDismissRequest=onDismissRequest,
        title=title,
        text=text,
        confirmButton=confirmButton,
        dismissButton=dismissButton,
        shape=RoundedCornerShape(24.dp),
        containerColor=HoneyStyle.Surface,
        titleContentColor=HoneyStyle.Ink,
        textContentColor=HoneyStyle.Muted,
        iconContentColor=HoneyStyle.Amber,
        tonalElevation=0.dp,
    )
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

private enum class ProductTourTarget { WIFI_SCAN, DEMO_SAMPLE, HONEYPOT_ACTION, ACTIVITY_SECTIONS, AUTOMATIC_SCANS, ACCOUNT_PROFILE }

private data class ProductTourStep(
    val icon:ImageVector,
    val title:String,
    val purpose:String,
    val instruction:String,
    val destination:AppDestination,
    val target:ProductTourTarget,
)

private val PRODUCT_TOUR_STEPS = listOf(
    ProductTourStep(Icons.Default.Wifi,"Red Wi-Fi","Consulta la señal, el tipo de seguridad y la conexión de esta red.","El escaneo manual comienza al tocar «Escanear red» y necesita una conexión Wi-Fi.",AppDestination.HOME,ProductTourTarget.WIFI_SCAN),
    ProductTourStep(Icons.Default.Science,"Ejemplos","Explora resultados simulados para aprender a leer los indicadores.","Estos casos son ficticios. Solo se guardan si eliges guardarlos.",AppDestination.DEMO_GALLERY,ProductTourTarget.DEMO_SAMPLE),
    ProductTourStep(Icons.Default.Security,"Señuelo","Activa un sitio local que registra solicitudes recibidas mientras está escuchando.","Elige una apariencia y activa el señuelo desde un dispositivo de prueba conectado a la misma Wi-Fi.",AppDestination.HONEYPOT,ProductTourTarget.HONEYPOT_ACTION),
    ProductTourStep(Icons.Default.History,"Actividad","Revisa las lecturas Wi-Fi y las solicitudes recibidas por el señuelo.","Cambia de pestaña para ver cada tipo de registro y usa «Actualizar» para consultar el servidor.",AppDestination.HISTORY,ProductTourTarget.ACTIVITY_SECTIONS),
    ProductTourStep(Icons.Default.BatteryChargingFull,"Escaneos automáticos","HoneyPhone puede guardar lecturas en segundo plano, aproximadamente cada 15 minutos.","Activa o pausa esta función desde Cuenta → Ajustes del dispositivo. Solo escanea con Wi-Fi e Internet verificado; los datos móviles solos no generan lecturas.",AppDestination.PROFILE,ProductTourTarget.AUTOMATIC_SCANS),
    ProductTourStep(Icons.Default.Person,"Cuenta","Tu historial pertenece a tu cuenta y se mantiene separado del de otras personas.","Desde Cuenta puedes cerrar sesión o solicitar la eliminación de tu cuenta y sus datos.",AppDestination.PROFILE,ProductTourTarget.ACCOUNT_PROFILE),
)

@Composable
private fun productTourAnchor(active:Boolean,onBounds:(Rect)->Unit):Modifier {
    val requester=remember { BringIntoViewRequester() }
    LaunchedEffect(active) {
        if(active) {
            delay(180)
            requester.bringIntoView()
        }
    }
    return if(active) Modifier.bringIntoViewRequester(requester).onGloballyPositioned { onBounds(it.boundsInRoot()) }
    else Modifier
}

@Composable
private fun ProductTourOverlay(step:Int,targetBounds:Rect?,onPrevious:()->Unit,onNext:()->Unit,onFinish:()->Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val targetIsLow=targetBounds?.center?.y?.let { it>constraints.maxHeight*0.54f } ?: false
        val cardPlacement=if(targetIsLow) Modifier.align(Alignment.TopCenter).padding(top=64.dp)
            else Modifier.align(Alignment.BottomCenter).padding(bottom=12.dp)
        Canvas(Modifier.fillMaxSize().clickable(interactionSource=remember { androidx.compose.foundation.interaction.MutableInteractionSource() },indication=null) { }
            .graphicsLayer { compositingStrategy=CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha=0.78f))
            targetBounds?.let { bounds ->
                val inset=10.dp.toPx()
                val left=(bounds.left-inset).coerceAtLeast(0f)
                val top=(bounds.top-inset).coerceAtLeast(0f)
                val width=(bounds.width+inset*2).coerceAtMost(size.width-left)
                val height=(bounds.height+inset*2).coerceAtMost(size.height-top)
                val corner=CornerRadius(20.dp.toPx(),20.dp.toPx())
                drawRoundRect(Color.Transparent,Offset(left,top),androidx.compose.ui.geometry.Size(width,height),corner,blendMode=BlendMode.Clear)
                drawRoundRect(Color.White.copy(alpha=0.94f),Offset(left,top),androidx.compose.ui.geometry.Size(width,height),corner,style=Stroke(2.dp.toPx()))
            }
        }
        Surface(
            modifier=cardPlacement.fillMaxWidth().padding(horizontal=16.dp),
            shape=RoundedCornerShape(24.dp),
            color=HoneyStyle.Surface,
            contentColor=HoneyStyle.Ink,
            border=BorderStroke(1.dp,HoneyStyle.Line),
            shadowElevation=24.dp,
        ) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Eyebrow("RECORRIDO  ·  ${step+1}/${PRODUCT_TOUR_STEPS.size}",HoneyStyle.Muted)
                    TextButton(onClick=onFinish){Text("Salir")}
                }
                LinearProgressIndicator(progress={(step+1f)/PRODUCT_TOUR_STEPS.size},modifier=Modifier.fillMaxWidth(),color=HoneyStyle.Ink,trackColor=HoneyStyle.Line)
                AnimatedContent(
                    targetState=step,
                    transitionSpec={
                        (fadeIn(tween(220))+slideInHorizontally(tween(220)){it/7}) togetherWith
                            (fadeOut(tween(160))+slideOutHorizontally(tween(160)){-it/7})
                    },
                    label="product-tour-step",
                ) { index ->
                    val current=PRODUCT_TOUR_STEPS[index.coerceIn(PRODUCT_TOUR_STEPS.indices)]
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Icon(current.icon,null,tint=HoneyStyle.Ink,modifier=Modifier.size(26.dp))
                            Text(current.title,style=MaterialTheme.typography.headlineSmall)
                        }
                        Text(current.purpose,style=MaterialTheme.typography.bodyLarge,color=HoneyStyle.Ink)
                        Text(current.instruction,style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick=onPrevious,enabled=step>0,modifier=Modifier.weight(1f).heightIn(min=50.dp)){Text("Anterior")}
                    Button(onClick=if(step==PRODUCT_TOUR_STEPS.lastIndex)onFinish else onNext,modifier=Modifier.weight(1f).heightIn(min=50.dp)) {
                        Text(if(step==PRODUCT_TOUR_STEPS.lastIndex)"Terminar" else "Siguiente")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HoneypotHomeScreen(status:HoneypotStatus,events:List<HoneypotEvent>,wifiConnected:Boolean,selectedProfile:HoneypotProfile,onSelectProfile:(HoneypotProfile)->Unit,onStart:()->Unit,onStop:()->Unit,onClear:()->Unit,onToast:(String)->Unit,tourTarget:ProductTourTarget?,onTourBounds:(Rect)->Unit) {
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
    if(confirmClear) HoneyAlertDialog(onDismissRequest={confirmClear=false},title={Text("¿Borrar solicitudes guardadas?")},
        text={Text("Esto elimina del teléfono las solicitudes recibidas y guardadas localmente.")},
        confirmButton={TextButton(onClick={onClear();confirmClear=false}){Text("Borrar")}},
        dismissButton={TextButton(onClick={confirmClear=false}){Text("Cancelar")}})
    Page {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Señuelo",style=MaterialTheme.typography.headlineMedium)
            }
            StatusPill(when(status.phase){HoneypotPhase.STARTING->"Activando";HoneypotPhase.LISTENING->"Escuchando";HoneypotPhase.ERROR->"No se inició";HoneypotPhase.STOPPED->"Detenido"},
                when(status.phase){HoneypotPhase.LISTENING->HoneyStyle.Lime;HoneypotPhase.ERROR->HoneyStyle.Amber;else->HoneyStyle.Muted})
        }
        if(!wifiConnected && status.phase!=HoneypotPhase.LISTENING) InfoStrip("Conecta a una red Wi‑Fi","El señuelo solo puede aceptar solicitudes desde una red Wi‑Fi activa.",HoneyStyle.Amber)
        Box(Modifier.fillMaxWidth().height(292.dp),contentAlignment=Alignment.Center) {
            HoneypotFlytrap(status.phase==HoneypotPhase.LISTENING,currentEventId,size=284.dp)
        }
        PrimaryAction(if(status.phase==HoneypotPhase.LISTENING)"Detener señuelo" else if(status.phase==HoneypotPhase.STARTING)"Activando…" else "Activar señuelo",
            enabled=status.phase!=HoneypotPhase.STARTING && (wifiConnected || status.phase==HoneypotPhase.LISTENING),onClick=if(status.phase==HoneypotPhase.LISTENING)onStop else startWithVisibleNotification,
            modifier=productTourAnchor(tourTarget==ProductTourTarget.HONEYPOT_ACTION,onTourBounds))
        Glass {
            Eyebrow("Apariencia del señuelo",HoneyStyle.Muted)
            Text("Elige qué página verá quien lo visite.",style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                HoneypotProfile.entries.forEach { profile ->
                    val selected=selectedProfile==profile
                    val enabled=status.phase !in setOf(HoneypotPhase.STARTING,HoneypotPhase.LISTENING)
                    Surface(
                        modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .clickable(enabled=enabled,role=Role.RadioButton){onSelectProfile(profile)},
                        shape=RoundedCornerShape(14.dp),
                        color=if(selected)Color.White.copy(alpha=0.07f) else HoneyStyle.Background,
                        border=BorderStroke(1.dp,if(selected)Color.White.copy(alpha=0.5f) else HoneyStyle.Line),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=10.dp),
                            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha=if(selected)0.08f else 0.035f)),
                                contentAlignment=Alignment.Center) {
                                HoneypotScenarioGlyph(profile,selected)
                            }
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                Text(honeypotProfileTitle(profile),style=MaterialTheme.typography.titleSmall,color=HoneyStyle.Ink)
                                Text(honeypotProfileDescription(profile),style=MaterialTheme.typography.bodySmall,color=HoneyStyle.Muted)
                            }
                            Box(Modifier.size(16.dp).clip(RoundedCornerShape(50))
                                .border(1.5.dp,if(selected)HoneyStyle.Ink else HoneyStyle.Muted.copy(alpha=0.55f),RoundedCornerShape(50)),
                                contentAlignment=Alignment.Center) {
                                if(selected) Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(HoneyStyle.Ink))
                            }
                        }
                    }
                }
            }
            Text("Solo en redes autorizadas · no guarda lo que escriban.",style=MaterialTheme.typography.labelSmall,color=HoneyStyle.Muted)
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
                onToast("Dirección del señuelo copiada")
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
private fun honeypotProfileDescription(profile:HoneypotProfile)=when(profile) {
    HoneypotProfile.LOGIN_PORTAL->"Formulario de acceso simulado."
    HoneypotProfile.ADMIN_CONSOLE->"Panel con rutas de administración."
    HoneypotProfile.COMPROMISED_SITE->"Página con apariencia intervenida."
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
    "synced"->"Enviado"
    "syncing"->"Enviando…"
    "failed"->"Error al enviar"
    "pending"->"Sin enviar"
    else->"Enviado"
}
private fun syncColor(status: String)=when(status.substringBefore(":").lowercase()) {
    "synced"->HoneyStyle.Aqua
    "failed"->HoneyStyle.Amber
    "syncing"->HoneyStyle.Aqua
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
