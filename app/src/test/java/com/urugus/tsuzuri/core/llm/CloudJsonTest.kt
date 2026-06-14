package com.urugus.tsuzuri.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudJsonTest {

    @Test
    fun chatCompletionRequest_escapesPromptAndModel() {
        val json = CloudJson.chatCompletionRequest(
            model = "gpt-test",
            prompt = "一行目\n\"引用\"",
        )

        assertTrue(json.contains("\"model\": \"gpt-test\""))
        assertTrue(json.contains("一行目\\n\\\"引用\\\""))
    }

    @Test
    fun assistantContent_readsFirstChoiceMessage() {
        val response = """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "今日はどうでしたか？"
                  }
                }
              ]
            }
        """.trimIndent()

        assertEquals("今日はどうでしたか？", CloudJson.assistantContent(response))
    }

    @Test
    fun eventDrafts_parsesJsonArrayEvenWhenWrappedInText() {
        val drafts = CloudJson.eventDrafts(
            """
            JSON:
            [
              {
                "title": "朝の散歩",
                "body": "朝に公園を散歩した",
                "time": "07:30",
                "category": null,
                "location": "公園"
              }
            ]
            """.trimIndent(),
        )

        assertEquals(1, drafts.size)
        assertEquals("朝の散歩", drafts[0].title)
        assertEquals("07:30", drafts[0].time)
        assertEquals("公園", drafts[0].location)
    }
}
