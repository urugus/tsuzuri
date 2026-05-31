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
) : LlmProvider {

    private val engine: LlmInference by lazy {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(MAX_TOKENS)
            .build()
        LlmInference.createFromOptions(context, options)
    }

    override suspend fun reply(history: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        runCatching { engine.generateResponse(buildChatPrompt(history)) }
            .getOrElse { fallback.reply(history) }
    }

    override suspend fun extractEvents(history: List<ChatMessage>, date: LocalDate): List<Event> =
        fallback.extractEvents(history, date)

    override suspend fun reconstruct(events: List<Event>, date: LocalDate): String =
        withContext(Dispatchers.IO) {
            if (events.isEmpty()) return@withContext fallback.reconstruct(events, date)
            runCatching { engine.generateResponse(buildReconstructPrompt(events, date)) }
                .getOrElse { fallback.reconstruct(events, date) }
        }

    private fun buildChatPrompt(history: List<ChatMessage>): String = buildString {
        append("あなたは日記作成を手伝うアシスタントです。ユーザーの一日の出来事を引き出す短い質問を1つしてください。\n\n")
        history.takeLast(MAX_TURNS).forEach { m ->
            val who = if (m.role == ChatRole.USER) "ユーザー" else "アシスタント"
            append(who).append(": ").append(m.content).append('\n')
        }
        append("アシスタント: ")
    }

    private fun buildReconstructPrompt(events: List<Event>, date: LocalDate): String = buildString {
        append("以下の出来事メモから、$date の自然な日記を日本語で書いてください。事実を創作しないこと。\n\n")
        events.forEach { e ->
            e.time?.let { append("[").append(it).append("] ") }
            append(e.body.ifBlank { e.title }).append('\n')
        }
        append("\n日記:\n")
    }

    fun close() {
        runCatching { engine.close() }
    }

    private companion object {
        const val MAX_TOKENS = 1024
        const val MAX_TURNS = 8
    }
}
