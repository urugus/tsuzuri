package com.urugus.tsuzuri.data.vault

import com.urugus.tsuzuri.core.model.Event
import com.urugus.tsuzuri.data.markdown.DiaryDocument
import java.time.LocalDate

/**
 * vault(Markdown=SSOT) とアプリモデルをつなぐリポジトリ。
 * [DiaryDocument] のロスレス性を保ったまま、日付単位で読み書きする。
 * Android 非依存（[VaultStorage] 経由）なので Fake で JVM テスト可能。
 */
class DiaryRepository(private val storage: VaultStorage) {

    /** 当日のドキュメント。ファイルが無ければフロントマター付きの新規文書を返す。 */
    suspend fun loadDay(date: LocalDate): DiaryDocument {
        val text = storage.readText(VaultPaths.fileName(date))
        return if (text.isNullOrEmpty()) {
            DiaryDocument.create(date)
        } else {
            DiaryDocument.parse(text, fallbackDate = date)
        }
    }

    suspend fun saveDay(date: LocalDate, document: DiaryDocument) {
        storage.writeText(VaultPaths.fileName(date), document.render())
    }

    /** 当日ファイルの生Markdownを取得（無ければ空）。 */
    suspend fun loadRawText(date: LocalDate): String =
        storage.readText(VaultPaths.fileName(date)).orEmpty()

    /**
     * 生Markdownをそのまま当日ファイルに保存する（ユーザーによる直接編集用）。
     * テキストの所有者はユーザーなので [DiaryDocument] を介さず保存し、次回の [loadDay] で再パースする。
     */
    suspend fun saveRawText(date: LocalDate, text: String) {
        storage.writeText(VaultPaths.fileName(date), text)
    }

    /** イベントを当日ファイルに upsert して保存し、更新後の文書を返す。 */
    suspend fun upsertEvent(event: Event): DiaryDocument {
        val updated = loadDay(event.date).upsertEvent(event)
        saveDay(event.date, updated)
        return updated
    }

    suspend fun removeEvent(date: LocalDate, eventId: String) {
        val updated = loadDay(date).removeEvent(eventId)
        saveDay(date, updated)
    }

    suspend fun eventsForDay(date: LocalDate): List<Event> = loadDay(date).events()

    /** vault に存在する日記の日付一覧（昇順）。 */
    suspend fun listDays(): List<LocalDate> =
        storage.listFileNames().mapNotNull { VaultPaths.dateOf(it) }.sorted()
}
