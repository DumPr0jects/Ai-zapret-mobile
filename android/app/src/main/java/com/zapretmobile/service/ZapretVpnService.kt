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
    private var currentStrategyId: String = StrategyProvider.getDefaultStrategy().id

    override fun onCreate() {
        super.onCreate()
        NotificationManager.createNotificationChannel(this)
        Log.d(TAG, "Service onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")

        if (isRunning) {
            Log.w(TAG, "Service already running")
            return START_STICKY
        }

        currentStrategyId = intent?.getStringExtra(Constants.EXTRA_STRATEGY_ID)
            ?: StrategyProvider.getPreferredStrategy(this)

        Log.i(TAG, "Starting with strategy: $currentStrategyId")

        // Тест подключения (не блокирующий)
        if (!ConnectionTester.testConnectivity()) {
            Log.w(TAG, "No internet, but starting anyway...")
        }

        // Foreground notification
        startForeground(
            Constants.NOTIFICATION_ID_VPN_ACTIVE,
            NotificationManager.buildActiveVpnNotification(
                this,
                StrategyProvider.getStrategy(currentStrategyId)?.displayName ?: currentStrategyId
            )
        )

        // Запуск в фоне
        Thread {
            try {
                startVpnStack()
                isRunning = true
                Log.i(TAG, "VPN stack started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start: ${e.message}", e)
                showUserError("Start failed: ${e.message}")
                stopSelf()
            }
        }.start()

        return START_STICKY
    }

    private fun startVpnStack() {
        // 1. Бинарники
        if (!BinaryHandler.verifyAll(this)) {
            if (!BinaryHandler.extractAll(this)) {
                throw IllegalStateException("Binary extraction failed")
            }
        }

        // 2. TUN интерфейс
        tunFd = Builder()
            .addAddress(Constants.VPN_ADDRESS, Constants.VPN_PREFIX_LENGTH)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(Constants.DNS_SERVER_PRIMARY)
            .setMtu(Constants.VPN_MTU)
            .setBlocking(true)
            .setSession("Zapret: ${StrategyProvider.getStrategy(currentStrategyId)?.displayName}")
            .addDisallowedApplication(packageName)
            .establish() ?: throw IllegalStateException("TUN creation failed")

        Log.d(TAG, "TUN interface created")

        // 3. Запуск dpi-proxy
        startDpiProxy()
        Thread.sleep(1000)

        // 4. Запуск sing-box
        startSingBox()
        Log.i(TAG, "All components started")

        // 5. Пост-старт тест
        if (ConnectionTester.testWithProxy()) {
            Log.i(TAG, "Proxy connectivity verified")
        } else {
            Log.w(TAG, "Proxy may not be working correctly")
        }
    }

    private fun startDpiProxy() {
        val proxyPath = BinaryHandler.getBinaryPath(this, Constants.FILE_NAME_DPI_PROXY)
            ?: throw IllegalStateException("dpi-proxy not found")

        val args = StrategyProvider.buildProxyArgs(currentStrategyId, filesDir.absolutePath)
        // 🔧 ИСПРАВЛЕНО: явное преобразование в массив для ProcessBuilder
        val cmd = (listOf(proxyPath) + args + listOf("--test=false")).toTypedArray()

        Log.d(TAG, "Starting proxy: ${cmd.joinToString(" ")}")

        proxyProcess = ProcessBuilder(*cmd)
            .redirectErrorStream(true)
            .start()

        // Чтение логов в фоне
        Thread {
            try {
                proxyProcess?.inputStream?.bufferedReader()?.forEachLine { line ->
                    when {
                        line.contains("error", true) -> Log.e(TAG, "Proxy: $line")
                        line.contains("Connected via strategy", true) -> Log.i(TAG, "Proxy: $line")
                        else -> Log.d(TAG, "Proxy: $line")
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun startSingBox() {
        val singPath = BinaryHandler.getBinaryPath(this, Constants.FILE_NAME_SING_BOX)
            ?: throw IllegalStateException("sing-box not found")
        val confPath = BinaryHandler.getBinaryPath(this, Constants.FILE_NAME_SING_BOX_CONFIG)
            ?: throw IllegalStateException("config not found")

        // 🔧 ИСПРАВЛЕНО: явный массив строк
        val cmd = arrayOf(singPath, "run", "-c", confPath)

        Log.d(TAG, "Starting sing-box: ${cmd.joinToString(" ")}")

        singProcess = ProcessBuilder(*cmd)
            .redirectErrorStream(true)
            .start()

        // Чтение логов в фоне
        Thread {
            try {
                singProcess?.inputStream?.bufferedReader()?.forEachLine { line ->
                    if (line.contains("error", true) || line.contains("fatal", true)) {
                        Log.e(TAG, "sing-box: $line")
                    } else {
                        Log.d(TAG, "sing-box: $line")
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun showUserError(msg: String) {
        try {
            val notification = NotificationManager.buildErrorNotification(this, msg ?: "Unknown error")
            NotificationManager.showNotification(this, Constants.NOTIFICATION_ID_ERROR, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show notification", e)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        super.onDestroy()
        stopVpnStack()
        NotificationManager.hideAllNotifications(this)
    }

    private fun stopVpnStack() {
        isRunning = false
        try { singProcess?.destroy() } catch (_: Exception) {}
        try { proxyProcess?.destroy() } catch (_: Exception) {}
        try { tunFd?.close() } catch (_: Exception) {}
        runCatching {
            proxyProcess?.waitFor(2000, TimeUnit.MILLISECONDS)
            singProcess?.waitFor(2000, TimeUnit.MILLISECONDS)
        }
        Log.d(TAG, "VPN stack stopped")
    }

    override fun onRevoke() {
        Log.w(TAG, "VPN permission revoked")
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
