package com.urugus.tsuzuri.core.llm

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * オンデバイスLLMのモデルファイル(.task/.bin)をアプリ内に保持する。
 *
 * コスト0方針のため、アプリ内ダウンロード(要認証)ではなく、ユーザーが端末に用意した
 * モデルファイルを SAF で取り込む方式にする（例: Kaggle/HuggingFace から取得した Gemma の .task）。
 */
@Singleton
class ModelStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }

    val modelFile: File = File(modelsDir, "ondevice-model.task")
    private val tempFile: File = File(modelsDir, "ondevice-model.task.tmp")

    fun isAvailable(): Boolean = modelFile.exists() && modelFile.length() > 0L

    /**
     * ユーザーが選んだモデルファイルをアプリ内にコピーする。成功で true。
     *
     * 一時ファイルにコピーし、完全に書けた場合のみ本ファイルへ置換する（アトミック）。
     * 途中で失敗しても既存の正常なモデルは壊さず、部分ファイルも残さない。
     */
    suspend fun importFrom(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            tempFile.delete()
            val wrote = context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
                tempFile.length() > 0L
            } ?: false
            if (!wrote) {
                tempFile.delete()
                return@runCatching false
            }
            // 成功時のみ本ファイルへ置換。
            if (!tempFile.renameTo(modelFile)) {
                tempFile.copyTo(modelFile, overwrite = true)
                tempFile.delete()
            }
            true
        }.getOrElse {
            tempFile.delete()
            false
        }
    }

    fun clear() {
        runCatching { modelFile.delete() }
    }
}
