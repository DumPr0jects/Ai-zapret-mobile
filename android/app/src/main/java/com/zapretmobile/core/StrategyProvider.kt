package com.zapretmobile.core

import android.content.Context

data class Strategy(
    val id: String,
    val displayName: String,
    val description: String,
    val proxyArgs: List<String>
)

object StrategyProvider {
    // 🔧 ИСПРАВЛЕНО: публичное свойство (без private)
    val ALL = listOf(
        Strategy("mixed", "🎲 Mixed", "Комбинация методов", listOf("--strategy=mixed")),
        Strategy("split-sni", "✂️ Split SNI", "Разделение на границе SNI", listOf("--strategy=split-sni")),
        Strategy("fake-tls", "🎭 Fake TLS", "Подделка TLS handshake", listOf("--strategy=fake-tls")),
        Strategy("padding", "📦 Padding", "Случайный паддинг", listOf("--strategy=padding")),
        Strategy("none", "❌ No Bypass", "Прямое подключение", emptyList())
    )

    fun getDefault(): Strategy = ALL.first()
    fun get(id: String): Strategy? = ALL.find { it.id == id }

    fun getPreferredStrategy(context: Context): String {
        return context.getSharedPreferences("zapret_prefs", Context.MODE_PRIVATE)
            .getString("preferred_strategy", getDefault().id) ?: getDefault().id
    }

    fun buildProxyArgs(strategyId: String, assetsPath: String): List<String> {
        val strategy = get(strategyId) ?: getDefault()
        return mutableListOf("--port=1080", "--log=false") + strategy.proxyArgs
    }

    fun savePreferredStrategy(context: Context, id: String) {
        context.getSharedPreferences("zapret_prefs", Context.MODE_PRIVATE)
            .edit().putString("preferred_strategy", id).apply()
    }
}
