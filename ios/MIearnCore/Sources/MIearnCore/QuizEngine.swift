import Foundation

public enum QuizEngine {
    public static func isSpellingCorrect(answer: String, expected: String) -> Bool {
        normalizeAnswer(answer) == normalizeAnswer(expected)
    }

    public static func blankExample(_ example: String, primaryEnglish: String) -> String {
        let target = primaryEnglish.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !target.isEmpty,
              let range = example.range(of: target, options: [.caseInsensitive]) else {
            return example
        }
        return example.replacingCharacters(in: range, with: "______")
    }

    public static func choiceOptions(
        answer: String,
        candidates: [String],
        seed: Int
    ) -> [String] {
        var seen: Set<String> = []
        let distinct = candidates.compactMap { candidate -> String? in
            let value = candidate.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !value.isEmpty, value != answer, seen.insert(value).inserted else {
                return nil
            }
            return value
        }
        var distractorGenerator = SeededGenerator(seed: seed)
        let distractors = Array(distinct.shuffled(using: &distractorGenerator).prefix(3))
        var optionGenerator = SeededGenerator(seed: seed ^ 0x5F37_59DF)
        return (distractors + [answer]).shuffled(using: &optionGenerator)
    }

    private static func normalizeAnswer(_ value: String) -> String {
        value.replacingOccurrences(of: "’", with: "'")
            .replacingOccurrences(of: "‘", with: "'")
            .components(separatedBy: .whitespacesAndNewlines)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
            .lowercased()
    }
}

private struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64

    init(seed: Int) {
        state = UInt64(bitPattern: Int64(seed)) &+ 0x9E37_79B9_7F4A_7C15
    }

    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var value = state
        value = (value ^ (value >> 30)) &* 0xBF58_476D_1CE4_E5B9
        value = (value ^ (value >> 27)) &* 0x94D0_49BB_1331_11EB
        return value ^ (value >> 31)
    }
}
