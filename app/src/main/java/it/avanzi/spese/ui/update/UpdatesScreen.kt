package it.avanzi.spese.ui.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.avanzi.spese.R
import it.avanzi.spese.data.PreferencesRepository
import it.avanzi.spese.data.SpesePreferences
import it.avanzi.spese.data.update.AvailableUpdate
import it.avanzi.spese.data.update.UpdateError
import it.avanzi.spese.data.update.UpdateManager
import it.avanzi.spese.data.update.UpdateState
import it.avanzi.spese.domain.ItalianFormats
import it.avanzi.spese.domain.UpdateChannel
import it.avanzi.spese.ui.components.AppBottomSheet
import it.avanzi.spese.ui.components.ProgressBar
import it.avanzi.spese.ui.components.SegmentedControl
import it.avanzi.spese.ui.components.SwitchRow
import it.avanzi.spese.ui.home.Action
import it.avanzi.spese.ui.home.Card
import it.avanzi.spese.ui.home.IconAction
import it.avanzi.spese.ui.home.PageHeader
import it.avanzi.spese.ui.theme.LocalReducedMotion
import it.avanzi.spese.ui.theme.SpeseColors
import it.avanzi.spese.ui.theme.SpeseShapes
import it.avanzi.spese.ui.theme.SpeseText
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun UpdatesScreen(
    state: UpdateState,
    preferences: SpesePreferences,
    store: PreferencesRepository,
    manager: UpdateManager,
    back: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installed = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    var editing by remember { mutableStateOf(false) }
    var credential by remember { mutableStateOf("") }
    val available = state.available
    PageHeader(stringResource(R.string.updates), back)
    Card {
        if (available != null) {
            Text(stringResource(R.string.available_version, available.version.versionName), style = SpeseText.CardTitle)
            Text(stringResource(R.string.installed_version, installed, megabytes(available.size)), style = SpeseText.Meta, color = SpeseColors.TextMuted)
            if (available.version.notes.isNotBlank()) Text(available.version.notes, style = SpeseText.Body)
            val progress = state.progress
            if (progress != null) {
                Text(stringResource(R.string.downloading), style = SpeseText.BodyMedium)
                val ratio = progress.toFloat() / available.size
                ProgressBar(ratio)
                Text(
                    stringResource(R.string.download_progress, (ratio * 100).toInt(), megabytes(progress), megabytes(available.size)),
                    style = SpeseText.Meta, color = SpeseColors.TextMuted,
                )
            } else if (state.ready != null) {
                Action(stringResource(R.string.install_now), {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                    } else scope.launch { manager.install() }
                }, Modifier.fillMaxWidth(), primary = true, icon = "download")
                Text(stringResource(R.string.install_note), style = SpeseText.Meta, color = SpeseColors.TextMuted)
            } else Action(
                stringResource(R.string.download_install), { scope.launch { manager.download() } },
                Modifier.fillMaxWidth(), primary = true, icon = "download",
            )
        } else {
            val current = state.error == null && !state.checking && preferences.lastCheck != null
            Text(stringResource(if (current) R.string.up_to_date else R.string.available_version, installed), style = SpeseText.CardTitle)
        }
        state.error?.let {
            Text(stringResource(when (it) {
                UpdateError.CREDENTIALS -> R.string.update_private
                UpdateError.NETWORK -> R.string.update_network_error
                UpdateError.WIFI -> R.string.update_needs_wifi
                UpdateError.DOWNLOAD -> R.string.update_download_error
                UpdateError.INSTALL -> R.string.update_install_error
            }), color = SpeseColors.Warning, style = SpeseText.MetaMedium)
        }
    }
    Card {
        SwitchRow(stringResource(R.string.auto_update), preferences.autoCheck, { value ->
            scope.launch { store.setAutoCheck(value); manager.schedule() }
        }, subtitle = stringResource(R.string.auto_update_hint))
        SwitchRow(stringResource(R.string.wifi_only), preferences.wifiOnly, { value ->
            scope.launch { store.setWifiOnly(value); manager.schedule() }
        })
        Text(stringResource(R.string.update_channel), style = SpeseText.Meta, color = SpeseColors.TextMuted)
        SegmentedControl(
            UpdateChannel.entries, preferences.channel,
            { stringResource(if (it == UpdateChannel.STABLE) R.string.stable else R.string.beta) },
            { value -> scope.launch { store.setChannel(value); manager.check(force = true) } },
        )
    }
    val last = preferences.lastCheck?.atZone(ZoneId.systemDefault())
    Text(
        if (last == null) stringResource(R.string.never_checked) else stringResource(
            R.string.last_check, ItalianFormats.shortDate(last.toLocalDate()), ItalianFormats.time(last.toLocalTime()),
        ),
        color = SpeseColors.TextMuted, style = SpeseText.Meta,
    )
    val reduced = LocalReducedMotion.current
    val spin = remember { Animatable(0f) }
    LaunchedEffect(state.checking, reduced) {
        spin.snapTo(0f)
        while (state.checking && !reduced) {
            spin.animateTo(360f, tween(900, easing = LinearEasing))
            spin.snapTo(0f)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.Icon(
            it.avanzi.spese.ui.icons.SpeseIcons["reload"], null,
            Modifier.size(18.dp).graphicsLayer { rotationZ = spin.value }, tint = SpeseColors.TextMuted,
        )
        Action(stringResource(if (state.checking) R.string.checking else R.string.check_now), {
            scope.launch { manager.check(force = true) }
        }, enabled = !state.checking && state.progress == null)
    }
    if (state.error == UpdateError.CREDENTIALS || manager.hasCredentials()) {
        Action(stringResource(R.string.update_access), { credential = ""; editing = true })
    }
    Text(stringResource(R.string.signed_releases_note), color = SpeseColors.TextFaint, style = SpeseText.Meta)
    AppBottomSheet(editing, stringResource(R.string.update_access), { editing = false; credential = "" }) {
        Text(stringResource(R.string.update_token_hint), style = SpeseText.Body)
        androidx.compose.material3.OutlinedTextField(
            value = credential, onValueChange = { credential = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.update_token)) },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
        )
        Action(stringResource(R.string.save), {
            val value = credential
            credential = ""
            editing = false
            scope.launch { manager.setCredential(value) }
        }, Modifier.fillMaxWidth(), primary = true, enabled = credential.isNotBlank())
        if (manager.hasCredentials()) Action(stringResource(R.string.remove_token), {
            editing = false
            credential = ""
            scope.launch { manager.setCredential(null) }
        }, danger = true)
    }
}

