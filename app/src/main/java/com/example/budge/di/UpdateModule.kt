package com.example.budge.di

import com.example.budge.data.update.GithubReleaseSource
import com.example.budge.data.update.HttpGet
import com.example.budge.data.update.ReleaseSource
import com.example.budge.data.update.UrlConnectionGet
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the release reader and its one HTTP call.
 *
 * The view model depends on [ReleaseSource] rather than the concrete client, and the
 * client depends on [HttpGet] rather than opening a connection itself, which is what lets
 * the channel and version rules, and the fallback from the API to the release feed, be
 * tested without a network.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {
    @Binds
    @Singleton
    abstract fun bindReleaseSource(impl: GithubReleaseSource): ReleaseSource

    @Binds
    @Singleton
    abstract fun bindHttpGet(impl: UrlConnectionGet): HttpGet
}
