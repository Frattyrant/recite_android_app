import Foundation

public enum LearningPhase: String, Codable, Sendable, Hashable {
    case review
    case browse
    case consolidate
    case reinforcement
    case complete
}

public struct LearningSession: Codable, Equatable, Sendable {
    public var reviewIds: [String]
    public var newIds: [String]
    public var reinforcementIds: [String]
    public var phase: LearningPhase
    public var index: Int
    public var completedNew: Int
    public var completedReview: Int
    public var correctFirstTry: Int
    public var answeredFirstTry: Int
    public var cardExpanded: Bool
    public var pendingFirstCorrect: Bool?

    public var currentId: String? {
        switch phase {
        case .review:
            return reviewIds[safe: index]
        case .browse, .consolidate:
            return newIds[safe: index]
        case .reinforcement:
            return reinforcementIds[safe: index]
        case .complete:
            return nil
        }
    }

    public var isComplete: Bool { phase == .complete }

    public var phaseTotal: Int {
        switch phase {
        case .review:
            return reviewIds.count
        case .browse, .consolidate:
            return newIds.count
        case .reinforcement:
            return reinforcementIds.count
        case .complete:
            return 0
        }
    }

    public static func start(reviewIds: [String], newIds: [String]) -> LearningSession {
        let phase: LearningPhase
        if !reviewIds.isEmpty {
            phase = .review
        } else if !newIds.isEmpty {
            phase = .browse
        } else {
            phase = .complete
        }
        return LearningSession(
            reviewIds: reviewIds,
            newIds: newIds,
            reinforcementIds: [],
            phase: phase,
            index: 0,
            completedNew: 0,
            completedReview: 0,
            correctFirstTry: 0,
            answeredFirstTry: 0,
            cardExpanded: false,
            pendingFirstCorrect: nil
        )
    }

    public func submitAnswer(firstCorrect: Bool) -> LearningSession {
        guard Self.answerPhases.contains(phase),
              let currentId,
              pendingFirstCorrect == nil else {
            return self
        }
        let firstTry = phase != .reinforcement
        var next = self
        if !firstCorrect && !next.reinforcementIds.contains(currentId) {
            next.reinforcementIds.append(currentId)
        }
        if firstTry && phase == .consolidate {
            next.completedNew += 1
        }
        if firstTry && phase == .review {
            next.completedReview += 1
        }
        if firstTry && firstCorrect {
            next.correctFirstTry += 1
        }
        if firstTry {
            next.answeredFirstTry += 1
        }
        next.pendingFirstCorrect = firstCorrect
        return next
    }

    public func continueAfterAnswer() -> LearningSession {
        guard Self.answerPhases.contains(phase), pendingFirstCorrect != nil else {
            return self
        }
        switch phase {
        case .review:
            if index + 1 < reviewIds.count {
                return updatingIndex(index + 1)
            }
            return transitionAfterReview()
        case .consolidate:
            if index + 1 < newIds.count {
                return updatingIndex(index + 1)
            }
            return transitionToReinforcementOrComplete()
        case .reinforcement:
            if index + 1 < reinforcementIds.count {
                return updatingIndex(index + 1)
            }
            return completing()
        case .browse, .complete:
            return self
        }
    }

    public func nextBrowse() -> LearningSession {
        guard phase == .browse, !newIds.isEmpty else { return self }
        var next = self
        if index + 1 < newIds.count {
            next.index += 1
        } else {
            next.phase = .consolidate
            next.index = 0
            next.pendingFirstCorrect = nil
        }
        next.cardExpanded = false
        return next
    }

    public func previousBrowse() -> LearningSession {
        guard phase == .browse, index > 0 else { return self }
        var next = self
        next.index -= 1
        next.cardExpanded = false
        return next
    }

    public func browseTo(_ targetIndex: Int) -> LearningSession {
        guard phase == .browse, newIds.indices.contains(targetIndex) else { return self }
        var next = self
        next.index = targetIndex
        next.cardExpanded = false
        return next
    }

    public func toggleCardExpanded() -> LearningSession {
        guard phase == .browse else { return self }
        var next = self
        next.cardExpanded.toggle()
        return next
    }

    private func updatingIndex(_ newIndex: Int) -> LearningSession {
        var next = self
        next.index = newIndex
        next.pendingFirstCorrect = nil
        return next
    }

    private func transitionAfterReview() -> LearningSession {
        if !newIds.isEmpty {
            var next = self
            next.phase = .browse
            next.index = 0
            next.cardExpanded = false
            next.pendingFirstCorrect = nil
            return next
        }
        return transitionToReinforcementOrComplete()
    }

    private func transitionToReinforcementOrComplete() -> LearningSession {
        guard !reinforcementIds.isEmpty else { return completing() }
        var next = self
        next.phase = .reinforcement
        next.index = 0
        next.cardExpanded = false
        next.pendingFirstCorrect = nil
        return next
    }

    private func completing() -> LearningSession {
        var next = self
        next.phase = .complete
        next.index = 0
        next.cardExpanded = false
        next.pendingFirstCorrect = nil
        return next
    }

    private static let answerPhases: Set<LearningPhase> = [
        .review,
        .consolidate,
        .reinforcement,
    ]
}

private extension Array {
    subscript(safe index: Index) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
