package com.urugus.tsuzuri.core.llm

enum class LlmProviderMode(val wire: String) {
    STUB("stub"),
    ON_DEVICE("on_device"),
    CLOUD("cloud"),
    ;

    companion object {
        fun fromWire(value: String?): LlmProviderMode =
            entries.firstOrNull { it.wire == value } ?: STUB
    }
}
