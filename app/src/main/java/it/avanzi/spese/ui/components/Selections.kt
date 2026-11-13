package it.avanzi.spese.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.avanzi.spese.ui.icons.SpeseIcons
import it.avanzi.spese.ui.theme.LocalReducedMotion
import it.avanzi.spese.ui.theme.SpeseColors
import it.avanzi.spese.ui.theme.SpeseMotion
import it.avanzi.spese.ui.theme.SpeseShapes
import it.avanzi.spese.ui.theme.SpeseText
import it.avanzi.spese.ui.theme.pressScale

@Composable
fun CategoryChip(
    label: String,
    iconKey: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier.heightIn(min = 44.dp).pressScale(source).clip(SpeseShapes.Pill)
            .background(if (selected) color else SpeseColors.Surface2)
            .selectable(selected, source, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(start = 9.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(
            iconKey, if (selected) SpeseColors.Bg.copy(alpha = .14f) else color,
            size = 26.dp, iconSize = 15.dp, rounded = true,
        )
        Text(label, color = if (selected) SpeseColors.Bg else SpeseColors.Text, style = SpeseText.MetaMedium)
    }
}

@Composable
fun CategoryTile(
    iconKey: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp,
    rounded: Boolean = false,
) {
    val shape = when {
        rounded -> SpeseShapes.Pill
        size < 42.dp -> SpeseShapes.SmallTile
        else -> SpeseShapes.Tile
    }
    Box(modifier.size(size).background(color, shape), contentAlignment = Alignment.Center) {
        Icon(SpeseIcons[iconKey], null, Modifier.size(iconSize), tint = SpeseColors.Bg)
    }
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconKey: String? = null,
) {
    val reduced = LocalReducedMotion.current
    val source = remember { MutableInteractionSource() }
    val position by animateDpAsState(
        if (checked) 20.dp else 0.dp,
        if (reduced) snap() else SpeseMotion.springBack(),
        label = "Switch position",
    )
    val track by animateColorAsState(
        if (checked) SpeseColors.Accent else SpeseColors.Surface3,
        tween(if (reduced) 120 else 200),
        label = "Switch track",
    )
    val knob by animateColorAsState(
        if (checked) SpeseColors.Bg else SpeseColors.TextSoft,
        tween(if (reduced) 120 else 200),
        label = "Switch knob",
    )
    Row(
        modifier.fillMaxWidth().heightIn(min = if (subtitle == null) 50.dp else 62.dp)
            .pressScale(source, .98f)
            .toggleable(checked, source, indication = null, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        iconKey?.let { Icon(SpeseIcons[it], null, Modifier.size(18.dp), tint = SpeseColors.TextSoft) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = SpeseText.BodyMedium)
            subtitle?.let { Text(it, color = SpeseColors.TextFaint, style = SpeseText.Meta) }
        }
        Box(Modifier.size(48.dp, 30.dp).background(track, SpeseShapes.Pill).padding(4.dp)) {
            Box(Modifier.offset(x = position).size(22.dp).background(knob, SpeseShapes.Pill))
        }
    }
}

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            val source = remember(option) { MutableInteractionSource() }
            val active = option == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).pressScale(source)
                    .clip(shape)
                    .selectable(active, source, indication = null, role = Role.RadioButton) { onSelect(option) }
                    .padding(vertical = 4.dp),
            ) {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 36.dp)
                        .background(if (active) SpeseColors.Surface3 else Color.Transparent, shape)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        color = if (active) SpeseColors.Text else SpeseColors.TextMuted,
                        style = SpeseText.MetaMedium.copy(fontWeight = FontWeight.SemiBold),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