@Composable
fun UpdateBanner(update: AvailableUpdate, open: () -> Unit, dismiss: () -> Unit) {
    val reduce = LocalReducedMotion.current
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(reduce) {
        while (!reduce) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(1600))
        }
    }
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF1B2410), SpeseShapes.Row)
            .border(1.dp, Color(0xFF3A4D14), SpeseShapes.Row).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                if (!reduce) Box(Modifier.size(8.dp).graphicsLayer {
                    scaleX = 1 + pulse.value * 1.4f
                    scaleY = scaleX
                    alpha = .55f * (1 - pulse.value)
                }.background(SpeseColors.Accent, SpeseShapes.Pill))
                Box(Modifier.size(8.dp).background(SpeseColors.Accent, SpeseShapes.Pill))
            }
            Text(stringResource(R.string.version_available, update.version.versionName), Modifier.weight(1f), style = SpeseText.CardTitle)
            IconAction("close", stringResource(R.string.close), dismiss)
        }
        if (update.version.notes.isNotBlank()) Text(update.version.notes, style = SpeseText.Meta, color = SpeseColors.TextSoft, maxLines = 3)
        Action(stringResource(R.string.update_now), open)
    }
}

private fun megabytes(bytes: Long): String = String.format(Locale.ITALY, "%.1f", bytes / 1_000_000.0)
