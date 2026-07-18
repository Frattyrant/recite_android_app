import Foundation

public struct StudyProgress: Codable, Equatable, Sendable {
    public var easeFactor: Double
    public var intervalDays: Int
    public var repetitions: Int
    public var lapseCount: Int
    public var nextReviewEpochDay: Int64
    public var lastReviewedEpochDay: Int64?
    public var firstLearnedEpochDay: Int64?
    public var mastered: Bool

    public init(
        easeFactor: Double = 2.5,
        intervalDays: Int = 0,
        repetitions: Int = 0,
        lapseCount: Int = 0,
        nextReviewEpochDay: Int64 = 0,
        lastReviewedEpochDay: Int64? = nil,
        firstLearnedEpochDay: Int64? = nil,
        mastered: Bool = false
    ) {
        self.easeFactor = easeFactor
        self.intervalDays = intervalDays
        self.repetitions = repetitions
        self.lapseCount = lapseCount
        self.nextReviewEpochDay = nextReviewEpochDay
        self.lastReviewedEpochDay = lastReviewedEpochDay
        self.firstLearnedEpochDay = firstLearnedEpochDay
        self.mastered = mastered
    }
}

public enum ReviewScheduler {
    public static func grade(
        current: StudyProgress,
        quality: Int,
        todayEpochDay: Int64
    ) throws -> StudyProgress {
        guard (0...5).contains(quality) else {
            throw ReviewSchedulerError.invalidQuality
        }

        let qualityGap = 5 - quality
        let nextEase = max(
            1.3,
            current.easeFactor + 0.1
                - Double(qualityGap) * (0.08 + Double(qualityGap) * 0.02)
        )
        let nextRepetitions = quality < 3 ? 0 : current.repetitions + 1
        let nextInterval: Int
        if quality < 3 {
            nextInterval = 1
        } else if nextRepetitions == 1 {
            nextInterval = 1
        } else if nextRepetitions == 2 {
            nextInterval = 6
        } else {
            nextInterval = max(
                1,
                Int((Double(current.intervalDays) * nextEase).rounded())
            )
        }

        var progress = current
        progress.easeFactor = nextEase
        progress.intervalDays = nextInterval
        progress.repetitions = nextRepetitions
        progress.lapseCount += (quality < 3 ? 1 : 0)
        progress.nextReviewEpochDay = todayEpochDay + Int64(nextInterval)
        progress.lastReviewedEpochDay = todayEpochDay
        progress.firstLearnedEpochDay = current.firstLearnedEpochDay ?? todayEpochDay
        progress.mastered = quality >= 3 && nextRepetitions >= 3 && nextInterval >= 21
        return progress
    }
}

public enum ReviewSchedulerError: Error, Equatable {
    case invalidQuality
}
