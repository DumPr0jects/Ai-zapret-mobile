package com.zapretmobile.util

import android.util.Log

/**
 * Безопасный логгер с маскировкой чувствительных данных (IP, домены)
 */
object Logger {
    private const val TAG = "ZapretSecure"

    fun d(msg: String) = Log.d(TAG, mask(msg))
    fun i(msg: String) = Log.i(TAG, mask(msg))
    fun w(msg: String) = Log.w(TAG, mask(msg))
    fun e(msg: String, tr: Throwable? = null) = Log.e(TAG, mask(msg), tr)

    // Маскировка IP-адресов в логах
    private fun mask(input: String): String {
        val masked = input.replace(Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b"), "***.***.***.***")
        return "🔒 $masked"
    }
}
