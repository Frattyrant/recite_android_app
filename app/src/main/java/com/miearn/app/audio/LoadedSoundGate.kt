package com.miearn.app.audio

internal class LoadedSoundGate(
    private val playLoaded: (Int) -> Unit,
) {
    private val loaded = mutableSetOf<Int>()
    private val pending = ArrayDeque<Int>()
    private var closed = false

    @Synchronized
    fun request(soundId: Int) {
        if (closed) return
        if (soundId in loaded) {
            playLoaded(soundId)
        } else {
            pending += soundId
        }
    }

    @Synchronized
    fun onLoadComplete(soundId: Int, status: Int) {
        if (closed) return
        if (status != 0) {
            pending.removeAll { it == soundId }
            return
        }
        loaded += soundId
        val ready = pending.filter { it == soundId }
        pending.removeAll { it == soundId }
        ready.forEach { playLoaded(it) }
    }

    @Synchronized
    fun close() {
        closed = true
        pending.clear()
        loaded.clear()
    }
}
