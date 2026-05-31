package com.urugus.tsuzuri.feature.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.urugus.tsuzuri.core.llm.LlmProvider
import com.urugus.tsuzuri.core.model.Event
import com.urugus.tsuzuri.data.vault.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class DayDetailUiState(
    val events: List<Event> = emptyList(),
    val rawMarkdown: String = "",
    val reconstructed: String? = null,
    val showRaw: Boolean = false,
    val busy: Boolean = false,
)

@HiltViewModel
class DayDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vault: VaultManager,
    private val provider: LlmProvider,
) : ViewModel() {

    val date: LocalDate = LocalDate.parse(requireNotNull(savedStateHandle[ARG_DATE]) as String)

    private val _state = MutableStateFlow(DayDetailUiState())
    val state: StateFlow<DayDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val doc = vault.repository()?.loadDay(date)
            val events = doc?.events().orEmpty()
                .sortedWith(compareBy({ it.time ?: LocalTime.MAX }, { it.createdAt }, { it.id }))
            _state.update { it.copy(events = events, rawMarkdown = doc?.render().orEmpty()) }
        }
    }

    /** その日の出来事をAI(現状はStub)で日記文章に再構成する。 */
    fun reconstruct() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val text = provider.reconstruct(_state.value.events, date)
            _state.update { it.copy(busy = false, reconstructed = text) }
        }
    }

    fun toggleRaw() = _state.update { it.copy(showRaw = !it.showRaw) }

    companion object {
        const val ARG_DATE = "date"
    }
}
