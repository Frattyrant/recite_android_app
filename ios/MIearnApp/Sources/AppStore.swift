import Foundation
import MIearnCore

struct StoredWordProgress: Codable, Equatable {
    var study = StudyProgress()
    var favorite = false
    var wrongCount = 0
}

struct DailyStudyRecord: Codable, Equatable, Identifiable {
    var id: Int64 { epochDay }
    let epochDay: Int64
    var newCount = 0
    var reviewCount = 0
    var correctFirstTry = 0
    var answerCount = 0
}

struct AppSettings: Codable, Equatable {
    var dailyGoal = 20
    var autoPronounce = true
    var reminderEnabled = false
    var reminderHour = 10
    var reminderMinute = 0
}

private struct PersistedState: Codable {
    var progress: [String: StoredWordProgress]
    var daily: [Int64: DailyStudyRecord]
    var settings: AppSettings
    var selectedCategory: String
    var customWords: [WordSeed]
    var session: LearningSession?
}

@MainActor
final class AppStore: ObservableObject {
    @Published private(set) var words: [WordSeed] = []
    @Published private(set) var isReady = false
    @Published var errorMessage: String?
    @Published var selectedCategory = "mechanical" { didSet { persist() } }
    @Published var progress: [String: StoredWordProgress] = [:]
    @Published var daily: [Int64: DailyStudyRecord] = [:]
    @Published var settings = AppSettings()
    @Published var activeSession: LearningSession?
    @Published var customWords: [WordSeed] = []

    private var builtInWords: [WordSeed] = []
    private let defaultsKey = "miearn.ios.state.v232"

    var categories: [(id: String, label: String, count: Int)] {
        Dictionary(grouping: words, by: \.category)
            .map { key, values in (key, values.first?.categoryLabel ?? key, values.count) }
            .sorted { $0.label.localizedStandardCompare($1.label) == .orderedAscending }
    }

    var selectedWords: [WordSeed] {
        words.filter { $0.category == selectedCategory }
    }

    var dueCount: Int {
        selectedWords.count { word in
            guard let item = progress[word.id], item.study.firstLearnedEpochDay != nil else { return false }
            return item.study.nextReviewEpochDay <= Self.todayEpochDay
        }
    }

    var learnedCount: Int {
        selectedWords.count { progress[$0.id]?.study.firstLearnedEpochDay != nil }
    }

    var masteredCount: Int {
        selectedWords.count { progress[$0.id]?.study.mastered == true }
    }

    var favoriteWords: [WordSeed] { words.filter { progress[$0.id]?.favorite == true } }
    var wrongWords: [WordSeed] { words.filter { (progress[$0.id]?.wrongCount ?? 0) > 0 } }

    static var todayEpochDay: Int64 {
        Int64(Calendar.current.startOfDay(for: Date()).timeIntervalSince1970 / 86_400)
    }

    func bootstrap() async {
        guard !isReady else { return }
        do {
            guard let url = Bundle.main.url(forResource: "words_v1", withExtension: "json") else {
                throw CocoaError(.fileNoSuchFile)
            }
            builtInWords = try ContentSeed.decode(Data(contentsOf: url)).words
            restore()
            words = builtInWords + customWords
            if !words.contains(where: { $0.category == selectedCategory }) {
                selectedCategory = words.first?.category ?? "mechanical"
            }
            isReady = true
            await ReminderService.shared.reconcile(settings: settings)
        } catch {
            errorMessage = "词库载入失败：\(error.localizedDescription)"
        }
    }

    func startLearning() {
        if let activeSession, !activeSession.isComplete { return }
        let today = Self.todayEpochDay
        let due = selectedWords.filter {
            guard let item = progress[$0.id], item.study.firstLearnedEpochDay != nil else { return false }
            return item.study.nextReviewEpochDay <= today
        }.map(\.id)
        let fresh = selectedWords.filter {
            progress[$0.id]?.study.firstLearnedEpochDay == nil
        }.prefix(settings.dailyGoal).map(\.id)
        activeSession = LearningSession.start(reviewIds: due, newIds: Array(fresh))
        persist()
    }

    func word(id: String?) -> WordSeed? {
        guard let id else { return nil }
        return words.first { $0.id == id }
    }

    func submitAnswer(correct: Bool) {
        guard var session = activeSession,
              let id = session.currentId,
              session.pendingFirstCorrect == nil else { return }
        let firstTry = session.phase != .reinforcement
        let isNew = session.phase == .consolidate
        session = session.submitAnswer(firstCorrect: correct)
        if firstTry {
            var item = progress[id] ?? StoredWordProgress()
            item.study = (try? ReviewScheduler.grade(
                current: item.study,
                quality: correct ? 5 : 2,
                todayEpochDay: Self.todayEpochDay
            )) ?? item.study
            if !correct { item.wrongCount += 1 }
            progress[id] = item
            var record = daily[Self.todayEpochDay]
                ?? DailyStudyRecord(epochDay: Self.todayEpochDay)
            if isNew { record.newCount += 1 } else { record.reviewCount += 1 }
            record.answerCount += 1
            if correct { record.correctFirstTry += 1 }
            daily[Self.todayEpochDay] = record
        }
        activeSession = session
        persist()
    }

