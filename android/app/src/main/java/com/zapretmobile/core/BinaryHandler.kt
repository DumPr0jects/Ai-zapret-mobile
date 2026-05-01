package com.zapretmobile.core

import android.content.Context
import android.util.Log
import java.io.File

object BinaryHandler {
    private const val TAG = "BinaryHandler"
    
    fun verifyAll(context: Context): Boolean {
        val dir = context.filesDir
        return listOf("dpi-proxy", "sing-box", "sing-box.json").all { name ->
            val f = File(dir, name)
            f.exists() && f.canRead() && (name == "sing-box.json" || f.canExecute())
        }
    }

    fun extractAll(context: Context): Boolean {
        return try {
            val destDir = context.filesDir
            listOf("dpi-proxy", "sing-box", "sing-box.json").forEach { name ->
                val target = File(destDir, name)
                if (target.exists() && target.length() > 100) return@forEach
                context.assets.open(name).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                if (name != "sing-box.json") target.setExecutable(true)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed", e)
            false
        }
    }

    fun getBinaryPath(context: Context, name: String): String {
        return File(context.filesDir, name).absolutePath
    }

    fun clearAll(context: Context): Boolean {
        return try {
            listOf("dpi-proxy", "sing-box", "sing-box.json").forEach {
                File(context.filesDir, it).deleteRecursively()
            }
            true
        } catch (e: Exception) { false }
    }
}
