package com.urugus.tsuzuri.data.markdown

import com.urugus.tsuzuri.core.model.Event
import com.urugus.tsuzuri.core.model.EventSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 1日分の日記Markdown（vault の `YYYY-MM-DD.md`）を **ロスレス** に表現する文書モデル。
 *
 * Markdown が SSOT(真実)であるため、本モデルは原文を完全保持する:
 * - 未編集の領域（手書きの散文・画像・`#`/`##` 見出し・コードフェンス・未知frontmatterキー等）は
 *   [render] でバイト単位に原文再現される。
 * - 書き込みは [upsertEvent]/[removeEvent] による **id一致イベントブロックのパッチ** のみ。
 *   全再生成はしないので、アプリ管理外の内容を破壊しない。
 *
 * ## 書式
 * ```
 * ---
 * date: 2026-05-30
 * ---
 *
 * ## 09:30 朝の散歩
 * <!-- tsuzuri:event id=...; source=chat; time=09:30; category=健康; created=2026-05-30T09:35:00Z -->
 *
 * 近所の公園を30分散歩した。
 * ```
 * - イベント判定は厳格: `## ` 見出しの **直下行が `tsuzuri:event` メタ行** の場合のみイベント。
 *   それ以外の `## ` 見出しやコードフェンス内の `## ` は通常Markdownとして保持。
 * - 時刻の正は **メタの `time=`**（秒・ナノ秒も保持）。見出しの時刻は表示用。
 * - メタ値は `; < > 改行` をパーセントエンコードして破損を防ぐ。
 * - アプリが書くイベント本文中の `## ` 行は `\## ` に退避してから保存し、読み込み時に復元する
 *   ため、本文に `## ` を含むイベントも round-trip 安全。なお手書きのトップレベル `## `（未退避）は
 *   Markdown 仕様どおりセクション区切りとして扱う（本文に入れるなら `### ` 以下を推奨）。
 */
