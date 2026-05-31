package com.urugus.tsuzuri.core.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.urugus.tsuzuri.core.model.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * MediaPipe LLM Inference によるオンデバイス実装。
 *
 * - 会話応答([reply])と日記の再構成([reconstruct])はモデルの生成に任せる。
 * - 出来事抽出([extractEvents])は小型モデルでは不安定なため、堅牢な [fallback]（Stub）へ委譲する。
 * - いずれも生成失敗時は [fallback] にフォールバックし、アプリが止まらないようにする。
 *
 * 注意: 実推論は対応端末（十分なRAM/GPU）と有効なモデルファイルが必要。本クラスは
 * モデルが [ModelStore] に取り込まれている場合のみ [routeProvider] から使われる。
 * 公式は MediaPipe LLM Inference → LiteRT-LM への移行を案内しており、将来差し替え余地を残す。
 */
class MediaPipeLlmProvider(
    private val context: Context,
    private val modelPath: String,
    private val fallback: LlmProvider,
    private val prompts: LlmPromptBuilder,
) : LlmProvider {

    @Volatile
    private var engineOrNull: LlmInference? = null

    /** 初回利用時にのみエンジンを生成する（close()では生成しない）。 */
    private fun engine(): LlmInference =
        engineOrNull ?: synchronized(this) {
            engineOrNull ?: LlmInference.createFromOptions(
                context,
                LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(MAX_TOKENS)
                    .build(),
            ).also { engineOrNull = it }
        }

    override suspend fun reply(history: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        runCatching { engine().generateResponse(prompts.chatPrompt(history)) }
            .getOrElse { fallback.reply(history) }
    }

    override suspend fun extractEvents(history: List<ChatMessage>, date: LocalDate): List<Event> =
        fallback.extractEvents(history, date)

    override suspend fun reconstruct(events: List<Event>, date: LocalDate): String =
        withContext(Dispatchers.IO) {
            if (events.isEmpty()) return@withContext fallback.reconstruct(events, date)
            runCatching { engine().generateResponse(prompts.reconstructPrompt(events, date)) }
                .getOrElse { fallback.reconstruct(events, date) }
        }

    /** 初期化済みの場合のみ解放する（未初期化なら何もしない＝無用な初期化をしない）。 */
    fun close() {
        synchronized(this) {
            engineOrNull?.let { runCatching { it.close() } }
            engineOrNull = null
        }
    }

    private companion object {
        const val MAX_TOKENS = 1024
    }
}
