package com.urugus.tsuzuri.core.llm

import com.urugus.tsuzuri.core.model.Event
import com.urugus.tsuzuri.core.model.EventSource
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * 本物のLLM導入前のダミー実装。固定の質問を返し、ユーザー発話を素朴に出来事化する。
 * これにより「会話→抽出→vault保存」の導線をオフライン・無料で通せる。
 * [clock] を注入することで抽出時刻が決定的になり、ユニットテスト可能。
 */
class StubLlmProvider @Inject constructor(
    private val clock: Clock,
) : LlmProvider {

    private val prompts = listOf(
        "今日はどんな一日でしたか？印象に残った出来事を教えてください。",
        "それはいつ頃のことですか？どう感じたかも教えてください。",
        "他に記録しておきたいことはありますか？",
    )

    override suspend fun reply(history: List<ChatMessage>): String {
        val userTurns = history.count { it.role == ChatRole.USER }
        return prompts[userTurns.coerceIn(0, prompts.lastIndex)]
    }

    override suspend fun extractEvents(history: List<ChatMessage>, date: LocalDate): List<Event> =
        history
            .filter { it.role == ChatRole.USER && it.content.isNotBlank() }
            .map { msg ->
                val body = msg.content.trim()
                Event(
                    id = Event.newId(),
                    date = date,
                    time = null,
                    title = body.lineSequence().first().take(TITLE_MAX),
                    body = body,
                    category = null,
                    location = null,
                    source = EventSource.CHAT,
                    createdAt = Instant.now(clock),
                )
            }

    private companion object {
        const val TITLE_MAX = 40
    }
}
