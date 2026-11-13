package it.avanzi.spese.ui.components

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import it.avanzi.spese.R
import it.avanzi.spese.ui.icons.SpeseIcons
import it.avanzi.spese.ui.theme.LocalReducedMotion
import it.avanzi.spese.ui.theme.SpeseColors
import it.avanzi.spese.ui.theme.SpeseMotion
import it.avanzi.spese.ui.theme.SpeseShapes
import it.avanzi.spese.ui.theme.SpeseText
import it.avanzi.spese.ui.theme.pressScale

@Composable
fun AppBottomSheet(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val transition = remember { MutableTransitionState(false) }
    LaunchedEffect(visible) { transition.targetState = visible }
    if (!visible && !transition.currentState && transition.isIdle) return

    val reduced = LocalReducedMotion.current
    val closeLabel = stringResource(R.string.close)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
            windowTitle = title,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) }
        BackHandler(onBack = onDismiss)
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                transition, Modifier.fillMaxSize(),
                enter = fadeIn(tween(if (reduced) 120 else 300)),
                exit = fadeOut(tween(if (reduced) 120 else 260)),
            ) {
                val source = remember { MutableInteractionSource() }
                Box(
                    Modifier.fillMaxSize().background(SpeseColors.Scrim)
                        .clickable(source, indication = null, enabled = visible, onClick = onDismiss)
                        .semantics { hideFromAccessibility() },
                )
            }
            AnimatedVisibility(
                transition,
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding().padding(top = 16.dp),
                enter = if (reduced) fadeIn(tween(120)) else slideInVertically(
                    tween(420, easing = SpeseMotion.Enter),
                ) { it },
                exit = if (reduced) fadeOut(tween(120)) else slideOutVertically(
                    tween(280, easing = SpeseMotion.Exit),
                ) { it },
            ) {
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth()
                        .clip(SpeseShapes.Sheet).background(SpeseColors.Surface)
                        .semantics { paneTitle = title }
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).size(40.dp, 5.dp)
                            .background(androidx.compose.ui.graphics.Color(0xFF3A3E48), SpeseShapes.Pill),
                    )
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, Modifier.weight(1f).semantics { heading() }, style = SpeseText.SheetTitle)
                        val source = remember { MutableInteractionSource() }
                        Box(
                            Modifier.size(44.dp).pressScale(source).clip(SpeseShapes.Tile)
                                .background(SpeseColors.Surface2)
                                .clickable(source, indication = null, enabled = visible, role = Role.Button, onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(SpeseIcons["close"], closeLabel, Modifier.size(18.dp), tint = SpeseColors.Text)
                        }
                    }
                    content()
                }
            }
        }
    }
}
