package com.miearn.app.audio

class OwnerTtsLifecycle<T>(
    private val postToOwner: (() -> Unit) -> Boolean,
    private val configure: (T, Int) -> Boolean,
    private val shutdown: (T) -> Unit,
    private val onReady: () -> Unit,
    private val onUnavailable: () -> Unit,
) {
    var current: T? = null
        private set

    private var generation = 0L
    private var closed = false

    fun start(factory: (((Int) -> Unit) -> T)) {
        if (closed) {
            return
        }
        generation += 1
        val attempt = generation
        current?.let(shutdown)
        current = null
        val created = try {
            factory { result ->
                postToOwner {
                    complete(attempt, result)
                }
            }
        } catch (_: RuntimeException) {
            onUnavailable()
            return
        }
        if (closed || generation != attempt) {
            shutdown(created)
        } else {
            current = created
        }
    }

    fun close() {
        if (closed) {
            return
        }
        closed = true
        generation += 1
        current?.let(shutdown)
        current = null
    }

    private fun complete(attempt: Long, result: Int) {
        if (closed || generation != attempt) {
            return
        }
        val active = current ?: return
        val configured = runCatching {
            configure(active, result)
        }.getOrDefault(false)
        if (configured) {
            onReady()
        } else {
            shutdown(active)
            current = null
            onUnavailable()
        }
    }
}
