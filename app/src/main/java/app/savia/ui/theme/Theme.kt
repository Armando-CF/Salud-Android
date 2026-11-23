package app.savia.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
    primary = Color(0xFF1B6B58),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4EEDA),
    onPrimaryContainer = Color(0xFF002018),
    secondary = Color(0xFF4B635A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE9DC),
    onSecondaryContainer = Color(0xFF072019),
    tertiary = Color(0xFFA6405F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E1),
    onTertiaryContainer = Color(0xFF3F001B),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FAF6),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FAF6),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DE),
    onSurfaceVariant = Color(0xFF404944),
    outline = Color(0xFF707974),
    outlineVariant = Color(0xFFBFC9C2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F0),
    surfaceContainer = Color(0xFFE9EFEA),
    surfaceContainerHigh = Color(0xFFE3E9E5),
    surfaceContainerHighest = Color(0xFFDEE4DF),
    inverseSurface = Color(0xFF2B322E),
    inverseOnSurface = Color(0xFFECF2ED),
    inversePrimary = Color(0xFF8BD6BC),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8BD6BC),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF005140),
    onPrimaryContainer = Color(0xFFB4EEDA),
    secondary = Color(0xFFB2CCC1),
    onSecondary = Color(0xFF1E352D),
    secondaryContainer = Color(0xFF344C43),
    onSecondaryContainer = Color(0xFFCEE9DC),
    tertiary = Color(0xFFFFB1C5),
    onTertiary = Color(0xFF650B31),
    tertiaryContainer = Color(0xFF862847),
    onTertiaryContainer = Color(0xFFFFD9E1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4DF),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4DF),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFBFC9C2),
    outline = Color(0xFF89938D),
    outlineVariant = Color(0xFF404944),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
    inverseSurface = Color(0xFFDEE4DF),
    inverseOnSurface = Color(0xFF2B322E),
    inversePrimary = Color(0xFF1B6B58),
)

/** Colores con significado propio de la app (además del esquema Material). */
@Immutable
data class SaviaColors(
    val isDark: Boolean,
    /** Menstruación. */
    val period: Color,
    val periodSoft: Color,
    /** Ventana fértil. */
    val fertile: Color,
    val fertileSoft: Color,
    /** Ovulación. */
    val ovulation: Color,
    /** Bien / dentro de rango. */
    val good: Color,
    /** Atención. */
    val warn: Color,
    /** Mal / fuera de rango. */
    val bad: Color,
    /** Paleta para series de gráficas. */
    val chart: List<Color>,
    /** Piel neutra del atlas corporal. */
    val bodyFill: Color,
    val bodyStroke: Color,
    val bone: Color,
) {
    /** Color según intensidad 1..10 (verde → ámbar → rojo). */
    fun intensity(i: Int): Color = when {
        i <= 3 -> good
        i <= 6 -> warn
        else -> bad
    }
}

private val LightSavia = SaviaColors(
    isDark = false,
    period = Color(0xFFD23F63),
    periodSoft = Color(0xFFFAD3DC),
    fertile = Color(0xFF2E8FB3),
    fertileSoft = Color(0xFFCFE9F3),
    ovulation = Color(0xFF7A55C7),
    good = Color(0xFF2E8F5B),
    warn = Color(0xFFC77E00),
    bad = Color(0xFFC6403A),
    chart = listOf(Color(0xFF1B6B58), Color(0xFFD23F63), Color(0xFF2E8FB3), Color(0xFFC77E00), Color(0xFF7A55C7), Color(0xFF5E6B2F)),
    bodyFill = Color(0xFFE6EEE9),
    bodyStroke = Color(0xFF8FA39A),
    bone = Color(0xFFF2EBDD),
)

private val DarkSavia = SaviaColors(
    isDark = true,
    period = Color(0xFFFF7A98),
    periodSoft = Color(0xFF5C2333),
    fertile = Color(0xFF6CC4E4),
    fertileSoft = Color(0xFF1D4656),
    ovulation = Color(0xFFB79BF2),
    good = Color(0xFF6FD39A),
    warn = Color(0xFFF2B64A),
    bad = Color(0xFFFF8A80),
    chart = listOf(Color(0xFF8BD6BC), Color(0xFFFF7A98), Color(0xFF6CC4E4), Color(0xFFF2B64A), Color(0xFFB79BF2), Color(0xFFC3D08A)),
    bodyFill = Color(0xFF26302B),
    bodyStroke = Color(0xFF6F837A),
    bone = Color(0xFFD9D0BE),
)

val LocalSaviaColors = staticCompositionLocalOf { LightSavia }

/** Acceso corto: `SaviaTheme.colors.period`. */
object SaviaTheme {
    val colors: SaviaColors
        @Composable get() = LocalSaviaColors.current
}

private val SaviaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** @param themeMode "system", "light" o "dark". */
@Composable
fun SaviaTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalSaviaColors provides if (dark) DarkSavia else LightSavia) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            shapes = SaviaShapes,
            content = content,
        )
    }
}
