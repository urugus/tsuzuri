package com.urugus.tsuzuri.core.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

interface CloudChatClient {
    suspend fun complete(apiKey: String, model: String, prompt: String): String
}

class OpenAiChatClient @Inject constructor() : CloudChatClient {

    override suspend fun complete(apiKey: String, model: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
            }
            val body = CloudJson.chatCompletionRequest(model = model, prompt = prompt)
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            connection.disconnect()

            if (status !in 200..299) {
                throw IllegalStateException("Cloud AI request failed ($status): ${response.take(ERROR_PREVIEW_MAX)}")
            }
            CloudJson.assistantContent(response).trim()
        }

    private companion object {
        const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
        const val TIMEOUT_MS = 30_000
        const val ERROR_PREVIEW_MAX = 240
    }
}
