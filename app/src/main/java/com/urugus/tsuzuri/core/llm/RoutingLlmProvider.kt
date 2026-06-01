package com.urugus.tsuzuri.core.llm

import android.content.Context
import com.urugus.tsuzuri.core.model.Event
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 設定とモデル/APIキーの有無に応じて Stub / オンデバイス / クラウドを切り替える [LlmProvider]。
 *
 * 既定は [StubLlmProvider]。ユーザーがオンデバイスを選び、かつモデルが
 * [ModelStore] に取り込まれている場合のみ [MediaPipeLlmProvider] を使う。
 * これにより、対応端末が無くてもアプリは常に動作する（コスト0で一周できる）。
 */
@Singleton
class RoutingLlmProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val stub: StubLlmProvider,
    private val cloud: CloudLlmProvider,
    private val credentials: CloudCredentialStore,
    private val modelStore: ModelStore,
    private val settings: LlmSettings,
    private val prompts: LlmPromptBuilder,
) : LlmProvider {

    private var onDevice: MediaPipeLlmProvider? = null
    private var loadedStamp: Long = -1L

    /**
     * 設定/モデルに応じて実体を返す。モデルが差し替えられた(lastModified変化)場合は
     * 古いエンジンを閉じて作り直す。複数コルーチンからの同時呼び出しで二重生成しないよう同期する。
     */
    @Synchronized
    private fun active(): LlmProvider {
        when (settings.providerMode) {
            LlmProviderMode.ON_DEVICE -> {
                if (modelStore.isAvailable()) {
                    val stamp = modelStore.modelFile.lastModified()
                    val cached = onDevice
                    if (cached != null && stamp == loadedStamp) return cached
                    cached?.close()
                    return MediaPipeLlmProvider(
                        context = context,
                        modelPath = modelStore.modelFile.path,
                        fallback = stub,
                        prompts = prompts,
                    ).also {
                        onDevice = it
                        loadedStamp = stamp
                    }
                }
            }
            LlmProviderMode.CLOUD -> {
                if (credentials.hasApiKey()) {
                    releaseOnDevice()
                    return cloud
                }
            }
            LlmProviderMode.STUB -> Unit
        }
        // OFF/モデル無し: 確保済みのエンジンがあれば解放してメモリを返す。
        releaseOnDevice()
        return stub
    }

    private fun releaseOnDevice() {
        onDevice?.close()
        onDevice = null
        loadedStamp = -1L
    }

    override suspend fun reply(history: List<ChatMessage>): String =
        active().reply(history)

    override suspend fun extractEvents(history: List<ChatMessage>, date: LocalDate): List<Event> =
        active().extractEvents(history, date)

    override suspend fun reconstruct(events: List<Event>, date: LocalDate): String =
        active().reconstruct(events, date)
}
