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

    var providerMode: LlmProviderMode
        get() {
            val stored = prefs.getString(KEY_PROVIDER_MODE, null)
            if (stored != null) return LlmProviderMode.fromWire(stored)
            return if (prefs.getBoolean(KEY_USE_ON_DEVICE_LEGACY, false)) {
                LlmProviderMode.ON_DEVICE
            } else {
                LlmProviderMode.STUB
            }
        }
        set(value) = prefs.edit().putString(KEY_PROVIDER_MODE, value.wire).apply()

    @Deprecated("Use providerMode instead.")
    var useOnDevice: Boolean
        get() = providerMode == LlmProviderMode.ON_DEVICE
        set(value) {
            providerMode = if (value) LlmProviderMode.ON_DEVICE else LlmProviderMode.STUB
        }

    private companion object {
        const val PREFS = "llm_prefs"
        const val KEY_PROVIDER_MODE = "provider_mode"
        const val KEY_USE_ON_DEVICE_LEGACY = "use_on_device"
    }
}
