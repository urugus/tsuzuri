package com.urugus.tsuzuri.core.llm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** LLMの動作設定（オンデバイス利用フラグ等）。 */
@Singleton
class LlmSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var useOnDevice: Boolean
        get() = prefs.getBoolean(KEY_USE_ON_DEVICE, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_ON_DEVICE, value).apply()

    private companion object {
        const val PREFS = "llm_prefs"
        const val KEY_USE_ON_DEVICE = "use_on_device"
    }
}
