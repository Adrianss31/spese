package it.avanzi.spese.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.avanzi.spese.R
import it.avanzi.spese.ui.icons.SpeseIcons
import it.avanzi.spese.ui.theme.LocalReducedMotion
import it.avanzi.spese.ui.theme.SpeseColors
import it.avanzi.spese.ui.theme.SpeseMotion
import it.avanzi.spese.ui.theme.SpeseShapes
import it.avanzi.spese.ui.theme.SpeseText
import it.avanzi.spese.ui.theme.pressScale
import kotlinx.coroutines.delay

data class ToastMessage(val id: Long, val text: String, val canUndo: Boolean = true)

@Composable
fun UndoToast(
    message: ToastMessage?,
    onDismiss: (Long) -> Unit,
    onUndo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val accessibility = LocalAccessibilityManager.current
    val dismiss by rememberUpdatedState(onDismiss)
    val lastMessage = remember { androidx.compose.runtime.mutableStateOf<ToastMessage?>(null) }
    LaunchedEffect(message) {
        if (message != null) {
            lastMessage.value = message
            val duration = accessibility?.calculateRecommendedTimeoutMillis(
                3400, containsIcons = true, containsText = true, containsControls = message.canUndo,
            ) ?: 3400L
            delay(duration)
            dismiss(message.id)
        }
    }
    AnimatedVisibility(
        message != null, modifier,
        enter = if (reduced) fadeIn(tween(120)) else
            fadeIn(tween(360)) + slideInVertically(tween(360, easing = SpeseMotion.Enter)) { -it },
        exit = if (reduced) fadeOut(tween(120)) else
            fadeOut(tween(280)) + slideOutVertically(tween(280, easing = SpeseMotion.Exit)) { -it },
    ) {
        val item = message ?: lastMessage.value ?: return@AnimatedVisibility
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .shadow(14.dp, SpeseShapes.Row).background(SpeseColors.Toast, SpeseShapes.Row)
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(28.dp).background(SpeseColors.Accent, SpeseShapes.Pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SpeseIcons["check"], null, Modifier.size(16.dp), tint = SpeseColors.Bg)
            }
            Text(
                item.text,
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                color = SpeseColors.Bg,
                style = SpeseText.BodyMedium.copy(fontSize = 14.sp),
            )
            if (item.canUndo) {
                val source = remember { MutableInteractionSource() }
                Box(
                    Modifier.heightIn(min = 44.dp).pressScale(source).clip(RoundedCornerShape(12.dp))
                        .clickable(source, indication = null, enabled = message != null, role = Role.Button) {
                            onUndo(item.id)
                            dismiss(item.id)
                        }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.undo), color = SpeseColors.Bg,
                        style = SpeseText.Body.copy(
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline,
                        ),
                    )
                }
            }
        }
    }
}
