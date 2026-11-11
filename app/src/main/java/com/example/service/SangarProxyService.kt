package com.example.service

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
import com.example.MainActivity
import com.example.R
import com.example.data.LogManager
import com.example.data.ProfileRepository
import com.example.model.ProxyProfile
import com.example.proxy.SangarSocksServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SangarProxyService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var socksServer: SangarSocksServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sangar:ProxyWakeLock").apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopProxy()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWithNotification()
        startProxy()

        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification("Starting Sangar Proxy...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startProxy() {
        val repository = ProfileRepository.getInstance(applicationContext)
        val profile = repository.getActiveProfile()

        if (profile == null) {
            LogManager.error("No active profile configured to start proxy.")
            stopSelf()
            return
        }

        socksServer?.stop()
        val server = SangarSocksServer(profile, serviceScope)
        socksServer = server

        val started = server.start()
        if (started) {
            wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours max
            _isRunningFlow.value = true
            _runningProfileFlow.value = profile

            // Collect connections and traffic stats
            serviceScope.launch {
                server.tunnelManager.connectionsFlow.collect { count ->
                    _activeConnectionsFlow.value = count
                    updateNotification(profile, count)
                }
            }
            serviceScope.launch {
                server.tunnelManager.uploadBytesFlow.collect { bytes ->
                    _uploadBytesFlow.value = bytes
                }
            }
            serviceScope.launch {
                server.tunnelManager.downloadBytesFlow.collect { bytes ->
                    _downloadBytesFlow.value = bytes
                }
            }
            updateNotification(profile, 0)
        } else {
            stopSelf()
        }
    }

    private fun stopProxy() {
        socksServer?.stop()
        socksServer = null
        _isRunningFlow.value = false
        _runningProfileFlow.value = null
        _activeConnectionsFlow.value = 0

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (ignored: Exception) {}
    }

    private fun updateNotification(profile: ProxyProfile, activeConnections: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val text = "Listening on 127.0.0.1:${profile.localPort} • $activeConnections active tunnels"
        val notification = buildNotification(text, profile.name)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(contentText: String, titleSuffix: String = ""): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SangarProxyService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (titleSuffix.isNotBlank()) "Sangar • $titleSuffix" else "Sangar SOCKS5"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sangar Proxy Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active Sangar SOCKS5 tunnel status and controls"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopProxy()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.sangar.ACTION_START"
        const val ACTION_STOP = "com.example.sangar.ACTION_STOP"
        private const val CHANNEL_ID = "sangar_proxy_channel"
        private const val NOTIFICATION_ID = 40446

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        private val _runningProfileFlow = MutableStateFlow<ProxyProfile?>(null)
        val runningProfileFlow: StateFlow<ProxyProfile?> = _runningProfileFlow.asStateFlow()

        private val _activeConnectionsFlow = MutableStateFlow(0)
        val activeConnectionsFlow: StateFlow<Int> = _activeConnectionsFlow.asStateFlow()

        private val _uploadBytesFlow = MutableStateFlow(0L)
        val uploadBytesFlow: StateFlow<Long> = _uploadBytesFlow.asStateFlow()

        private val _downloadBytesFlow = MutableStateFlow(0L)
        val downloadBytesFlow: StateFlow<Long> = _downloadBytesFlow.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, SangarProxyService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SangarProxyService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
