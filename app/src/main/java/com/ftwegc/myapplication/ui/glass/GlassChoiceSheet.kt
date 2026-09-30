package com.ftwegc.myapplication.ui.glass

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A glass sheet with one choice per row (a check marks the current one); picking closes it.
 * Keeps its options while it closes.
 */
@Composable
fun <T> GlassChoiceSheet(
    visible: Boolean,
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    description: String? = null,
    optionDescription: (@Composable (T) -> String?)? = null,
) {
    val env = currentGlass()
    GlassSheet(visible = visible, onDismissRequest = onDismiss, contentDescription = title) {
        Text(title, color = env.ink.primary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        if (description != null) {
            Text(description, color = env.ink.secondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            options.forEachIndexed { i, option ->
                if (i > 0) GlassDivider(indent = 12.dp)
                GlassChoiceRow(
                    label = label(option),
                    selected = option == selected,
                    description = optionDescription?.invoke(option),
                    onClick = {
                        onPick(option)
                        onDismiss()
                    },
                )
            }
        }
    }
}
