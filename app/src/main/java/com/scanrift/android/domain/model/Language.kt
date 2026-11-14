package com.scanrift.android.domain.model

/**
 * The language a physical copy was printed in.
 *
 * A property of the **owned copy**, not of the card: Chinese and Korean prints share
 * set codes and collector numbers with English, so `publicCode` identifies the card and
 * the language is something you own. [value] is wire format — it goes into Room, the
 * backup snapshot and the iOS app verbatim. Never store `name` or the ordinal.
 *
 * Declaration order is the display order (collection sections, pickers).
 */
enum class CardLanguage(
    val value: String,
    val flag: String,
    val code: String,
    val displayName: String,
) {
    EN("en", "🇬🇧", "EN", "English"),
    FR("fr", "🇫🇷", "FR", "French"),
    ZH("zh", "🇨🇳", "ZH", "Chinese"),
    KO("ko", "🇰🇷", "KO", "Korean"),
    ;

    companion object {
        val DEFAULT = EN

        /** Unknown or absent values decode to English, matching an absent snapshot key. */
        fun fromValue(value: String?): CardLanguage = entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}

/**
 * Which ban list applies. Raw values are wire format: they are the `?region=` query
 * parameter of `/legality` and the stored preference, identical on iOS.
 *
 * A region behind the global release schedule uses the global ban list as it stood at
 * the end of the season of the latest set released there — the API resolves that; the
 * app only passes the region.
 */
enum class CompetitiveRegion(val value: String, val displayName: String) {
    GLOBAL("global", "Global"),
    KR("kr", "Korea"),
    TC("tc", "Traditional Chinese (TW / HK / MO)"),
    ;

    companion object {
        fun fromValue(value: String?): CompetitiveRegion = entries.firstOrNull { it.value == value } ?: GLOBAL
    }
}

/** The scanner's language setting: detect, or force one language. Raw values are wire format. */
enum class ScanLanguage(val value: String, val language: CardLanguage?) {
    AUTO("auto", null),
    EN("en", CardLanguage.EN),
    FR("fr", CardLanguage.FR),
    ZH("zh", CardLanguage.ZH),
    KO("ko", CardLanguage.KO),
    ;

    val label: String get() = language?.let { "${it.flag} ${it.code}" } ?: "Auto"

    companion object {
        fun fromValue(value: String?): ScanLanguage = entries.firstOrNull { it.value == value } ?: AUTO
    }
}
