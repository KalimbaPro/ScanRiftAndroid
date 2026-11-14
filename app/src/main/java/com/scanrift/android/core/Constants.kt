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
        /**
         * The own API (RiftCodex-compatible paths plus `/legality`, Radiance, bans,
         * errata and zh/ko localisations). Placeholder host until the Cloudflare account
         * exists; iOS's `Constants.API.baseURL` must hold the same value.
         */
        const val BASE_URL = "https://riftbound-api.kalimbapro.workers.dev"
        const val DEFAULT_PAGE_SIZE = 100

        /**
         * Budget for the `GET /version` check that runs on every launch. Far below the
         * general OkHttp timeouts: the answer is 90 bytes, and a launch on a dead
         * network should give up quickly and keep the local catalogue.
         */
        const val VERSION_CHECK_TIMEOUT_MS = 5_000L

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

        const val RESUME_DELAY_MS = 500L
        const val MAX_CAMERA_RETRIES = 3
        const val CAMERA_RETRY_DELAY_MS = 500L

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

        /**
         * Focus mode (single-card scanning with neighbours in view, e.g. a binder page):
         * entered when at least this many collector codes are read in one frame.
         */
        const val FOCUS_MIN_CARDS = 2

        /** Processed frames with no card outside the guide before focus mode releases. */
        const val FOCUS_RELEASE_FRAMES = 3

        /** Fraction of a card's estimated region that must lie in the guide for it to count. */
        const val FOCUS_MIN_OVERLAP = 0.5f

        /** A card's art, as a fraction of its outline: where printings differ most. */
        val ART_REGION = com.scanrift.android.service.scanning.NormalizedBox(0.08f, 0.07f, 0.92f, 0.50f)

        /** Art-hash (64-bit dHash) bits a frame may differ from the printing it picks. */
        const val ART_HASH_MAX_DISTANCE = 22

        /** Bits the best printing must beat the runner-up by, or the scan stays ambiguous. */
        const val ART_HASH_MIN_MARGIN = 6

        /** Binder page stills are scaled to at most this many pixels on the long edge. */
        const val PAGE_MAX_DIMENSION = 4096

        /** Each 3×3 tile is upscaled to at least this long edge before its OCR pass. */
        const val PAGE_TILE_MIN_DIMENSION = 1600
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
        const val VICTORY_POINTS = 8
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
        const val DYNAMIC_COLOR = "dynamicColor"
        const val LAST_DATABASE_SYNC = "lastDatabaseSync"

        /** The API's `/version` tag the local catalogue was last fully synced to. */
        const val CATALOGUE_TAG = "catalogueTag"

        const val LAST_BACKUP = "lastBackup"
        const val BACKUP_URI = "backupUri"
        const val CLOUD_SNAPSHOT_AUTO_SYNC = "cloudSnapshotAutoSync"
        const val POINT_TRACKER_ROSTER = "pointTracker.roster.v1"

        /** Raw [com.scanrift.android.domain.model.CompetitiveRegion.value]: `global` | `kr` | `tc`. */
        const val COMPETITIVE_REGION = "competitiveRegion"

        /** Raw [com.scanrift.android.domain.model.ScanLanguage.value]: `auto` | `en` | `fr` | `zh` | `ko`. */
        const val SCAN_LANGUAGE = "scanLanguage"

        /**
         * Android-only: the collection list pane's share of the two-pane width, a
         * proportion in (0, 1). Absent until the user first drags the divider.
         */
        const val COLLECTION_LIST_PANE_PROPORTION = "collectionListPaneProportion"
    }

    object ImageCache {
        const val DIRECTORY = "CardImages"
        const val DISK_CACHE_BYTES = 200L * 1024 * 1024
    }

    object Backup {
        const val AUTO_BACKUP_INTERVAL_MS = 5L * 60L * 1000L
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
