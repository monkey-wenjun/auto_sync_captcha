package com.awen.pushmessage.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 简单的事件总线，用于应用内组件间通信
 * 替代已废弃的LocalBroadcastManager
 */
object EventBus {
    // SMS更新事件
    private val _smsUpdateEvent = MutableSharedFlow<Unit>(replay = 0)
    val smsUpdateEvent: SharedFlow<Unit> = _smsUpdateEvent.asSharedFlow()

    /**
     * 发送SMS更新事件
     */
    suspend fun postSmsUpdateEvent() {
        _smsUpdateEvent.emit(Unit)
    }
} 