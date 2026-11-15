package com.scanrift.android.ui.game

import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Debug-only seam for the store-screenshot demo, which needs the tracker to open on a
 * game in progress.
 *
 * The tracker normally zeroes every score it restores (see [PlayerState]). When this is
 * bound and [keepsRestoredScores] is true, it keeps the counts, log and XP stored in the
 * roster instead. Only the debug source set binds it, so release builds always get an
 * empty `Optional` and the normal behaviour.
 */
fun interface RestoredScoresPolicy {
    fun keepsRestoredScores(): Boolean
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RestoredScoresPolicyModule {
    @BindsOptionalOf
    abstract fun restoredScoresPolicy(): RestoredScoresPolicy
}
