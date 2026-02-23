package com.scanrift.android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanrift.android.R

private data class KeywordStyle(val bg: Color, val fg: Color)

private val keywordStyles: Map<String, KeywordStyle> = run {
    val mighty = KeywordStyle(Color(0xFF6B7071), Color.White)
    val pink = KeywordStyle(Color(0xFFC9346B), Color.White)
    val green = KeywordStyle(Color(0xFF9AB331), Color.Black)
    val teal = KeywordStyle(Color(0xFF24A189), Color.White)
    mapOf(
        "mighty" to mighty,
        "assault" to pink, "shield" to pink, "tank" to pink,
        "deflect" to green, "ganking" to green, "deathknell" to green,
        "legion" to teal, "accelerate" to teal, "action" to teal, "reaction" to teal,
    )
}

private val defaultKeywordStyle = KeywordStyle(Color(0xFF6B7071), Color.White)

private data class IconRes(val resId: Int, val isTemplate: Boolean = false)

private val iconDrawableResIds: Map<String, IconRes> = mapOf(
    "might" to IconRes(R.drawable.ic_might, isTemplate = true),
    "exhaust" to IconRes(R.drawable.ic_exhaust, isTemplate = true),
    "rune_fury" to IconRes(R.drawable.ic_rune_fury),
    "rune_calm" to IconRes(R.drawable.ic_rune_calm),
    "rune_mind" to IconRes(R.drawable.ic_rune_mind),
    "rune_chaos" to IconRes(R.drawable.ic_rune_chaos),
    "rune_body" to IconRes(R.drawable.ic_rune_body),
    "rune_order" to IconRes(R.drawable.ic_rune_order),
    "rune_rainbow" to IconRes(R.drawable.ic_rune_rainbow),
    // Legacy short codes
    "fury" to IconRes(R.drawable.ic_rune_fury),
    "calm" to IconRes(R.drawable.ic_rune_calm),
    "mind" to IconRes(R.drawable.ic_rune_mind),
    "chaos" to IconRes(R.drawable.ic_rune_chaos),
    "body" to IconRes(R.drawable.ic_rune_body),
    "order" to IconRes(R.drawable.ic_rune_order),
)

/**
 * Renders rich card text with styled keywords, inline icons, and reminder text.
 *
 * Parses patterns:
 * - `:rb_xxx:` -> inline icon from drawable resources
 * - `[Keyword]` -> colored badge inline
 * - `(reminder text)` -> italic gray text
 * - `**bold**` -> bold text
 */
@Composable
fun RichCardText(
    text: String,
    modifier: Modifier = Modifier
) {
    val segments = remember(text) { parseCardText(text) }
    val textColor = MaterialTheme.colorScheme.onSurface

    // Collect all icon names used (including inside reminders)
    val allIconNames = remember(segments) {
        val names = mutableSetOf<String>()
        for (segment in segments) {
            when (segment) {
                is CardTextSegment.Icon -> names.add(segment.name)
                is CardTextSegment.Reminder -> {
                    for (rs in parseIconsInText(segment.text)) {
                        if (rs is CardTextSegment.Icon) names.add(rs.name)
                    }
                }
                else -> {}
            }
        }
        names
    }

    // Build inline content map for icons
    val inlineContent = remember(allIconNames, textColor) {
        val map = mutableMapOf<String, InlineTextContent>()
        for (name in allIconNames) {
            val iconRes = iconDrawableResIds[name] ?: continue
            val iconId = "icon_$name"
            map[iconId] = InlineTextContent(
                Placeholder(16.sp, 16.sp, PlaceholderVerticalAlign.TextCenter)
            ) {
                Image(
                    painter = painterResource(iconRes.resId),
                    contentDescription = name,
                    colorFilter = if (iconRes.isTemplate) ColorFilter.tint(textColor) else null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        map
    }

    val annotatedString = buildAnnotatedString {
        for (segment in segments) {
            when (segment) {
                is CardTextSegment.PlainText -> append(segment.text)
                is CardTextSegment.Icon -> {
                    val iconId = "icon_${segment.name}"
                    if (iconId in inlineContent) {
                        appendInlineContent(iconId, "[${segment.name}]")
                    } else {
                        append("[${segment.name}]")
                    }
                }
                is CardTextSegment.Keyword -> {
                    val style = keywordStyles[segment.text.lowercase()] ?: defaultKeywordStyle
                    withStyle(SpanStyle(
                        color = style.fg,
                        background = style.bg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )) {
                        append(" ${segment.text} ")
                    }
                }
                is CardTextSegment.Reminder -> {
                    val reminderSegments = parseIconsInText(segment.text)
                    withStyle(SpanStyle(
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF888888),
                        fontSize = 13.sp
                    )) {
                        append("(")
                        for (rs in reminderSegments) {
                            when (rs) {
                                is CardTextSegment.Icon -> {
                                    val iconId = "icon_${rs.name}"
                                    if (iconId in inlineContent) {
                                        appendInlineContent(iconId, "[${rs.name}]")
                                    } else {
                                        append("[${rs.name}]")
                                    }
                                }
                                else -> append((rs as CardTextSegment.PlainText).text)
                            }
                        }
                        append(")")
                    }
                }
                is CardTextSegment.Bold -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(segment.text)
                    }
                }
            }
        }
    }

    Text(
        text = annotatedString,
        inlineContent = inlineContent,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    )
}

