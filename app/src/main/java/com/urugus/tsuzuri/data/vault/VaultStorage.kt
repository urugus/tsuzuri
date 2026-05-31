package com.urugus.tsuzuri.data.vault

/**
 * vault ファイルの読み書きを抽象化するストレージ境界。
 * Android(SAF) 実装と、テスト用のインメモリ実装を差し替え可能にする。
 * 実装はファイル名（例 `2026-05-30.md`）をキーに単純なテキストI/Oを提供する。
 */
interface VaultStorage {
    /** 無ければ null。 */
    suspend fun readText(fileName: String): String?

    /** 既存なら上書き、無ければ新規作成。 */
    suspend fun writeText(fileName: String, content: String)

    /** vault 直下のファイル名一覧（フィルタは呼び出し側）。 */
    suspend fun listFileNames(): List<String>

    /** 削除できたら true。 */
    suspend fun delete(fileName: String): Boolean
}