    func continueAfterAnswer() {
        guard let session = activeSession else { return }
        activeSession = session.continueAfterAnswer()
        persist()
    }

    func nextBrowse() {
        activeSession = activeSession?.nextBrowse()
        persist()
    }

    func previousBrowse() {
        activeSession = activeSession?.previousBrowse()
        persist()
    }

    func toggleCard() {
        activeSession = activeSession?.toggleCardExpanded()
        persist()
    }

    func closeSession() {
        AudioService.shared.stop()
        activeSession = nil
        persist()
    }

    func toggleFavorite(_ id: String) {
        var item = progress[id] ?? StoredWordProgress()
        item.favorite.toggle()
        progress[id] = item
        persist()
    }

    func clearWrong(_ id: String) {
        var item = progress[id] ?? StoredWordProgress()
        item.wrongCount = 0
        progress[id] = item
        persist()
    }

    func updateSettings(_ update: (inout AppSettings) -> Void) async {
        update(&settings)
        settings.dailyGoal = min(200, max(5, (settings.dailyGoal / 5) * 5))
        persist()
        await ReminderService.shared.reconcile(settings: settings)
    }

    func importCSV(data: Data, sourceName: String) throws -> Int {
        guard let text = String(data: data, encoding: .utf8) else {
            throw ImportError.notUTF8
        }
        let source = sourceName.replacingOccurrences(of: ".csv", with: "")
        let category = "custom_" + String(source.lowercased().filter { $0.isLetter || $0.isNumber }.prefix(24))
        var imported: [WordSeed] = []
        for (index, rawLine) in text.components(separatedBy: .newlines).enumerated() {
            let columns = rawLine.split(separator: ",", maxSplits: 2).map {
                $0.trimmingCharacters(in: .whitespacesAndNewlines)
            }
            guard columns.count >= 2, !columns[0].isEmpty, !columns[1].isEmpty else { continue }
            if index == 0 && columns[0].lowercased().contains("english") { continue }
            let english = columns[0]
            let idHash = english.unicodeScalars.reduce(into: UInt64(1469598103934665603)) {
                $0 = ($0 ^ UInt64($1.value)) &* 1099511628211
            }
            imported.append(WordSeed(
                id: String(format: "cus_%016llx", idHash),
                category: category,
                categoryLabel: source.isEmpty ? "自定义词库" : source,
                sourceIndex: index + 1,
                kind: EnglishVariantParser.inferKind(english),
                section: "",
                english: english,
                primaryEnglish: EnglishVariantParser.parse(english).first ?? english,
                phonetic: "/—/",
                chinese: columns[1],
                note: columns.count > 2 ? columns[2] : "",
                exampleEn: "Study \(english) in context.",
                exampleZh: "在语境中学习 \(columns[1])。",
                audioText: english,
                audioAsset: "audio/custom/\(idHash).ogg"
            ))
        }
        guard !imported.isEmpty else { throw ImportError.noRows }
        let existing = Set(words.map(\.id))
        customWords.append(contentsOf: imported.filter { !existing.contains($0.id) })
        words = builtInWords + customWords
        selectedCategory = category
        persist()
        return imported.count
    }

    func deleteCustomCategory(_ category: String) {
        let ids = Set(customWords.filter { $0.category == category }.map(\.id))
        customWords.removeAll { $0.category == category }
        for id in ids { progress.removeValue(forKey: id) }
        words = builtInWords + customWords
        selectedCategory = builtInWords.first?.category ?? "mechanical"
        persist()
    }

    func persist() {
        let state = PersistedState(
            progress: progress,
            daily: daily,
            settings: settings,
            selectedCategory: selectedCategory,
            customWords: customWords,
            session: activeSession
        )
        if let data = try? JSONEncoder().encode(state) {
            UserDefaults.standard.set(data, forKey: defaultsKey)
        }
    }

    private func restore() {
        guard let data = UserDefaults.standard.data(forKey: defaultsKey),
              let state = try? JSONDecoder().decode(PersistedState.self, from: data) else { return }
        progress = state.progress
        daily = state.daily
        settings = state.settings
        selectedCategory = state.selectedCategory
        customWords = state.customWords
        activeSession = state.session?.isComplete == false ? state.session : nil
    }
}

enum ImportError: LocalizedError {
    case notUTF8
    case noRows

    var errorDescription: String? {
        switch self {
        case .notUTF8: return "CSV 必须使用 UTF-8 编码"
        case .noRows: return "没有找到有效的“英文,中文”数据行"
        }
    }
}
