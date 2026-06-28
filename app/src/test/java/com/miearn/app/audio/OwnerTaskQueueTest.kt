package com.miearn.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class OwnerTaskQueueTest {
    @Test
    fun workIsDispatchedAndNeverRunsOnTheCallingThreadInline() {
        val dispatcher = QueuedDispatcher()
        val owner = OwnerTaskQueue(dispatcher)
        val events = mutableListOf<String>()

        assertTrue(owner.submit { events += "owner-work" })

        assertEquals(emptyList<String>(), events)
        dispatcher.runNext()
        assertEquals(listOf("owner-work"), events)
    }

    @Test
    fun closeDropsQueuedAndFutureWorkButRunsCleanupExactlyOnce() {
        val dispatcher = QueuedDispatcher()
        val owner = OwnerTaskQueue(dispatcher)
        val events = mutableListOf<String>()
        owner.submit { events += "stale-work" }

        assertTrue(owner.close { events += "cleanup" })
        assertFalse(owner.close { events += "duplicate-cleanup" })
        assertFalse(owner.submit { events += "late-work" })
        dispatcher.runAll()

        assertEquals(listOf("cleanup"), events)
        assertTrue(owner.isClosed)
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
}
