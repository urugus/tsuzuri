package com.urugus.tsuzuri.data.vault

import com.urugus.tsuzuri.core.model.Event
import com.urugus.tsuzuri.core.model.EventSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class DiaryRepositoryTest {

    private val date = LocalDate.of(2026, 5, 30)

    private fun event(
        id: String,
        time: LocalTime?,
        title: String,
        body: String = "",
        date: LocalDate = this.date,
    ) = Event(
        id = id,
        date = date,
        time = time,
        title = title,
        body = body,
        category = null,
        location = null,
        source = EventSource.CHAT,
        createdAt = Instant.parse("2026-05-30T09:00:00Z"),
    )

    @Test
    fun loadDay_newFile_hasFrontmatterWithDate() = runTest {
        val repo = DiaryRepository(FakeVaultStorage())
        val doc = repo.loadDay(date)
        assertEquals(date, doc.date)
        assertTrue(doc.events().isEmpty())
        assertTrue(doc.render().startsWith("---\ndate: 2026-05-30\n---\n"))
    }

    @Test
    fun upsertEvent_persistsAndReloads() = runTest {
        val storage = FakeVaultStorage()
        val repo = DiaryRepository(storage)
        val e = event("evt-1", LocalTime.of(9, 30), "朝の散歩", body = "公園を散歩")

        repo.upsertEvent(e)

        // ファイル名は YYYY-MM-DD.md
        assertTrue(storage.files.containsKey("2026-05-30.md"))
        // 別インスタンスのrepoでも読み戻せる（永続化されている）
        val reloaded = DiaryRepository(storage).eventsForDay(date)
        assertEquals(listOf(e), reloaded)
    }

    @Test
    fun upsertEvent_twice_keepsBothInDocumentOrder() = runTest {
        // ロスレスのため並べ替えはしない（時刻ソートは表示層の責務）。挿入順を保持する。
        val repo = DiaryRepository(FakeVaultStorage())
        repo.upsertEvent(event("evt-late", LocalTime.of(18, 0), "夜"))
        repo.upsertEvent(event("evt-early", LocalTime.of(6, 0), "朝"))
        val events = repo.eventsForDay(date)
        assertEquals(listOf("evt-late", "evt-early"), events.map { it.id })
    }

    @Test
    fun removeEvent_dropsIt() = runTest {
        val repo = DiaryRepository(FakeVaultStorage())
        repo.upsertEvent(event("evt-1", LocalTime.of(9, 0), "朝"))
        repo.removeEvent(date, "evt-1")
        assertTrue(repo.eventsForDay(date).isEmpty())
    }

    @Test
    fun listDays_filtersNonDiaryFilesAndSorts() = runTest {
        val storage = FakeVaultStorage(
            mapOf(
                "2026-05-30.md" to "---\ndate: 2026-05-30\n---\n",
                "2026-05-28.md" to "---\ndate: 2026-05-28\n---\n",
                "README.md" to "# not a diary",
                "notes.txt" to "x",
            ),
        )
        val days = DiaryRepository(storage).listDays()
        assertEquals(listOf(LocalDate.of(2026, 5, 28), LocalDate.of(2026, 5, 30)), days)
    }

    @Test
    fun loadDay_missing_returnsEmptyButValidDoc() = runTest {
        val repo = DiaryRepository(FakeVaultStorage())
        assertNull(repo.loadDay(date).events().firstOrNull())
    }

    @Test
    fun saveRawText_persistsVerbatimAndReparses() = runTest {
        val storage = FakeVaultStorage()
        val repo = DiaryRepository(storage)
        val raw = "---\ndate: 2026-05-30\n---\n\n" +
            "## 09:00 朝\n<!-- tsuzuri:event id=evt-a; source=manual; time=09:00; created=2026-05-30T09:00:00Z -->\n\n本文\n\n" +
            "# 手書きメモ\n自由記述。\n"

        repo.saveRawText(date, raw)

        // 生テキストはそのまま保存される
        assertEquals(raw, storage.files["2026-05-30.md"])
        assertEquals(raw, repo.loadRawText(date))
        // 再パースで出来事も認識される（手書き散文は保持）
        val events = repo.eventsForDay(date)
        assertEquals(1, events.size)
        assertEquals("朝", events.single().title)
    }
}
