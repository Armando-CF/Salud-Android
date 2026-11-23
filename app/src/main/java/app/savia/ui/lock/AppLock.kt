package app.savia.ui.lock

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.withResumed
import app.savia.Savia

/**
 * Estado y utilidades del bloqueo de la app (huella / rostro / PIN, patrón o contraseña del teléfono).
 *
 * El estado vive a nivel de proceso: girar la pantalla no vuelve a pedir desbloqueo, pero si el
 * sistema mata el proceso (o la app pasa más de [RELOCK_AFTER_MS] en segundo plano) sí.
 */
object AppLock {
    const val RELOCK_AFTER_MS = 60_000L
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** true cuando la persona ya se autenticó en esta sesión. */
    var unlocked by mutableStateOf(false)
        private set

    private var stoppedAt = 0L

    fun markUnlocked() {
        unlocked = true
    }

    fun lock() {
        unlocked = false
    }

    /** La activity dejó de verse. */
    fun onStop(changingConfigurations: Boolean) {
        if (!changingConfigurations) stoppedAt = SystemClock.elapsedRealtime()
    }

    /** La activity volvió a verse: si estuvo demasiado tiempo fuera, se vuelve a bloquear. */
    fun onStart() {
        val at = stoppedAt
        stoppedAt = 0L
        if (at != 0L && SystemClock.elapsedRealtime() - at > RELOCK_AFTER_MS) lock()
    }

    /** ¿El teléfono tiene algún método de bloqueo (huella, rostro, PIN, patrón o contraseña)? */
    fun isAvailable(context: Context): Boolean {
        val status = try {
            BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)
        } catch (_: Throwable) {
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN
        }
        if (status == BiometricManager.BIOMETRIC_SUCCESS) return true
        return try {
            context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Muestra el diálogo del sistema para confirmar identidad.
     * @param onError recibe el mensaje a mostrar, o null si la persona simplemente canceló.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String? = null,
        onSuccess: () -> Unit,
        onError: (String?) -> Unit,
    ) {
        try {
            // Se crea un prompt nuevo cada vez: el callback vigente es siempre el del último creado.
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_CANCELED
                        onError(if (cancelled) null else errString.toString())
                    }
                    // onAuthenticationFailed (huella no reconocida): el propio diálogo deja reintentar.
                },
            )
            val builder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .setConfirmationRequired(false)
            if (subtitle != null) builder.setSubtitle(subtitle)
            prompt.authenticate(builder.build())
        } catch (_: Throwable) {
            onError("No se pudo abrir el desbloqueo del teléfono.")
        }
    }
}

/** Busca la FragmentActivity que contiene este contexto (o null, p. ej. en vistas previas). */
fun Context.findFragmentActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

/** Si el bloqueo está activo en ajustes, pide huella / PIN del teléfono antes de mostrar [content]. */
@Composable
fun AppLockGate(activity: FragmentActivity, content: @Composable () -> Unit) {
    val enabled by Savia.prefs.appLock.flow.collectAsStateWithLifecycle()

    // Con el bloqueo activado, el contenido no aparece en "recientes" ni en capturas de pantalla.
    DisposableEffect(activity, enabled) {
        if (enabled) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { }
    }

    // Volver a bloquear tras un rato en segundo plano.
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> AppLock.onStop(activity.isChangingConfigurations)
                Lifecycle.Event.ON_START -> AppLock.onStart()
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    val unlocked = AppLock.unlocked
    // Si el teléfono ya no tiene método de bloqueo no se puede pedir nada: no dejamos a nadie fuera de sus datos.
    val available = remember(enabled, unlocked) { !enabled || AppLock.isAvailable(activity) }
    val locked = enabled && !unlocked && available
    var noticeDismissed by remember { mutableStateOf(false) }

    // El contenido se desmonta mientras está bloqueado (nada que leer por debajo) y su estado
    // (navegación, formularios, scroll) se conserva y se restaura al desbloquear.
    val holder = rememberSaveableStateHolder()
    if (locked) {
        LockScreenHost(activity)
    } else {
        holder.SaveableStateProvider("savia_content") { content() }
    }

    if (enabled && !available) {
        SideEffect { AppLock.markUnlocked() }
        if (!noticeDismissed) {
            AlertDialog(
                onDismissRequest = { noticeDismissed = true },
                icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                title = { Text("No se pudo pedir el desbloqueo") },
                text = {
                    Text(
                        "Tu teléfono ya no tiene huella, PIN, patrón ni contraseña configurados, así que Savia no puede " +
                            "proteger la entrada. Configura un bloqueo de pantalla en los ajustes del teléfono o desactiva " +
                            "esta opción en los ajustes de Savia.",
                    )
                },
                confirmButton = { TextButton(onClick = { noticeDismissed = true }) { Text("Entendido") } },
            )
        }
    }
}

@Composable
private fun LockScreenHost(activity: FragmentActivity) {
    var error by remember { mutableStateOf<String?>(null) }
    val prompt = {
        error = null
        AppLock.authenticate(
            activity,
            title = "Desbloquear Savia",
            subtitle = "Confirma que eres tú para ver tus datos",
            onSuccess = { AppLock.markUnlocked() },
            onError = { error = it },
        )
    }
    LaunchedEffect(Unit) {
        // Se lanza solo al aparecer, cuando la activity ya está en primer plano.
        activity.lifecycle.withResumed { prompt() }
    }
    LockScreen(error = error, onUnlock = prompt)
}

/** Pantalla que cubre la app mientras está bloqueada. */
@Composable
fun LockScreen(error: String?, onUnlock: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SaviaLogo(size = 112.dp)
            Spacer(Modifier.height(20.dp))
            Text("Savia", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Tus datos de salud están protegidos.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onUnlock) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Desbloquear")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Usa tu huella, rostro o el PIN, patrón o contraseña de tu teléfono.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (error != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Logo de Savia: hoja con una línea de pulso sobre un círculo del color principal. */
@Composable
fun SaviaLogo(size: Dp, modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.primary
    val leaf = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier.size(size).clip(CircleShape).background(bg)) {
        // Mismo dibujo que el icono de la app (motivo entre 27 y 82 de un lienzo de 108), ampliado.
        val scale = this.size.minDimension / 78f
        val dx = this.size.width / 2f - 54f * scale
        val dy = this.size.height / 2f - 54.5f * scale
        fun x(v: Float) = dx + v * scale
        fun y(v: Float) = dy + v * scale
        val path = Path().apply {
            moveTo(x(54f), y(27f))
            cubicTo(x(70f), y(35f), x(78f), y(48f), x(76f), y(62f))
            cubicTo(x(74f), y(74f), x(65f), y(81f), x(54f), y(82f))
            cubicTo(x(43f), y(81f), x(34f), y(74f), x(32f), y(62f))
            cubicTo(x(30f), y(48f), x(38f), y(35f), x(54f), y(27f))
            close()
        }
        drawPath(path, leaf)
        val pulse = Path().apply {
            moveTo(x(37f), y(58f))
            lineTo(x(46f), y(58f))
            lineTo(x(50f), y(49f))
            lineTo(x(56f), y(67f))
            lineTo(x(60f), y(58f))
            lineTo(x(71f), y(58f))
        }
        drawPath(pulse, bg, style = Stroke(width = 3.4f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Brillo sutil para dar volumen.
        drawCircle(Color.White.copy(alpha = 0.07f), radius = this.size.minDimension * 0.55f, center = Offset(this.size.width * 0.25f, this.size.height * 0.15f))
    }
}
