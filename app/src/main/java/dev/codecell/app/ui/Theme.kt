package dev.codecell.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Cs {
    val Bg = Color(0xFF111113)
    val Sheet = Color(0xFF17171A)
    val Input = Color(0xFF1C1C1F)
    val Bubble = Color(0xFF2A2A2E)
    val Text = Color(0xFFEDEDEF)
    val Muted = Color(0xFF9A9AA2)
    val Dim = Color(0xFF6E6E76)
    val Line = Color(0xFF333338)
    val Error = Color(0xFFFF8A80)
    val Dot = Color(0xFF4A4A52)
}

@Composable
fun CodeCellTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cs.Text,
            onPrimary = Cs.Bg,
            background = Cs.Bg,
            onBackground = Cs.Text,
            surface = Cs.Bg,
            onSurface = Cs.Text,
            surfaceVariant = Cs.Input,
            onSurfaceVariant = Cs.Muted,
            outline = Cs.Line,
            error = Cs.Error,
        ),
        content = content,
    )
}
