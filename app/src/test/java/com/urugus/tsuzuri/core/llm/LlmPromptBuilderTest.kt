package com.urugus.tsuzuri.core.llm

import com.urugus.tsuzuri.core.model.Event
import com.urugus.tsuzuri.core.model.EventSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class LlmPromptBuilderTest {

    private val builder = LlmPromptBuilder()
    private val date = LocalDate.of(2026, 5, 30)

    @Test
    fun chatPrompt_includesRecentHistoryAndAssistantCue() {
        val prompt = builder.chatPrompt(
            listOf(
                ChatMessage(ChatRole.ASSISTANT, "古い質問"),
                ChatMessage(ChatRole.USER, "朝に散歩した"),
            ),
        )

        assertTrue(prompt.contains("日記作成を手伝うアシスタント"))
        assertTrue(prompt.contains("ユーザー: 朝に散歩した"))
        assertTrue(prompt.endsWith("アシスタント: "))
    }

    @Test
    fun chatPrompt_limitsHistoryToRecentTurns() {
        val history = (1..10).map { ChatMessage(ChatRole.USER, "発話$it") }
        val prompt = builder.chatPrompt(history)

        assertFalse(prompt.contains("ユーザー: 発話1\n"))
        assertFalse(prompt.contains("ユーザー: 発話2\n"))
        assertTrue(prompt.contains("ユーザー: 発話3\n"))
        assertTrue(prompt.contains("ユーザー: 発話10\n"))
    }

    @Test
    fun reconstructPrompt_listsEventsWithoutInventingFactsInstruction() {
        val event = Event(
            id = "evt-1",
            date = date,
            time = LocalTime.of(7, 30),
            title = "朝の散歩",
            body = "朝に公園を散歩した",
            category = null,
            location = null,
            source = EventSource.CHAT,
            createdAt = Instant.parse("2026-05-30T12:00:00Z"),
        )

        val prompt = builder.reconstructPrompt(listOf(event), date)

        assertTrue(prompt.contains("事実を創作せず"))
        assertTrue(prompt.contains("[07:30] 朝に公園を散歩した"))
        assertTrue(prompt.trimEnd().endsWith("日記:"))
    }
}
