package com.scanrift.android.util

object Constants {

    object Api {
        const val BASE_URL = "https://api.riftcodex.com"
        const val CARDS_ENDPOINT = "/cards"
        const val DEFAULT_PAGE_SIZE = 100
    }

    object Scanning {
        const val AUTO_ACCEPT_CONFIDENCE = 0.80
        const val MINIMUM_MATCH_CONFIDENCE = 0.40
        const val MAX_CANDIDATES = 5
        const val SCAN_DEBOUNCE_MS = 1000L
        const val CARD_STABILITY_MS = 300L
        const val FRAME_SKIP_RATE = 3
        const val MATCH_DISPLAY_DURATION_MS = 2000L
    }

    object MotionDetection {
        const val STABLE_FRAMES_REQUIRED = 1
        const val MOTION_THRESHOLD = 0.02f
        const val PIXEL_CHANGE_THRESHOLD = 30
        const val SAMPLE_STRIDE = 10
    }

    object UI {
        const val CARD_ASPECT_RATIO = 0.716f
        const val GRID_ITEM_MIN_WIDTH_DP = 100
        const val GRID_ITEM_MAX_WIDTH_DP = 150
        const val TABLET_GRID_COLUMN_COUNT = 6
        const val TABLET_CARD_IMAGE_MAX_WIDTH_DP = 400
        const val TABLET_SCAN_GUIDE_WIDTH_DP = 300
        const val TABLET_SCAN_GUIDE_HEIGHT_DP = 420
    }

    object Deck {
        const val MAIN_DECK_MINIMUM = 40
        const val RUNE_COUNT = 12
        const val BATTLEFIELD_COUNT = 3
        const val SIDEBOARD_MAXIMUM = 8
        const val MAX_COPIES_PER_NAME = 3
        const val MAX_SIGNATURE_CARDS = 3
    }

    object StorageKeys {
        const val HAPTIC_FEEDBACK = "haptic_feedback"
        const val SOUND_FEEDBACK = "sound_feedback"
        const val AUTO_ADD_TO_COLLECTION = "auto_add_to_collection"
        const val LAST_DATABASE_SYNC = "last_database_sync"
        const val DEBUG_MODE = "debug_mode"
    }

    object FileNames {
        const val EXPORT_CSV = "riftbound_collection.csv"
        const val EXPORT_JSON = "riftbound_collection.json"
    }
}
