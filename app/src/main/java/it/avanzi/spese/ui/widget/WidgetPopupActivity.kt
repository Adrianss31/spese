package it.avanzi.spese.ui.widget

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color as AndroidColor
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import it.avanzi.spese.R
import it.avanzi.spese.data.LedgerWorker
import it.avanzi.spese.domain.ItalianFormats
import it.avanzi.spese.ui.expense.ExpenseEditor
import it.avanzi.spese.ui.home.SpeseViewModel
import it.avanzi.spese.ui.icons.SpeseIcons
import it.avanzi.spese.ui.theme.LocalReducedMotion
import it.avanzi.spese.ui.theme.SpeseColors
import it.avanzi.spese.ui.theme.SpeseMotion
import it.avanzi.spese.ui.theme.SpeseShapes
import it.avanzi.spese.ui.theme.SpeseText
import it.avanzi.spese.ui.theme.SpeseTheme
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WidgetPopupActivity : ComponentActivity() {
    private val model by viewModels<SpeseViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark-only app: always light system bar icons, whatever the system theme.
        enableEdgeToEdge(SystemBarStyle.dark(AndroidColor.TRANSPARENT), SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= 31) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = 18 }
        }
        val bounds = intent.sourceBounds
        val metrics = resources.displayMetrics
        val pivot = if (bounds == null) TransformOrigin.Center else TransformOrigin(
            (bounds.exactCenterX() / metrics.widthPixels).coerceIn(0f, 1f),
            (bounds.exactCenterY() / metrics.heightPixels).coerceIn(0f, 1f),
        )
        setContent { SpeseTheme { Popup(model, pivot, this) } }
    }
}

@Composable
private fun Popup(model: SpeseViewModel, pivot: TransformOrigin, activity: Activity) {
    val ledger by model.state.collectAsState()
    val reduce = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val animation = remember { Animatable(0f) }
    var saving by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val close = {
        if (!saving && !closing) closing = true
    }
    BackHandler(onBack = close)
    LaunchedEffect(closing, reduce) {
        animation.animateTo(
            if (closing) 0f else 1f,
            tween(if (reduce) 120 else 340, easing = if (closing) SpeseMotion.Exit else SpeseMotion.Enter),
        )
        if (closing) activity.finishAndRemoveTask()
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = animation.value }
                .background(Color(0x8C040605))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = close),
        )
        Column(
            Modifier.align(Alignment.Center).safeDrawingPadding().imePadding().padding(16.dp)
                .widthIn(max = 480.dp).fillMaxWidth().graphicsLayer {
                    transformOrigin = pivot
                    scaleX = if (reduce) 1f else .4f + animation.value * .6f
                    scaleY = scaleX
                    alpha = animation.value
                }.background(SpeseColors.Surface, RoundedCornerShape(30.dp))
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(38.dp).background(SpeseColors.Accent, SpeseShapes.SmallTile), contentAlignment = Alignment.Center) {
                    Icon(SpeseIcons["plus"], null, Modifier.size(20.dp), tint = SpeseColors.Bg)
                }
                Column {
                    Text(stringResource(R.string.new_expense), style = SpeseText.SheetTitle)
                    Text(stringResource(R.string.widget_now, ItalianFormats.time(LocalTime.now())), style = SpeseText.Meta, color = SpeseColors.TextMuted)
                }
            }
            ExpenseEditor(
                ledger.categories, saving || closing,
                onSave = { draft ->
                    if (!saving && !closing) {
                        saving = true
                        failed = false
                        scope.launch {
                            try {
                                model.save(draft.cents, draft.category, draft.description, draft.date, null)
                                SpeseWidget.refresh(activity, saved = true)
                                LedgerWorker.onOpen(activity)
                                closing = true
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                failed = true
                            } finally {
                                saving = false
                            }
                        }
                    }
                }, compact = true, onCancel = close,
            )
            if (failed) Text(stringResource(R.string.operation_failed), color = SpeseColors.Danger, style = SpeseText.Meta)
        }
    }
}
