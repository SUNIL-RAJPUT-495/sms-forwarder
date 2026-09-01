package com.smsforwarder.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.app.data.repository.MessageRepository
import com.smsforwarder.app.domain.model.InboundMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val messages: List<InboundMessage> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val uiState: StateFlow<HistoryUiState> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                messageRepository.allInboundMessages
            } else {
                messageRepository.searchInboundMessages(query)
            }
        }
        .map { messages -> HistoryUiState(messages = messages, searchQuery = _searchQuery.value) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun markCopied(messageId: String) {
        viewModelScope.launch {
            messageRepository.markMessageCopied(messageId)
        }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            messageRepository.deleteInboundMessage(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            messageRepository.clearAllInboundMessages()
        }
    }
}
