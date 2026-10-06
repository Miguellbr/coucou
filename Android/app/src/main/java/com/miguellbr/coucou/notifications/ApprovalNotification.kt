package com.miguellbr.coucou.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.miguellbr.coucou.MainActivity
import com.miguellbr.coucou.R

object ApprovalNotification {
    const val CHANNEL_ID = "approvals"
    const val ACTION_APPROVE = "com.miguellbr.coucou.APPROVE"
    const val ACTION_DENY = "com.miguellbr.coucou.DENY"
    const val EXTRA_FINGERPRINT = "fingerprint"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Aprovações do Coucou",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Pedidos de aprovação vindos do Mac" }
        )
    }

    fun show(context: Context, sessionName: String, fingerprint: String, command: String) {
        ensureChannel(context)
        val approve = action(context, ACTION_APPROVE, fingerprint, 1001)
        val deny = action(context, ACTION_DENY, fingerprint, 1002)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Coucou: aprovação necessária")
            .setContentText(sessionName)
            .setStyle(NotificationCompat.BigTextStyle().bigText(command))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(0, "Permitir", approve)
            .addAction(0, "Negar", deny)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 1000,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(fingerprint.hashCode(), notification)
    }

    fun cancel(context: Context, fingerprint: String) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(fingerprint.hashCode())
    }

    private fun action(context: Context, action: String, fingerprint: String, requestCode: Int) =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ApprovalActionReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_FINGERPRINT, fingerprint)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
