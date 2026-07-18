import Testing
@testable import MIearnCore

@Suite struct ReviewSchedulerTests {
    @Test func firstAndSecondSuccessMatchAndroidIntervals() throws {
        let first = try ReviewScheduler.grade(
            current: StudyProgress(),
            quality: 5,
            todayEpochDay: 100
        )
        #expect(first.repetitions == 1)
        #expect(first.intervalDays == 1)
        #expect(first.nextReviewEpochDay == 101)

        let second = try ReviewScheduler.grade(
            current: first,
            quality: 5,
            todayEpochDay: 101
        )
        #expect(second.repetitions == 2)
        #expect(second.intervalDays == 6)
        #expect(second.nextReviewEpochDay == 107)
    }

    @Test func wrongAnswerResetsAndClearsMastered() throws {
        let current = StudyProgress(
            repetitions: 4,
            lapseCount: 2,
            mastered: true
        )
        let result = try ReviewScheduler.grade(
            current: current,
            quality: 2,
            todayEpochDay: 200
        )

        #expect(result.repetitions == 0)
        #expect(result.intervalDays == 1)
        #expect(result.lapseCount == 3)
        #expect(!result.mastered)
    }

    @Test func masteryRequiresThirdSuccessAndTwentyOneDayInterval() throws {
        let current = StudyProgress(
            easeFactor: 2.5,
            intervalDays: 8,
            repetitions: 2
        )
        let result = try ReviewScheduler.grade(
            current: current,
            quality: 5,
            todayEpochDay: 500
        )

        #expect(result.intervalDays == 21)
        #expect(result.mastered)
    }

    @Test func invalidQualityFailsClosed() {
        #expect(throws: (any Error).self) {
            try ReviewScheduler.grade(
                current: StudyProgress(),
                quality: 6,
                todayEpochDay: 100
            )
        }
    }
}
