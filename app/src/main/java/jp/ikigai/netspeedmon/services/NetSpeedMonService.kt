package jp.ikigai.netspeedmon.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.net.TrafficStats
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import jp.ikigai.netspeedmon.MainActivity
import jp.ikigai.netspeedmon.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class NetSpeedMonService : Service() {

    private val channelID = "netspeedmon_notification_channel"
    private val notificationID = 16249

    private val handler = Handler(Looper.getMainLooper())

    private var previousRxBytes: Long = 0
    private var previousTxBytes: Long = 0
    private var sessionStartRxBytes: Long = 0
    private var sessionStartTxBytes: Long = 0
    private val intervalMs: Long = 1000

    private val binder = LocalBinder()

    private val _serviceState = MutableStateFlow(false)
    val serviceState = _serviceState.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): NetSpeedMonService = this@NetSpeedMonService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            if (intent.action == "ACTION_STOP") {
                _serviceState.update { false }
                stopMon()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                start()
            }
        } else {
            start()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopMon()
        super.onDestroy()
    }

    private fun start() {
        if (!serviceState.value) {
            _serviceState.update { true }
            createNotificationChannels()
            createForegroundNotification()
            startMon()
        }
    }

    private fun startMon() {
        previousRxBytes = TrafficStats.getTotalRxBytes()
        previousTxBytes = TrafficStats.getTotalTxBytes()
        sessionStartRxBytes = TrafficStats.getTotalRxBytes()
        sessionStartTxBytes = TrafficStats.getTotalTxBytes()

        handler.postDelayed(calcSpeedRunnable, intervalMs)
    }

    private fun stopMon() {
        _serviceState.update { false }
        sessionStartRxBytes = 0
        sessionStartTxBytes = 0

        handler.removeCallbacks(calcSpeedRunnable)
    }

    private val calcSpeedRunnable = object : Runnable {
        override fun run() {
            val currentRxBytes = TrafficStats.getTotalRxBytes()
            val currentTxBytes = TrafficStats.getTotalTxBytes()

            val downloadSpeed = currentRxBytes - previousRxBytes
            val uploadSpeed = currentTxBytes - previousTxBytes

            val sessionDownload = currentRxBytes - sessionStartRxBytes
            val sessionUpload = currentTxBytes - sessionStartTxBytes

            updateNotification(
                totalSpeed = formatSpeedForIcon(downloadSpeed + uploadSpeed),
                downloadSpeed = formatSpeed(downloadSpeed),
                uploadSpeed = formatSpeed(uploadSpeed),
                sessionDownload = formatSessionUsage(sessionDownload),
                sessionUpload = formatSessionUsage(sessionUpload)
            )

            previousRxBytes = currentRxBytes
            previousTxBytes = currentTxBytes

            handler.postDelayed(this, intervalMs)
        }
    }

    private fun updateNotification(
        totalSpeed: Pair<String, String>,
        downloadSpeed: String,
        uploadSpeed: String,
        sessionDownload: String,
        sessionUpload: String
    ) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.notify(
            notificationID,
            createNotification(
                totalSpeed,
                downloadSpeed,
                uploadSpeed,
                sessionDownload,
                sessionUpload
            )
        )
    }

    private fun formatSpeedForIcon(speed: Long): Pair<String, String> {
        return when {
            speed < 1000 -> Pair("$speed", "B/s")
            speed in 1000..1023 -> {
                val speedInK = (speed / 1024.0).toString()
                val endIndex = speedInK.indexOf(".") + 2
                Pair(speedInK.take(endIndex.coerceAtMost(speedInK.length)), "K/s")
            }

            speed in 1024..(999 * 1024) -> {
                Pair("${speed / 1024}", "K/s")
            }

            speed in ((999 * 1024) + 1)..<(10 * 1024 * 1024) -> {
                val speedInM = (speed / (1024 * 1024 * 1.0)).toString()
                val endIndex = speedInM.indexOf(".") + 2
                Pair(speedInM.take(endIndex.coerceAtMost(speedInM.length)), "M/s")
            }

            else -> {
                Pair("${speed / (1024 * 1024)}", "M/s")
            }
        }
    }

    private fun formatSpeed(speed: Long): String {
        return when {
            speed < 1024 -> "$speed B/s"
            speed in 1024..<(1024 * 1024) -> {
                val speedInK = (speed / 1024.0).toString()
                val endIndex = speedInK.indexOf(".") + 3
                "${speedInK.take(endIndex.coerceAtMost(speedInK.length))} Kb/s"
            }

            else -> {
                val speedInM = (speed / (1024 * 1024 * 1.0)).toString()
                val endIndex = speedInM.indexOf(".") + 3
                "${speedInM.take(endIndex.coerceAtMost(speedInM.length))} Mb/s"
            }
        }
    }

    private fun formatSessionUsage(totalBytes: Long): String {
        return when {
            totalBytes < 1024 -> "$totalBytes B"
            totalBytes in 1024..<(1024 * 1024) -> {
                val totalBytesInK = (totalBytes / 1024.0).toString()
                val endIndex = totalBytesInK.indexOf(".") + 3
                "${totalBytesInK.take(endIndex.coerceAtMost(totalBytesInK.length))} KB"
            }

            totalBytes in (1024 * 1024)..<(1024 * 1024 * 1024) -> {
                val totalBytesInM = (totalBytes / (1024 * 1024 * 1.0)).toString()
                val endIndex = totalBytesInM.indexOf(".") + 3
                "${totalBytesInM.take(endIndex.coerceAtMost(totalBytesInM.length))} MB"
            }

            else -> {
                val totalBytesInG = (totalBytes / (1024 * 1024 * 1024 * 1.0)).toString()
                val endIndex = totalBytesInG.indexOf(".") + 3
                "${totalBytesInG.take(endIndex.coerceAtMost(totalBytesInG.length))} GB"
            }
        }
    }

    private fun createSpeedIcon(speed: Pair<String, String>): IconCompat {
        val bitmap = createBitmap(48, 48)
        val canvas = Canvas(bitmap)
        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 23f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawText(speed.first, 24f, 20f, numberPaint)
        canvas.drawText(speed.second, 24f, 46f, unitPaint)

        return IconCompat.createWithBitmap(bitmap)
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notificationChannel = NotificationChannel(
            channelID,
            "NetSpeedMon Notification",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationChannel.setSound(null, null)
        notificationChannel.enableVibration(false)
        notificationManager.createNotificationChannel(notificationChannel)
    }

    private fun createForegroundNotification() {
        startForeground(
            notificationID,
            createNotification(
                Pair("0", "Kb/s"),
                "0Kb/s",
                "0Kb/s",
                "0Kb",
                "0Kb"
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    private fun createNotification(
        totalSpeed: Pair<String, String>,
        downloadSpeed: String,
        uploadSpeed: String,
        sessionDownload: String,
        sessionUpload: String
    ): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val tapPendingIntent =
            PendingIntent.getActivity(this, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE)
        val stopActionIntent = Intent(this, NetSpeedMonService::class.java).apply {
            action = "ACTION_STOP"
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationBuilder = NotificationCompat.Builder(this, channelID)
            .setSmallIcon(createSpeedIcon(totalSpeed))
            .setOngoing(true)
            .setContentTitle("Down: $downloadSpeed  Up: $uploadSpeed")
            .setContentText("Down: $sessionDownload  Up: $sessionUpload")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tapPendingIntent)
            .setOnlyAlertOnce(true)
            .setAllowSystemGeneratedContextualActions(false)
            .addAction(R.drawable.outline_stop_circle_24, "Stop", stopPendingIntent)

        notificationBuilder.foregroundServiceBehavior = Notification.FOREGROUND_SERVICE_IMMEDIATE

        return notificationBuilder.build()
    }
}