// android/app/src/main/java/com/zapretmobile/service/ZapretVpnService.kt
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
import java.io.File
import java.util.concurrent.TimeUnit

class ZapretVpnService : VpnService() {
    companion object { private const val TAG = "ZapretVpnService" }

    private var tunFd: ParcelFileDescriptor? = null
    private var proxyProcess: Process? = null
    private var singProcess: Process? = null
    private var isRunning = false
    private var currentStrategyId: String = StrategyProvider.getDefaultStrategy().id

    override fun onCreate() {
        super.onCreate()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationManager.createNotificationChannel(this)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) return START_STICKY
        
        currentStrategyId = intent?.getStringExtra(Constants.EXTRA_STRATEGY_ID)
            ?: StrategyProvider.getPreferredStrategy(this)
        
        Log.i(TAG, "Starting: strategy=$currentStrategyId")

        // 🔥 Тест подключения перед стартом
        if (!ConnectionTester.testConnectivity()) {
            Log.w(TAG, "⚠️ No internet connectivity, but starting anyway...")
        }

        startForeground(
            Constants.NOTIFICATION_ID_VPN_ACTIVE,
            NotificationManager.buildActiveVpnNotification(
                this, StrategyProvider.getStrategy(currentStrategyId)?.displayName ?: currentStrategyId
            )
        )

        // Запуск в фоне
        Thread {
            try {
                startVpnStack()
                isRunning = true
                Log.i(TAG, "✅ VPN stack started")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed: ${e.message}", e)
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

        // 3. Запуск dpi-proxy с тестовой стратегией
        startDpiProxy()
        Thread.sleep(1000) // Ждём инициализации

        // 4. Запуск sing-box
        startSingBox()
        Log.i(TAG, "🎉 All components started")

        // 5. Пост-старт тест
        if (ConnectionTester.testWithProxy()) {
            Log.i(TAG, "✅ Proxy connectivity verified")
        } else {
            Log.w(TAG, "⚠️ Proxy may not be working correctly")
        }
    }

    private fun startDpiProxy() {
        val proxyPath = BinaryHandler.getBinaryPath(this, Constants.FILE_NAME_DPI_PROXY)
            ?: throw IllegalStateException("dpi-proxy not found")
        
        val args = StrategyProvider.buildProxyArgs(currentStrategyId, filesDir.absolutePath)
        val cmd = mutableListOf(proxyPath) + args + listOf("--test=false") // Отключаем внутренний тест
        
        Log.d(TAG, "Starting proxy: ${cmd.joinToString(" ")}")
        
        proxyProcess = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
        
        // Логи в фоне
        Thread {
            proxyProcess?.inputStream?.bufferedReader()?.forEachLine { line ->
                when {
                    line.contains("error", true) -> Log.e(TAG, "Proxy: $line")
                    line.contains("Connected via strategy", true) -> Log.i(TAG, "Proxy: $line")
                    else -> Log.d(TAG, "Proxy: $line")
                }
            }
        }.start()
    }

    private fun startSingBox() {
        val singPath = BinaryHandler.getBinaryPath(this, Constants.FILE_NAME_SING_BOX)
            ?: throw IllegalStateException("sing-box not found")
        val confPath = BinaryHandler.getBinaryPath(this, Constants.FILE_NAME_SING_BOX_CONFIG)
            ?: throw IllegalStateException("config not found")
        
        val cmd = arrayOf(singPath, "run", "-c", confPath)
        Log.d(TAG, "Starting sing-box: ${cmd.joinToString(" ")}")
        
        singProcess = ProcessBuilder(*cmd)
            .redirectErrorStream(true)
            .start()
        
        Thread {
            singProcess?.inputStream?.bufferedReader()?.forEachLine { line ->
                if (line.contains("error", true) || line.contains("fatal", true)) {
                    Log.e(TAG, "sing-box: $line")
                } else {
                    Log.d(TAG, "sing-box: $line")
                }
            }
        }.start()
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
        singProcess?.destroy(); proxyProcess?.destroy(); tunFd?.close()
        runCatching {
            proxyProcess?.waitFor(2000, TimeUnit.MILLISECONDS)
            singProcess?.waitFor(2000, TimeUnit.MILLISECONDS)
        }
        NotificationManager.hideAllNotifications(this)
        Log.d(TAG, "Service stopped")
    }

    override fun onRevoke() { stopSelf() }
    override fun onBind(intent: Intent?): IBinder? = null
}
