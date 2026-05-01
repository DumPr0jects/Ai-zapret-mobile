package com.zapretmobile.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.zapretmobile.util.Constants
import com.zapretmobile.ui.MainActivity

object NotificationManager {
    private const val CHANNEL_ID = "zapret_vpn"
    const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Zapret VPN", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun buildActive(context: Context, strategy: String): android.app.Notification {
        val intent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("🔒 Zapret Active")
            .setContentText("Strategy: $strategy")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    fun buildError(context: Context, msg: String): android.app.Notification {
        val intent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("⚠️ Zapret Error")
            .setContentText(msg)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
    }

    fun show(context: Context, id: Int, notification: android.app.Notification) {
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    fun hideAll(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }
}
