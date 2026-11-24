package dev.honeyphone.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dev.honeyphone.app.BuildConfig
import dev.honeyphone.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun AccountGate(
    busy: Boolean,
    message: String?,
    apiConfigured: Boolean,
    googleConfigured: Boolean,
    onSubmit: (String, String, Boolean) -> Unit,
    onGoogleToken: (String) -> Unit,
    onResendVerification: (String) -> Unit,
    onPasswordRecovery: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var createAccount by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var googleMessage by remember { mutableStateOf<String?>(null) }
    var accountAction by rememberSaveable { mutableStateOf<String?>(null) }
    var actionEmail by rememberSaveable { mutableStateOf("") }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070707))
            .imePadding(),
    ) {
        val density = LocalDensity.current
        val imeBottom = WindowInsets.ime.getBottom(density)
        val keyboardVisible = imeBottom > 0
        val fullHeight = maxHeight + with(density) { imeBottom.toDp() }
        val compact = fullHeight < 680.dp
        val veryCompact = fullHeight < 510.dp
        val horizontalPadding = if (compact) 14.dp else 22.dp

        AuthParticleBackdrop(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 430.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(
                    horizontal = horizontalPadding,
                    vertical = when {
                        keyboardVisible -> 6.dp
                        compact -> 8.dp
                        else -> 18.dp
                    },
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xF20E0F10),
                border = BorderStroke(1.dp, Color(0xFF292B2D)),
                shadowElevation = 22.dp,
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = if (compact) 15.dp else 24.dp,
                        vertical = when {
                            keyboardVisible -> 12.dp
                            veryCompact -> 10.dp
                            compact -> 14.dp
                            else -> 22.dp
                        },
                    ),
                    verticalArrangement = Arrangement.spacedBy(
                        when {
                            keyboardVisible -> 8.dp
                            veryCompact -> 6.dp
                            compact -> 8.dp
                            else -> 12.dp
                        },
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!veryCompact) ImageBrandHeader(compact = compact || keyboardVisible)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF191A1B), RoundedCornerShape(17.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        AuthModeTab(
                            label = "Iniciar sesión",
                            selected = !createAccount,
                            modifier = Modifier.weight(1f),
                            compact = compact || keyboardVisible,
                            enabled = !busy,
                            onClick = { createAccount = false; googleMessage = null },
                        )
                        AuthModeTab(
                            label = "Registrarse",
                            selected = createAccount,
                            modifier = Modifier.weight(1f),
                            compact = compact || keyboardVisible,
                            enabled = !busy,
                            onClick = { createAccount = true; googleMessage = null },
                        )
                    }

                    if (!compact && !keyboardVisible) {
                        AnimatedContent(
                            targetState = createAccount,
                            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
                            label = "account-mode",
                        ) { registering ->
                            Text(
                                text = if (registering) "Crea tu cuenta privada" else "Accede a tus revisiones",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFD6D7D8),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth().height(
                            when {
                                veryCompact -> 58.dp
                                compact -> 62.dp
                                else -> 64.dp
                            },
                        ),
                        singleLine = true,
                        enabled = !busy,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                        label = {
                            Text("Correo electrónico", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
                        },
                        placeholder = {
                            Text("tu@correo.com", color = Color(0xFF777A7D), fontSize = 17.sp)
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        shape = RoundedCornerShape(16.dp),
                        colors = authFieldColors(),
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth().height(
                            when {
                                veryCompact -> 58.dp
                                compact -> 62.dp
                                else -> 64.dp
                            },
                        ),
                        singleLine = true,
                        enabled = !busy,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                        label = {
                            Text("Contraseña", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
                        },
                        placeholder = {
                            Text(
                                if (createAccount) "Mínimo 10 caracteres" else "••••••••••",
                                color = Color(0xFF777A7D),
                                fontSize = 17.sp,
                            )
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !busy) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                                    tint = Color(0xFFB8BABC),
                                )
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = authFieldColors(),
                    )

                    if (createAccount && !compact && !keyboardVisible) {
                        Text(
                            text = "Usa al menos 10 caracteres. Tus revisiones y sesiones quedarán asociadas a esta cuenta.",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = Color(0xFF9A9D9F),
                        )
                    }

                    val visibleMessage = message?.takeIf(String::isNotBlank)
                    if (visibleMessage != null) {
                        AuthMessage(visibleMessage, compact = compact)
                    } else if (!apiConfigured) {
                        AuthMessage(
                            "La dirección HTTPS de la API no está configurada.",
                            compact = compact,
                        )
                    }
                    googleMessage?.takeIf(String::isNotBlank)?.let { AuthMessage(it, compact = compact) }

                    Button(
                        onClick = { onSubmit(email.trim(), password, createAccount) },
                        enabled = !busy && apiConfigured && email.contains("@") && password.length >= 10,
                        modifier = Modifier.fillMaxWidth().height(if (veryCompact) 48.dp else 54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HoneyStyle.Amber,
                            contentColor = Color(0xFF15130A),
                            disabledContainerColor = Color(0xFF292A2B),
                            disabledContentColor = Color(0xFF777A7D),
                        ),
                    ) {
                        if (busy) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(0.7f),
                                color = Color(0xFF16140A),
                                trackColor = Color(0x33FFFFFF),
                            )
                        } else {
                            Text(
                                if (createAccount) "Crear cuenta" else "Iniciar sesión",
                                fontFamily = HoneyStyle.Display,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 0.25.sp,
                            )
                        }
                    }

                    if (!createAccount) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { actionEmail = email.trim(); accountAction = "verify" }, enabled = !busy) {
                                Text("Reenviar verificación", color = Color(0xFFCACDCF), fontSize = 12.sp)
                            }
                            TextButton(onClick = { actionEmail = email.trim(); accountAction = "reset" }, enabled = !busy) {
                                Text("Olvidé mi contraseña", color = Color(0xFFCACDCF), fontSize = 12.sp)
                            }
                        }
                    }

                    AuthDivider(compact = veryCompact)

                    Button(
                        onClick = {
                            googleMessage = null
                            scope.launch {
                                try {
                                    val option = GetSignInWithGoogleOption.Builder(
                                        BuildConfig.GOOGLE_SERVER_CLIENT_ID,
                                    ).build()
                                    val request = GetCredentialRequest.Builder()
                                        .addCredentialOption(option)
                                        .build()
                                    val result = CredentialManager.create(context).getCredential(context, request)
                                    val credential = result.credential as? CustomCredential
                                        ?: throw IllegalStateException("Google no devolvió una credencial compatible.")
                                    if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                        throw IllegalStateException("Google no devolvió un token de acceso.")
                                    }
                                    onGoogleToken(GoogleIdTokenCredential.createFrom(credential.data).idToken)
                                } catch (exception: CancellationException) {
                                    throw exception
                                } catch (exception: Exception) {
                                    googleMessage = exception.localizedMessage?.take(120)
                                        ?: "No se pudo iniciar sesión con Google."
                                }
                            }
                        },
                        enabled = !busy && apiConfigured && googleConfigured,
                        modifier = Modifier.fillMaxWidth().height(if (veryCompact) 48.dp else 54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF7F7F7),
                            contentColor = Color(0xFF202124),
                            disabledContainerColor = Color(0xFF292A2B),
                            disabledContentColor = Color(0xFF777A7D),
                        ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_google_logo),
                                contentDescription = null,
                                modifier = Modifier.size(21.dp),
                            )
                            Text(
                                "Continuar con Google",
                                fontFamily = HoneyStyle.Sans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }

                    if (!googleConfigured && !compact && !keyboardVisible) {
                        Text(
                            "Google requiere el cliente OAuth de tipo Web.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = Color(0xFF9A9D9F),
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (!compact && !keyboardVisible) {
                        Text(
                            "HoneyPhone guarda tus datos bajo tu cuenta.",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = Color(0xFF85898C),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    accountAction?.let { action ->
        AlertDialog(
            onDismissRequest = { if (!busy) accountAction = null },
            containerColor = Color(0xFF151718),
            title = { Text(if (action == "reset") "Recuperar acceso" else "Confirmar correo", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (action == "reset") "Te enviaremos un enlace para elegir una contraseña nueva."
                        else "Te enviaremos de nuevo el enlace para confirmar tu cuenta.",
                        color = Color(0xFFB7BBBD),
                    )
                    OutlinedTextField(
                        value = actionEmail,
                        onValueChange = { actionEmail = it },
                        singleLine = true,
                        label = { Text("Correo electrónico") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(14.dp),
                        colors = authFieldColors(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && actionEmail.contains("@"),
                    onClick = {
                        if (action == "reset") onPasswordRecovery(actionEmail.trim())
                        else onResendVerification(actionEmail.trim())
                        accountAction = null
                    },
                ) { Text("Enviar") }
            },
            dismissButton = { TextButton(onClick = { accountAction = null }, enabled = !busy) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ImageBrandHeader(compact: Boolean) {
    if (compact) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.logo_sin_fondo),
                contentDescription = "HoneyPhone",
                modifier = Modifier.size(35.dp),
            )
            Spacer(Modifier.size(9.dp))
            Text(
                "HoneyPhone",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontFamily = HoneyStyle.Display,
            )
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Image(
                painter = painterResource(R.drawable.logo_sin_fondo),
                contentDescription = "HoneyPhone",
                modifier = Modifier.size(54.dp),
            )
            Text(
                "HoneyPhone",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontFamily = HoneyStyle.Display,
            )
            Text(
                "PROTECCIÓN PARA TU RED",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                color = HoneyStyle.Amber,
                fontFamily = HoneyStyle.Mono,
                letterSpacing = 1.2.sp,
            )
        }
    }
}

@Composable
private fun AuthModeTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(if (compact) 36.dp else 42.dp),
        shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            containerColor = if (selected) Color(0xFF343535) else Color.Transparent,
            contentColor = if (selected) Color.White else Color(0xFF9A9D9F),
        ),
    ) {
        Text(
            label,
            fontFamily = HoneyStyle.Sans,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = Color(0xFF151617),
    unfocusedContainerColor = Color(0xFF151617),
    disabledContainerColor = Color(0xFF101112),
    focusedBorderColor = HoneyStyle.Amber,
    unfocusedBorderColor = Color(0xFF363839),
    disabledBorderColor = Color(0xFF292A2B),
    focusedLabelColor = HoneyStyle.Amber,
    unfocusedLabelColor = Color(0xFFAAACAD),
    cursorColor = HoneyStyle.Amber,
)

@Composable
private fun AuthMessage(text: String, compact: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF261917),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF57312B)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compact) 7.dp else 9.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = Color(0xFFFFB4A8),
            maxLines = if (compact) 2 else 3,
        )
    }
}

