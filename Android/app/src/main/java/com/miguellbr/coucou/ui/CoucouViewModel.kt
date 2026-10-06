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
    private val notified = mutableSetOf<String>()

    fun discoverAndConnect() {
        viewModelScope.launch {
            val candidate = discovery.discover() ?: return@launch
            connect("http://${candidate.host}:${candidate.port}", candidate.token)
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
                result.onSuccess { updateSessions(it) }
                delay(2000)
            }
        }
    }

    private fun updateSessions(value: List<Session>) {
        _sessions.value = value
        value.filter { it.needsApproval && it.approvalFingerprint.isNotBlank() }.forEach { session ->
            if (notified.add(session.approvalFingerprint)) {
                ApprovalNotification.show(
                    getApplication(),
                    session.name,
                    session.approvalFingerprint,
                    session.finalLine.ifBlank { "Uma ação precisa da sua aprovação." }
                )
            }
        }
        val active = value.map { it.approvalFingerprint }.toSet()
        notified.retainAll(active)
    }

    fun approve(session: Session) {
        viewModelScope.launch {
            if (client?.approval(session.approvalFingerprint, "allow") == true) {
                ApprovalNotification.cancel(getApplication(), session.approvalFingerprint)
                notified.remove(session.approvalFingerprint)
            }
        }
    }

    fun deny(session: Session) {
        viewModelScope.launch {
            if (client?.approval(session.approvalFingerprint, "deny") == true) {
                ApprovalNotification.cancel(getApplication(), session.approvalFingerprint)
                notified.remove(session.approvalFingerprint)
            }
        }
    }
}
