package com.miguellbr.coucou

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.miguellbr.coucou.notifications.ApprovalNotification
import com.miguellbr.coucou.service.CoucouService
import com.miguellbr.coucou.ui.CoucouScreen

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApprovalNotification.ensureChannel(this)

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        ContextCompat.startForegroundService(
            this,
            Intent(this, CoucouService::class.java).setAction(CoucouService.ACTION_START)
        )

        setContent {
            MaterialTheme {
                Surface { CoucouScreen() }
            }
        }
    }
}
