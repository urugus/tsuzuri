package com.urugus.tsuzuri.core.llm

import com.urugus.tsuzuri.core.model.EventSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class StubLlmProviderTest {

    private val clock = Clock.fixed(Instant.parse("2026-05-30T12:00:00Z"), ZoneOffset.UTC)
    private val provider = StubLlmProvider(clock, LlmPromptBuilder())
    private val date = LocalDate.of(2026, 5, 30)

    private fun user(text: String) = ChatMessage(ChatRole.USER, text)
    private fun assistant(text: String) = ChatMessage(ChatRole.ASSISTANT, text)

    @Test
    fun reply_advancesWithUserTurns() = runTest {
        val first = provider.reply(emptyList())
        val second = provider.reply(listOf(assistant(first), user("散歩した")))
        assertTrue(first.isNotBlank())
        assertTrue(second.isNotBlank())
        assertTrue("ユーザー発話が増えると質問が進む", first != second)
    }

    @Test
    fun extractEvents_oneEventPerUserMessage() = runTest {
        val history = listOf(
            assistant("今日はどんな一日でしたか？"),
            user("朝に公園を散歩した。\n気持ちよかった。"),
            assistant("どう感じましたか？"),
            user("昼は同僚とカレー"),
            user("   "), // 空白のみは無視
        )
        val events = provider.extractEvents(history, date)

        assertEquals(2, events.size)
        events.forEach {
            assertEquals(date, it.date)
            assertEquals(EventSource.CHAT, it.source)
            assertNull(it.time)
            assertEquals(Instant.parse("2026-05-30T12:00:00Z"), it.createdAt)
        }
        // タイトルは本文の先頭行、本文は全文
        assertEquals("朝に公園を散歩した。", events[0].title)
        assertEquals("朝に公園を散歩した。\n気持ちよかった。", events[0].body)
        assertEquals("昼は同僚とカレー", events[1].title)
    }

    @Test
    fun reconstruct_emptyDay() = runTest {
        assertEquals("2026-05-30 の記録はまだありません。", provider.reconstruct(emptyList(), date))
    }

    @Test
    fun reconstruct_ordersByTimeAndIncludesBodies() = runTest {
        val events = provider.extractEvents(
            listOf(user("夜は読書"), user("朝は散歩")),
            date,
        ).mapIndexed { i, e ->
            e.copy(time = if (i == 0) LocalTime.of(21, 0) else LocalTime.of(7, 0))
        }
        val text = provider.reconstruct(events, date)
        assertTrue(text.startsWith("2026-05-30 の振り返り"))
        // 時刻順（07:00 が 21:00 より前）
        assertTrue(text.indexOf("朝は散歩") < text.indexOf("夜は読書"))
        assertTrue(text.contains("07:00"))
    }
}
