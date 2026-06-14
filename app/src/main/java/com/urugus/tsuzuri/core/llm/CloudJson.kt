package com.urugus.tsuzuri.core.llm

internal data class CloudEventDraft(
    val title: String?,
    val body: String?,
    val time: String?,
    val category: String?,
    val location: String?,
)

internal object CloudJson {

    fun chatCompletionRequest(model: String, prompt: String): String =
        """
        {
          "model": ${quote(model)},
          "messages": [
            {
              "role": "user",
              "content": ${quote(prompt)}
            }
          ]
        }
        """.trimIndent()

    fun assistantContent(response: String): String {
        val root = Parser(response).parseValue() as? Map<*, *>
            ?: error("Cloud AI response is not an object")
        val choices = root["choices"] as? List<*>
            ?: error("Cloud AI response has no choices")
        val first = choices.firstOrNull() as? Map<*, *>
            ?: error("Cloud AI response has empty choices")
        val message = first["message"] as? Map<*, *>
            ?: error("Cloud AI response choice has no message")
        return message["content"] as? String
            ?: error("Cloud AI response message has no content")
    }

    fun eventDrafts(text: String): List<CloudEventDraft> {
        val json = extractJsonArray(text) ?: return emptyList()
        val value = Parser(json).parseValue() as? List<*> ?: return emptyList()
        return value.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            CloudEventDraft(
                title = map["title"] as? String,
                body = map["body"] as? String,
                time = map["time"] as? String,
                category = map["category"] as? String,
                location = map["location"] as? String,
            )
        }
    }

    private fun extractJsonArray(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) return trimmed
        val start = trimmed.indexOf('[')
        val end = trimmed.lastIndexOf(']')
        return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else null
    }

    fun quote(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        append("\\u")
                        append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        append(ch)
                    }
                }
            }
        }
        append('"')
    }

    private class Parser(private val input: String) {
        private var index = 0

        fun parseValue(): Any? {
            skipWhitespace()
            if (index >= input.length) error("Unexpected end of JSON")
            return when (input[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                else -> parseNumber()
            }.also {
                skipWhitespace()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            skipWhitespace()
            if (peek('}')) {
                index++
                return emptyMap()
            }
            val map = linkedMapOf<String, Any?>()
            while (true) {
                val key = parseString()
                skipWhitespace()
                expect(':')
                map[key] = parseValue()
                skipWhitespace()
                when {
                    peek(',') -> {
                        index++
                        skipWhitespace()
                    }
                    peek('}') -> {
                        index++
                        return map
                    }
                    else -> error("Expected ',' or '}' at $index")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            skipWhitespace()
            if (peek(']')) {
                index++
                return emptyList()
            }
            val list = mutableListOf<Any?>()
            while (true) {
                list += parseValue()
                skipWhitespace()
                when {
                    peek(',') -> {
                        index++
                        skipWhitespace()
                    }
                    peek(']') -> {
                        index++
                        return list
                    }
                    else -> error("Expected ',' or ']' at $index")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            return buildString {
                while (index < input.length) {
                    val ch = input[index++]
                    when (ch) {
                        '"' -> return@buildString
                        '\\' -> append(parseEscape())
                        else -> append(ch)
                    }
                }
                error("Unterminated string")
            }
        }

        private fun parseEscape(): Char {
            if (index >= input.length) error("Unterminated escape")
            return when (val ch = input[index++]) {
                '"', '\\', '/' -> ch
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    val end = index + 4
                    if (end > input.length) error("Invalid unicode escape")
                    input.substring(index, end).toInt(16).toChar().also { index = end }
                }
                else -> error("Invalid escape: $ch")
            }
        }

        private fun parseLiteral(literal: String, value: Any?): Any? {
            if (!input.startsWith(literal, index)) error("Expected $literal at $index")
            index += literal.length
            return value
        }

        private fun parseNumber(): String {
            val start = index
            while (index < input.length && !isValueTerminator(input[index])) {
                index++
            }
            if (start == index) error("Unexpected character '${input[index]}' at $index")
            return input.substring(start, index)
        }

        private fun expect(ch: Char) {
            skipWhitespace()
            if (index >= input.length || input[index] != ch) error("Expected '$ch' at $index")
            index++
        }

        private fun peek(ch: Char): Boolean = index < input.length && input[index] == ch

        private fun skipWhitespace() {
            while (index < input.length && input[index].isWhitespace()) index++
        }

        private fun isValueTerminator(ch: Char): Boolean =
            ch == ',' || ch == '}' || ch == ']' || ch.isWhitespace()
    }
}
