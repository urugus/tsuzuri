package com.urugus.tsuzuri.core.llm

/** 会話の1メッセージ。Android非依存。 */
data class ChatMessage(
    val role: ChatRole,
    val content: String,
)

enum class ChatRole { USER, ASSISTANT }
