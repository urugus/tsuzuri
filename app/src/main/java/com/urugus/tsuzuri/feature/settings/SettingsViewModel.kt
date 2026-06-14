package com.urugus.tsuzuri.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.urugus.tsuzuri.core.llm.CloudCredentialStore
import com.urugus.tsuzuri.core.llm.LlmProviderMode
import com.urugus.tsuzuri.core.llm.LlmSettings
import com.urugus.tsuzuri.core.llm.ModelStore
import com.urugus.tsuzuri.data.vault.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val folderName: String? = null,
    val dayCount: Int = 0,
    val recentDates: List<String> = emptyList(),
    val loading: Boolean = false,
    // LLM
    val modelAvailable: Boolean = false,
    val providerMode: LlmProviderMode = LlmProviderMode.STUB,
    val importingModel: Boolean = false,
    val cloudApiKeySaved: Boolean = false,
    val cloudApiKeyInput: String = "",
    val cloudModel: String = "",
    val notice: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val vault: VaultManager,
    private val modelStore: ModelStore,
    private val llmSettings: LlmSettings,
    private val credentials: CloudCredentialStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onFolderSelected(uri: Uri) {
        val ok = vault.setVaultFolder(uri)
        if (!ok) {
            _state.update { it.copy(notice = "フォルダの永続アクセス権を取得できませんでした") }
        }
        refresh()
    }

    fun onModelSelected(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(importingModel = true) }
            val ok = try {
                modelStore.importFrom(uri)
            } catch (_: Exception) {
                false
            } finally {
                _state.update { it.copy(importingModel = false) }
            }
            _state.update {
                it.copy(notice = if (ok) "モデルを読み込みました" else "モデルの読み込みに失敗しました")
            }
            refresh()
        }
    }

    fun onProviderModeSelected(mode: LlmProviderMode) {
        val nextMode = when {
            mode == LlmProviderMode.ON_DEVICE && !modelStore.isAvailable() -> LlmProviderMode.STUB
            mode == LlmProviderMode.CLOUD && !credentials.hasApiKey() -> LlmProviderMode.STUB
            else -> mode
        }
        llmSettings.providerMode = nextMode
        _state.update {
            it.copy(
                providerMode = nextMode,
                notice = when {
                    mode == LlmProviderMode.ON_DEVICE && nextMode != mode ->
                        "先にモデルファイル(.task)を読み込んでください"
                    mode == LlmProviderMode.CLOUD && nextMode != mode ->
                        "先にクラウドAIのAPIキーを保存してください"
                    else -> it.notice
                },
            )
        }
    }

    fun onCloudApiKeyInputChange(value: String) = _state.update { it.copy(cloudApiKeyInput = value) }

    fun onCloudModelChange(value: String) = _state.update { it.copy(cloudModel = value) }

    fun saveCloudSettings() {
        val apiKey = _state.value.cloudApiKeyInput.trim()
        val model = _state.value.cloudModel.trim()
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    if (model.isNotEmpty()) {
                        llmSettings.cloudModel = model
                    }
                    if (apiKey.isNotEmpty()) {
                        credentials.saveApiKey(apiKey)
                    }
                    llmSettings.cloudModel to credentials.hasApiKey()
                }
            }
            result.fold(
                onSuccess = { (cloudModel, cloudApiKeySaved) ->
                    _state.update {
                        it.copy(
                            cloudApiKeyInput = "",
                            cloudApiKeySaved = cloudApiKeySaved,
                            cloudModel = cloudModel,
                            notice = if (apiKey.isNotEmpty()) "クラウドAI設定を保存しました" else "クラウドAIモデルを保存しました",
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(notice = "クラウドAI設定の保存に失敗しました: ${e.message ?: "不明なエラー"}")
                    }
                },
            )
        }
    }

    fun clearCloudApiKey() {
        credentials.clearApiKey()
        if (llmSettings.providerMode == LlmProviderMode.CLOUD) {
            llmSettings.providerMode = LlmProviderMode.STUB
        }
        _state.update {
            it.copy(
                cloudApiKeyInput = "",
                cloudApiKeySaved = false,
                providerMode = llmSettings.providerMode,
                notice = "クラウドAIのAPIキーを削除しました",
            )
        }
    }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val days = try {
                vault.repository()?.listDays().orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
            _state.update {
                it.copy(
                    folderName = vault.displayName(),
                    dayCount = days.size,
                    recentDates = days.takeLast(5).reversed().map { d -> d.toString() },
                    loading = false,
                    modelAvailable = modelStore.isAvailable(),
                    providerMode = llmSettings.providerMode,
                    cloudApiKeySaved = credentials.hasApiKey(),
                    cloudModel = llmSettings.cloudModel,
                )
            }
        }
    }
}
