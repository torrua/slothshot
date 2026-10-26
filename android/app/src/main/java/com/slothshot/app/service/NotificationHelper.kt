package com.slothshot.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.slothshot.app.R
import com.slothshot.app.model.TriageItem
import com.slothshot.app.ui.MainActivity

object NotificationHelper {
    const val CHANNEL_MONITOR = "slothshot_monitor_channel"
    const val CHANNEL_TRIAGE = "slothshot_triage_channel"
    const val FOREGROUND_NOTIF_ID = 1001

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val monitorChannel = NotificationChannel(
                CHANNEL_MONITOR,
                "Background Screenshot Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows SlothShot background service status"
            }

            val triageChannel = NotificationChannel(
                CHANNEL_TRIAGE,
                "Screenshot Triage Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Instant notifications with extracted takeaways and smart actions"
                enableVibration(true)
            }

            nm.createNotificationChannel(monitorChannel)
            nm.createNotificationChannel(triageChannel)
        }
    }

    fun buildForegroundNotification(context: Context) =
        NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setContentTitle("SlothShot")
            .setContentText("Авто-разбор новых скриншотов")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()

    fun showTriageNotification(context: Context, item: TriageItem) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = item.id.hashCode()

        // Content intent: open app
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("selected_item_id", item.id)
        }
        val pendingContentIntent = PendingIntent.getActivity(
            context,
            notifId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_TRIAGE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${item.category.displayName}: ${item.title}")
            .setContentText(item.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (item.primaryActionPayload.isNotBlank()) "${item.summary}\n\n${item.primaryActionPayload}" else item.summary))
            .setContentIntent(pendingContentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Add Quick Action button if payload exists
        if (item.primaryActionPayload.isNotBlank()) {
            val copyIntent = Intent(context, QuickActionReceiver::class.java).apply {
                action = QuickActionReceiver.ACTION_COPY
                putExtra(QuickActionReceiver.EXTRA_PAYLOAD, item.primaryActionPayload)
                putExtra(QuickActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
            }
            val pendingCopy = PendingIntent.getBroadcast(
                context,
                notifId + 1,
                copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_notification,
                item.primaryActionLabel,
                pendingCopy
            )
        }

        // Add Open App action
        builder.addAction(
            R.drawable.ic_notification,
            "Открыть",
            pendingContentIntent
        )

        nm.notify(notifId, builder.build())
    }
}
