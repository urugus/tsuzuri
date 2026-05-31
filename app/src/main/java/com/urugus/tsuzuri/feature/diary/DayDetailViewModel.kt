package com.urugus.tsuzuri.feature.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.urugus.tsuzuri.core.llm.LlmProvider
import com.urugus.tsuzuri.core.model.Event
import com.urugus.tsuzuri.core.model.EventSource
import com.urugus.tsuzuri.data.vault.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** 出来事の追加/編集フォームの下書き。[id] が null なら新規。 */
data class EventDraft(
    val id: String? = null,
    val time: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = "",
)

data class DayDetailUiState(
    val events: List<Event> = emptyList(),
    val rawMarkdown: String = "",
    val reconstructed: String? = null,
    val showRaw: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val editing: EventDraft? = null,
)

@HiltViewModel
class DayDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vault: VaultManager,
    private val provider: LlmProvider,
    private val clock: Clock,
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

    // ---- 手動の出来事編集 ----------------------------------------------------

    fun startAdd() = _state.update { it.copy(editing = EventDraft()) }

    fun startEdit(event: Event) = _state.update {
        it.copy(
            editing = EventDraft(
                id = event.id,
                time = event.time?.format(TIME_FORMAT).orEmpty(),
                title = event.title,
                body = event.body,
                category = event.category.orEmpty(),
            ),
        )
    }

    fun updateDraft(draft: EventDraft) = _state.update { it.copy(editing = draft) }

    fun cancelEdit() = _state.update { it.copy(editing = null) }

    /** 下書きを検証して保存する。タイトル必須。時刻は "HH:mm" 任意。 */
    fun saveDraft() {
        val draft = _state.value.editing ?: return
        if (draft.title.isBlank()) {
            _state.update { it.copy(error = "タイトルを入力してください") }
            return
        }
        val time = draft.time.trim().takeIf { it.isNotEmpty() }?.let {
            runCatching { LocalTime.parse(it.padTimeHour()) }.getOrNull()
                ?: run {
                    _state.update { s -> s.copy(error = "時刻は HH:mm 形式で入力してください") }
                    return
                }
        }
        val existing = _state.value.events.firstOrNull { it.id == draft.id }
        val event = Event(
            id = draft.id ?: Event.newId(),
            date = date,
            time = time,
            title = draft.title.trim(),
            body = draft.body.trim(),
            category = draft.category.trim().takeIf { it.isNotEmpty() },
            location = existing?.location,
            source = existing?.source ?: EventSource.MANUAL,
            createdAt = existing?.createdAt ?: Instant.now(clock),
        )
        viewModelScope.launch {
            try {
                vault.repository()?.upsertEvent(event)
                    ?: run { _state.update { it.copy(error = "先に設定でVaultフォルダを選んでください") }; return@launch }
                _state.update { it.copy(editing = null, error = null) }
                load()
            } catch (e: Exception) {
                _state.update { it.copy(error = "保存に失敗しました: ${e.message ?: "不明なエラー"}") }
            }
        }
    }

    fun deleteEvent(id: String) {
        viewModelScope.launch {
            try {
                vault.repository()?.removeEvent(date, id)
                load()
            } catch (e: Exception) {
                _state.update { it.copy(error = "削除に失敗しました: ${e.message ?: "不明なエラー"}") }
            }
        }
    }

    companion object {
        const val ARG_DATE = "date"
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        private fun String.padTimeHour(): String {
            val parts = split(":")
            if (parts.size != 2) return this
            return parts[0].padStart(2, '0') + ":" + parts[1]
        }
    }
}
