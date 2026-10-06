package com.miguellbr.coucou.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.miguellbr.coucou.network.CoucouClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ApprovalActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = app.getSharedPreferences("coucou", Context.MODE_PRIVATE)
                val baseUrl = prefs.getString("baseUrl", null)
                val token = prefs.getString("token", null)
                val fingerprint = intent.getStringExtra(ApprovalNotification.EXTRA_FINGERPRINT)

                if (baseUrl != null && token != null && fingerprint != null) {
                    val decision = when (intent.action) {
                        ApprovalNotification.ACTION_APPROVE -> "allow"
                        ApprovalNotification.ACTION_DENY -> "deny"
                        else -> null
                    }
                    if (decision != null) {
                        CoucouClient(baseUrl, token).approval(fingerprint, decision)
                        ApprovalNotification.cancel(app, fingerprint)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
