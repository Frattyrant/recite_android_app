import Foundation

public enum EnglishVariantParser {
    private static let sentenceStarters: Set<String> = [
        "i", "we", "you", "he", "she", "they", "it",
        "can", "could", "would", "should", "will", "do", "does", "did",
        "what", "when", "where", "which", "who", "why", "how",
    ]

    public static func parse(_ english: String, kind: String = "TERM") -> [String] {
        if kind.caseInsensitiveCompare("PHRASE") == .orderedSame {
            return parsePhrase(english)
        }
        return parseTerm(english)
    }

    public static func toSpeechText(_ text: String) -> String {
        collapseWhitespace(
            text.replacingOccurrences(of: "/", with: " ")
                .replacingOccurrences(of: "\\", with: " ")
        )
    }

    public static func inferKind(_ english: String) -> String {
        let words = matches(in: english, pattern: #"[A-Za-z]+(?:'[A-Za-z]+)?"#)
        let startsLikeSentence = words.first.map {
            sentenceStarters.contains($0.lowercased())
        } ?? false
        let sentencePunctuation = contains(
            english,
            pattern: #"[!?]|[.]\s*$|[.!?]\s+["']?[A-Z]"#
        )
        return sentencePunctuation || (startsLikeSentence && words.count >= 5)
            ? "PHRASE"
            : "TERM"
    }

    private static func parseTerm(_ english: String) -> [String] {
        split(english, separators: [";", "；"])
            .flatMap(splitTermSlashes)
            .map(trim)
            .filter { contains($0, pattern: #"[A-Za-z0-9]"#) }
    }

    private static func splitTermSlashes(_ text: String) -> [String] {
        let characters = Array(text)
        var result: [String] = []
        var current = ""
        for index in characters.indices {
            let character = characters[index]
            guard character == "/" || character == "\\" else {
                current.append(character)
                continue
            }
            let right = String(characters.dropFirst(index + 1))
            let unitSlash = contains(
                current,
                pattern: #"\d+(?:\.\d+)?[A-Za-z]*$"#
            ) && contains(right, pattern: #"^[smh](?:\b|$)"#, caseInsensitive: true)
            let previous = index > 0 ? characters[index - 1] : nil
            let next = index + 1 < characters.count ? characters[index + 1] : nil
            let previousPrevious = index > 1 ? characters[index - 2] : nil
            let nextNext = index + 2 < characters.count ? characters[index + 2] : nil
            let initialismSlash = previous?.isUppercase == true
                && next?.isUppercase == true
                && previousPrevious?.isLetter != true
                && nextNext?.isLetter != true
            if unitSlash || initialismSlash {
                current.append(character)
            } else {
                appendNonEmpty(current, to: &result)
                current = ""
            }
        }
        appendNonEmpty(current, to: &result)
        return result
    }

    private static func parsePhrase(_ english: String) -> [String] {
        split(english, separators: [";", "；"])
            .flatMap(splitPhraseSlashes)
            .flatMap(splitSentences)
            .map(trim)
            .filter { contains($0, pattern: #"[A-Za-z]+(?:'[A-Za-z]+)?"#) }
    }

    private static func splitPhraseSlashes(_ text: String) -> [String] {
        let characters = Array(text)
        var result: [String] = []
        var current = ""
        for index in characters.indices {
            let character = characters[index]
            guard character == "/" || character == "\\" else {
                current.append(character)
                continue
            }
            let previous = index > 0 ? characters[index - 1] : nil
            let next = index + 1 < characters.count ? characters[index + 1] : nil
            let leftClause = current.components(separatedBy: CharacterSet(charactersIn: ".!?"))
                .last ?? current
            let rightRemainder = String(characters.dropFirst(index + 1))
            let rightClause = String(
                rightRemainder.prefix { !"/\\.!?".contains($0) }
            )
            let prefix = String(characters.prefix(index))
            let unitSlash = contains(prefix, pattern: #"\d+(?:\.\d+)?[A-Za-z]*$"#)
                && contains(rightRemainder, pattern: #"^[smh](?:\b|$)"#, caseInsensitive: true)
            let isBoundary = !unitSlash && (
                previous?.isWhitespace == true
                    || next?.isWhitespace == true
                    || (previous.map { ".!?".contains($0) } ?? false)
                    || (
                        matches(in: leftClause, pattern: #"[A-Za-z]+(?:'[A-Za-z]+)?"#).count >= 3
                            && matches(in: rightClause, pattern: #"[A-Za-z]+(?:'[A-Za-z]+)?"#).count >= 3
                    )
            )
            if isBoundary {
                appendNonEmpty(current, to: &result)
                current = ""
            } else {
                current.append(character)
            }
        }
        appendNonEmpty(current, to: &result)
        return result
    }

    private static func splitSentences(_ text: String) -> [String] {
        let characters = Array(text)
        var result: [String] = []
        var current = ""
        for index in characters.indices {
            let character = characters[index]
            current.append(character)
            guard ".!?".contains(character) else { continue }
            var cursor = index + 1
            while cursor < characters.count && characters[cursor].isWhitespace {
                cursor += 1
            }
            if cursor < characters.count && (characters[cursor] == "\"" || characters[cursor] == "'") {
                cursor += 1
            }
            if cursor < characters.count && characters[cursor].isUppercase {
                appendNonEmpty(current, to: &result)
                current = ""
            }
        }
        appendNonEmpty(current, to: &result)
        return result
    }

    private static func split(_ text: String, separators: Set<Character>) -> [String] {
        var result: [String] = []
        var current = ""
        for character in text {
            if separators.contains(character) {
                appendNonEmpty(current, to: &result)
                current = ""
            } else {
                current.append(character)
            }
        }
        appendNonEmpty(current, to: &result)
        return result
    }

    private static func appendNonEmpty(_ value: String, to result: inout [String]) {
        let cleaned = trim(value)
        if !cleaned.isEmpty {
            result.append(cleaned)
        }
    }

    private static func trim(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func collapseWhitespace(_ value: String) -> String {
        value.components(separatedBy: .whitespacesAndNewlines)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }

    private static func contains(
        _ value: String,
        pattern: String,
        caseInsensitive: Bool = false
    ) -> Bool {
        !matches(in: value, pattern: pattern, caseInsensitive: caseInsensitive).isEmpty
    }

    private static func matches(
        in value: String,
        pattern: String,
        caseInsensitive: Bool = false
    ) -> [String] {
        let options: NSRegularExpression.Options = caseInsensitive ? [.caseInsensitive] : []
        guard let expression = try? NSRegularExpression(pattern: pattern, options: options) else {
            return []
        }
        let range = NSRange(value.startIndex..<value.endIndex, in: value)
        return expression.matches(in: value, range: range).compactMap { match in
            Range(match.range, in: value).map { String(value[$0]) }
        }
    }
}
