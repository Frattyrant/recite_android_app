import Testing
import Foundation
@testable import MIearnCore

@Suite struct LearningSessionTests {
    @Test func reviewBrowseConsolidateOrderMatchesAndroid() {
        var state = LearningSession.start(reviewIds: ["r1"], newIds: ["n1", "n2"])
        #expect(state.phase == .review)
        #expect(state.currentId == "r1")

        state = state.submitAnswer(firstCorrect: true).continueAfterAnswer()
        #expect(state.phase == .browse)
        #expect(state.currentId == "n1")

        state = state.nextBrowse().nextBrowse()
        #expect(state.phase == .consolidate)
        #expect(state.currentId == "n1")
    }

    @Test func wrongAnswerQueuesOneReinforcementAndCountsOnlyFirstTry() {
        var state = LearningSession.start(reviewIds: ["r1"], newIds: [])
        state = state.submitAnswer(firstCorrect: false)
        #expect(state.reinforcementIds == ["r1"])
        #expect(state.answeredFirstTry == 1)
        #expect(state.completedReview == 1)

        state = state.continueAfterAnswer()
        #expect(state.phase == .reinforcement)
        state = state.submitAnswer(firstCorrect: false).continueAfterAnswer()
        #expect(state.reinforcementIds == ["r1"])
        #expect(state.answeredFirstTry == 1)
        #expect(state.phase == .complete)
    }

    @Test func browseNavigationAndExpansionMatchAndroid() {
        var state = LearningSession.start(reviewIds: [], newIds: ["n1", "n2", "n3"])
        state = state.nextBrowse().previousBrowse()
        #expect(state.currentId == "n1")
        state = state.browseTo(2).toggleCardExpanded()
        #expect(state.currentId == "n3")
        #expect(state.cardExpanded)
        state = state.nextBrowse()
        #expect(state.phase == .consolidate)
        #expect(!state.cardExpanded)
    }

    @Test func emptySessionIsComplete() {
        let state = LearningSession.start(reviewIds: [], newIds: [])
        #expect(state.isComplete)
        #expect(state.currentId == nil)
    }

    @Test func activeSessionCanBePersistedAndRestored() throws {
        let original = LearningSession.start(reviewIds: ["r1"], newIds: ["n1"])
            .submitAnswer(firstCorrect: false)
        let data = try JSONEncoder().encode(original)
        let restored = try JSONDecoder().decode(LearningSession.self, from: data)
        #expect(restored == original)
    }
}
