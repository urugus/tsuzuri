package com.urugus.tsuzuri.core.llm

import android.content.Context
import com.urugus.tsuzuri.core.model.Event
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 設定とモデルの有無に応じて Stub / オンデバイス を切り替える [LlmProvider]。
 *
 * 既定は [StubLlmProvider]。ユーザーが「オンデバイス利用」をONにし、かつモデルが
 * [ModelStore] に取り込まれている場合のみ [MediaPipeLlmProvider] を使う。
 * これにより、対応端末が無くてもアプリは常に動作する（コスト0で一周できる）。
 */
@Singleton
class RoutingLlmProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val stub: StubLlmProvider,
    private val modelStore: ModelStore,
    private val settings: LlmSettings,
) : LlmProvider {

    @Volatile
    private var onDevice: MediaPipeLlmProvider? = null

    private fun active(): LlmProvider {
        if (settings.useOnDevice && modelStore.isAvailable()) {
            return onDevice ?: MediaPipeLlmProvider(
                context = context,
                modelPath = modelStore.modelFile.path,
                fallback = stub,
            ).also { onDevice = it }
        }
        return stub
    }

    override suspend fun reply(history: List<ChatMessage>): String =
        active().reply(history)

    override suspend fun extractEvents(history: List<ChatMessage>, date: LocalDate): List<Event> =
        active().extractEvents(history, date)

    override suspend fun reconstruct(events: List<Event>, date: LocalDate): String =
        active().reconstruct(events, date)
}
