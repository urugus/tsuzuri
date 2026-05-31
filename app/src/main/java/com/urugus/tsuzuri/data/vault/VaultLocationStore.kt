package com.urugus.tsuzuri.data.vault

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** ユーザーが選んだ vault フォルダ(ツリーUri)を永続化する。 */
@Singleton
class VaultLocationStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var treeUri: Uri?
        get() = prefs.getString(KEY_TREE_URI, null)?.let(Uri::parse)
        set(value) = prefs.edit().putString(KEY_TREE_URI, value?.toString()).apply()

    private companion object {
        const val PREFS = "vault_prefs"
        const val KEY_TREE_URI = "tree_uri"
    }
}
