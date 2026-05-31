package com.urugus.tsuzuri.data.vault

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Storage Access Framework(SAF) によるvault実装。ユーザーが選んだツリーUri配下を読み書きする。
 *
 * 注意(SAFの癖):
 * - [DocumentFile.findFile]/[DocumentFile.listFiles] はディレクトリ走査のため件数に比例して遅い（MVPでは許容）。
 * - createFile の表示名はプロバイダ依存。ローカルフォルダ(ExternalStorageProvider)では指定名を保持するが、
 *   一部プロバイダで拡張子付与の差異がありうる。MVPではローカルフォルダ運用を前提とする。
 */
class SafVaultStorage(
    private val context: Context,
    private val treeUri: Uri,
) : VaultStorage {

    private fun tree(): DocumentFile? = DocumentFile.fromTreeUri(context, treeUri)

    override suspend fun readText(fileName: String): String? = withContext(Dispatchers.IO) {
        val file = tree()?.findFile(fileName)?.takeIf { it.isFile } ?: return@withContext null
        context.contentResolver.openInputStream(file.uri)?.use { it.reader().readText() }
    }

    override suspend fun writeText(fileName: String, content: String) = withContext(Dispatchers.IO) {
        val dir = tree() ?: error("vault フォルダにアクセスできません")
        val existing = dir.findFile(fileName)
        if (existing != null && !existing.isFile) {
            error("同名のフォルダが存在するため書き込めません: $fileName")
        }
        val file = existing ?: dir.createFile(MIME_MARKDOWN, fileName)
            ?: error("ファイル作成に失敗しました: $fileName")
        // "wt" でトラックを切り詰めてから書き込む（古い内容の残留を防ぐ）。
        context.contentResolver.openOutputStream(file.uri, "wt")?.use {
            it.write(content.toByteArray(Charsets.UTF_8))
        } ?: error("ファイル書き込みに失敗しました: $fileName")
    }

    override suspend fun listFileNames(): List<String> = withContext(Dispatchers.IO) {
        // ファイルのみ（同名ディレクトリを日記ファイルと誤認しないよう除外）。
        tree()?.listFiles().orEmpty().filter { it.isFile }.mapNotNull { it.name }
    }

    override suspend fun delete(fileName: String): Boolean = withContext(Dispatchers.IO) {
        tree()?.findFile(fileName)?.delete() ?: false
    }

    private companion object {
        const val MIME_MARKDOWN = "text/markdown"
    }
}
