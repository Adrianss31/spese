package it.avanzi.spese.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.avanzi.spese.ui.theme.LocalReducedMotion
import it.avanzi.spese.ui.theme.SpeseColors
import it.avanzi.spese.ui.theme.SpeseMotion
import it.avanzi.spese.ui.theme.SpeseText
import java.util.Locale

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(Locale.ITALY),
            Modifier.weight(1f).semantics { heading() },
            color = SpeseColors.TextFaint,
            style = SpeseText.Section,
        )
        trailing?.let { Text(it, color = SpeseColors.TextMuted, style = SpeseText.MetaMedium) }
    }
}

@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = SpeseColors.Accent,
    height: Dp = 10.dp,
    cascadeIndex: Int = 0,
) {
    val target = fraction.coerceIn(0f, 1f)
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) target else 0f) }
    LaunchedEffect(target, reduced, cascadeIndex) {
        if (reduced) progress.snapTo(target)
        else progress.animateTo(target, tween(900, cascadeIndex * 60, SpeseMotion.Standard))
    }

    Canvas(modifier.fillMaxWidth().height(height).semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
    }) {
        val radius = size.height / 2
        drawRoundRect(SpeseColors.Track, cornerRadius = CornerRadius(radius))
        val width = size.width * (if (reduced) target else progress.value)
        if (width > 0f) {
            drawRoundRect(
                color, size = Size(width, size.height),
                cornerRadius = CornerRadius(radius.coerceAtMost(width / 2)),
            )
        }
    }
}
