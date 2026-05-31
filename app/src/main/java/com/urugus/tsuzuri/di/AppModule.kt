package com.urugus.tsuzuri.di

import com.urugus.tsuzuri.core.llm.LlmProvider
import com.urugus.tsuzuri.core.llm.StubLlmProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LlmModule {
    /** MVPの既定LLM。後フェーズで実装を差し替える。 */
    @Binds
    abstract fun bindLlmProvider(impl: StubLlmProvider): LlmProvider
}
