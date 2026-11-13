package it.avanzi.spese.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.avanzi.spese.ui.icons.SpeseIcons
import it.avanzi.spese.ui.theme.LocalReducedMotion
import it.avanzi.spese.ui.theme.SpeseColors
import it.avanzi.spese.ui.theme.SpeseMotion
import it.avanzi.spese.ui.theme.SpeseShapes
import it.avanzi.spese.ui.theme.SpeseText
import it.avanzi.spese.ui.theme.pressScale
import java.math.BigDecimal

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(SpeseColors.Surface, SpeseShapes.Card).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}

@Composable
fun Action(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    dimmed: Boolean = false,
    icon: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val background = when {
        danger -> SpeseColors.DangerSurface
        primary -> SpeseColors.Accent
        else -> SpeseColors.Surface2
    }
    val foreground = when {
        danger -> SpeseColors.DangerText
        primary -> SpeseColors.Bg
        else -> SpeseColors.Text
    }
    Row(
        modifier.heightIn(min = if (primary) 56.dp else 44.dp).pressScale(interaction)
            .clip(SpeseShapes.Row).background(background.copy(alpha = if (!enabled || dimmed) .45f else 1f))
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        icon?.let { Icon(SpeseIcons[it], null, Modifier.size(20.dp), tint = foreground) }
        Text(label, style = SpeseText.BodyMedium.copy(fontWeight = FontWeight.SemiBold), color = foreground)
    }
}

@Composable
fun IconAction(icon: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier.size(44.dp).pressScale(interaction).clip(SpeseShapes.Tile)
            .background(SpeseColors.Surface)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(SpeseIcons[icon], label, Modifier.size(20.dp), tint = SpeseColors.Text)
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier,
        singleLine = true, label = { Text(label) },
        placeholder = if (placeholder == null) null else { { Text(placeholder) } },
        shape = RoundedCornerShape(14.dp), textStyle = if (decimal) SpeseText.RowAmount else SpeseText.Body,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SpeseColors.Bg, unfocusedContainerColor = SpeseColors.Bg,
            unfocusedBorderColor = SpeseColors.Border,
        ),
    )
}

@Composable
fun ExpandOptions(visible: Boolean, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible,
        enter = if (reduced) fadeIn(tween(120)) else fadeIn(tween(320)) +
            expandVertically(tween(320, easing = SpeseMotion.Standard)),
        exit = if (reduced) fadeOut(tween(120)) else fadeOut(tween(320)) +
            shrinkVertically(tween(320, easing = SpeseMotion.Standard)),
    ) { content() }
}

fun parseCents(value: String): Long? {
    val normalized = value.trim().replace(',', '.')
    if (!normalized.matches(Regex("[0-9]+(?:\\.[0-9]{0,2})?"))) return null
    return runCatching { BigDecimal(normalized).movePointRight(2).longValueExact() }
        .getOrNull()?.takeIf { it > 0 }
}
