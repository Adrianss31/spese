package it.avanzi.spese.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

object SpeseMotion {
    val Standard = CubicBezierEasing(.2f, .8f, .2f, 1f)
    val Enter = CubicBezierEasing(.2f, .9f, .25f, 1f)
    val Exit = CubicBezierEasing(.4f, 0f, 1f, 1f)
    fun <T> springBack() = spring<T>(dampingRatio = .55f, stiffness = 600f)
}

val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun ProvideSpeseMotion(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var reduced by remember(context) { mutableStateOf(context.hasReducedMotion()) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = context.hasReducedMotion()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        reduced = context.hasReducedMotion()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    CompositionLocalProvider(LocalReducedMotion provides reduced, content = content)
}

private fun Context.hasReducedMotion(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = .94f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) pressedScale else 1f,
        animationSpec = when {
            reduced -> snap()
            pressed -> tween(80)
            else -> SpeseMotion.springBack()
        },
        label = "Press scale",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}
