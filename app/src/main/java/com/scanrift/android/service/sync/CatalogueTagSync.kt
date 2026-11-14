package com.scanrift.android.service.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/** What one pass of [CatalogueTagSync] did. */
sealed interface CatalogueSyncOutcome {
    /** The API's tag matches the stored one. Nothing was written. */
    data class UpToDate(val tag: String) : CatalogueSyncOutcome

    /** A full refresh ran and [tag] is now stored (null when the tag was unknown). */
    data class Refreshed(val tag: String?, val cardCount: Int) : CatalogueSyncOutcome

    /** `/version` could not be read. Nothing was attempted; the stored tag is kept. */
    data class CheckFailed(val error: Throwable) : CatalogueSyncOutcome

    /** The refresh failed part-way. The stored tag is kept, so the next launch retries. */
    data class RefreshFailed(val error: Throwable) : CatalogueSyncOutcome
}

/**
 * The catalogue freshness rule, free of Room, Retrofit and DataStore so it runs on
 * plain JUnit.
 *
 * The API's `/version` tag changes exactly when cards, sets or any region's legality
 * change. So:
 *
 * - same tag as stored → do nothing at all;
 * - different or no stored tag → full refresh, then store the tag;
 * - [run] with `force` (the manual refresh in Settings) → full refresh regardless.
 *
 * The tag stored is the one read **before** the refresh. If the API moves on while the
 * refresh runs, the stored tag is the older one and the next launch simply refreshes
 * again — the safe direction. Storing it only after [refresh] returns normally means
 * any failure (a page, `/sets`, legality) leaves the old tag in place and the next
 * launch retries.
 */
class CatalogueTagSync(
    private val fetchRemoteTag: suspend () -> String,
    private val readStoredTag: suspend () -> String?,
    private val writeStoredTag: suspend (String) -> Unit,
    /** Every step of a full refresh. Must throw on any failure. Returns the card count. */
    private val refresh: suspend () -> Int,
) {

    suspend fun run(force: Boolean = false): CatalogueSyncOutcome {
        val remoteTag = try {
            fetchRemoteTag()
        } catch (error: Throwable) {
            rethrowIfCancelled(error)
            // Unforced, an unknown remote tag means nothing to compare: keep what we have.
            if (!force) return CatalogueSyncOutcome.CheckFailed(error)
            null
        }

        if (!force && remoteTag != null && remoteTag == readStoredTag()) {
            return CatalogueSyncOutcome.UpToDate(remoteTag)
        }

        val count = try {
            refresh()
        } catch (error: Throwable) {
            rethrowIfCancelled(error)
            return CatalogueSyncOutcome.RefreshFailed(error)
        }
        if (remoteTag != null) writeStoredTag(remoteTag)
        return CatalogueSyncOutcome.Refreshed(remoteTag, count)
    }

    /**
     * A cancelled launch must stay cancelled. A timeout is a failure like any other:
     * `withTimeout` reports it as a [CancellationException] subclass.
     */
    private fun rethrowIfCancelled(error: Throwable) {
        if (error is CancellationException && error !is TimeoutCancellationException) throw error
    }
}
