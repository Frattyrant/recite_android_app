package com.miearn.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningSessionTest {
    @Test
    fun sessionRunsReviewThenBrowseThenConsolidate() {
        var state = LearningSession.start(
            reviewIds = listOf("r1"),
            newIds = listOf("n1", "n2"),
        )

        assertEquals(LearningPhase.REVIEW, state.phase)
        assertEquals("r1", state.currentId)

        state = state.submitAnswer(firstCorrect = true)
        assertEquals(true, state.pendingFirstCorrect)
        state = state.continueAfterAnswer()

        assertEquals(LearningPhase.BROWSE, state.phase)
        assertEquals("n1", state.currentId)

        state = state.nextBrowse()
        assertEquals("n2", state.currentId)
        state = state.nextBrowse()

        assertEquals(LearningPhase.CONSOLIDATE, state.phase)
        assertEquals("n1", state.currentId)
    }

    @Test
    fun wrongFirstAnswerAppearsOnceAtEndAndIsCountedOnce() {
        var state = LearningSession.start(
            reviewIds = listOf("r1"),
            newIds = emptyList(),
        )

        state = state.submitAnswer(firstCorrect = false)
        assertEquals(listOf("r1"), state.reinforcementIds)
        assertEquals(1, state.answeredFirstTry)
        assertEquals(0, state.correctFirstTry)
        assertEquals(1, state.completedReview)

        val duplicate = state.submitAnswer(firstCorrect = true)
        assertEquals(state, duplicate)

        state = state.continueAfterAnswer()
        assertEquals(LearningPhase.REINFORCEMENT, state.phase)
        assertEquals("r1", state.currentId)

        state = state.submitAnswer(firstCorrect = false)
        assertEquals(1, state.answeredFirstTry)
        assertEquals(1, state.completedReview)
        assertEquals(listOf("r1"), state.reinforcementIds)

        state = state.continueAfterAnswer()
        assertEquals(LearningPhase.COMPLETE, state.phase)
        assertNull(state.currentId)
    }

    @Test
    fun consolidationWrongAnswerCountsAsNewAndQueuesReinforcement() {
        var state = LearningSession.start(emptyList(), listOf("n1"))
        assertEquals(LearningPhase.BROWSE, state.phase)
        state = state.nextBrowse()
        assertEquals(LearningPhase.CONSOLIDATE, state.phase)

        state = state.submitAnswer(firstCorrect = false)

        assertEquals(1, state.completedNew)
        assertEquals(1, state.answeredFirstTry)
        assertEquals(listOf("n1"), state.reinforcementIds)
    }

    @Test
    fun browseSupportsBackAndForthWithoutCompletingEarly() {
        var state = LearningSession.start(emptyList(), listOf("n1", "n2", "n3"))
        state = state.nextBrowse()
        state = state.previousBrowse()

        assertEquals(LearningPhase.BROWSE, state.phase)
        assertEquals("n1", state.currentId)
        assertEquals(0, state.index)

        state = state.browseTo(2)
        assertEquals("n3", state.currentId)
        state = state.nextBrowse()
        assertEquals(LearningPhase.CONSOLIDATE, state.phase)
    }

    @Test
    fun expansionOnlyChangesDuringBrowse() {
        var state = LearningSession.start(listOf("r1"), listOf("n1"))
        assertFalse(state.cardExpanded)
        assertEquals(state, state.toggleCardExpanded())

        state = state.submitAnswer(true).continueAfterAnswer()
        state = state.toggleCardExpanded()
        assertTrue(state.cardExpanded)
        state = state.nextBrowse()
        assertFalse(state.cardExpanded)
        assertEquals(state, state.toggleCardExpanded())
    }

    @Test
    fun emptySessionIsComplete() {
        val state = LearningSession.start(emptyList(), emptyList())

        assertEquals(LearningPhase.COMPLETE, state.phase)
        assertNull(state.currentId)
        assertTrue(state.isComplete)
    }

    @Test
    fun reinforcementDeduplicatesIds() {
        var state = LearningSession.start(listOf("same"), listOf("same"))
        state = state.submitAnswer(false).continueAfterAnswer()
        state = state.nextBrowse()
        state = state.submitAnswer(false)

        assertEquals(listOf("same"), state.reinforcementIds)
    }
}
