package com.urugus.tsuzuri.data.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * vault フォルダの選択状態と、そこに紐づく [DiaryRepository] の提供を担う。
 * フォルダUriはランタイムでユーザーがSAFで選ぶため、Hiltで直接 storage を注入せずここで生成する。
 */
@Singleton
class VaultManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val locationStore: VaultLocationStore,
) {
    val vaultUri: Uri? get() = locationStore.treeUri

    val isConfigured: Boolean get() = locationStore.treeUri != null

    /** SAFで選んだフォルダを永続権限付きで記録する。 */
    fun setVaultFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        locationStore.treeUri = uri
    }

    /** 未設定なら null。 */
    fun repository(): DiaryRepository? =
        locationStore.treeUri?.let { DiaryRepository(SafVaultStorage(context, it)) }

    /** 表示用のフォルダ名（取得できなければ Uri の末尾）。 */
    fun displayName(): String? = locationStore.treeUri?.let { uri ->
        DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment
    }
}
