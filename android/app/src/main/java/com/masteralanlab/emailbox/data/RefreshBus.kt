package com.masteralanlab.emailbox.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 详情页保存成功后通知列表页重新拉取。 */
object RefreshBus {

    private val _events = MutableSharedFlow<Kind>(extraBufferCapacity = 4)
    val events: SharedFlow<Kind> = _events.asSharedFlow()

    enum class Kind { Accounts, Groups }

    fun request(kind: Kind) {
        _events.tryEmit(kind)
    }
}
