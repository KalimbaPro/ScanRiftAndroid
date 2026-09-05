package com.scanrift.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultDispatcher

/** Injected rather than referenced directly, so tests can swap in a test dispatcher. */
@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
