package com.miguellbr.coucou.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.miguellbr.coucou.network.CoucouClient
import com.miguellbr.coucou.network.RelayDiscovery
import com.miguellbr.coucou.notifications.ApprovalNotification
import kotlinx.coroutines.*

class CoucouService : Service() {
    companion object {
        const val CHANNEL_ID = "connection"
        const val NOTIFICATION_ID = 900
        const val ACTION_START = "com.miguellbr.coucou.START"
        const val ACTION_STOP = "com.miguellbr.coucou.STOP"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val discovery = RelayDiscovery()
    private var job: Job? = null
    private val notified = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Procurando o Mac…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive != true) startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        job = scope.launch {
            while (isActive) {
                val prefs = getSharedPreferences("coucou", MODE_PRIVATE)
                var baseUrl = prefs.getString("baseUrl", null)
                var token = prefs.getString("token", null)

                if (baseUrl == null || token == null) {
                    val candidate = discovery.discover()
                    if (candidate != null) {
                        baseUrl = "http://" + candidate.host + ":" + candidate.port
                        token = candidate.token
                        prefs.edit().putString("baseUrl", baseUrl).putString("token", token).apply()
                    }
                }

                if (baseUrl != null && token != null) {
                    val client = CoucouClient(baseUrl, token)
                    runCatching { client.sessions() }.onSuccess { handleSessions(it) }
                    val streamed = runCatching {
                        client.streamSessions { sessions ->
                            handleSessions(sessions)
                            startForeground(NOTIFICATION_ID, notification("Conectado ao Mac"))
                        }
                    }
                    if (streamed.isFailure) {
                        startForeground(NOTIFICATION_ID, notification("Mac desconectado, tentando reconectar…"))
                    }
                }
                delay(1000)
            }
        }
    }

    private fun handleSessions(sessions: List<com.miguellbr.coucou.model.Session>) {
        startForeground(NOTIFICATION_ID, notification("Conectado ao Mac"))
        val active = sessions.flatMap { listOf(it.approvalFingerprint, "question:" + it.questionFingerprint) }.toSet()

        sessions.filter { it.needsAnswer && it.questionFingerprint.isNotBlank() }.forEach { session ->
            if (notified.add("question:" + session.questionFingerprint)) {
                ApprovalNotification.showQuestion(
                    this,
                    session.name,
                    session.questionFingerprint,
                    session.questionPayload?.items?.firstOrNull()?.question
                        ?: "O Coucou precisa de uma resposta."
                )
            }
        }

        sessions.filter { it.needsApproval && it.approvalFingerprint.isNotBlank() }.forEach { session ->
            if (notified.add(session.approvalFingerprint)) {
                ApprovalNotification.show(
                    this,
                    session.name,
                    session.approvalFingerprint,
                    session.finalLine.ifBlank { "Uma ação precisa da sua aprovação." }
                )
            }
        }
        notified.retainAll(active)
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Conexão do Coucou", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Coucou")
            .setContentText(text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
