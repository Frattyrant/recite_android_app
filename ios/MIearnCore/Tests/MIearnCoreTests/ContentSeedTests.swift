import Testing
import Foundation
@testable import MIearnCore

@Suite struct ContentSeedTests {
    @Test func decodesAndroidSeedContract() throws {
        let seed = try ContentSeed.decode(Data(validJSON.utf8))

        #expect(seed.schemaVersion == 1)
        #expect(seed.contentVersion == "test-v1")
        #expect(seed.words.first?.id == "mec_0001")
        #expect(seed.words.first?.english == "fixture；jig")
    }

    @Test func rejectsDuplicateIdsAndBlankRequiredFields() {
        let duplicate = """
        {
          "schemaVersion": 1,
          "contentVersion": "test-v1",
          "words": [\(wordJSON), \(wordJSON)]
        }
        """
        #expect(throws: (any Error).self) {
            try ContentSeed.decode(Data(duplicate.utf8))
        }

        let blank = validJSON.replacingOccurrences(of: "夹具", with: "")
        #expect(throws: (any Error).self) {
            try ContentSeed.decode(Data(blank.utf8))
        }
    }

    private var validJSON: String {
        """
        {
          "schemaVersion": 1,
          "contentVersion": "test-v1",
          "words": [\(wordJSON)]
        }
        """
    }

    private var wordJSON: String {
        """
        {
          "id": "mec_0001",
          "category": "mechanical",
          "categoryLabel": "机械专业词汇",
          "sourceIndex": 1,
          "kind": "TERM",
          "section": "",
          "english": "fixture；jig",
          "primaryEnglish": "fixture",
          "phonetic": "/ˈfɪkstʃər/",
          "chinese": "夹具",
          "note": "",
          "exampleEn": "The technician checked the fixture.",
          "exampleZh": "技术员检查了夹具。",
          "audioText": "fixture；jig",
          "audioAsset": "audio/mec_0001.ogg"
        }
        """
    }
}
