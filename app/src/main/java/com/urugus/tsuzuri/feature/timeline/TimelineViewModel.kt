package com.urugus.tsuzuri.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.urugus.tsuzuri.data.vault.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TimelineUiState(
    val days: List<LocalDate> = emptyList(),
    val vaultConfigured: Boolean = false,
    val loading: Boolean = false,
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val vault: VaultManager,
) : ViewModel() {

    private val _state = MutableStateFlow(TimelineUiState())
    val state: StateFlow<TimelineUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, vaultConfigured = vault.isConfigured) }
            try {
                val days = vault.repository()?.listDays().orEmpty().sortedDescending()
                _state.update { it.copy(days = days) }
            } catch (_: Exception) {
                _state.update { it.copy(days = emptyList()) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
