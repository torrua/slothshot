package com.slothshot.app.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

class QuickActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val payload = intent.getStringExtra(EXTRA_PAYLOAD) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SlothShot", payload)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(context, "Copied: $payload", Toast.LENGTH_SHORT).show()

        if (notifId != -1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notifId)
        }
    }

    companion object {
        const val ACTION_COPY = "com.slothshot.app.ACTION_COPY"
        const val EXTRA_PAYLOAD = "extra_payload"
        const val EXTRA_NOTIFICATION_ID = "extra_notif_id"
    }
}
