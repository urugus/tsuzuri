package com.urugus.tsuzuri.core.llm

import com.urugus.tsuzuri.core.model.Event
import com.urugus.tsuzuri.core.model.EventSource
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * 本物のLLM導入前のダミー実装。固定の質問を返し、ユーザー発話を素朴に出来事化する。
 * これにより「会話→抽出→vault保存」の導線をオフライン・無料で通せる。
 * [clock] を注入することで抽出時刻が決定的になり、ユニットテスト可能。
 */
class StubLlmProvider @Inject constructor(
    private val clock: Clock,
    private val prompts: LlmPromptBuilder,
) : LlmProvider {

    override suspend fun reply(history: List<ChatMessage>): String {
        val userTurns = history.count { it.role == ChatRole.USER }
        return prompts.nextQuestion(userTurns)
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

    override suspend fun reconstruct(events: List<Event>, date: LocalDate): String {
        if (events.isEmpty()) return "$date の記録はまだありません。"
        val ordered = events.sortedWith(
            compareBy({ it.time ?: LocalTime.MAX }, { it.createdAt }, { it.id }),
        )
        return buildString {
            append("$date の振り返り\n\n")
            ordered.forEach { e ->
                e.time?.let { append(TIME_FORMAT.format(it)).append("　") }
                append(e.body.ifBlank { e.title })
                append("\n")
            }
        }.trimEnd()
    }

    private companion object {
        const val TITLE_MAX = 40
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
