package com.scanrift.android.ui.collection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanrift.android.ui.components.QuantityTextField
import com.scanrift.android.ui.theme.Motion
import com.scanrift.android.ui.util.lightImpact
import com.scanrift.android.ui.util.mediumImpact

@Composable
fun CollectionFab(
    quantity: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSetQuantity: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedback = LocalHapticFeedback.current
    val expanded = quantity > 0
    val transition = updateTransition(expanded, label = "fab")
    val horizontalPadding by transition.animateDp({ Motion.pill() }, label = "padding") { if (it) 14.dp else 8.dp }
    val plusFrame by transition.animateDp({ Motion.pill() }, label = "frame") { if (it) 32.dp else 40.dp }
    val plusGlyph by transition.animateDp({ Motion.pill() }, label = "glyph") { if (it) 18.dp else 22.dp }
    val plusRotation by transition.animateFloat({ Motion.pill() }, label = "rotation") { if (it) -90f else 0f }
    val trailingScale = TransformOrigin(1f, 0.5f)

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
        modifier = modifier.dropShadow(CircleShape) {
            color = Color.Black
            alpha = 0.25f
            radius = 6f * density
            offset = Offset(0f, 3f * density)
        },
    ) {
        Row(
            modifier = Modifier
                .animateContentSize(Motion.pill())
                .padding(horizontal = horizontalPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            transition.AnimatedVisibility(
                visible = { it },
                enter = scaleIn(Motion.pill(), 0.6f, trailingScale) + fadeIn(Motion.pill()),
                exit = scaleOut(Motion.pill(), 0.6f, trailingScale) + fadeOut(Motion.pill()),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable {
                                feedback.lightImpact()
                                onDecrement()
                            }
                            .semantics { contentDescription = "Decrease quantity" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    QuantityTextField(
                        quantity = quantity,
                        onQuantityChange = onSetQuantity,
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                        ),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(plusFrame)
                    .clickable {
                        feedback.mediumImpact()
                        onIncrement()
                    }
                    .semantics { contentDescription = "Increase quantity" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(plusGlyph).graphicsLayer { rotationZ = plusRotation },
                )
            }
        }
    }
}