class DiaryDocument private constructor(
    /** フロントマターから読めた日付（無ければ呼び出し側のフォールバック、それも無ければ null）。 */
    val date: LocalDate?,
    private val segments: List<Segment>,
) {
    /** 原文を完全再現（未編集ブロックはバイト一致、編集済みイベントのみ再生成）。 */
    fun render(): String = buildString { segments.forEach { append(it.rawText) } }

    /** インデックス/AIコンテキスト用にイベントだけ抽出。 */
    fun events(): List<Event> = segments.filterIsInstance<Segment.EventSeg>().map { it.event }

    /**
     * 同一idのイベントがあれば本文/メタを差し替え、無ければ末尾に追記する。
     * [date] が確定済みなら event.date との一致を要求する。
     */
    fun upsertEvent(event: Event): DiaryDocument {
        require(date == null || event.date == date) {
            "event.date(${event.date}) が文書の date($date) と一致しません"
        }
        val newSegments = segments.toMutableList()
        val index = newSegments.indexOfFirst { it is Segment.EventSeg && it.event.id == event.id }
        if (index >= 0) {
            val old = newSegments[index] as Segment.EventSeg
            val trailing = old.rawText.takeLastWhile { it == '\n' }
            val text = renderEventBlock(event).trimEnd('\n') + trailing
            newSegments[index] = Segment.EventSeg(event, text)
        } else {
            // 追記時のセパレータ判定は末尾2文字だけで足りる（全文連結を避け O(1) 化）。
            val tail = lastChars(newSegments, 2)
            val separator = when {
                tail.isEmpty() -> ""
                tail.endsWith("\n\n") -> ""
                tail.endsWith("\n") -> "\n"
                else -> "\n\n"
            }
            if (separator.isNotEmpty()) newSegments.add(Segment.Raw(separator))
            newSegments.add(Segment.EventSeg(event, renderEventBlock(event)))
        }
        return DiaryDocument(date, newSegments)
    }

    fun removeEvent(id: String): DiaryDocument {
        val newSegments = segments.filterNot { it is Segment.EventSeg && it.event.id == id }
        return DiaryDocument(date, newSegments)
    }

    private sealed class Segment {
        abstract val rawText: String

        /** アプリ管理外の生テキスト（原文保持）。 */
        data class Raw(override val rawText: String) : Segment()

        /** 1イベントのブロック。[rawText] は原文（または再生成したテキスト）。 */
        data class EventSeg(val event: Event, override val rawText: String) : Segment()
    }

    companion object {
        private val DISPLAY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val META_LINE = Regex("""^<!--\s*tsuzuri:event\s+(.*?)\s*-->$""")
        private val LEADING_TIME = Regex("""^(\d{1,2}:\d{2}(?::\d{2})?)\s+(.*)$""")
        private const val HEADING_PREFIX = "## "
        private val META_ENCODE = charArrayOf('%', ';', '<', '>', '\n', '\r')

        /** 空のフロントマターだけを持つ新規文書。 */
        fun create(date: LocalDate): DiaryDocument =
            DiaryDocument(date, listOf(Segment.Raw("---\ndate: $date\n---\n")))

        /**
         * Markdown をロスレスにパースする。
         * @param fallbackDate フロントマターに date が無い場合に使う日付（Vault層がファイル名から渡す想定）。
         */
        fun parse(text: String, fallbackDate: LocalDate? = null): DiaryDocument {
            if (text.isEmpty()) return DiaryDocument(fallbackDate, emptyList())

            val lines = splitKeepEol(text)
            val segments = mutableListOf<Segment>()
            var bodyStart = 0
            var date: LocalDate? = null

            // --- フロントマター（閉じ `---` がある場合のみ有効。無ければ本文扱いで原文保持）---
            if (content(lines[0]).trim() == "---") {
                val close = (1 until lines.size).firstOrNull { content(lines[it]).trim() == "---" }
                if (close != null) {
                    date = parseFrontmatterDate(lines, 1, close)
                    segments.add(Segment.Raw(joinLines(lines, 0, close + 1)))
                    bodyStart = close + 1
                }
            }
            val effectiveDate = date ?: fallbackDate

            // --- コードフェンスを除外しつつトップレベル `## ` 見出し位置を特定 ---
            val isHeading = BooleanArray(lines.size)
            var inFence = false
            var fenceChar = ' '
            var fenceLen = 0
            for (i in bodyStart until lines.size) {
                val trimmed = content(lines[i]).trimStart()
                val run = fenceRun(trimmed)
                if (run == null) {
                    if (!inFence && content(lines[i]).startsWith(HEADING_PREFIX)) isHeading[i] = true
                } else if (!inFence) {
                    inFence = true; fenceChar = run.first; fenceLen = run.second
                } else if (run.first == fenceChar && run.second >= fenceLen &&
                    trimmed.drop(run.second).isBlank()
                ) {
                    inFence = false
                }
            }

            // --- 前置き（最初の見出しまで）---
            val firstHeading = (bodyStart until lines.size).firstOrNull { isHeading[it] } ?: lines.size
            if (firstHeading > bodyStart) {
                segments.add(Segment.Raw(joinLines(lines, bodyStart, firstHeading)))
            }

            // --- セクション（各 `## ` から次の `## ` まで）---
            var k = firstHeading
            while (k < lines.size) {
                val next = ((k + 1) until lines.size).firstOrNull { isHeading[it] } ?: lines.size
                val sectionText = joinLines(lines, k, next)
                val metaContent = if (k + 1 < next) {
                    META_LINE.find(content(lines[k + 1]).trim())?.groupValues?.get(1)
                } else {
                    null
                }
                if (metaContent != null) {
                    segments.add(Segment.EventSeg(buildEvent(lines, k, next, metaContent, effectiveDate), sectionText))
                } else {
                    segments.add(Segment.Raw(sectionText))
                }
                k = next
            }

            return DiaryDocument(effectiveDate, segments)
        }

        // ---- event serialization ------------------------------------------------

        private fun renderEventBlock(event: Event): String = buildString {
            append(HEADING_PREFIX)
            event.time?.let { append(DISPLAY_TIME.format(it)).append(' ') }
            append(event.title).append('\n')
            append("<!-- tsuzuri:event ").append(metaParts(event).joinToString("; ")).append(" -->\n")
            if (event.body.isNotEmpty()) {
                append('\n')
                append(escapeBody(event.body)) // 本文中の `## ` 行を `\## ` に退避（イベント境界の誤分割回避）
                if (!event.body.endsWith("\n")) append('\n')
            }
        }

        /** 本文行頭の `## `（イベント境界記号と衝突）を `\## ` にエスケープ。 */
        private fun escapeBody(body: String): String =
            body.split("\n").joinToString("\n") { if (it.startsWith(HEADING_PREFIX)) "\\$it" else it }

        private fun metaParts(event: Event): List<String> = buildList {
            add("id=${enc(event.id)}")
            add("source=${event.source.wire}")
            event.time?.let { add("time=${enc(it.toString())}") } // toString は秒/ナノ秒を保持
            event.category?.takeIf { it.isNotBlank() }?.let { add("category=${enc(it)}") }
            event.location?.takeIf { it.isNotBlank() }?.let { add("location=${enc(it)}") }
            add("created=${enc(event.createdAt.toString())}")
        }

        // ---- event parsing ------------------------------------------------------

        private fun buildEvent(
            lines: List<String>,
            headingIdx: Int,
            endIdx: Int,
            metaContent: String,
            date: LocalDate?,
        ): Event {
            val headingRaw = content(lines[headingIdx]).removePrefix(HEADING_PREFIX).trim()
            val meta = parseMeta(metaContent)

            val metaTime = meta["time"]?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            val headingMatch = LEADING_TIME.find(headingRaw)
            val headingTime = headingMatch?.let { runCatching { LocalTime.parse(padHour(it.groupValues[1])) }.getOrNull() }
            val time = metaTime ?: headingTime
            val title = when {
                time != null && headingMatch != null -> headingMatch.groupValues[2].trim()
                else -> headingRaw
            }

            // 本文 = メタ行の次から次セクション直前まで。構造上の前後空行のみ除去（インデントは保持）。
            // 書き出し時に退避した `\## ` を `## ` に復元する。
            val bodyLines = (headingIdx + 2 until endIdx).map { content(lines[it]) }
                .dropWhile { it.isBlank() }
                .dropLastWhile { it.isBlank() }
                .map { if (it.startsWith("\\$HEADING_PREFIX")) it.removePrefix("\\") else it }
            val body = bodyLines.joinToString("\n")

            val createdAt = meta["created"]?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()
                ?: Instant.EPOCH
            val eventDate = date ?: createdAt.atZone(ZoneOffset.UTC).toLocalDate()

            return Event(
                id = meta["id"] ?: stableId(eventDate, headingRaw, body),
                date = eventDate,
                time = time,
                title = title,
                body = body,
                category = meta["category"],
                location = meta["location"],
                source = EventSource.fromWire(meta["source"]),
                createdAt = createdAt,
            )
        }

        private fun parseMeta(content: String): Map<String, String> {
            val map = LinkedHashMap<String, String>()
            for (part in content.split(';')) {
                val t = part.trim()
                if (t.isEmpty()) continue
                val idx = t.indexOf('=')
                if (idx <= 0) continue
                val key = t.substring(0, idx).trim()
                val value = dec(t.substring(idx + 1).trim())
                if (value.isNotEmpty()) map[key] = value
            }
            return map
        }

        private fun parseFrontmatterDate(lines: List<String>, from: Int, until: Int): LocalDate? {
            for (i in from until until) {
                val line = content(lines[i])
                val idx = line.indexOf(':')
                if (idx <= 0) continue
                if (line.substring(0, idx).trim() != "date") continue
                val value = line.substring(idx + 1).trim().trim('"', '\'')
                return runCatching { LocalDate.parse(value) }.getOrNull()
            }
            return null
        }

        /** (date, heading, body) から決定的に導くフォールバックID（メタにidが無い手書きイベント用）。 */
        private fun stableId(date: LocalDate?, heading: String, body: String): String {
            val s = (date?.toString() ?: "") + " " + heading + " " + body
            var h = 1125899906842597L
            for (c in s) h = 31 * h + c.code
            return "evt-h" + java.lang.Long.toHexString(h)
        }

        // ---- meta value percent-encoding ---------------------------------------

        private fun enc(s: String): String = buildString {
            for (c in s) {
                if (c in META_ENCODE) append('%').append("%02X".format(c.code)) else append(c)
            }
        }

        private fun dec(s: String): String = buildString {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '%' && i + 2 < s.length) {
                    val code = s.substring(i + 1, i + 3).toIntOrNull(16)
                    if (code != null) {
                        append(code.toChar()); i += 3; continue
                    }
                }
                append(c); i++
            }
        }

        // ---- line helpers -------------------------------------------------------

        /** 改行(\n/\r\n)を各行の末尾に保持したまま分割する。最終行は改行なしのことがある。 */
        private fun splitKeepEol(s: String): List<String> {
            val out = mutableListOf<String>()
            var start = 0
            for (i in s.indices) {
                if (s[i] == '\n') {
                    out.add(s.substring(start, i + 1))
                    start = i + 1
                }
            }
            if (start < s.length) out.add(s.substring(start))
            return out
        }

        /** 行から末尾の改行(\r\n/\n)を除いた内容。 */
        private fun content(line: String): String = line.removeSuffix("\n").removeSuffix("\r")

        private fun joinLines(lines: List<String>, from: Int, until: Int): String =
            buildString { for (i in from until until) append(lines[i]) }

        /** 全文を連結せず、segment 列の末尾 [n] 文字を取り出す（追記セパレータ判定用）。 */
        private fun lastChars(segments: List<Segment>, n: Int): String {
            val sb = StringBuilder(n)
            outer@ for (s in segments.asReversed()) {
                val t = s.rawText
                for (j in t.indices.reversed()) {
                    sb.append(t[j])
                    if (sb.length >= n) break@outer
                }
            }
            return sb.reverse().toString()
        }

        private fun fenceRun(trimmedStart: String): Pair<Char, Int>? = when {
            trimmedStart.startsWith("```") -> '`' to trimmedStart.takeWhile { it == '`' }.length
            trimmedStart.startsWith("~~~") -> '~' to trimmedStart.takeWhile { it == '~' }.length
            else -> null
        }

        private fun padHour(time: String): String {
            val parts = time.split(":")
            if (parts.isEmpty()) return time
            return (listOf(parts[0].padStart(2, '0')) + parts.drop(1)).joinToString(":")
        }
    }
}
