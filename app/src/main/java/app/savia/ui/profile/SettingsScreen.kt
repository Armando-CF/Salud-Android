package app.savia.ui.profile

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.room.withTransaction
import app.savia.BuildConfig
import app.savia.Savia
import app.savia.data.Profile
import app.savia.reminders.ReminderScheduler
import app.savia.ui.components.ChoiceChips
import app.savia.ui.components.ConfirmDialog
import app.savia.ui.components.InfoNote
import app.savia.ui.components.NavRow
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SectionCard
import app.savia.ui.components.dismissKeyboardOnTapOutside
import app.savia.ui.lock.AppLock
import app.savia.ui.lock.SaviaLogo
import app.savia.ui.lock.findFragmentActivity
import app.savia.ui.nav.Routes
import app.savia.ui.nav.back
import app.savia.ui.theme.SaviaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val DELETE_WORD = "BORRAR"

private fun notificationsEnabled(context: Context): Boolean =
    try {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    } catch (_: Throwable) {
        true
    }

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No se pudieron abrir los ajustes del sistema", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun openSecuritySettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No se pudieron abrir los ajustes del sistema", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Borra todos los datos de salud del PERFIL ACTIVO (tablas hijas antes que padres) y sus archivos
 * adjuntos. Conserva los ajustes y la identidad del perfil (nombre, si es persona o mascota, especie,
 * parentesco y color) para que siga siendo reconocible en la lista de perfiles. Los demás perfiles no se tocan.
 */
internal suspend fun wipeAllData(context: Context) {
    val db = Savia.db
    val attachments = Savia.attachmentsDir()
    db.withTransaction {
        db.studies().clearAttachments()
        db.studies().clearResults()
        db.studies().clear()
        db.medications().clearIntakes()
        db.medications().clear()
        db.symptoms().clear()
        db.measurements().clear()
        db.dailyLogs().clear()
        db.cycle().clear()
        db.appointments().clear()
        db.selfExams().clear()
        db.vaccinations().clear()
        val old = db.profile().get() ?: Profile()
        db.profile().save(Profile(name = old.name, kind = old.kind, species = old.species, relation = old.relation, avatarColor = old.avatarColor))
    }
    deleteLooseFiles(attachments)
    File(context.cacheDir, "exports").deleteRecursively()
    ReminderScheduler.rescheduleAll(context)
}

/**
 * Borra solo los archivos sueltos de [dir]. La carpeta de adjuntos del perfil 1 contiene las
 * subcarpetas `p<N>` de los demás perfiles, que NO deben tocarse.
 */
internal fun deleteLooseFiles(dir: File) {
    val entries = dir.listFiles() ?: return
    for (f in entries) if (f.isFile) f.delete()
    if (dir.listFiles()?.isEmpty() == true) dir.delete()
}

