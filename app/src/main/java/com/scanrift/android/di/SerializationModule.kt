package com.scanrift.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApiJson

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class SnapshotJson

@Module
@InstallIn(SingletonComponent::class)
object SerializationModule {

    /**
     * Lenient by design: the API adds fields without warning (a `flavour` key and a
     * top-level `new` flag both appeared recently) and a strict parser would reject
     * the entire catalogue over one of them.
     */
    @Provides @Singleton @ApiJson
    fun provideApiJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    /**
     * Backup snapshots are read and written by both platforms.
     *
     * `ignoreUnknownKeys` lets an Android file carrying `gameRecords` load on a build
     * that predates it, and lets an iOS-written file load here. `explicitNulls = false`
     * keeps absent optionals absent rather than emitting `null`, matching Swift's
     * synthesized Codable output.
     */
    @Provides @Singleton @SnapshotJson
    fun provideSnapshotJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }
}
