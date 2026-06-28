package com.miearn.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class LoadedSoundGateTest {
    @Test
    fun requestBeforeLoadIsPlayedOnceAfterSuccessfulLoad() {
        val played = mutableListOf<Int>()
        val gate = LoadedSoundGate { played += it }

        gate.request(11)
        assertEquals(emptyList<Int>(), played)

        gate.onLoadComplete(soundId = 11, status = 0)

        assertEquals(listOf(11), played)
    }

    @Test
    fun loadedSoundPlaysImmediatelyAndFailedLoadDropsPendingRequest() {
        val played = mutableListOf<Int>()
        val gate = LoadedSoundGate { played += it }

        gate.onLoadComplete(soundId = 21, status = 0)
        gate.request(21)
        gate.request(22)
        gate.onLoadComplete(soundId = 22, status = -1)

        assertEquals(listOf(21), played)
    }

    @Test
    fun closeDropsPendingAndFutureRequests() {
        val played = mutableListOf<Int>()
        val gate = LoadedSoundGate { played += it }
        gate.request(31)

        gate.close()
        gate.onLoadComplete(soundId = 31, status = 0)
        gate.request(31)

        assertEquals(emptyList<Int>(), played)
    }
}
