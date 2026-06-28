package com.miearn.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.ArrayDeque

class OwnerTtsLifecycleTest {
    @Test
    fun immediateInitFailureIsHandledAfterResourceAssignmentThenDisposed() {
        val fixture = Fixture()

        fixture.owner.submit {
            fixture.lifecycle.start { callback ->
                callback(INIT_ERROR)
                fixture.resource
            }
        }
        fixture.dispatcher.runNext()

        assertEquals(fixture.resource, fixture.lifecycle.current)
        assertEquals(0, fixture.shutdownCount)
        assertEquals(0, fixture.unavailableCount)

        fixture.dispatcher.runNext()

        assertNull(fixture.lifecycle.current)
        assertEquals(1, fixture.shutdownCount)
        assertEquals(1, fixture.unavailableCount)
    }

    @Test
    fun configurationFailureDisposesAndReportsUnavailable() {
        val fixture = Fixture(configureResult = false)
        fixture.owner.submit {
            fixture.lifecycle.start { callback ->
                callback(INIT_SUCCESS)
                fixture.resource
            }
        }

        fixture.dispatcher.runAll()

        assertNull(fixture.lifecycle.current)
        assertEquals(1, fixture.shutdownCount)
        assertEquals(1, fixture.unavailableCount)
        assertEquals(0, fixture.readyCount)
    }

    @Test
    fun releaseBeforeLateInitCallbackDisposesOnceAndIgnoresCallback() {
        val fixture = Fixture()
        var initCallback: ((Int) -> Unit)? = null
        fixture.owner.submit {
            fixture.lifecycle.start { callback ->
                initCallback = callback
                fixture.resource
            }
        }
        fixture.dispatcher.runAll()

        fixture.owner.close {
            fixture.lifecycle.close()
        }
        initCallback?.invoke(INIT_ERROR)
        fixture.dispatcher.runAll()

        assertNull(fixture.lifecycle.current)
        assertEquals(1, fixture.shutdownCount)
        assertEquals(0, fixture.unavailableCount)
        assertEquals(0, fixture.readyCount)
    }

    private class Fixture(
        private val configureResult: Boolean = true,
    ) {
        val dispatcher = QueuedDispatcher()
        val owner = OwnerTaskQueue(dispatcher)
        val resource = FakeTts()
        var shutdownCount = 0
        var unavailableCount = 0
        var readyCount = 0
        val lifecycle = OwnerTtsLifecycle<FakeTts>(
            postToOwner = owner::submit,
            configure = { _, result -> result == INIT_SUCCESS && configureResult },
            shutdown = { shutdownCount += 1 },
            onReady = { readyCount += 1 },
            onUnavailable = { unavailableCount += 1 },
        )
    }

    private class QueuedDispatcher : OwnerTaskDispatcher {
        private val tasks = ArrayDeque<() -> Unit>()

        override fun post(task: () -> Unit): Boolean {
            tasks.addLast(task)
            return true
        }

        fun runNext() {
            tasks.removeFirst().invoke()
        }

        fun runAll() {
            while (tasks.isNotEmpty()) {
                runNext()
            }
        }
    }

    private class FakeTts

    private companion object {
        const val INIT_SUCCESS = 0
        const val INIT_ERROR = -1
    }
}
