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
    val error: String? = null,
)

@HiltViewModel
class DayDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vault: VaultManager,
    private val provider: LlmProvider,
) : ViewModel() {

    // 不正な引数でも落ちないよう防御（通常のナビ遷移では常に妥当なISO日付）。
    val date: LocalDate = runCatching { LocalDate.parse(savedStateHandle.get<String>(ARG_DATE)) }
        .getOrElse { LocalDate.now() }

    private val _state = MutableStateFlow(DayDetailUiState())
    val state: StateFlow<DayDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val doc = vault.repository()?.loadDay(date)
                val events = doc?.events().orEmpty()
                    .sortedWith(compareBy({ it.time ?: LocalTime.MAX }, { it.createdAt }, { it.id }))
                _state.update { it.copy(events = events, rawMarkdown = doc?.render().orEmpty(), error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "読み込みに失敗しました: ${e.message ?: "不明なエラー"}") }
            }
        }
    }

    /** その日の出来事をAI(現状はStub)で日記文章に再構成する。 */
    fun reconstruct() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val text = provider.reconstruct(_state.value.events, date)
                _state.update { it.copy(reconstructed = text) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "再構成に失敗しました: ${e.message ?: "不明なエラー"}") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun toggleRaw() = _state.update { it.copy(showRaw = !it.showRaw) }

    companion object {
        const val ARG_DATE = "date"
    }
}
