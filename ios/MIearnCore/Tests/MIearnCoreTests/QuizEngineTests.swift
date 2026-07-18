import Testing
@testable import MIearnCore

@Suite struct QuizEngineTests {
    @Test func spellingIgnoresCaseWhitespaceAndSmartApostrophes() {
        #expect(
            QuizEngine.isSpellingCorrect(
                answer: "  I'M ON BOARD ",
                expected: "I’m on board"
            )
        )
    }

    @Test func blankExampleHidesFirstCaseInsensitiveMatch() {
        #expect(
            QuizEngine.blankExample(
                "The engineer verified the limit switch during commissioning.",
                primaryEnglish: "limit switch"
            ) == "The engineer verified the ______ during commissioning."
        )
    }

    @Test func optionsAreUniqueDeterministicAndContainAnswer() {
        let candidates = ["错误一", "错误一", "正确", "错误二", "错误三", "错误四"]
        let first = QuizEngine.choiceOptions(
            answer: "正确",
            candidates: candidates,
            seed: 42
        )
        let second = QuizEngine.choiceOptions(
            answer: "正确",
            candidates: candidates,
            seed: 42
        )

        #expect(first.count == 4)
        #expect(Set(first).count == 4)
        #expect(first.contains("正确"))
        #expect(first == second)
    }
}
