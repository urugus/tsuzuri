package com.urugus.tsuzuri.core.llm

import com.urugus.tsuzuri.core.model.Event
import java.time.LocalDate

/**
 * LLM を差し替え可能にする抽象。
 *
 * MVPは [StubLlmProvider]（ヒューリスティックなダミー）を既定にし、
 * 後フェーズでオンデバイス(MediaPipe/LiteRT-LM) や Gemini Nano / BYOKクラウドへ差し替える。
 * 実装先の現実的な制約（高性能端末前提・モデル配布等）は着手時に再確認する。
 */
interface LlmProvider {
    /** 会話履歴に対する次のアシスタント発話（出来事を引き出す質問など）。 */
    suspend fun reply(history: List<ChatMessage>): String

    /** 会話から、指定日に属する出来事(イベント)を抽出する。 */
    suspend fun extractEvents(history: List<ChatMessage>, date: LocalDate): List<Event>
}
