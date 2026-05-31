package com.urugus.tsuzuri.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.urugus.tsuzuri.data.vault.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val folderName: String? = null,
    val dayCount: Int = 0,
    val recentDates: List<String> = emptyList(),
    val loading: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val vault: VaultManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onFolderSelected(uri: Uri) {
        vault.setVaultFolder(uri)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val days = vault.repository()?.listDays().orEmpty()
            _state.value = SettingsUiState(
                folderName = vault.displayName(),
                dayCount = days.size,
                recentDates = days.takeLast(5).reversed().map { it.toString() },
                loading = false,
            )
        }
    }
}
