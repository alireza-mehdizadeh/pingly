package ir.byalireza.pingly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import ir.byalireza.pingly.core.Persian
import ir.byalireza.pingly.ui.theme.PinglyColors

private val FieldShape = RoundedCornerShape(18.dp)

/**
 * Filled field in the style of the references: soft grey well, leading icon, optional label
 * above, navy outline while focused.
 */
@Composable
fun PinglyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLength: Int? = null,
    ltr: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier.fillMaxWidth()) {
        if (label != null) FieldLabel(label)
        BasicTextField(
            value = value,
            onValueChange = { next -> onValueChange(if (maxLength != null) next.take(maxLength) else next) },
            singleLine = singleLine,
            minLines = minLines,
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = SolidColor(PinglyColors.Pink),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = PinglyColors.Ink,
                textAlign = if (ltr) TextAlign.Left else TextAlign.Start,
                textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content,
            ),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(FieldShape)
                        .background(if (focused) PinglyColors.Surface else PinglyColors.SurfaceMuted)
                        .border(1.5.dp, if (focused) PinglyColors.Ink else Color.Transparent, FieldShape)
                        .padding(horizontal = 16.dp, vertical = if (singleLine) 0.dp else 16.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, tint = if (focused) PinglyColors.Ink else PinglyColors.InkFaint, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                    }
                    Box(Modifier.weight(1f).padding(vertical = if (singleLine) 16.dp else 0.dp)) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content,
                                ),
                                color = PinglyColors.InkFaint,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = if (ltr) TextAlign.Left else TextAlign.Start,
                            )
                        }
                        inner()
                    }
                    if (trailing != null) {
                        Spacer(Modifier.width(8.dp))
                        trailing()
                    }
                }
            },
        )
        if (maxLength != null && !singleLine) {
            Text(
                Persian.digits("${value.length}/$maxLength"),
                style = MaterialTheme.typography.labelSmall,
                color = PinglyColors.InkFaint,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, end = 6.dp),
                textAlign = TextAlign.End,
            )
        }
    }
}
