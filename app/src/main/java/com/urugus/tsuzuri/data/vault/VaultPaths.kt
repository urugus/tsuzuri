package com.urugus.tsuzuri.data.vault

import java.time.LocalDate

/**
 * vault 内の日次ファイル名と日付の対応（1日1ファイル `YYYY-MM-DD.md`）。
 * Android 非依存なので JVM ユニットテスト可能。
 */
object VaultPaths {
    private val FILE_NAME = Regex("""^(\d{4}-\d{2}-\d{2})\.md$""")

    fun fileName(date: LocalDate): String = "$date.md" // LocalDate.toString() は ISO yyyy-MM-dd

    fun dateOf(fileName: String): LocalDate? =
        FILE_NAME.find(fileName)?.let { runCatching { LocalDate.parse(it.groupValues[1]) }.getOrNull() }

    fun isDiaryFile(fileName: String): Boolean = dateOf(fileName) != null
}
