package com.miguellbr.coucou.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguellbr.coucou.model.Session
import com.miguellbr.coucou.network.CoucouClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CoucouViewModel : ViewModel() {
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var client: CoucouClient? = null
    private var pollJob: Job? = null

    fun connect(baseUrl: String, token: String) {
        pollJob?.cancel()
        client = CoucouClient(baseUrl.trimEnd('/'), token.trim())
        pollJob = viewModelScope.launch {
            while (true) {
                val result = runCatching { client!!.sessions() }
                _connected.value = result.isSuccess
                result.onSuccess { _sessions.value = it }
                delay(2000)
            }
        }
    }

    fun approve(session: Session) {
        viewModelScope.launch { client?.approval(session.approvalFingerprint, "allow") }
    }

    fun deny(session: Session) {
        viewModelScope.launch { client?.approval(session.approvalFingerprint, "deny") }
    }
}
