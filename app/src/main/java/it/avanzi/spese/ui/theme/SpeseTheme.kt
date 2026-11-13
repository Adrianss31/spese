package it.avanzi.spese.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object SpeseColors {
    val Bg = Color(0xFF0E0F12)
    val Surface = Color(0xFF17191E)
    val Surface2 = Color(0xFF22252C)
    val Surface3 = Color(0xFF30343E)
    val Track = Color(0xFF262931)
    val BarMuted = Color(0xFF2E323B)
    val Divider = Color(0xFF22252C)
    val Border = Color(0xFF2C3039)
    val Text = Color(0xFFF2F2EE)
    val TextSoft = Color(0xFFC9CBD1)
    val TextMuted = Color(0xFF9A9DA6)
    val TextFaint = Color(0xFF8B8F99)
    val TextDisabled = Color(0xFF5A5E68)
    val Accent = Color(0xFFC6F432)
    val Danger = Color(0xFFFF7A59)
    val Warning = Color(0xFFF5D06B)
    val DangerSurface = Color(0xFF2A1A16)
    val DangerText = Color(0xFFFF9C82)
    val Scrim = Color(0x9E050608)
    val Toast = Text

    val CategoryPalette = listOf(
        Color(0xFF5EE0B5), Color(0xFF7CC4FF), Color(0xFFB9A4FF), Color(0xFFF5D06B),
        Color(0xFFFF9EC7), Color(0xFFFFB86B), Color(0xFF8FE388), Color(0xFF7FD8D8),
        Color(0xFFE7A6FF), Color(0xFFA7AAB3),
    )
}

object SpeseShapes {
    val Card = RoundedCornerShape(24.dp)
    val Group = RoundedCornerShape(20.dp)
    val Row = RoundedCornerShape(18.dp)
    val Key = RoundedCornerShape(16.dp)
    val Tile = RoundedCornerShape(14.dp)
    val SmallTile = RoundedCornerShape(13.dp)
    val Pill = CircleShape
    val Sheet = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val FloatingButton = RoundedCornerShape(22.dp)
}

private val colors = with(SpeseColors) {
    darkColorScheme(
        primary = Accent, onPrimary = Bg, primaryContainer = Surface3, onPrimaryContainer = Text,
        inversePrimary = Bg, secondary = TextSoft, onSecondary = Bg,
        secondaryContainer = Surface2, onSecondaryContainer = Text,
        tertiary = Warning, onTertiary = Bg, tertiaryContainer = Surface3, onTertiaryContainer = Warning,
        background = Bg, onBackground = Text, surface = Surface, onSurface = Text,
        surfaceVariant = Surface2, onSurfaceVariant = TextMuted, surfaceTint = Color.Transparent,
        inverseSurface = Toast, inverseOnSurface = Bg, error = Danger, onError = Bg,
        errorContainer = DangerSurface, onErrorContainer = DangerText, outline = TextFaint,
        outlineVariant = Border, scrim = Scrim, surfaceBright = Surface3, surfaceDim = Bg,
        surfaceContainerLowest = Bg, surfaceContainerLow = Surface, surfaceContainer = Surface,
        surfaceContainerHigh = Surface2, surfaceContainerHighest = Surface3,
        primaryFixed = Accent, primaryFixedDim = Accent, onPrimaryFixed = Bg,
        onPrimaryFixedVariant = Bg, secondaryFixed = TextSoft, secondaryFixedDim = TextMuted,
        onSecondaryFixed = Bg, onSecondaryFixedVariant = Bg, tertiaryFixed = Warning,
        tertiaryFixedDim = Warning, onTertiaryFixed = Bg, onTertiaryFixedVariant = Bg,
    )
}

private val typography = with(SpeseText) {
    Typography(
        displayLarge = Total, displayMedium = Amount, displaySmall = TotalSuffix,
        headlineLarge = ScreenTitle, headlineMedium = SheetTitle, headlineSmall = CardTitle,
        titleLarge = SheetTitle, titleMedium = CardTitle, titleSmall = RowAmount,
        bodyLarge = Body, bodyMedium = Body, bodySmall = Meta,
        labelLarge = BodyMedium, labelMedium = MetaMedium, labelSmall = Section,
    )
}

@Composable
fun SpeseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = Shapes(
            extraSmall = SpeseShapes.SmallTile,
            small = SpeseShapes.Key,
            medium = SpeseShapes.Row,
            large = SpeseShapes.Group,
            extraLarge = SpeseShapes.Card,
        ),
        content = {
            // Default text and icon colour for every surface, including sheets shown in their own dialog window.
            CompositionLocalProvider(LocalContentColor provides SpeseColors.Text) { ProvideSpeseMotion(content) }
        },
    )
}
