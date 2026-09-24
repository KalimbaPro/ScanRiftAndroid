package com.scanrift.android.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun TappableQuantityStepper(
    quantity: Int,
    onQuantityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minValue: Int = 0,
    maxValue: Int = 999,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf(quantity.toString()) }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(quantity, focused) {
        if (!focused) text = quantity.toString()
    }

    fun commit() {
        val value = (text.filter(Char::isDigit).toIntOrNull() ?: minValue).coerceIn(minValue, maxValue)
        text = value.toString()
        if (value != quantity) onQuantityChange(value)
    }

    fun step(delta: Int) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onQuantityChange((quantity + delta).coerceIn(minValue, maxValue))
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { step(-1) }, enabled = quantity > minValue) {
            Icon(Icons.Filled.RemoveCircle, contentDescription = "Decrease quantity")
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = textStyle.copy(
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .widthIn(min = 24.dp)
                .onFocusChanged {
                    if (focused && !it.isFocused) commit()
                    focused = it.isFocused
                }
                .semantics { contentDescription = "Quantity" },
        )
        IconButton(onClick = { step(1) }, enabled = quantity < maxValue) {
            Icon(
                Icons.Filled.AddCircle,
                contentDescription = "Increase quantity",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
