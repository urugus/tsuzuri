package com.urugus.tsuzuri.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.urugus.tsuzuri.core.llm.LlmSettings
import com.urugus.tsuzuri.core.llm.ModelStore
import com.urugus.tsuzuri.data.vault.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val folderName: String? = null,
    val dayCount: Int = 0,
    val recentDates: List<String> = emptyList(),
    val loading: Boolean = false,
    // オンデバイスLLM
    val modelAvailable: Boolean = false,
    val useOnDevice: Boolean = false,
    val importingModel: Boolean = false,
    val notice: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val vault: VaultManager,
    private val modelStore: ModelStore,
    private val llmSettings: LlmSettings,
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

    fun onToggleOnDevice(enabled: Boolean) {
        llmSettings.useOnDevice = enabled
        _state.update { it.copy(useOnDevice = enabled) }
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
                    useOnDevice = llmSettings.useOnDevice,
                )
            }
        }
    }
}
