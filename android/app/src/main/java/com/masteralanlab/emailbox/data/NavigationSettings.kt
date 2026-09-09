package com.masteralanlab.emailbox.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object NavigationSettings {
    var order by mutableStateOf(listOf("mail", "overview", "ledger", "notes"))
        private set

    fun load() {
        order = Prefs.navOrder
        Prefs.navOrder = order
    }

    fun move(id: String, delta: Int) {
        val next = order.toMutableList()
        val from = next.indexOf(id)
        val to = (from + delta).coerceIn(0, next.lastIndex)
        if (from < 0 || from == to) return
        next.add(to, next.removeAt(from))
        order = next
        Prefs.navOrder = next
    }
}

/** Preserve the user's order, migrate legacy IDs and append only missing destinations. */
internal fun normalizeNavOrder(saved: List<String>): List<String> {
    val defaults = listOf("mail", "overview", "ledger", "notes")
    return (saved.map { if (it == "tokens") "notes" else it }.filter { it in defaults } + defaults).distinct()
}
