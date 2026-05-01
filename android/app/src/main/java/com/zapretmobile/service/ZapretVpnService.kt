package com.zapretmobile.service

import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.zapretmobile.core.BinaryHandler
import com.zapretmobile.core.ConnectionTester
import com.zapretmobile.core.NotificationManager
import com.zapretmobile.core.StrategyProvider
import com.zapretmobile.util.Constants
import java.util.concurrent.TimeUnit

class ZapretVpnService : VpnService() {
    companion object {
        private const val TAG = "ZapretVpnService"
    }

    private var tunFd: ParcelFileDescriptor? = null
    private var proxyProcess: Process? = null
    private var singProcess: Process? = null
    private var isRunning = false
    private var currentStrategyId: String = StrategyProvider.getDefault().id

    override fun onCreate() {
        super.onCreate()
        NotificationManager.createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) return START_STICKY
        
        currentStrategyId = intent?.getStringExtra(Constants.EXTRA_STRATEGY_ID)
            ?: StrategyProvider.getPreferredStrategy(this)
        
        startForeground(
            Constants.NOTIFICATION_ID_VPN_ACTIVE,
            NotificationManager.buildActiveVpnNotification(
                this,
                StrategyProvider.get(currentStrategyId)?.displayName ?: currentStrategyId
            )
        )

        Thread {
            try {
                startVpnStack()
                isRunning = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed: ${e.message}", e)
                showUserError("Start failed: ${e.message}")
                stopSelf()
            }
        }.start()

        return START_STICKY
    }

    private fun startVpnStack() {
        if (!BinaryHandler.verifyAll(this)) {
            if (!BinaryHandler.extractAll(this)) {
                throw IllegalStateException("Binary extraction failed")
            }
        }

        tunFd = Builder()
            .addAddress(Constants.VPN_ADDRESS, Constants.VPN_PREFIX_LENGTH)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(Constants.DNS_SERVER_PRIMARY)
            .setMtu(Constants.VPN_MTU)
            .setBlocking(true)
            .setSession("Zapret")
            .addDisallowedApplication(packageName)
            .establish() ?: throw IllegalStateException("TUN creation failed")

        startDpiProxy()
        Thread.sleep(1000)
        startSingBox()
    }

    private fun startDpiProxy() {
        val proxyPath = BinaryHandler.getBinaryPath(this, Constants.FILE_NAME_DPI_PROXY)
            ?: throw IllegalStateException("dpi-proxy not found")
        val args = StrategyProvider.buildProxyArgs(currentStrategyId, filesDir.absolutePath)
        val cmd = (listOf(proxyPath) + args).toTypedArray()
        
        proxyProcess = ProcessBuilder(*cmd)
            .redirectErrorStream(true)
            .start()
    }

    private fun startSingBox() {
        val singPath = BinaryHandler.getBinaryPath(this, Constants.FILE_NAME_SING_BOX)
            ?: throw IllegalStateException("sing-box not found")
        val confPath = BinaryHandler.getBinaryPath(this, Constants.FILE_NAME_SING_BOX_CONFIG)
            ?: throw IllegalStateException("config not found")
        val cmd = arrayOf(singPath, "run", "-c", confPath)
        
        singProcess = ProcessBuilder(*cmd)
            .redirectErrorStream(true)
            .start()
    }

    private fun showUserError(msg: String) {
        NotificationManager.showNotification(
            this,
            Constants.NOTIFICATION_ID_ERROR,
            NotificationManager.buildErrorNotification(this, msg)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        singProcess?.destroy()
        proxyProcess?.destroy()
        tunFd?.close()
        NotificationManager.hideAllNotifications(this)
    }

    override fun onRevoke() { stopSelf() }
    override fun onBind(intent: Intent?): IBinder? = null
}
