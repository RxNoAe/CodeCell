package dev.codecell.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.codecell.app.MainActivity
import dev.codecell.app.R
import java.io.File

class NotifyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val resultPath = intent.getStringExtra(EXTRA_RESULT)
        val title = intent.getStringExtra(EXTRA_TITLE)?.trim().orEmpty().ifBlank { "CodeCell" }
        val text = intent.getStringExtra(EXTRA_TEXT)?.trim().orEmpty()
            .ifBlank { "Notification from opencode" }
        val tag = intent.getStringExtra(EXTRA_TAG)?.trim().orEmpty().ifBlank { "codecell" }

        try {
            val manager = context.getSystemService(NotificationManager::class.java)
                ?: throw IllegalStateException("NotificationManager unavailable")
            if (!manager.areNotificationsEnabled()) {
                writeResult(
                    resultPath,
                    "error: notifications are disabled for ${context.packageName}; " +
                        "enable Settings > Apps > CodeCell > Notifications",
                )
                return
            }

            createChannel(manager)
            val channel = manager.getNotificationChannel(CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                writeResult(
                    resultPath,
                    "error: notification channel $CHANNEL_ID is blocked; " +
                        "enable Settings > Apps > CodeCell > Notifications",
                )
                return
            }

            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            manager.notify(tag, (System.currentTimeMillis() and 0x7fffffffL).toInt(), notification)
            writeResult(resultPath, "ok")
        } catch (e: Exception) {
            writeResult(resultPath, "error: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun createChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Agent alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications requested by opencode tools"
            },
        )
    }

    private fun writeResult(path: String?, value: String) {
        if (path.isNullOrBlank()) return
        val target = runCatching { File(path).canonicalFile }.getOrNull() ?: return
        val workspace = runCatching { WORKSPACE.canonicalFile }.getOrNull() ?: return
        if (!target.path.startsWith(workspace.path + File.separator)) return
        if (!target.name.startsWith(".notify-result.")) return
        runCatching { target.writeText(value) }
    }

    companion object {
        const val ACTION_NOTIFY = "dev.codecell.app.NOTIFY"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_TAG = "tag"
        const val EXTRA_RESULT = "result"
        private const val CHANNEL_ID = "codecell_alerts"
        private const val WORKSPACE = "/storage/emulated/0/CodeCell/workspace"
    }
}
