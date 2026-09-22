package com.example.budge.di

import com.example.budge.data.update.GithubReleaseSource
import com.example.budge.data.update.ReleaseSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the release reader to its GitHub implementation.
 *
 * The view model depends on [ReleaseSource] rather than the concrete client, which is
 * what lets the channel and version rules be tested without a network.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {
    @Binds
    @Singleton
    abstract fun bindReleaseSource(impl: GithubReleaseSource): ReleaseSource
}
