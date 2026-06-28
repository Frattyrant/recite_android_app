package com.miearn.app.domain

enum class LearningPhase {
    REVIEW,
    BROWSE,
    CONSOLIDATE,
    REINFORCEMENT,
    COMPLETE,
}

data class LearningSession(
    val reviewIds: List<String>,
    val newIds: List<String>,
    val reinforcementIds: List<String>,
    val phase: LearningPhase,
    val index: Int,
    val completedNew: Int,
    val completedReview: Int,
    val correctFirstTry: Int,
    val answeredFirstTry: Int,
    val cardExpanded: Boolean,
    val pendingFirstCorrect: Boolean?,
) {
    val currentId: String?
        get() = when (phase) {
            LearningPhase.REVIEW -> reviewIds.getOrNull(index)
            LearningPhase.BROWSE, LearningPhase.CONSOLIDATE -> newIds.getOrNull(index)
            LearningPhase.REINFORCEMENT -> reinforcementIds.getOrNull(index)
            LearningPhase.COMPLETE -> null
        }

    val isComplete: Boolean
        get() = phase == LearningPhase.COMPLETE

    val phaseTotal: Int
        get() = when (phase) {
            LearningPhase.REVIEW -> reviewIds.size
            LearningPhase.BROWSE, LearningPhase.CONSOLIDATE -> newIds.size
            LearningPhase.REINFORCEMENT -> reinforcementIds.size
            LearningPhase.COMPLETE -> 0
        }

    fun submitAnswer(firstCorrect: Boolean): LearningSession {
        if (
            phase !in ANSWER_PHASES ||
            currentId == null ||
            pendingFirstCorrect != null
        ) {
            return this
        }
        val firstTry = phase != LearningPhase.REINFORCEMENT
        val nextReinforcement = if (!firstCorrect && currentId !in reinforcementIds) {
            reinforcementIds + checkNotNull(currentId)
        } else {
            reinforcementIds
        }
        return copy(
            reinforcementIds = nextReinforcement,
            completedNew = completedNew +
                if (firstTry && phase == LearningPhase.CONSOLIDATE) 1 else 0,
            completedReview = completedReview +
                if (firstTry && phase == LearningPhase.REVIEW) 1 else 0,
            correctFirstTry = correctFirstTry +
                if (firstTry && firstCorrect) 1 else 0,
            answeredFirstTry = answeredFirstTry + if (firstTry) 1 else 0,
            pendingFirstCorrect = firstCorrect,
        )
    }

    fun continueAfterAnswer(): LearningSession {
        if (phase !in ANSWER_PHASES || pendingFirstCorrect == null) return this
        return when (phase) {
            LearningPhase.REVIEW -> {
                if (index + 1 < reviewIds.size) {
                    copy(index = index + 1, pendingFirstCorrect = null)
                } else {
                    transitionAfterReview()
                }
            }

            LearningPhase.CONSOLIDATE -> {
                if (index + 1 < newIds.size) {
                    copy(index = index + 1, pendingFirstCorrect = null)
                } else {
                    transitionToReinforcementOrComplete()
                }
            }

            LearningPhase.REINFORCEMENT -> {
                if (index + 1 < reinforcementIds.size) {
                    copy(index = index + 1, pendingFirstCorrect = null)
                } else {
                    complete()
                }
            }

            else -> this
        }
    }

    fun nextBrowse(): LearningSession {
        if (phase != LearningPhase.BROWSE || newIds.isEmpty()) return this
        return if (index + 1 < newIds.size) {
            copy(index = index + 1, cardExpanded = false)
        } else {
            copy(
                phase = LearningPhase.CONSOLIDATE,
                index = 0,
                cardExpanded = false,
                pendingFirstCorrect = null,
            )
        }
    }

    fun previousBrowse(): LearningSession =
        if (phase == LearningPhase.BROWSE && index > 0) {
            copy(index = index - 1, cardExpanded = false)
        } else {
            this
        }

    fun browseTo(targetIndex: Int): LearningSession =
        if (phase == LearningPhase.BROWSE && targetIndex in newIds.indices) {
            copy(index = targetIndex, cardExpanded = false)
        } else {
            this
        }

    fun toggleCardExpanded(): LearningSession =
        if (phase == LearningPhase.BROWSE) {
            copy(cardExpanded = !cardExpanded)
        } else {
            this
        }

    private fun transitionAfterReview(): LearningSession =
        if (newIds.isNotEmpty()) {
            copy(
                phase = LearningPhase.BROWSE,
                index = 0,
                cardExpanded = false,
                pendingFirstCorrect = null,
            )
        } else {
            transitionToReinforcementOrComplete()
        }

    private fun transitionToReinforcementOrComplete(): LearningSession =
        if (reinforcementIds.isNotEmpty()) {
            copy(
                phase = LearningPhase.REINFORCEMENT,
                index = 0,
                cardExpanded = false,
                pendingFirstCorrect = null,
            )
        } else {
            complete()
        }

    private fun complete(): LearningSession =
        copy(
            phase = LearningPhase.COMPLETE,
            index = 0,
            cardExpanded = false,
            pendingFirstCorrect = null,
        )

    companion object {
        private val ANSWER_PHASES = setOf(
            LearningPhase.REVIEW,
            LearningPhase.CONSOLIDATE,
            LearningPhase.REINFORCEMENT,
        )

        fun start(
            reviewIds: List<String>,
            newIds: List<String>,
        ): LearningSession {
            val phase = when {
                reviewIds.isNotEmpty() -> LearningPhase.REVIEW
                newIds.isNotEmpty() -> LearningPhase.BROWSE
                else -> LearningPhase.COMPLETE
            }
            return LearningSession(
                reviewIds = reviewIds,
                newIds = newIds,
                reinforcementIds = emptyList(),
                phase = phase,
                index = 0,
                completedNew = 0,
                completedReview = 0,
                correctFirstTry = 0,
                answeredFirstTry = 0,
                cardExpanded = false,
                pendingFirstCorrect = null,
            )
        }
    }
}
