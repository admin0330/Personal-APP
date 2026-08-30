package com.masteralanlab.emailbox.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object NavigationSettings {
    var order by mutableStateOf(listOf("mail", "ledger", "notes"))
        private set

    fun load() { order = Prefs.navOrder }

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
