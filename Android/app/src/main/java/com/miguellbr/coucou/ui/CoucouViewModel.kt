package com.miguellbr.coucou.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miguellbr.coucou.model.Session
import com.miguellbr.coucou.network.CoucouClient
import com.miguellbr.coucou.network.RelayDiscovery
import com.miguellbr.coucou.notifications.ApprovalNotification
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CoucouViewModel(application: Application) : AndroidViewModel(application) {
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var client: CoucouClient? = null
    private var pollJob: Job? = null
    private val discovery = RelayDiscovery()
    private val prefs = application.getSharedPreferences("coucou", 0)

    fun discoverAndConnect() {
        viewModelScope.launch {
            val candidate = discovery.discover() ?: return@launch
            connect("http://" + candidate.host + ":" + candidate.port, candidate.token)
        }
    }

    fun connect(baseUrl: String, token: String) {
        pollJob?.cancel()
        val cleanUrl = baseUrl.trimEnd('/')
        val cleanToken = token.trim()
        client = CoucouClient(cleanUrl, cleanToken)
        prefs.edit().putString("baseUrl", cleanUrl).putString("token", cleanToken).apply()

        pollJob = viewModelScope.launch {
            while (true) {
                val result = runCatching { client!!.sessions() }
                _connected.value = result.isSuccess
                result.onSuccess { _sessions.value = it }
                delay(2000)
            }
        }
    }

    fun sendInstruction(session: Session, text: String) {
        viewModelScope.launch {
            if (!session.acceptsInstructions || text.isBlank()) return@launch
            client?.instruction(session.pillId, text.trim())
        }
    }

    fun answer(session: Session, selections: List<List<String>>) {
        viewModelScope.launch {
            val payload = session.questionPayload ?: return@launch
            if (payload.fingerprint != session.questionFingerprint || !payload.accepts(selections)) return@launch
            if (client?.answer(session.questionFingerprint, selections) == true) {
                ApprovalNotification.cancelQuestion(getApplication(), session.questionFingerprint)
                _sessions.value = _sessions.value.map {
                    if (it.pillId == session.pillId) it.copy(needsAnswer = false) else it
                }
            }
        }
    }

    fun approve(session: Session) {
        viewModelScope.launch {
            if (client?.approval(session.approvalFingerprint, "allow") == true) {
                ApprovalNotification.cancel(getApplication(), session.approvalFingerprint)
            }
        }
    }

    fun deny(session: Session) {
        viewModelScope.launch {
            if (client?.approval(session.approvalFingerprint, "deny") == true) {
                ApprovalNotification.cancel(getApplication(), session.approvalFingerprint)
            }
        }
    }
}
