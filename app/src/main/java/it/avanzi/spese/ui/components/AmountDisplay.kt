package it.avanzi.spese.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.avanzi.spese.R
import it.avanzi.spese.domain.AmountInput
import it.avanzi.spese.ui.theme.LocalReducedMotion
import it.avanzi.spese.ui.theme.SpeseColors
import it.avanzi.spese.ui.theme.SpeseMotion
import it.avanzi.spese.ui.theme.SpeseShapes
import it.avanzi.spese.ui.theme.SpeseText
import kotlinx.coroutines.delay

@Composable
fun AmountDisplay(
    input: AmountInput,
    bumpCount: Int,
    errorCount: Int,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val scale = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    var caretVisible by remember { mutableStateOf(true) }

    LaunchedEffect(bumpCount, reduced) {
        scale.snapTo(if (bumpCount > 0 && !reduced) 1.07f else 1f)
        if (bumpCount > 0 && !reduced) scale.animateTo(1f, SpeseMotion.springBack())
    }
    LaunchedEffect(errorCount, reduced) {
        shake.snapTo(0f)
        if (errorCount > 0 && !reduced) {
            shake.animateTo(0f, keyframes {
                durationMillis = 380
                -7f at 76
                7f at 152
                -7f at 228
                7f at 304
            })
        }
    }
    LaunchedEffect(reduced, input) {
        caretVisible = true
        while (!reduced) {
            delay(525)
            caretVisible = !caretVisible
        }
    }

    BoxWithConstraints(
        modifier.heightIn(min = 76.dp),
        contentAlignment = Alignment.Center,
    ) {
        val digits = input.display.length
        val amountSize = ((maxWidth.value - 40f) / (digits * .64f)).coerceIn(24f, 60f).sp
        Row(
            Modifier.graphicsLayer {
                translationX = shake.value.dp.toPx()
                scaleX = scale.value
                scaleY = scale.value
            }.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                input.display,
                color = if (input.isEmpty) SpeseColors.TextDisabled else SpeseColors.Text,
                style = SpeseText.Amount.copy(fontSize = amountSize),
                maxLines = 1,
                softWrap = false,
            )
            Box(
                Modifier.size(width = 3.dp, height = 48.dp)
                    .graphicsLayer { alpha = if (caretVisible || reduced) 1f else 0f }
                    .background(SpeseColors.Accent, SpeseShapes.Pill),
            )
            Text(
                stringResource(R.string.currency_symbol),
                color = SpeseColors.TextMuted,
                style = SpeseText.TotalSuffix.copy(fontSize = 28.sp),
            )
        }
    }
}
