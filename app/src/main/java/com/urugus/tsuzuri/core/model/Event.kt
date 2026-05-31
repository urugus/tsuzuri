package com.urugus.tsuzuri.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * 出来事(イベント) — 日記の原子単位。
 *
 * 日記の「文章」は保存せず、Eventを蓄積して要求時にAIが再構成する設計。
 * このクラスは Android 非依存（java.time のみ）なので、JVMユニットテストで直接検証できる。
 *
 * 規約:
 * - [body] は前後の空白を含まない正規化済みテキスト（Markdown codec が trim する）。
 * - [date] はそのイベントが属する日（vault の日次ファイルに対応）。
 */
data class Event(
    val id: String,
    val date: LocalDate,
    val time: LocalTime?,
    val title: String,
    val body: String,
    val category: String?,
    val location: String?,
    val source: EventSource,
    val createdAt: Instant,
) {
    companion object {
        /**
         * 新規イベント用の安定ID（vault や Room インデックスの突合キー）。
         * 衝突回避のためフルUUIDを使う（短縮はUI表示側の責務）。
         */
        fun newId(): String = "evt-" + UUID.randomUUID().toString()
    }
}

/** イベントの出所。Markdown には [wire] 文字列で保存する。 */
enum class EventSource(val wire: String) {
    CHAT("chat"),
    MANUAL("manual"),
    CALENDAR("calendar"),
    LOCATION("location"),
    IMPORT("import"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromWire(value: String?): EventSource {
            val v = value?.trim()?.lowercase()
            return entries.firstOrNull { it.wire == v } ?: UNKNOWN
        }
    }
}
