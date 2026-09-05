package com.scanrift.android.ui.text

import com.scanrift.android.R

/**
 * Parses the markup Riftbound card text is written in.
 *
 * Three constructs, matched by one regex in a single pass, in iOS's resolution order:
 * `:rb_xxx:` icons, `[Keyword]` badges, and `(reminder text)` in italics.
 */
object CardTextParser {

    private val PATTERN = Regex(""":(\w+):|\[([^\]]+)\]|(\([^)]+\))""")

    /** `:rb_xxx:` codes to drawables. Both the current and legacy short codes. */
    private val ICONS: Map<String, Int> = mapOf(
        "rb_might" to R.drawable.ic_might,
        "rb_exhaust" to R.drawable.ic_exhaust,
        "rb_rune_fury" to R.drawable.ic_rune_fury,
        "rb_rune_calm" to R.drawable.ic_rune_calm,
        "rb_rune_mind" to R.drawable.ic_rune_mind,
        "rb_rune_chaos" to R.drawable.ic_rune_chaos,
        "rb_rune_body" to R.drawable.ic_rune_body,
        "rb_rune_order" to R.drawable.ic_rune_order,
        "rb_rune_rainbow" to R.drawable.ic_rune_rainbow,
        // Legacy short codes still present in older card text.
        "rb_fury" to R.drawable.ic_rune_fury,
        "rb_calm" to R.drawable.ic_rune_calm,
        "rb_mind" to R.drawable.ic_rune_mind,
        "rb_chaos" to R.drawable.ic_rune_chaos,
        "rb_body" to R.drawable.ic_rune_body,
        "rb_order" to R.drawable.ic_rune_order,
    )

    /** Tinted to follow the text colour rather than drawn in their own palette. */
    val TEMPLATE_ICONS = setOf(R.drawable.ic_might, R.drawable.ic_exhaust)

    private const val ENERGY_PREFIX = "rb_energy_"

    sealed interface Segment {
        data class PlainText(val text: String) : Segment
        data class Icon(val drawableRes: Int, val key: String) : Segment
        data class EnergyCost(val value: Int) : Segment
        data class Keyword(val label: String) : Segment
        /** Reminder text is italic, and is itself re-parsed for icons. */
        data class Reminder(val segments: List<Segment>) : Segment
    }

    fun parse(text: String): List<Segment> = parseInternal(text, allowReminders = true)

    private fun parseInternal(text: String, allowReminders: Boolean): List<Segment> {
        if (text.isEmpty()) return emptyList()

        // iOS card text carries literal "\n" escapes rather than real newlines.
        val source = text.replace("\\n", "\n")
        val segments = mutableListOf<Segment>()
        var cursor = 0

        PATTERN.findAll(source).forEach { match ->
            if (match.range.first > cursor) {
                segments += Segment.PlainText(source.substring(cursor, match.range.first))
            }
            cursor = match.range.last + 1

            val iconKey = match.groupValues[1]
            val keyword = match.groupValues[2]
            val reminder = match.groupValues[3]

            when {
                iconKey.isNotEmpty() -> segments += resolveIcon(iconKey)
                keyword.isNotEmpty() -> segments += Segment.Keyword(keyword)
                reminder.isNotEmpty() && allowReminders -> {
                    // Strip the outer parentheses before recursing, then put them back
                    // as text. Passing them through would make the inner pass match the
                    // same parenthetical again and emit the whole thing — icons
                    // included — as literal text.
                    val inner = reminder.removeSurrounding("(", ")")
                    segments += Segment.Reminder(
                        buildList {
                            add(Segment.PlainText("("))
                            addAll(parseInternal(inner, allowReminders = false))
                            add(Segment.PlainText(")"))
                        },
                    )
                }
                reminder.isNotEmpty() -> segments += Segment.PlainText(reminder)
            }
        }

        if (cursor < source.length) segments += Segment.PlainText(source.substring(cursor))
        return segments
    }

    /**
     * Resolution order matters: a named icon wins, then an energy cost, and anything
     * unrecognised falls through as literal text rather than disappearing.
     */
    private fun resolveIcon(key: String): Segment {
        ICONS[key]?.let { return Segment.Icon(it, key) }

        if (key.startsWith(ENERGY_PREFIX)) {
            key.removePrefix(ENERGY_PREFIX).toIntOrNull()?.let { return Segment.EnergyCost(it) }
        }
        return Segment.PlainText(":$key:")
    }

    /**
     * Badge colours by keyword group, ported from iOS.
     *
     * @return background to foreground.
     */
    fun keywordStyle(keyword: String): Pair<Long, Long> = when (keyword.lowercase().trim()) {
        "assault", "shield", "tank" -> 0xFFC9346B to 0xFFFFFFFF
        "deflect", "ganking", "deathknell" -> 0xFF9AB331 to 0xFF000000
        "legion", "accelerate", "action", "reaction" -> 0xFF24A189 to 0xFFFFFFFF
        // "mighty" and everything unrecognised share the neutral grey.
        else -> 0xFF6B7071 to 0xFFFFFFFF
    }
}