@Composable
private fun AuthDivider(compact: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        HorizontalDivider(Modifier.weight(1f), color = Color(0xFF292B2C))
        Text("o", color = Color(0xFF85898B), fontFamily = HoneyStyle.Mono, fontSize = 12.sp)
        HorizontalDivider(Modifier.weight(1f), color = Color(0xFF292B2C))
    }
}

@Composable
private fun AuthParticleBackdrop(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "auth-particles")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "particle-progress",
    )

    Canvas(modifier) {
        val amber = HoneyStyle.Amber
        repeat(34) { index ->
            val baseX = ((index * 47 + 13) % 101) / 100f
            val baseY = ((index * 67 + 29) % 103) / 103f
            val drift = 0.012f + (index % 5) * 0.004f
            val phase = progress * (2f * PI.toFloat()) + index * 0.71f
            val x = ((baseX + sin(phase) * drift + 1f) % 1f) * size.width
            val y = ((baseY - progress * (0.18f + (index % 4) * 0.035f) + 1f) % 1f) * size.height
            val pulse = 0.5f + 0.5f * sin(phase * 1.4f)
            val radius = (1.1f + (index % 3) * 0.45f).dp.toPx()
            val color = if (index % 6 == 0) amber else Color.White
            val alpha = (if (index % 6 == 0) 0.2f else 0.12f) * (0.55f + pulse * 0.45f)
            drawCircle(color.copy(alpha = alpha), radius, Offset(x, y))
        }
    }
}
