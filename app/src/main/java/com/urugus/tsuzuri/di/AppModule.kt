package com.urugus.tsuzuri.di

import com.urugus.tsuzuri.core.llm.AndroidCloudCredentialStore
import com.urugus.tsuzuri.core.llm.CloudChatClient
import com.urugus.tsuzuri.core.llm.CloudCredentialStore
import com.urugus.tsuzuri.core.llm.CloudModelSettings
import com.urugus.tsuzuri.core.llm.LlmProvider
import com.urugus.tsuzuri.core.llm.LlmSettings
import com.urugus.tsuzuri.core.llm.OpenAiChatClient
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
    /** 設定とモデル/APIキーの有無で Stub/オンデバイス/クラウドを切り替える。 */
    @Binds
    abstract fun bindLlmProvider(impl: RoutingLlmProvider): LlmProvider

    @Binds
    abstract fun bindCloudCredentialStore(impl: AndroidCloudCredentialStore): CloudCredentialStore

    @Binds
    abstract fun bindCloudChatClient(impl: OpenAiChatClient): CloudChatClient

    @Binds
    abstract fun bindCloudModelSettings(impl: LlmSettings): CloudModelSettings
}
