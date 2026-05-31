package com.urugus.tsuzuri.di

import com.urugus.tsuzuri.core.llm.LlmProvider
import com.urugus.tsuzuri.core.llm.RoutingLlmProvider
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
    /** 設定とモデルの有無で Stub/オンデバイス を切り替える。 */
    @Binds
    abstract fun bindLlmProvider(impl: RoutingLlmProvider): LlmProvider
}
