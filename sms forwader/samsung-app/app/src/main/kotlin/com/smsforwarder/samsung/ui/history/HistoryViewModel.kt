package com.smsforwarder.samsung.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.samsung.data.local.dao.ForwardedMessageDao
import com.smsforwarder.samsung.data.local.entity.ForwardedMessageEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val messageDao: ForwardedMessageDao
) : ViewModel() {
    val messages: StateFlow<List<ForwardedMessageEntity>> =
        messageDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
