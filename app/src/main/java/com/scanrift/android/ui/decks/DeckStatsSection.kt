package com.scanrift.android.ui.decks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.scanrift.android.domain.model.CardType
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.service.deck.CurveSegment
import com.scanrift.android.service.deck.DeckStats
import com.scanrift.android.service.deck.StatEntry
import com.scanrift.android.ui.theme.DomainColorless
import com.scanrift.android.ui.theme.Motion
import com.scanrift.android.ui.theme.ScanRiftBlue
import com.scanrift.android.ui.theme.domainColor
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun DeckStatsSection(deck: Deck) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val stats = remember(deck) { DeckStats.of(deck) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(expanded) {
        if (expanded) {
            progress.snapTo(0f)
            delay(CHART_DELAY_MS)
            progress.animateTo(1f, tween(Motion.CHART_ENTRY_MS))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitleRow("Statistics", expanded, onToggle = { expanded = !expanded })
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(SECTION_TOGGLE_MS)),
            exit = shrinkVertically(tween(SECTION_TOGGLE_MS)),
        ) {
            if (stats.cardCount == 0) {
                Text(
                    "Add cards to see statistics",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                StatsContent(stats, progress.value)
            }
        }
    }
}

@Composable
private fun StatsContent(stats: DeckStats, progress: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AverageBox("Avg. Energy", stats.averageEnergy, Icons.Filled.Bolt, WarningOrange, Modifier.weight(1f))
                AverageBox("Avg. Power", stats.averagePower, Icons.Filled.LocalFireDepartment, ScanRiftBlue, Modifier.weight(1f))
            }
            Text(
                "${stats.cardCount} cards analyzed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        ChartTitle("Energy Curve")
        StackedBarChart(stats.energyCurve, progress)

        ChartTitle("Power Curve")
        if (stats.powerCurve.isEmpty()) {
            Text(
                "No cards with power",
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            StackedBarChart(stats.powerCurve, progress)
        }

        ChartTitle("Card Types")
        Donut(stats.types, progress, colorFor = ::typeColor)

        ChartTitle("Domains")
        Donut(stats.domains, progress, colorFor = ::domainColor, showDomainIcon = true)
    }
}

@Composable
private fun AverageBox(label: String, value: Double, icon: ImageVector, tint: Color, modifier: Modifier) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                String.format(Locale.ROOT, "%.1f", value),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun ChartTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StackedBarChart(segments: List<CurveSegment>, progress: Float) {
    val buckets = segments.groupBy { it.bucket }
    val maxTotal = buckets.values.maxOf { bucket -> bucket.sumOf { it.count } }.coerceAtLeast(1)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val ticks = listOf(0, maxTotal / 2, maxTotal).distinct()

    Canvas(Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
        val axisWidth = 24.dp.toPx()
        val labelHeight = 16.dp.toPx()
        val plotHeight = size.height - labelHeight
        val plotWidth = size.width - axisWidth
        val slot = plotWidth / buckets.size
        val barWidth = slot * 0.6f

        ticks.forEach { tick ->
            val y = plotHeight - plotHeight * tick / maxTotal
            drawLine(gridColor, Offset(0f, y), Offset(plotWidth, y), strokeWidth = 1f)
            val text = measurer.measure("$tick", labelStyle)
            drawText(text, topLeft = Offset(plotWidth + 4.dp.toPx(), (y - text.size.height / 2).coerceAtLeast(0f)))
        }

        buckets.entries.forEachIndexed { index, (bucket, parts) ->
            val left = slot * index + (slot - barWidth) / 2
            var top = plotHeight
            parts.filter { it.count > 0 }.forEach { part ->
                val height = plotHeight * part.count / maxTotal * progress
                top -= height
                drawRoundRect(
                    color = domainColor(part.domain),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            val text = measurer.measure(bucket, labelStyle)
            drawText(text, topLeft = Offset(slot * index + (slot - text.size.width) / 2, plotHeight + 2.dp.toPx()))
        }
    }
}

@Composable
private fun Donut(
    entries: List<StatEntry>,
    progress: Float,
    colorFor: (String) -> Color,
    showDomainIcon: Boolean = false,
) {
    val total = entries.sumOf { it.count }.coerceAtLeast(1)
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(
            Modifier
                .size(DONUT_SIZE)
                .graphicsLayer {
                    alpha = progress
                    val scale = 0.5f + 0.5f * progress
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            val ringWidth = size.minDimension / 2 * (1 - INNER_RADIUS_RATIO)
            val inset = ringWidth / 2
            var start = -90f
            entries.forEach { entry ->
                val sweep = 360f * entry.count / total * progress
                drawArc(
                    color = colorFor(entry.label),
                    startAngle = start + ANGULAR_INSET / 2,
                    sweepAngle = (sweep - ANGULAR_INSET).coerceAtLeast(0f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - ringWidth, size.height - ringWidth),
                    style = Stroke(width = ringWidth),
                )
                start += sweep
            }
        }
        Column(Modifier.weight(1f).alpha(progress), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            entries.forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (showDomainIcon) {
                        DomainIcons(listOf(entry.label), 14.dp)
                    } else {
                        Box(Modifier.size(8.dp).background(colorFor(entry.label), CircleShape))
                    }
                    Text(entry.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(
                        "${entry.count}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun typeColor(type: String): Color = when (type) {
    CardType.UNIT -> ScanRiftBlue
    CardType.SPELL -> Color(0xFFAF52DE)
    CardType.GEAR -> WarningOrange
    else -> DomainColorless
}

private val CHART_HEIGHT = 150.dp
private val DONUT_SIZE = 100.dp
private const val CHART_DELAY_MS = 50L
private const val INNER_RADIUS_RATIO = 0.55f
private const val ANGULAR_INSET = 1.5f
