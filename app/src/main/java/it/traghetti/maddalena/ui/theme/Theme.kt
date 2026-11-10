package it.traghetti.maddalena.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.traghetti.maddalena.domain.Company

object Sea {
    val Deep = Color(0xFF0B3C5D)
    val Ocean = Color(0xFF0B6E99)
    val Lagoon = Color(0xFF14A3B8)
    val Foam = Color(0xFFE8F6FA)
    val Sand = Color(0xFFF6F3EC)
    val Coral = Color(0xFFF2784B)
    val Delcomar = Color(0xFFE8730C)
    val MaddalenaLines = Color(0xFF1565C0)
    val Live = Color(0xFF1E9E6A)

    val headerBrush: Brush get() = Brush.verticalGradient(listOf(Deep, Ocean, Lagoon))
    val headerBrushDark: Brush get() = Brush.verticalGradient(listOf(Color(0xFF06202F), Color(0xFF0A4059), Color(0xFF0D6676)))
}

val Company.color: Color
    get() = when (this) {
        Company.DELCOMAR -> Sea.Delcomar
        Company.MADDALENA_LINES -> Sea.MaddalenaLines
    }

private val LightColors = lightColorScheme(
    primary = Sea.Ocean,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBF7),
    onPrimaryContainer = Color(0xFF062A3D),
    secondary = Sea.Coral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE1D4),
    onSecondaryContainer = Color(0xFF4A1A05),
    tertiary = Sea.Lagoon,
    background = Sea.Sand,
    onBackground = Color(0xFF14212B),
    surface = Color.White,
    onSurface = Color(0xFF14212B),
    surfaceVariant = Color(0xFFE9EEF1),
    onSurfaceVariant = Color(0xFF51606B),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF3F6F8),
    surfaceContainerLow = Color(0xFFFAFBFC),
    outline = Color(0xFFB7C3CB),
    outlineVariant = Color(0xFFDDE4E8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CCDEE),
    onPrimary = Color(0xFF03293A),
    primaryContainer = Color(0xFF0D4C69),
    onPrimaryContainer = Color(0xFFCDEBF7),
    secondary = Color(0xFFFFB597),
    onSecondary = Color(0xFF4A1A05),
    secondaryContainer = Color(0xFF6B2D12),
    onSecondaryContainer = Color(0xFFFFE1D4),
    tertiary = Color(0xFF6FD6E4),
    background = Color(0xFF0B151C),
    onBackground = Color(0xFFE3EAEE),
    surface = Color(0xFF142029),
    onSurface = Color(0xFFE3EAEE),
    surfaceVariant = Color(0xFF22313B),
    onSurfaceVariant = Color(0xFFA9B8C2),
    surfaceContainer = Color(0xFF142029),
    surfaceContainerHigh = Color(0xFF1B2A34),
    surfaceContainerLow = Color(0xFF101B23),
    outline = Color(0xFF4B5C67),
    outlineVariant = Color(0xFF2B3B46),
)

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Cifre a larghezza fissa per gli orari, così le colonne restano allineate. */
val TimeStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, fontFeatureSettings = "tnum")

@Composable
fun TraghettiTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
