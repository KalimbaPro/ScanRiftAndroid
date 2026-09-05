package com.scanrift.android.core

/**
 * Tuning constants, ported 1:1 from the iOS app's `Utilities/Constants.swift`.
 *
 * Values here are part of the cross-platform contract: the deck rules, the scanning
 * thresholds and the motion-detection tuning must match iOS exactly or the two apps
 * disagree about whether a deck is legal or a frame is stable. Change them in both
 * places or not at all.
 *
 * Cardmarket pricing constants are deliberately absent — that feature is not ported.
 */
object Constants {

    object Api {
        const val BASE_URL = "https://api.riftcodex.com"
        const val DEFAULT_PAGE_SIZE = 100

        /**
         * Minimum interval between background freshness checks. Prevents hammering
         * `/sets` on every app launch — one check per hour is plenty for a card game.
         */
        const val UPDATE_CHECK_TTL_MS = 60L * 60L * 1000L

        /** OkHttp timeouts. The old Android build configured none at all. */
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val READ_TIMEOUT_SECONDS = 30L
        const val WRITE_TIMEOUT_SECONDS = 15L
        const val CALL_TIMEOUT_SECONDS = 60L
        const val HTTP_CACHE_BYTES = 20L * 1024 * 1024
    }

    object Scanning {
        /** Minimum OCR confidence before a frame is worth matching against. */
        const val MINIMUM_OCR_CONFIDENCE = 0.3

        /** Debounce between accepted scans. */
        const val SCAN_DEBOUNCE_MS = 1_000L

        /** Camera frames skipped between processing attempts. */
        const val FRAME_SKIP_RATE = 3

        /** How long a successful match stays on screen before scanning resumes. */
        const val MATCH_DISPLAY_DURATION_MS = 2_000L

        /** Analyzer target resolution. Unset on the old build, which starved OCR at 640x480. */
        const val ANALYSIS_TARGET_WIDTH = 1280
        const val ANALYSIS_TARGET_HEIGHT = 720

        /**
         * Vision-space bands used to split recognised text into regions.
         * These are normalised bottom-left coordinates (see `OcrGeometry`).
         */
        const val NAME_BAND_MIN_Y = 0.35f
        const val NAME_BAND_MAX_Y = 0.65f
        const val NAME_BAND_MAX_MID_X = 0.7f
        const val SET_CODE_BAND_MAX_Y = 0.25f
    }

    object MotionDetection {
        /** Consecutive stable frames before a scan is allowed. */
        const val STABLE_FRAMES_REQUIRED = 1

        /** Fraction of sampled pixels that must change to count as motion. */
        const val MOTION_THRESHOLD = 0.02f

        /** Minimum intensity delta for a single sample to count as changed. */
        const val PIXEL_CHANGE_THRESHOLD = 30

        /** Sampling stride — roughly 100 samples per frame. */
        const val SAMPLE_STRIDE = 10
    }

    /**
     * Riftbound deck-construction rules. Enforced by `DeckValidator`.
     * These must stay identical to iOS `Constants.Deck`.
     */
    object Deck {
        const val MAIN_DECK_MINIMUM = 40
        const val RUNE_COUNT = 12
        const val BATTLEFIELD_COUNT = 3
        const val SIDEBOARD_MAXIMUM = 10
        const val MAX_COPIES_PER_NAME = 3
        const val MAX_SIGNATURE_CARDS = 3

        /** Runes are exempt from the copy limit — a deck needs 12 of them. */
        const val MAX_RUNE_COPIES = RUNE_COUNT

        /** Energy curve buckets run 0..6 plus a "7+" overflow bucket. */
        const val ENERGY_BUCKET_MAX = 7
    }

    object PointTracker {
        const val SCORE_MIN = -99
        const val SCORE_MAX = 99
        const val XP_MAX = 99
        const val MIN_PLAYERS = 2
        const val MAX_PLAYERS = 4
        const val DEFAULT_STARTING_SCORE = 0
        const val LEGEND_OVERLAY_OPACITY = 0.30f
        const val BACKGROUND_OPACITY = 0.35f
        const val RANDOMIZE_ANIMATION_MS = 1_500L
        const val RANDOMIZE_HIGHLIGHT_INTERVAL_MS = 100L
        const val STARTING_HIGHLIGHT_FADE_MS = 3_000L
    }

    /**
     * DataStore preference keys.
     *
     * iOS uses camelCase UserDefaults keys; we keep the same spellings so the two
     * codebases are greppable together, even though the stores are unrelated.
     * iCloud-specific keys are omitted — Android has no cloud sync.
     */
    object PreferenceKeys {
        const val HAPTIC_FEEDBACK = "hapticFeedback"
        const val SOUND_FEEDBACK = "soundFeedback"
        const val AUTO_ADD_TO_COLLECTION = "autoAddToCollection"
        const val DEBUG_MODE = "debugMode"
        const val LUCHO_PARAMETER = "luchoParameter"
        const val SHOW_UNOWNED_IN_COLOR = "showUnownedInColor"
        const val DYNAMIC_COLOR = "dynamicColor"
        const val LAST_DATABASE_SYNC = "lastDatabaseSync"
        const val LAST_UPDATE_CHECK = "lastUpdateCheck"
        const val LAST_BACKUP = "lastBackup"
        const val BACKUP_URI = "backupUri"
        const val POINT_TRACKER_ROSTER = "pointTracker.roster.v1"
    }

    object FileNames {
        const val EXPORT_CSV = "riftbound_collection.csv"
        const val EXPORT_JSON = "riftbound_collection.json"
        const val EXPORT_DOTGG_CSV = "riftbound_collection_dotgg.csv"

        /** Shared with iOS — the same file restores on either platform. */
        const val BACKUP_SNAPSHOT = "collection-snapshot.json"
    }

    /** The single system card list every install is guaranteed to have. */
    object Wishlist {
        const val SYSTEM_TYPE = "wishlist"
        const val NAME = "Wishlist"
        const val COLOR_HEX = "#FFD60A"
    }
}
