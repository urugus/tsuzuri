package com.urugus.tsuzuri.core.llm

import com.urugus.tsuzuri.core.model.Event
import com.urugus.tsuzuri.core.model.EventSource
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BYOKのクラウドAI実装。通信・JSON変換だけを担当し、プロンプトは [LlmPromptBuilder] に寄せる。
 * API失敗時は既存の簡易Providerへ戻し、会話/保存/再構成の導線を止めない。
 */
@Singleton
class CloudLlmProvider @Inject constructor(
    private val credentials: CloudCredentialStore,
    private val settings: LlmSettings,
    private val client: CloudChatClient,
    private val prompts: LlmPromptBuilder,
    private val fallback: StubLlmProvider,
    private val clock: Clock,
) : LlmProvider {

    override suspend fun reply(history: List<ChatMessage>): String =
        runCatching {
            complete(prompts.chatPrompt(history)).ifBlank { fallback.reply(history) }
        }.getOrElse {
            fallback.reply(history)
        }

    override suspend fun extractEvents(history: List<ChatMessage>, date: LocalDate): List<Event> =
        runCatching {
            val text = complete(prompts.extractionPrompt(history, date))
            parseEvents(text, date)
        }.getOrElse {
            fallback.extractEvents(history, date)
        }

    override suspend fun reconstruct(events: List<Event>, date: LocalDate): String =
        runCatching {
            complete(prompts.reconstructPrompt(events, date)).ifBlank { fallback.reconstruct(events, date) }
        }.getOrElse {
            fallback.reconstruct(events, date)
        }

    private suspend fun complete(prompt: String): String {
        val apiKey = credentials.apiKey() ?: error("Cloud API key is not configured")
        return client.complete(apiKey = apiKey, model = settings.cloudModel, prompt = prompt)
    }

    private fun parseEvents(text: String, date: LocalDate): List<Event> {
        val now = Instant.now(clock)
        return CloudJson.eventDrafts(text).mapNotNull { draft ->
            val body = draft.body?.trim().orEmpty()
            val title = draft.title?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.take(TITLE_MAX)
                ?: body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(TITLE_MAX)
            val normalizedBody = body.ifBlank { title.orEmpty() }
            if (title.isNullOrBlank() || normalizedBody.isBlank()) return@mapNotNull null
            Event(
                id = Event.newId(),
                date = date,
                time = draft.time?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                title = title,
                body = normalizedBody,
                category = draft.category?.trim()?.takeIf { it.isNotEmpty() },
                location = draft.location?.trim()?.takeIf { it.isNotEmpty() },
                source = EventSource.CHAT,
                createdAt = now,
            )
        }
    }

    private companion object {
        const val TITLE_MAX = 40
    }
}
