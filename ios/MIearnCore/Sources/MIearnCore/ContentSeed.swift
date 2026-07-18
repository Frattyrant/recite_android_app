import Foundation

public struct ContentSeed: Decodable, Sendable {
    public let schemaVersion: Int
    public let contentVersion: String
    public let words: [WordSeed]

    public static func decode(_ data: Data) throws -> ContentSeed {
        let seed = try JSONDecoder().decode(ContentSeed.self, from: data)
        guard seed.schemaVersion == 1,
              !seed.contentVersion.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !seed.words.isEmpty else {
            throw ContentSeedError.invalidRoot
        }
        try seed.words.forEach { try $0.validate() }
        guard Set(seed.words.map(\.id)).count == seed.words.count else {
            throw ContentSeedError.duplicateWordId
        }
        return seed
    }
}

public struct WordSeed: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public let category: String
    public let categoryLabel: String
    public let sourceIndex: Int
    public let kind: String
    public let section: String
    public let english: String
    public let primaryEnglish: String
    public let phonetic: String
    public let chinese: String
    public let note: String
    public let exampleEn: String
    public let exampleZh: String
    public let audioText: String
    public let audioAsset: String

    public init(
        id: String,
        category: String,
        categoryLabel: String,
        sourceIndex: Int,
        kind: String,
        section: String,
        english: String,
        primaryEnglish: String,
        phonetic: String,
        chinese: String,
        note: String,
        exampleEn: String,
        exampleZh: String,
        audioText: String,
        audioAsset: String
    ) {
        self.id = id
        self.category = category
        self.categoryLabel = categoryLabel
        self.sourceIndex = sourceIndex
        self.kind = kind
        self.section = section
        self.english = english
        self.primaryEnglish = primaryEnglish
        self.phonetic = phonetic
        self.chinese = chinese
        self.note = note
        self.exampleEn = exampleEn
        self.exampleZh = exampleZh
        self.audioText = audioText
        self.audioAsset = audioAsset
    }

    fileprivate func validate() throws {
        let required = [
            id,
            category,
            categoryLabel,
            kind,
            english,
            primaryEnglish,
            phonetic,
            chinese,
            exampleEn,
            exampleZh,
            audioText,
            audioAsset,
        ]
        guard sourceIndex > 0,
              required.allSatisfy({ !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }),
              audioAsset.hasPrefix("audio/"),
              audioAsset.hasSuffix(".ogg") else {
            throw ContentSeedError.invalidWord(id)
        }
    }
}

public enum ContentSeedError: Error, Equatable {
    case invalidRoot
    case invalidWord(String)
    case duplicateWordId
}
