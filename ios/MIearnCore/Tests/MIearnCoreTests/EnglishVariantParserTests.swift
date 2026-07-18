import Testing
@testable import MIearnCore

@Suite struct EnglishVariantParserTests {
    @Test func termSeparatorsAndProtectedSlashesMatchAndroid() {
        #expect(EnglishVariantParser.parse(" fixture；jig; checking fixture ") == ["fixture", "jig", "checking fixture"])
        #expect(EnglishVariantParser.parse("support, pad/net") == ["support, pad", "net"])
        #expect(EnglishVariantParser.parse("Read at 300mm/s") == ["Read at 300mm/s"])
        #expect(EnglishVariantParser.parse("the assignment of the I/O") == ["the assignment of the I/O"])
    }

    @Test func phraseSplitsSentencesAndAlternativesButNotUnits() {
        #expect(
            EnglishVariantParser.parse(
                "For all robots, use only 300mm/s for gluing. "
                    + "With 600mm/s, the quality will not be fine. We have to reduce the speed.",
                kind: "PHRASE"
            ) == [
                "For all robots, use only 300mm/s for gluing.",
                "With 600mm/s, the quality will not be fine.",
                "We have to reduce the speed.",
            ]
        )
        #expect(
            EnglishVariantParser.parse(
                "Sorry, come again?/I didn't follow you, could you repeat?/Read at 300mm/s.",
                kind: "PHRASE"
            ) == [
                "Sorry, come again?",
                "I didn't follow you, could you repeat?",
                "Read at 300mm/s.",
            ]
        )
    }

    @Test func speechTextAndKindInferenceMatchAndroid() {
        #expect(EnglishVariantParser.toSpeechText("300mm/s") == "300mm s")
        #expect(EnglishVariantParser.toSpeechText("gun\\gripper") == "gun gripper")
        #expect(EnglishVariantParser.inferKind("support and clamp block") == "TERM")
        #expect(EnglishVariantParser.inferKind("Can you repeat that?") == "PHRASE")
        #expect(
            EnglishVariantParser.inferKind("We need to inspect the fixture before production")
                == "PHRASE"
        )
    }
}
