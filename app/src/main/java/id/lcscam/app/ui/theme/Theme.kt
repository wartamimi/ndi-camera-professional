package id.lcscam.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Cine {
    val Black = Color(0xFF070708)
    val Panel = Color(0xFF111113)
    val PanelRaised = Color(0xFF1A1A1D)
    val Line = Color(0xFF2B2B30)
    val Text = Color(0xFFE9E9EC)
    val Dim = Color(0xFF8B8B93)
    val Faint = Color(0xFF55555C)
    val Accent = Color(0xFFFF8A1F)
    val Rec = Color(0xFFE5322D)
    val Ok = Color(0xFF3CCB6B)
    val Warn = Color(0xFFFFC233)
    val Preview = Color(0xFF00C2FF)

    val Mono = FontFamily.Monospace
    val value = TextStyle(fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Text)
    val label = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp, color = Dim)
}

private val scheme = darkColorScheme(
    primary = Cine.Accent,
    onPrimary = Color.Black,
    secondary = Cine.Preview,
    background = Cine.Black,
    onBackground = Cine.Text,
    surface = Cine.Panel,
    onSurface = Cine.Text,
    surfaceVariant = Cine.PanelRaised,
    onSurfaceVariant = Cine.Dim,
    outline = Cine.Line,
    error = Cine.Rec,
)

@Composable
fun LcsCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        content = content,
    )
}
