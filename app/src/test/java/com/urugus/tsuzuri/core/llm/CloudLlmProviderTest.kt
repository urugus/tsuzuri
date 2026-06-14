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

class CloudLlmProviderTest {

    private val clock = Clock.fixed(Instant.parse("2026-05-30T12:00:00Z"), ZoneOffset.UTC)
    private val prompts = LlmPromptBuilder()
    private val fallback = StubLlmProvider(clock, prompts)
    private val date = LocalDate.of(2026, 5, 30)

    @Test
    fun reply_fallsBackWhenClientThrowsOrReturnsBlank() = runTest {
        val throwingProvider = provider(client = FakeCloudChatClient(error = IllegalStateException("network")))
        val blankProvider = provider(client = FakeCloudChatClient(response = "   "))

        assertEquals(fallback.reply(emptyList()), throwingProvider.reply(emptyList()))
        assertEquals(fallback.reply(emptyList()), blankProvider.reply(emptyList()))
    }

    @Test
    fun extractEvents_fallsBackWhenClientThrows() = runTest {
        val provider = provider(client = FakeCloudChatClient(error = IllegalStateException("network")))
        val history = listOf(ChatMessage(ChatRole.USER, "朝に散歩した"))

        val events = provider.extractEvents(history, date)

        assertEquals(1, events.size)
        assertEquals("朝に散歩した", events.single().title)
        assertEquals(EventSource.CHAT, events.single().source)
    }

    @Test
    fun extractEvents_parsesInvalidTimeAsNullAndCompletesTitleAndBody() = runTest {
        val provider = provider(
            client = FakeCloudChatClient(
                response = """
                    [
                      {
                        "title": null,
                        "body": "朝に公園を散歩した\n気持ちよかった",
                        "time": "not-a-time",
                        "category": "health",
                        "location": "公園"
                      },
                      {
                        "title": "昼食",
                        "body": "",
                        "time": "12:30",
                        "category": null,
                        "location": null
                      }
                    ]
                """.trimIndent(),
            ),
        )

        val events = provider.extractEvents(listOf(ChatMessage(ChatRole.USER, "日記メモ")), date)

        assertEquals(2, events.size)
        assertEquals("朝に公園を散歩した", events[0].title)
        assertEquals("朝に公園を散歩した\n気持ちよかった", events[0].body)
        assertNull(events[0].time)
        assertEquals("health", events[0].category)
        assertEquals("公園", events[0].location)
        assertEquals(Instant.parse("2026-05-30T12:00:00Z"), events[0].createdAt)

        assertEquals("昼食", events[1].title)
        assertEquals("昼食", events[1].body)
        assertEquals(LocalTime.of(12, 30), events[1].time)
        assertTrue(events.all { it.source == EventSource.CHAT })
    }

    private fun provider(client: CloudChatClient): CloudLlmProvider =
        CloudLlmProvider(
            credentials = FakeCloudCredentialStore(),
            settings = FakeCloudModelSettings(),
            client = client,
            prompts = prompts,
            fallback = fallback,
            clock = clock,
        )

    private class FakeCloudCredentialStore : CloudCredentialStore {
        override fun apiKey(): String = "test-key"
        override fun saveApiKey(value: String) = Unit
        override fun clearApiKey() = Unit
    }

    private class FakeCloudModelSettings : CloudModelSettings {
        override val cloudModel: String = "test-model"
    }

    private class FakeCloudChatClient(
        private val response: String = "cloud response",
        private val error: Exception? = null,
    ) : CloudChatClient {
        override suspend fun complete(apiKey: String, model: String, prompt: String): String {
            error?.let { throw it }
            return response
        }
    }
}
