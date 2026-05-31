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

    fun isAvailable(): Boolean = modelFile.exists() && modelFile.length() > 0L

    /** ユーザーが選んだモデルファイルをアプリ内にコピーする。成功で true。 */
    suspend fun importFrom(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                modelFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching false
            modelFile.length() > 0L
        }.getOrDefault(false)
    }

    fun clear() {
        runCatching { modelFile.delete() }
    }
}
