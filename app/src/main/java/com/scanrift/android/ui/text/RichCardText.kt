package com.scanrift.android.ui.text

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent

/**
 * Renders Riftbound card text with its icons, keyword badges and reminder styling.
 *
 * Everything is memoised on `(text, isDark, density, fontScale)` — this renders inside
 * a pager and inside deck rows, and rebuilding the inline-content map and the badge
 * measurements on every recomposition is the classic cause of swipe jank.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun RichCardText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    if (text.isBlank()) return

    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val contentColor = LocalContentColor.current
    val measurer = rememberTextMeasurer()

    val segments = remember(text) { CardTextParser.parse(text) }

    val inlineContent = remember(text, isDark, density.density, density.fontScale, style) {
        buildInlineContent(segments, style, isDark, density, contentColor)
    }

    val annotated = remember(text, style) { buildAnnotated(segments, style) }

    Text(text = annotated, inlineContent = inlineContent, style = style, modifier = modifier)
}

private const val ICON_ID = "icon"
private const val ENERGY_ID = "energy"
private const val KEYWORD_ID = "keyword"

@OptIn(ExperimentalTextApi::class)
private fun buildAnnotated(
    segments: List<CardTextParser.Segment>,
    style: TextStyle,
): AnnotatedString = buildAnnotatedString {
    fun emit(list: List<CardTextParser.Segment>, italic: Boolean) {
        list.forEachIndexed { index, segment ->
            when (segment) {
                is CardTextParser.Segment.PlainText ->
                    if (italic) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(segment.text) }
                    } else {
                        append(segment.text)
                    }
                is CardTextParser.Segment.Icon -> appendInlineContent("$ICON_ID:${segment.key}", "◻")
                is CardTextParser.Segment.EnergyCost -> appendInlineContent("$ENERGY_ID:${segment.value}", "◻")
                is CardTextParser.Segment.Keyword -> appendInlineContent("$KEYWORD_ID:${segment.label}", "◻")
                is CardTextParser.Segment.Reminder -> emit(segment.segments, italic = true)
            }
            @Suppress("UNUSED_EXPRESSION") index
        }
    }
    emit(segments, italic = false)
}

@OptIn(ExperimentalTextApi::class)
private fun buildInlineContent(
    segments: List<CardTextParser.Segment>,
    style: TextStyle,
    isDark: Boolean,
    density: Density,
    contentColor: Color,
): Map<String, InlineTextContent> {
    val content = mutableMapOf<String, InlineTextContent>()
    val fontSize = style.fontSize.takeIf { it != TextUnit.Unspecified } ?: 14.sp

    fun collect(list: List<CardTextParser.Segment>) {
        list.forEach { segment ->
            when (segment) {
                is CardTextParser.Segment.Icon -> {
                    content["$ICON_ID:${segment.key}"] = InlineTextContent(
                        Placeholder(fontSize * 1.1f, fontSize * 1.1f, PlaceholderVerticalAlign.Center),
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(segment.drawableRes),
                            contentDescription = null,
                            // Might and exhaust follow the text colour; runes keep theirs.
                            colorFilter = if (segment.drawableRes in CardTextParser.TEMPLATE_ICONS) {
                                ColorFilter.tint(contentColor)
                            } else {
                                null
                            },
                            modifier = Modifier.size(fontSize.value.dp * 1.1f),
                        )
                    }
                }
                is CardTextParser.Segment.EnergyCost -> {
                    content["$ENERGY_ID:${segment.value}"] = InlineTextContent(
                        Placeholder(fontSize * 1.15f, fontSize * 1.15f, PlaceholderVerticalAlign.Center),
                    ) {
                        // Inverted per theme, as on iOS: light disc on dark, dark on light.
                        val disc = if (isDark) Color.White else Color.Black
                        val digit = if (isDark) Color.Black else Color.White
                        Box(
                            modifier = Modifier.size(fontSize.value.dp * 1.15f).clip(CircleShape).background(disc),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${segment.value}",
                                color = digit,
                                fontSize = fontSize * 0.66f,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                is CardTextParser.Segment.Keyword -> {
                    val (bg, fg) = CardTextParser.keywordStyle(segment.label)
                    val widthEm = (segment.label.length * 0.62f + 1.4f)
                    content["$KEYWORD_ID:${segment.label}"] = InlineTextContent(
                        Placeholder(fontSize * widthEm, fontSize * 1.35f, PlaceholderVerticalAlign.Center),
                    ) {
                        KeywordBadge(label = segment.label, background = Color(bg), foreground = Color(fg), fontSize = fontSize)
                    }
                }
                is CardTextParser.Segment.Reminder -> collect(segment.segments)
                is CardTextParser.Segment.PlainText -> Unit
            }
        }
    }
    collect(segments)
    return content
}

/**
 * The skewed parallelogram badge.
 *
 * SwiftUI gets the slant from `NSAttributedString`'s `obliqueness`, which Compose has
 * no equivalent for, so the shape is drawn directly: the top edge shifts right by 15%
 * of the badge height, matching iOS's 0.15.
 */
@Composable
private fun KeywordBadge(
    label: String,
    background: Color,
    foreground: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val skew = size.height * 0.15f
            drawPath(
                path = Path().apply {
                    moveTo(skew, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width - skew, size.height)
                    lineTo(0f, size.height)
                    close()
                },
                color = background,
            )
        }
        Text(
            text = label.uppercase(),
            color = foreground,
            fontSize = fontSize * 0.72f,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
        )
    }
}
