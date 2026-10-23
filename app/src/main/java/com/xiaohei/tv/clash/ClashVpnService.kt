package com.xiaohei.tv.clash

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.github.kr328.clash.core.Clash
import com.xiaohei.tv.MainActivity
import com.xiaohei.tv.R
import com.xiaohei.tv.XiaoheiApp
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking

class ClashVpnService : VpnService() {
    private val running = AtomicBoolean(false)
    private var tunnelFd: ParcelFileDescriptor? = null
    private val controller: ClashController
        get() = (application as XiaoheiApp).container.clash

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat(buildNotification(getString(R.string.vpn_notification_loading)))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            stopVpn()
            return START_NOT_STICKY
        }

        startForegroundCompat(buildNotification(getString(R.string.vpn_notification_loading)))

        if (!running.compareAndSet(false, true)) return START_STICKY

        thread(name = "clash-tun") {
            try {
                runBlocking { controller.ensureProfileLoaded() }
                val builder = Builder()
                    .addAddress(TUN_GATEWAY, TUN_SUBNET_PREFIX)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer(TUN_PORTAL)
                    .setBlocking(false)
                    .setMtu(TUN_MTU)
                    .setSession("XiaoheiTV")
                    .setConfigureIntent(
                        PendingIntent.getActivity(
                            this,
                            1,
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    )
                if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false)

                val pfd = builder.establish()
                    ?: throw IllegalStateException("VPN 权限未授予，请在设置中允许")
                tunnelFd = pfd

                val connectivity = getSystemService(ConnectivityManager::class.java)
                Clash.startTun(
                    fd = pfd.detachFd(),
                    stack = TUN_STACK,
                    gateway = "$TUN_GATEWAY/$TUN_SUBNET_PREFIX",
                    portal = TUN_PORTAL,
                    dns = TUN_PORTAL,
                    markSocket = { fd -> protect(fd) },
                    querySocketUid = { protocol, source: InetSocketAddress, target: InetSocketAddress ->
                        if (Build.VERSION.SDK_INT < 29) -1
                        else runCatching {
                            connectivity?.getConnectionOwnerUid(protocol, source, target) ?: -1
                        }.getOrDefault(-1)
                    },
                )

                controller.onVpnRunning()
                notify(buildNotification(getString(R.string.vpn_notification_running)))
                Log.i(TAG, "VPN started")
            } catch (error: Exception) {
                Log.e(TAG, "VPN start failed", error)
                controller.onVpnStopped(error.message ?: "VPN 启动失败")
                running.set(false)
                closeTunnel()
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onRevoke() {
        controller.onVpnStopped("VPN 权限已被系统收回")
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        running.set(false)
        closeTunnel()
        controller.onVpnStopped(null)
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        Log.i(TAG, "VPN service destroyed")
        super.onDestroy()
    }

    private fun stopVpn() {
        running.set(false)
        closeTunnel()
        controller.onVpnStopped(null)
        stopSelf()
    }

    private fun closeTunnel() {
        runCatching { Clash.stopTun() }
        runCatching { tunnelFd?.close() }
        tunnelFd = null
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notify(notification: Notification) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, ClashVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.vpn_stop), stopIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.vpn_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setShowBadge(false)
        manager?.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_CONNECT = "com.xiaohei.tv.clash.CONNECT"
        const val ACTION_DISCONNECT = "com.xiaohei.tv.clash.DISCONNECT"
        private const val CHANNEL_ID = "clash_vpn"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "ClashVpnService"
        private const val TUN_STACK = "mips"
        private const val TUN_MTU = 9000
        private const val TUN_SUBNET_PREFIX = 30
        private const val TUN_GATEWAY = "172.19.0.1"
        private const val TUN_PORTAL = "172.19.0.2"
    }
}
