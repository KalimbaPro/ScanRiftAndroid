package com.scanrift.android.demo

import com.scanrift.android.ui.game.RestoredScoresPolicy
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lets the point tracker open on the seeded game in progress instead of zeroing it.
 *
 * Held in memory only, and set only by the four-player seed, so it lasts for the
 * screenshot session's process and no longer: the next cold start of a debug build is
 * back to the normal zero-on-load behaviour.
 */
@Singleton
class DemoScores @Inject constructor() : RestoredScoresPolicy {
    @Volatile var keepScores: Boolean = false

    override fun keepsRestoredScores(): Boolean = keepScores
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DemoScoresModule {
    @Binds
    abstract fun restoredScoresPolicy(impl: DemoScores): RestoredScoresPolicy
}
