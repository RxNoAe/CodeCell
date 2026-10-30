package dev.codecell.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dev.codecell.app.MainActivity
import dev.codecell.app.R
import dev.codecell.app.data.AppConfig
import dev.codecell.app.data.LocalServer
import dev.codecell.app.data.OpencodeClient
import java.io.File
import java.net.URI

class ServerService : Service() {

    @Volatile
    private var stopping = false

    @Volatile
    private var workspace = File("")

    @Volatile
    private var password = ""

    @Volatile
    private var port = DEFAULT_PORT

    private var monitor: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var lastStartAt = 0L

    @Volatile
    private var healthFailures = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForegroundCompat()

        if (intent?.action == ACTION_STOP) {
            stopping = true
            monitor?.interrupt()
            monitor = null
            releaseWakeLock()
            LocalServer.stop()
            stopSelf()
            return START_NOT_STICKY
        }

        stopping = false
        acquireWakeLock()

        val config = AppConfig(this)
        workspace = File(
            intent?.getStringExtra(EXTRA_WORKSPACE)
                ?: config.workspacePath,
        )
        password = intent?.getStringExtra(EXTRA_PASSWORD)
            ?: config.serverPassword
        val configuredPort = portOf(config.serverUrl)
        port = intent?.getIntExtra(EXTRA_PORT, configuredPort) ?: configuredPort
        OpencodeClient.setCredentials("opencode", password)

        lastStartAt = System.currentTimeMillis()
        if (!LocalServer.isRunning()) {
            Thread {
                LocalServer.start(this, workspace, password, port)
            }.start()
        }
        startMonitor()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (stopping) return
        acquireWakeLock()
        startMonitor()
        runCatching { startForegroundCompat() }
    }

    override fun onDestroy() {
        stopping = true
        monitor?.interrupt()
        monitor = null
        releaseWakeLock()
        LocalServer.stop()
        super.onDestroy()
    }

    private fun startMonitor() {
        if (monitor?.isAlive == true) return
        monitor = Thread {
            while (!stopping) {
                try {
                    Thread.sleep(5_000)
                } catch (e: InterruptedException) {
                    return@Thread
                }
                if (stopping) return@Thread
                if (System.currentTimeMillis() - lastStartAt < 15_000) continue
                if (!LocalServer.isRunning()) {
                    healthFailures = 0
                    restartServer()
                    continue
                }
                if (isServerHealthy()) {
                    healthFailures = 0
                } else if (++healthFailures >= 2) {
                    healthFailures = 0
                    LocalServer.stop()
                    restartServer()
                }
            }
        }.apply {
            name = "opencode-watchdog"
            isDaemon = true
            start()
        }
    }

    private fun restartServer() {
        lastStartAt = System.currentTimeMillis()
        val started = LocalServer.start(
            this@ServerService,
            workspace,
            password,
            port,
        )
        if (!started) {
            try {
                Thread.sleep(10_000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun isServerHealthy(): Boolean = runCatching {
        OpencodeClient.health("http://127.0.0.1:$port", timeoutMs = 3_000)
    }.isSuccess

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CodeCell::opencode",
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun portOf(url: String): Int = runCatching {
        URI(url.trim()).port.takeIf { it > 0 } ?: DEFAULT_PORT
    }.getOrDefault(DEFAULT_PORT)

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("CodeCell")
            .setContentText("opencode is running")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "opencode server",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps the embedded opencode server running"
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val ACTION_STOP = "dev.codecell.app.STOP_SERVER"
        const val EXTRA_WORKSPACE = "workspace"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_PORT = "port"
        const val DEFAULT_PORT = 4096
        private const val CHANNEL_ID = "opencode"
        private const val NOTIFICATION_ID = 41

        fun start(context: Context, workspace: File, password: String, port: Int) {
            val intent = Intent(context, ServerService::class.java).apply {
                putExtra(EXTRA_WORKSPACE, workspace.absolutePath)
                putExtra(EXTRA_PASSWORD, password)
                putExtra(EXTRA_PORT, port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ServerService::class.java))
        }
    }
}
