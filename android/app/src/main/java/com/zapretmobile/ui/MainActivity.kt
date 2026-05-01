package com.zapretmobile.ui

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.zapretmobile.R
import com.zapretmobile.core.BinaryHandler
import com.zapretmobile.core.NotificationManager
import com.zapretmobile.core.StrategyProvider
import com.zapretmobile.service.ZapretVpnService

class MainActivity : AppCompatActivity() {
    private var isRunning = false
    private lateinit var statusText: TextView
    private lateinit var spinner: Spinner
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnClear: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        spinner = findViewById(R.id.strategySpinner)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnClear = findViewById(R.id.btnClear)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, StrategyProvider.ALL.map { it.name })
        spinner.adapter = adapter
        spinner.setSelection(StrategyProvider.ALL.indexOfFirst { it.id == StrategyProvider.load(this) })

        btnStart.setOnClickListener { startVpn() }
        btnStop.setOnClickListener { stopVpn() }
        btnClear.setOnClickListener { clearData() }

        NotificationManager.createChannel(this)
        updateUI()
    }

    private fun startVpn() {
        if (isRunning) return
        if (!BinaryHandler.verifyAll(this)) {
            if (!BinaryHandler.extractAll(this)) {
                Toast.makeText(this, "❌ Binaries extraction failed", Toast.LENGTH_LONG).show()
                return
            }
        }
        val sel = StrategyProvider.ALL[spinner.selectedItemPosition]
        StrategyProvider.save(this, sel.id)
        val intent = Intent(this, ZapretVpnService::class.java).putExtra("STRATEGY_ID", sel.id)
        val vpnReq = VpnService.prepare(this)
        if (vpnReq != null) startActivityForResult(vpnReq, 1) else onVpnResult(RESULT_OK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) onVpnResult(resultCode)
    }

    private fun onVpnResult(code: Int) {
        if (code == RESULT_OK) {
            val sel = StrategyProvider.ALL[spinner.selectedItemPosition]
            val i = Intent(this, ZapretVpnService::class.java).putExtra("STRATEGY_ID", sel.id)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
            isRunning = true
            Toast.makeText(this, "▶ Started: ${sel.name}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "❌ VPN permission denied", Toast.LENGTH_SHORT).show()
        }
        updateUI()
    }

    private fun stopVpn() {
        stopService(Intent(this, ZapretVpnService::class.java))
        isRunning = false
        updateUI()
    }

    private fun clearData() {
        if (isRunning) stopVpn()
        if (BinaryHandler.clearAll(this)) Toast.makeText(this, "✅ Cleared", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun updateUI() {
        btnStart.isEnabled = !isRunning
        btnStop.isEnabled = isRunning
        spinner.isEnabled = !isRunning
        statusText.text = if (isRunning) "▶ Running" else "⏹ Ready"
        btnClear.isEnabled = BinaryHandler.verifyAll(this)
    }

    override fun onResume() { super.onResume(); updateUI() }
}
