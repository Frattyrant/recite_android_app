package com.miearn.app.audio

import java.util.concurrent.atomic.AtomicBoolean

fun interface OwnerTaskDispatcher {
    fun post(task: () -> Unit): Boolean
}

class OwnerTaskQueue(
    private val dispatcher: OwnerTaskDispatcher,
) {
    private val closed = AtomicBoolean(false)

    val isClosed: Boolean
        get() = closed.get()

    fun submit(task: () -> Unit): Boolean {
        if (closed.get()) {
            return false
        }
        return dispatcher.post {
            if (!closed.get()) {
                task()
            }
        }
    }

    fun close(cleanup: () -> Unit): Boolean {
        if (!closed.compareAndSet(false, true)) {
            return false
        }
        return dispatcher.post(cleanup)
    }
}