private sealed class CardTextSegment {
    data class PlainText(val text: String) : CardTextSegment()
    data class Icon(val name: String) : CardTextSegment()
    data class Keyword(val text: String) : CardTextSegment()
    data class Reminder(val text: String) : CardTextSegment()
    data class Bold(val text: String) : CardTextSegment()
}

private fun parseCardText(text: String): List<CardTextSegment> {
    val segments = mutableListOf<CardTextSegment>()
    var i = 0
    val sb = StringBuilder()

    fun flushText() {
        if (sb.isNotEmpty()) {
            segments.add(CardTextSegment.PlainText(sb.toString()))
            sb.clear()
        }
    }

    while (i < text.length) {
        when {
            // Icon: :rb_xxx:
            text.startsWith(":rb_", i) -> {
                val endIndex = text.indexOf(':', i + 4)
                if (endIndex != -1) {
                    flushText()
                    val iconName = text.substring(i + 4, endIndex)
                    segments.add(CardTextSegment.Icon(iconName))
                    i = endIndex + 1
                } else {
                    sb.append(text[i])
                    i++
                }
            }

            // Keyword: [Keyword]
            text[i] == '[' -> {
                val endIndex = text.indexOf(']', i + 1)
                if (endIndex != -1) {
                    flushText()
                    val keyword = text.substring(i + 1, endIndex)
                    segments.add(CardTextSegment.Keyword(keyword))
                    i = endIndex + 1
                } else {
                    sb.append(text[i])
                    i++
                }
            }

            // Reminder: (text)
            text[i] == '(' -> {
                val endIndex = text.indexOf(')', i + 1)
                if (endIndex != -1) {
                    flushText()
                    val reminder = text.substring(i + 1, endIndex)
                    segments.add(CardTextSegment.Reminder(reminder))
                    i = endIndex + 1
                } else {
                    sb.append(text[i])
                    i++
                }
            }

            // Bold: **text**
            text.startsWith("**", i) -> {
                val endIndex = text.indexOf("**", i + 2)
                if (endIndex != -1) {
                    flushText()
                    val boldText = text.substring(i + 2, endIndex)
                    segments.add(CardTextSegment.Bold(boldText))
                    i = endIndex + 2
                } else {
                    sb.append(text[i])
                    i++
                }
            }

            // Escaped newline
            text[i] == '\\' && i + 1 < text.length && text[i + 1] == 'n' -> {
                sb.append('\n')
                i += 2
            }

            else -> {
                sb.append(text[i])
                i++
            }
        }
    }
    flushText()
    return segments
}

/** Parse only :rb_xxx: icons in a string (for reminder text) */
private fun parseIconsInText(text: String): List<CardTextSegment> {
    val segments = mutableListOf<CardTextSegment>()
    var i = 0
    val sb = StringBuilder()

    fun flushText() {
        if (sb.isNotEmpty()) {
            segments.add(CardTextSegment.PlainText(sb.toString()))
            sb.clear()
        }
    }

    while (i < text.length) {
        if (text.startsWith(":rb_", i)) {
            val endIndex = text.indexOf(':', i + 4)
            if (endIndex != -1) {
                flushText()
                segments.add(CardTextSegment.Icon(text.substring(i + 4, endIndex)))
                i = endIndex + 1
            } else {
                sb.append(text[i])
                i++
            }
        } else {
            sb.append(text[i])
            i++
        }
    }
    flushText()
    return segments
}
