package com.zapretmobile.core

import android.content.Context

data class Strategy(val id: String, val name: String, val desc: String, val args: List<String>)

object StrategyProvider {
    val ALL = listOf(
        Strategy("mixed", "🎲 Mixed (Recommended)", "Комбинация методов", listOf("--strategy=mixed")),
        Strategy("split-sni", "✂️ Split SNI", "Разделение на границе SNI", listOf("--strategy=split-sni")),
        Strategy("fake-tls", "🎭 Fake TLS", "Подделка TLS handshake", listOf("--strategy=fake-tls")),
        Strategy("padding", "📦 Padding", "Случайный паддинг", listOf("--strategy=padding")),
        Strategy("none", "❌ No Bypass", "Прямое подключение", emptyList())
    )

    fun getDefault() = ALL.first()
    fun get(id: String) = ALL.find { it.id == id } ?: getDefault()

    fun buildArgs(id: String): List<String> {
        val s = get(id)
        return mutableListOf("--port=1080", "--log=false") + s.args
    }

    fun save(context: Context, id: String) {
        context.getSharedPreferences("zapret", Context.MODE_PRIVATE).edit().putString("strategy", id).apply()
    }

    fun load(context: Context) = context.getSharedPreferences("zapret", Context.MODE_PRIVATE).getString("strategy", getDefault().id) ?: getDefault().id
}
