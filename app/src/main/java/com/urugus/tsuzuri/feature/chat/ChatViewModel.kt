package com.urugus.tsuzuri.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.urugus.tsuzuri.core.llm.ChatMessage
import com.urugus.tsuzuri.core.llm.ChatRole
import com.urugus.tsuzuri.core.llm.LlmProvider
import com.urugus.tsuzuri.data.vault.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val vaultConfigured: Boolean = false,
    val busy: Boolean = false,
    val notice: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val provider: LlmProvider,
    private val vault: VaultManager,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val greeting = provider.reply(emptyList())
            _state.update {
                it.copy(
                    messages = listOf(ChatMessage(ChatRole.ASSISTANT, greeting)),
                    vaultConfigured = vault.isConfigured,
                )
            }
        }
    }

    fun onInputChange(value: String) = _state.update { it.copy(input = value) }

    fun onSend() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.busy) return
        val afterUser = _state.value.messages + ChatMessage(ChatRole.USER, text)
        _state.update { it.copy(messages = afterUser, input = "", busy = true) }
        viewModelScope.launch {
            val reply = provider.reply(afterUser)
            _state.update {
                it.copy(messages = it.messages + ChatMessage(ChatRole.ASSISTANT, reply), busy = false)
            }
        }
    }

    /** 会話から出来事を抽出し、当日のvaultファイルに保存する。 */
    fun onSaveDay() {
        val repository = vault.repository()
        if (repository == null) {
            _state.update { it.copy(notice = "先に「設定」でVaultフォルダを選んでください") }
            return
        }
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val date = LocalDate.now(clock)
            val events = provider.extractEvents(_state.value.messages, date)
            events.forEach { repository.upsertEvent(it) }
            _state.update {
                it.copy(busy = false, notice = "${events.size}件の出来事を $date に保存しました")
            }
        }
    }

    fun onResume() = _state.update { it.copy(vaultConfigured = vault.isConfigured) }

    fun consumeNotice() = _state.update { it.copy(notice = null) }
}
