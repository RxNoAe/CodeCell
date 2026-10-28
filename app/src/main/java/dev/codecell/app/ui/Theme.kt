package dev.codecell.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codecell.app.R

object Cs {
    val Bg = Color(0xFF0B0B0E)
    val BgTop = Color(0xFF0F0F14)
    val Sheet = Color(0xFF141418)
    val SheetTop = Color(0xFF1A1A20)
    val Input = Color(0xFF17171C)
    val InputTop = Color(0xFF1C1C23)
    val Bubble = Color(0xFF2A2A31)
    val BubbleTop = Color(0xFF2D2D35)
    val Text = Color(0xFFF4F4F6)
    val Body = Color(0xFFD6D6DD)
    val Muted = Color(0xFF9E9EA9)
    val Dim = Color(0xFF6C6C77)
    val Line = Color(0xFF25252C)
    val LineSoft = Color(0xFF1D1D23)
    val Error = Color(0xFFFF9A8F)
    val Success = Color(0xFF7ED6A5)
    val Warn = Color(0xFFFFC66B)
    val Reasoning = Color(0xFF9A9AA6)
    val ToolShell = Color(0xFFFFB86B)
    val ToolCode = Color(0xFF8AB4F8)
    val ToolRead = Color(0xFF7FD1B9)
    val ToolNet = Color(0xFF7DD3FC)
    val ToolAgent = Color(0xFFC4A7FF)

    val PageGradient = Brush.verticalGradient(listOf(Color(0xFF0E0E13), Bg))
    val ComposerGradient = Brush.verticalGradient(listOf(Color(0xFF1D1D24), Color(0xFF16161B)))
    val BubbleGradient = Brush.linearGradient(listOf(Color(0xFF303038), Color(0xFF26262D)))
    val ButtonGradient = Brush.verticalGradient(listOf(Color(0xFFF7F7F9), Color(0xFFE6E6EB)))
    val Glow = Color(0x14A9B0FF)
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.3.sp,
    ),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

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
            surfaceContainer = Cs.Sheet,
            surfaceContainerHigh = Cs.SheetTop,
            outline = Cs.Line,
            error = Cs.Error,
        ),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