@Composable
fun SettingsScreen(nav: NavController) {
    val context = LocalContext.current
    val prefs = Savia.prefs
    val themeMode by prefs.themeMode.flow.collectAsStateWithLifecycle()
    val cycleEnabled by prefs.cycleEnabled.flow.collectAsStateWithLifecycle()
    val atlasSex by prefs.atlasSex.flow.collectAsStateWithLifecycle()
    val appLock by prefs.appLock.flow.collectAsStateWithLifecycle()
    val periodReminder by prefs.periodReminder.flow.collectAsStateWithLifecycle()
    val selfExamReminder by prefs.selfExamReminder.flow.collectAsStateWithLifecycle()
    val active by Savia.active.collectAsStateWithLifecycle()
    val profileIds by Savia.profiles.ids.collectAsStateWithLifecycle()
    val pet = active.isPet

    var notifOn by remember { mutableStateOf(notificationsEnabled(context)) }
    var askedPermission by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        notifOn = notificationsEnabled(context)
        onPauseOrDispose { }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        askedPermission = true
        notifOn = granted && notificationsEnabled(context)
        if (granted) Savia.io { ReminderScheduler.rescheduleAll(context.applicationContext) }
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !askedPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings(context)
        }
    }

    fun reschedule() {
        Savia.io { ReminderScheduler.rescheduleAll(context.applicationContext) }
    }

    var lockMessage by remember { mutableStateOf<String?>(null) }
    var showNoLockMethod by remember { mutableStateOf(false) }
    var showDelete1 by remember { mutableStateOf(false) }
    var showDelete2 by remember { mutableStateOf(false) }

    fun toggleLock(turnOn: Boolean) {
        lockMessage = null
        val activity = context.findFragmentActivity()
        val available = AppLock.isAvailable(context)
        if (turnOn) {
            if (!available) {
                showNoLockMethod = true
                return
            }
            if (activity == null) {
                lockMessage = "No se pudo abrir el desbloqueo del teléfono."
                return
            }
            AppLock.authenticate(
                activity,
                title = "Activar bloqueo de Savia",
                subtitle = "Confirma que eres tú",
                onSuccess = {
                    AppLock.markUnlocked()
                    prefs.appLock.value = true
                },
                onError = { msg -> lockMessage = msg ?: "No se activó el bloqueo." },
            )
        } else {
            if (!available || activity == null) {
                // Sin método de bloqueo en el teléfono no hay nada que confirmar.
                prefs.appLock.value = false
                return
            }
            AppLock.authenticate(
                activity,
                title = "Desactivar bloqueo de Savia",
                subtitle = "Confirma que eres tú",
                onSuccess = { prefs.appLock.value = false },
                onError = { msg -> lockMessage = msg ?: "El bloqueo sigue activado." },
            )
        }
    }

    SaviaScaffold(title = "Ajustes", onBack = { nav.back() }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "Perfil") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(active, size = 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Viendo a ${active.displayName}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        val sub = profileSubtitle(active)
                        Text(
                            sub.ifEmpty { if (pet) "Mascota" else "Perfil principal" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                NavRow(
                    Icons.Filled.Groups, "Perfiles (familia y mascotas)", { nav.navigate(Routes.PROFILES) },
                    subtitle = if (profileIds.size <= 1) "Agrega a tu familia y a tus mascotas" else "${profileIds.size} perfiles · cambiar, agregar o eliminar",
                )
            }

            SectionCard(title = "Apariencia") {
                SettingHeader(Icons.Filled.Palette, "Tema", "Colores claros, oscuros o los que use tu teléfono.")
                Spacer(Modifier.height(4.dp))
                ChoiceChips(
                    listOf("system" to "Según el sistema", "light" to "Claro", "dark" to "Oscuro"),
                    themeMode,
                    { it?.let { v -> prefs.themeMode.value = v } },
                )
            }

            if (!pet) SectionCard(title = "Módulos") {
                SwitchRow(
                    Icons.Filled.WaterDrop, "Ciclo menstrual",
                    "Calendario del periodo, predicciones y estadísticas. Tus registros se conservan aunque lo apagues.",
                    checked = cycleEnabled,
                    onCheckedChange = {
                        prefs.cycleEnabled.value = it
                        reschedule()
                    },
                    iconTint = SaviaTheme.colors.period,
                )
                if (cycleEnabled) {
                    NavRow(Icons.Filled.Tune, "Ajustes del ciclo", { nav.navigate(Routes.CYCLE_SETTINGS) }, subtitle = "Duraciones, fertilidad y recordatorios")
                }
                RowDivider()
                SettingHeader(Icons.Filled.Accessibility, "Modelo del atlas corporal", "Figura que se muestra al abrir la sección Cuerpo.")
                Spacer(Modifier.height(4.dp))
                ChoiceChips(
                    listOf("F" to "Mujer", "M" to "Hombre"),
                    atlasSex,
                    { it?.let { v -> prefs.atlasSex.value = v } },
                )
            }

            SectionCard(title = "Privacidad y seguridad") {
                SwitchRow(
                    Icons.Filled.Fingerprint, "Bloquear con huella o PIN del teléfono",
                    "Pide tu huella, rostro, PIN, patrón o contraseña al abrir Savia. También oculta el contenido en apps recientes y capturas de pantalla.",
                    checked = appLock,
                    onCheckedChange = { toggleLock(it) },
                )
                lockMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 62.dp, bottom = 6.dp))
                }
                RowDivider()
                SettingHeader(
                    Icons.Filled.CloudOff, "Tus datos se quedan contigo",
                    "Savia funciona sin internet y sin cuentas: todo se guarda únicamente en este teléfono. Nada se envía a ningún servidor.",
                )
            }

            SectionCard(title = "Notificaciones") {
                if (notifOn) {
                    SettingHeader(Icons.Filled.Notifications, "Notificaciones permitidas", "Savia puede avisarte de medicamentos, citas y recordatorios.", tint = SaviaTheme.colors.good)
                } else {
                    SettingHeader(
                        Icons.Filled.NotificationsOff, "Notificaciones desactivadas",
                        "Sin este permiso no llegarán los recordatorios de medicamentos, citas ni los de abajo.",
                        tint = SaviaTheme.colors.warn,
                    )
                    Row(Modifier.padding(start = 54.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (Build.VERSION.SDK_INT >= 33 && !askedPermission) {
                            FilledTonalButton(onClick = { requestNotifications() }) { Text("Permitir") }
                        }
                        TextButton(onClick = { openNotificationSettings(context) }) { Text("Abrir ajustes del sistema") }
                    }
                }
                if (!pet) RowDivider()
                if (cycleEnabled && !pet) {
                    SwitchRow(
                        Icons.Filled.WaterDrop, "Aviso antes del periodo",
                        "Un recordatorio unos días antes de la fecha estimada.",
                        checked = periodReminder,
                        onCheckedChange = {
                            prefs.periodReminder.value = it
                            if (it && !notifOn) requestNotifications()
                            reschedule()
                        },
                        iconTint = SaviaTheme.colors.period,
                    )
                }
                if (!pet) SwitchRow(
                    Icons.Filled.SelfImprovement, "Recordatorio de autoexploración",
                    "Un aviso mensual para revisarte (mamas, testículos, piel).",
                    checked = selfExamReminder,
                    onCheckedChange = {
                        prefs.selfExamReminder.value = it
                        if (it && !notifOn) requestNotifications()
                        reschedule()
                    },
                )
            }

            SectionCard(title = "Datos") {
                NavRow(
                    if (pet) Icons.Filled.Pets else Icons.Filled.Person,
                    if (pet || active.id != 1) "Ficha de ${active.displayName}" else "Mi ficha",
                    { nav.navigate(Routes.PROFILE) },
                    subtitle = if (pet) "Datos de la mascota y su veterinaria" else "Datos personales y de emergencia",
                )
                NavRow(Icons.Filled.IosShare, "Exportar y respaldo", { nav.navigate(Routes.EXPORT) }, subtitle = "Excel, PDF, CSV y copia de seguridad")
                NavRow(
                    Icons.Filled.DeleteForever, "Borrar los datos de este perfil", { showDelete1 = true },
                    subtitle = "Elimina todo lo registrado de ${active.displayName}",
                    iconTint = MaterialTheme.colorScheme.error,
                )
            }

            SectionCard(title = "Acerca de") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SaviaLogo(size = 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Savia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Versión ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                InfoNote(
                    "Savia es una bitácora personal de salud. No da diagnósticos ni sustituye la atención de profesionales de la salud. " +
                        "En una emergencia, llama a los servicios de emergencia de tu localidad.",
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showNoLockMethod) {
        AlertDialog(
            onDismissRequest = { showNoLockMethod = false },
            title = { Text("Configura primero un bloqueo de pantalla") },
            text = { Text("Para proteger Savia, tu teléfono necesita tener huella, rostro, PIN, patrón o contraseña. Configúralo en los ajustes de seguridad del teléfono y vuelve a intentarlo.") },
            confirmButton = {
                TextButton(onClick = { showNoLockMethod = false; openSecuritySettings(context) }) { Text("Abrir ajustes") }
            },
            dismissButton = { TextButton(onClick = { showNoLockMethod = false }) { Text("Cancelar") } },
        )
    }

    if (showDelete1) {
        ConfirmDialog(
            title = "¿Borrar los datos de ${active.displayName}?",
            text = "Se eliminará de este teléfono todo lo registrado en el perfil de ${active.displayName}: síntomas, mediciones, vacunas, estudios y archivos adjuntos, " +
                (if (pet) "" else "ciclo, bienestar, ") + "medicamentos, citas y los datos de su ficha. Se conservan el nombre del perfil y los ajustes; los demás perfiles no se tocan. " +
                "No se puede deshacer. Si quieres conservarlos, primero haz un respaldo en «Exportar y respaldo».",
            confirmLabel = "Continuar",
            onConfirm = { showDelete1 = false; showDelete2 = true },
            onDismiss = { showDelete1 = false },
        )
    }

    if (showDelete2) {
        var typed by remember { mutableStateOf("") }
        val ok = typed.trim().equals(DELETE_WORD, ignoreCase = true)
        val focus = LocalFocusManager.current
        val wipeName = active.displayName
        AlertDialog(
            onDismissRequest = { showDelete2 = false },
            icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Confirma el borrado") },
            text = {
                // El diálogo vive en su propia ventana: aquí se cierra el teclado al tocar fuera del campo.
                Column(
                    Modifier.dismissKeyboardOnTapOutside().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Para confirmar que quieres eliminar de forma definitiva los datos de $wipeName, escribe $DELETE_WORD.")
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it.take(12) },
                        singleLine = true,
                        placeholder = { Text(DELETE_WORD) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete2 = false
                        val app = context.applicationContext
                        Savia.io {
                            wipeAllData(app)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(app, "Se borraron los datos de $wipeName", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = ok,
                ) { Text("Borrar datos", color = if (ok) MaterialTheme.colorScheme.error else Color.Unspecified) }
            },
            dismissButton = { TextButton(onClick = { showDelete2 = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
private fun IconBadge(icon: ImageVector, tint: Color) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint) }
}

/** Encabezado informativo de un ajuste (sin acción propia). */
@Composable
private fun SettingHeader(icon: ImageVector, title: String, subtitle: String, tint: Color = MaterialTheme.colorScheme.primary) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, iconTint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
