package com.zapretmobile.core

import android.content.Context
import android.util.Log
import java.io.File

object BinaryHandler {
    private const val TAG = "BinaryHandler"
    private val BINARIES = listOf("dpi-proxy", "sing-box", "sing-box.json")

    fun extractAll(context: Context): Boolean {
        return try {
            val destDir = context.filesDir
            BINARIES.forEach { name ->
                val target = File(destDir, name)
                if (target.exists() && target.length() > 100) return@forEach
                context.assets.open(name).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                if (name != "sing-box.json") target.setExecutable(true)
                Log.d(TAG, "Extracted: $name")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed", e)
            false
        }
    }

    fun verifyAll(context: Context): Boolean {
        val dir = context.filesDir
        return BINARIES.all { name ->
            val f = File(dir, name)
            f.exists() && f.canRead() && (name == "sing-box.json" || f.canExecute())
        }
    }

    fun clearAll(context: Context): Boolean {
        return try {
            BINARIES.forEach { File(context.filesDir, it).deleteRecursively() }
            true
        } catch (e: Exception) { false }
    }

    fun getPath(context: Context, name: String): String = File(context.filesDir, name).absolutePath
}
