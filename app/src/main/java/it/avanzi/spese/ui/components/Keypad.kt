package it.avanzi.spese.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.avanzi.spese.R
import it.avanzi.spese.domain.AmountKey
import it.avanzi.spese.ui.icons.SpeseIcons
import it.avanzi.spese.ui.theme.SpeseColors
import it.avanzi.spese.ui.theme.SpeseShapes
import it.avanzi.spese.ui.theme.SpeseText
import it.avanzi.spese.ui.theme.pressScale

private val keyRows = AmountKey.entries.chunked(3)

@Composable
fun Keypad(
    onKey: (AmountKey) -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 54.dp,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keyRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val source = remember { MutableInteractionSource() }
                    val pressed by source.collectIsPressedAsState()
                    val label = when (key) {
                        AmountKey.Decimal -> stringResource(R.string.key_decimal)
                        AmountKey.Delete -> stringResource(R.string.key_delete)
                        else -> null
                    }
                    Box(
                        Modifier.weight(1f).heightIn(min = keyHeight)
                            .pressScale(source).clip(SpeseShapes.Key)
                            .background(if (pressed) SpeseColors.Surface3 else SpeseColors.Surface2)
                            .clickable(source, indication = null, role = Role.Button) { onKey(key) }
                            .then(if (label == null) Modifier else Modifier.semantics {
                                contentDescription = label
                            }),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == AmountKey.Delete) {
                            Icon(SpeseIcons["delete"], null, tint = SpeseColors.Text)
                        } else {
                            Text(key.digit?.toString() ?: ",", style = SpeseText.Key)
                        }
                    }
                }
            }
        }
    }
}
