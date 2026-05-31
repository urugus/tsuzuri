package com.urugus.tsuzuri.core.llm

import com.urugus.tsuzuri.core.model.Event
import java.time.LocalDate
import javax.inject.Inject

/**
 * LLMへ渡すプロンプトを一箇所に集約する。
 *
 * provider実装は推論エンジンとの接続だけを担当し、会話設計や出力制約はここで管理する。
 */
class LlmPromptBuilder @Inject constructor() {

    fun nextQuestion(userTurnCount: Int): String =
        diaryQuestions[userTurnCount.coerceIn(0, diaryQuestions.lastIndex)]

    fun chatPrompt(history: List<ChatMessage>): String = buildString {
        appendLine("あなたは日記作成を手伝うアシスタントです。")
        appendLine("ユーザーの一日の出来事を引き出す短い質問を1つだけしてください。")
        appendLine("質問は日本語で、押しつけがましくない自然な文にしてください。")
        appendLine()
        history.takeLast(MAX_TURNS).forEach { m ->
            val who = if (m.role == ChatRole.USER) "ユーザー" else "アシスタント"
            append(who).append(": ").appendLine(m.content)
        }
        append("アシスタント: ")
    }

    fun extractionPrompt(history: List<ChatMessage>, date: LocalDate): String = buildString {
        appendLine("以下の会話から、$date に属する日記の出来事だけを抽出してください。")
        appendLine("出力はJSON配列のみ。各要素は title, body, time, category, location を持ちます。")
        appendLine("time は HH:mm 形式または null。不明な事実を補完しないでください。")
        appendLine()
        history.takeLast(MAX_TURNS).forEach { m ->
            val who = if (m.role == ChatRole.USER) "ユーザー" else "アシスタント"
            append(who).append(": ").appendLine(m.content)
        }
        appendLine()
        append("JSON: ")
    }

    fun reconstructPrompt(events: List<Event>, date: LocalDate): String = buildString {
        appendLine("以下の出来事メモから、$date の自然な日記を日本語で書いてください。")
        appendLine("事実を創作せず、書かれている内容だけを使ってください。")
        appendLine("過度に飾らず、あとで読み返しやすい落ち着いた文章にしてください。")
        appendLine()
        events.forEach { e ->
            e.time?.let { append("[").append(it).append("] ") }
            appendLine(e.body.ifBlank { e.title })
        }
        appendLine()
        appendLine("日記:")
    }

    private companion object {
        const val MAX_TURNS = 8

        val diaryQuestions = listOf(
            "今日はどんな一日でしたか？印象に残った出来事を教えてください。",
            "それはいつ頃のことですか？どう感じたかも教えてください。",
            "他に記録しておきたいことはありますか？",
        )
    }
}
