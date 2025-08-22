package com.example.cellularsocks.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object LogBus {
    private val _flow = MutableSharedFlow<String>(extraBufferCapacity = 1024)
    val flow = _flow.asSharedFlow()
    fun post(msg: String) { _flow.tryEmit(msg) }
} 