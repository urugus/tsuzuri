package com.urugus.tsuzuri.data.markdown

import com.urugus.tsuzuri.core.model.Event
import com.urugus.tsuzuri.core.model.EventSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class DiaryDocumentTest {

    private val date = LocalDate.of(2026, 5, 30)

    private fun event(
        id: String,
        time: LocalTime?,
        title: String,
        body: String = "",
        category: String? = null,
        location: String? = null,
        source: EventSource = EventSource.CHAT,
        createdAt: String = "2026-05-30T00:00:00Z",
    ) = Event(
        id = id,
        date = date,
        time = time,
        title = title,
        body = body,
        category = category,
        location = location,
        source = source,
        createdAt = Instant.parse(createdAt),
    )

    // ---- ロスレス保証（あらゆる入力で render==原文）---------------------------

    @Test
    fun render_isByteExact_forArbitraryDocument() {
        val original = buildString {
            append("---\n")
            append("date: 2026-05-30\n")
            append("tags: [private]\n")     // 未知frontmatterキー
            append("mood: good\n")
            append("---\n")
            append("\n")
            append("# 今日のまとめ\n")        // 非イベント見出し
            append("![photo](./a.jpg)\n")    // 画像
            append("\n")
            append("## 09:00 朝\n")
            append("<!-- tsuzuri:event id=evt-a; source=manual; time=09:00; created=2026-05-30T09:00:00Z -->\n")
            append("\n")
            append("```text\n")
            append("## これはコード内の見出し\n") // フェンス内 → 分割されない
            append("```\n")
            append("\n")
            append("## ただのH2セクション\n")   // メタ無し → イベントではない
            append("- a\n")
        }
        val doc = DiaryDocument.parse(original)
        assertEquals(original, doc.render())
        // フェンス内/メタ無しH2はイベントにならない → イベントは1件
        assertEquals(1, doc.events().size)
        assertEquals("朝", doc.events().single().title)
    }

    @Test
    fun render_isByteExact_withCrlf() {
        val original = "---\r\ndate: 2026-05-30\r\n---\r\n\r\n## メモ\r\n本文\r\n"
        assertEquals(original, DiaryDocument.parse(original).render())
    }

    @Test
    fun render_isByteExact_whenFrontmatterUnclosed_noDataLoss() {
        val original = "---\ndate: 2026-05-30\n本文がそのまま続く\n## x\n"
        val doc = DiaryDocument.parse(original)
        assertEquals(original, doc.render()) // 全消し事故が起きない
        assertEquals(null, doc.date)         // 閉じ--- 無し → frontmatter無効
    }

    @Test
    fun parse_empty_rendersEmpty() {
        val doc = DiaryDocument.parse("")
        assertEquals("", doc.render())
        assertTrue(doc.events().isEmpty())
    }

    // ---- 編集はイベントブロックだけをパッチ -----------------------------------

    @Test
    fun upsert_patchesEventButPreservesOtherContent() {
        val original = buildString {
            append("---\ndate: 2026-05-30\ntags: [x]\n---\n\n")
            append("# 見出し\n散文はそのまま。\n\n")
            append("## 09:00 朝\n")
            append("<!-- tsuzuri:event id=evt-a; source=manual; time=09:00; created=2026-05-30T09:00:00Z -->\n\n")
            append("古い本文\n")
        }
        val doc = DiaryDocument.parse(original)
        val e = doc.events().single { it.id == "evt-a" }
        val updated = doc.upsertEvent(e.copy(body = "新しい本文"))
        val out = updated.render()

        assertTrue("未知frontmatterが残る", out.contains("tags: [x]"))
        assertTrue("散文が残る", out.contains("散文はそのまま。"))
        assertTrue("新本文に置換", out.contains("新しい本文"))
        assertFalse("旧本文は消える", out.contains("古い本文"))
        assertEquals("仕事=仕事のidは保持", "evt-a", updated.events().single().id)
    }

    @Test
    fun removeEvent_dropsBlockKeepsProse() {
        val original = buildString {
            append("---\ndate: 2026-05-30\n---\n\n")
            append("散文。\n\n")
            append("## 09:00 朝\n")
            append("<!-- tsuzuri:event id=evt-a; source=chat; time=09:00; created=2026-05-30T09:00:00Z -->\n\n")
            append("本文\n")
        }
        val doc = DiaryDocument.parse(original).removeEvent("evt-a")
        val out = doc.render()
        assertTrue(out.contains("散文。"))
        assertFalse(out.contains("tsuzuri:event"))
        assertFalse(out.contains("## 09:00 朝"))
        assertTrue(doc.events().isEmpty())
    }

    // ---- 往復（create→upsert→render→parse）------------------------------------

    @Test
    fun roundTrip_createUpsertParse_preservesFields() {
        val e1 = event(
            id = "evt-1", time = LocalTime.of(9, 30), title = "朝の散歩",
            body = "公園を30分。", category = "健康", location = "近所の公園",
            source = EventSource.CHAT, createdAt = "2026-05-30T09:35:00Z",
        )
        val e2 = event(
            id = "evt-2", time = null, title = "ふと思ったこと",
            source = EventSource.MANUAL, createdAt = "2026-05-30T22:00:00Z",
        )
        val rendered = DiaryDocument.create(date).upsertEvent(e1).upsertEvent(e2).render()
        val parsed = DiaryDocument.parse(rendered)
        assertEquals(listOf(e1, e2), parsed.events().sortedBy { it.id })
    }

    @Test
    fun roundTrip_preservesSecondPrecision() {
        val e = event(id = "evt-s", time = LocalTime.of(9, 30, 45), title = "秒あり")
        val parsed = DiaryDocument.parse(DiaryDocument.create(date).upsertEvent(e).render())
        assertEquals(LocalTime.of(9, 30, 45), parsed.events().single().time)
    }

    @Test
    fun roundTrip_preservesIndentedBody() {
        val e = event(id = "evt-i", time = LocalTime.of(15, 0), title = "コード", body = "    code\n    line2")
        val parsed = DiaryDocument.parse(DiaryDocument.create(date).upsertEvent(e).render())
        assertEquals("    code\n    line2", parsed.events().single().body)
    }

    @Test
    fun roundTrip_metaValuesWithSpecialChars() {
        val e = event(
            id = "evt-x", time = LocalTime.of(12, 0), title = "特殊文字",
            category = "仕事;重要", location = "店<A>-->B",
        )
        val rendered = DiaryDocument.create(date).upsertEvent(e).render()
        // メタ行は1行で、生の ; や --> で壊れていない
        val metaLine = rendered.lineSequence().first { it.contains("tsuzuri:event") }
        assertFalse("値中の生; が無い", metaLine.contains("category=仕事;重要"))
        val parsed = DiaryDocument.parse(rendered).events().single()
        assertEquals("仕事;重要", parsed.category)
        assertEquals("店<A>-->B", parsed.location)
    }

    @Test
    fun roundTrip_titleStartingWithTime_whenMetaTimePresent() {
        val e = event(id = "evt-t", time = LocalTime.of(9, 5), title = "映画を見た")
        val parsed = DiaryDocument.parse(DiaryDocument.create(date).upsertEvent(e).render()).events().single()
        assertEquals(LocalTime.of(9, 5), parsed.time)
        assertEquals("映画を見た", parsed.title)
    }

    // ---- 手書き（メタ欠落）の決定的フォールバック ------------------------------

    @Test
    fun parse_handAuthored_missingId_isDeterministic() {
        val md = "---\ndate: 2026-05-30\n---\n\n## 散歩\n<!-- tsuzuri:event source=manual -->\n\n本文\n"
        val first = DiaryDocument.parse(md).events().single()
        val second = DiaryDocument.parse(md).events().single()
        assertEquals(first.id, second.id)        // 再parseで不変
        assertTrue(first.id.startsWith("evt-h"))  // 決定的フォールバックの印
        assertEquals(EventSource.MANUAL, first.source)
    }

    @Test
    fun parse_quotedFrontmatterDate() {
        val md = "---\ndate: \"2026-05-30\"\n---\n\n## x\n<!-- tsuzuri:event id=e; source=chat; time=10:00; created=2026-05-30T10:00:00Z -->\n"
        assertEquals(date, DiaryDocument.parse(md).date)
    }

    // ---- H1: 本文中の `## ` を含むイベントが round-trip 安全 ----------------------

    @Test
    fun roundTrip_bodyContainingDoubleHashHeading() {
        val e = event(
            id = "evt-h2", time = LocalTime.of(10, 0), title = "メモ",
            body = "前段。\n## 本文中の見出し\n続きの本文。",
        )
        val rendered = DiaryDocument.create(date).upsertEvent(e).render()
        assertTrue("保存形では退避されている", rendered.contains("\\## 本文中の見出し"))
        val parsed = DiaryDocument.parse(rendered)
        assertEquals("イベントは1件のまま（分割されない）", 1, parsed.events().size)
        assertEquals("前段。\n## 本文中の見出し\n続きの本文。", parsed.events().single().body)
    }

    @Test
    fun handAuthored_topLevelDoubleHash_inBody_isSectionBreak() {
        // 手書き(未退避)のトップレベル `## ` は Markdown 仕様どおり区切り扱い（本文には含めない）。
        val md = buildString {
            append("---\ndate: 2026-05-30\n---\n\n")
            append("## 09:00 朝\n<!-- tsuzuri:event id=evt-a; source=chat; time=09:00; created=2026-05-30T09:00:00Z -->\n\n")
            append("本文。\n\n## 区切り\n中身\n")
        }
        val doc = DiaryDocument.parse(md)
        assertEquals(md, doc.render())              // ロスレス
        assertEquals(1, doc.events().size)
        assertEquals("本文。", doc.events().single().body) // 区切り以降は本文に含めない
    }

    // ---- M2: 追記セパレータ（末尾走査）の正当性 ---------------------------------

    @Test
    fun append_afterRemovingLastEvent_keepsSingleBlankLine() {
        // 末尾に区切りRawが残るケースでも余分な空行を増やさない。
        val e1 = event(id = "evt-1", time = LocalTime.of(9, 0), title = "一つ目", body = "A")
        val e2 = event(id = "evt-2", time = LocalTime.of(10, 0), title = "二つ目", body = "B")
        val doc = DiaryDocument.create(date).upsertEvent(e1).upsertEvent(e2).removeEvent("evt-2")
        val e3 = event(id = "evt-3", time = LocalTime.of(11, 0), title = "三つ目", body = "C")
        val out = doc.upsertEvent(e3).render()
        assertFalse("3つ以上連続改行が無い", out.contains("\n\n\n"))
        assertEquals(setOf("evt-1", "evt-3"), DiaryDocument.parse(out).events().map { it.id }.toSet())
    }

    @Test
    fun upsert_preservesStandaloneSectionBetweenEvents() {
        val md = buildString {
            append("---\ndate: 2026-05-30\n---\n\n")
            append("## 09:00 朝\n<!-- tsuzuri:event id=evt-a; source=chat; time=09:00; created=2026-05-30T09:00:00Z -->\n\nA\n\n")
            append("## 手書きの区切り\n散文\n\n")
            append("## 12:00 昼\n<!-- tsuzuri:event id=evt-b; source=chat; time=12:00; created=2026-05-30T12:00:00Z -->\n\nB\n")
        }
        val doc = DiaryDocument.parse(md)
        val a = doc.events().single { it.id == "evt-a" }
        val out = doc.upsertEvent(a.copy(body = "A改")).render()
        assertTrue("手書きセクションが残る", out.contains("## 手書きの区切り"))
        assertTrue("散文が残る", out.contains("散文"))
        assertTrue("編集が反映", out.contains("A改"))
        assertTrue("他イベントが残る", out.contains("id=evt-b"))
    }
}
