import MIearnCore
import SwiftUI
import UniformTypeIdentifiers

struct WordSearchView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var detailWord: WordSeed?
    @FocusState private var focused: Bool

    private var results: [WordSeed] {
        let value = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return [] }
        return Array(store.words.filter {
            $0.english.localizedCaseInsensitiveContains(value)
                || $0.chinese.localizedCaseInsensitiveContains(value)
        }.prefix(100))
    }

    var body: some View {
        NavigationStack {
            Group {
                if query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    EmptyState(icon: "magnifyingglass", title: "搜索全词库", message: "输入英文或中文；空查询不会载入全部词条。")
                } else if results.isEmpty {
                    EmptyState(icon: "text.magnifyingglass", title: "没有匹配结果", message: "尝试更短的关键词。")
                } else {
                    WordList(words: results, detailWord: $detailWord)
                }
            }
            .background(SoftBackground())
            .navigationTitle("搜索")
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "英文或中文")
            .focused($focused)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("关闭") { dismiss() }
                }
            }
            .sheet(item: $detailWord) { WordDetailView(word: $0) }
            .task { focused = true }
        }
    }
}

struct WordList: View {
    @EnvironmentObject private var store: AppStore
    let words: [WordSeed]
    @Binding var detailWord: WordSeed?

    var body: some View {
        List(words) { word in
            Button { detailWord = word } label: {
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 5) {
                        Text(word.english).font(.headline).foregroundStyle(.primary)
                        Text(word.chinese).font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
                        Text(word.categoryLabel).font(.caption).foregroundStyle(MIearnPalette.violet)
                    }
                    Spacer()
                    Button { AudioService.shared.play(word: word) } label: {
                        Image(systemName: "speaker.wave.2")
                    }
                    .buttonStyle(.plain)
                    Button { store.toggleFavorite(word.id) } label: {
                        Image(systemName: store.progress[word.id]?.favorite == true ? "heart.fill" : "heart")
                    }
                    .buttonStyle(.plain)
                }
                .padding(.vertical, 6)
            }
            .buttonStyle(.plain)
            .listRowBackground(Color.clear)
        }
        .scrollContentBackground(.hidden)
    }
}

struct WordDetailView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let word: WordSeed
    @State private var detailWord: WordSeed?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VariantChips(word: word, detailWord: $detailWord)
                    HStack {
                        Text(word.phonetic).font(.title3).foregroundStyle(.secondary)
                        Spacer()
                        Button { AudioService.shared.play(word: word) } label: {
                            Label("完整发音", systemImage: "speaker.wave.2.fill")
                        }
                    }
                    Text(word.chinese).font(.title2.bold())
                    if !word.note.isEmpty {
                        Text(word.note).font(.subheadline).foregroundStyle(.secondary)
                    }
                    Divider()
                    Text("例句").font(.headline)
                    Text(word.exampleEn)
                    Text(word.exampleZh).foregroundStyle(.secondary)
                    Button {
                        store.toggleFavorite(word.id)
                    } label: {
                        Label(
                            store.progress[word.id]?.favorite == true ? "取消收藏" : "收藏",
                            systemImage: store.progress[word.id]?.favorite == true ? "heart.fill" : "heart"
                        )
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(MIearnPalette.purple)
                }
                .padding(22)
            }
            .background(SoftBackground())
            .navigationTitle("词条详情")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { dismiss() } label: { Image(systemName: "xmark").frame(width: 44, height: 44) }
                }
            }
            .onDisappear { AudioService.shared.stop() }
        }
    }
}

struct MineView: View {
    @EnvironmentObject private var store: AppStore
    @State private var detailWord: WordSeed?
    @State private var showImporter = false
    @State private var importMessage: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    MonthActivityCard()
                    groupedLinks
                    customLibraries
                }
                .padding(18)
            }
            .background(SoftBackground())
            .navigationTitle("我的")
            .fileImporter(
                isPresented: $showImporter,
                allowedContentTypes: [.commaSeparatedText, .plainText],
                allowsMultipleSelection: false
            ) { result in
                do {
                    let urls = try result.get()
                    guard let url = urls.first else { throw ImportError.noRows }
                    guard url.startAccessingSecurityScopedResource() else { throw CocoaError(.fileReadNoPermission) }
                    defer { url.stopAccessingSecurityScopedResource() }
                    let count = try store.importCSV(data: Data(contentsOf: url), sourceName: url.lastPathComponent)
                    importMessage = "已导入 \(count) 条词条"
                } catch {
                    importMessage = error.localizedDescription
                }
            }
            .alert("词库导入", isPresented: Binding(
                get: { importMessage != nil },
                set: { if !$0 { importMessage = nil } }
            )) {
                Button("好") { importMessage = nil }
            } message: {
                Text(importMessage ?? "")
            }
            .sheet(item: $detailWord) { WordDetailView(word: $0) }
        }
    }

    private var groupedLinks: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("我的内容").font(.headline).frame(maxWidth: .infinity, alignment: .leading)
            NavigationLink {
                WordCollectionView(title: "收藏", words: store.favoriteWords)
            } label: {
                mineRow("heart.fill", "收藏", "\(store.favoriteWords.count)")
            }
            NavigationLink {
                WordCollectionView(title: "错题", words: store.wrongWords)
            } label: {
                mineRow("exclamationmark.triangle.fill", "错题", "\(store.wrongWords.count)")
            }
            NavigationLink {
                LearningDataView()
            } label: {
                mineRow("chart.xyaxis.line", "学习数据", "")
            }
        }
    }

    private var customLibraries: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("数据与工具").font(.headline)
            Button { showImporter = true } label: {
                mineRow("square.and.arrow.down", "导入 CSV 词库", "英文,中文,备注")
            }
            ForEach(store.categories.filter { $0.id.hasPrefix("custom_") }, id: \.id) { item in
                HStack {
                    mineRow("books.vertical", item.label, "\(item.count)")
                    Button(role: .destructive) { store.deleteCustomCategory(item.id) } label: {
                        Image(systemName: "trash")
                    }
                }
            }
        }
    }

    private func mineRow(_ icon: String, _ title: String, _ trailing: String) -> some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .foregroundStyle(MIearnPalette.purple)
                .frame(width: 34, height: 34)
                .background(MIearnPalette.violet.opacity(0.12), in: RoundedRectangle(cornerRadius: 11))
            Text(title).foregroundStyle(.primary)
            Spacer()
            Text(trailing).font(.caption).foregroundStyle(.secondary)
            Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
        }
        .padding(14)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 18))
    }
}

struct WordCollectionView: View {
    @EnvironmentObject private var store: AppStore
    let title: String
    let words: [WordSeed]
    @State private var detailWord: WordSeed?

    var body: some View {
        Group {
            if words.isEmpty {
                EmptyState(icon: "tray", title: "暂无\(title)", message: "学习过程中标记的内容会出现在这里。")
            } else {
                WordList(words: words, detailWord: $detailWord)
            }
        }
        .background(SoftBackground())
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $detailWord) { WordDetailView(word: $0) }
    }
}

struct MonthActivityCard: View {
    @EnvironmentObject private var store: AppStore

    private var days: [Date?] {
        let calendar = Calendar.current
        let now = Date()
        let start = calendar.date(from: calendar.dateComponents([.year, .month], from: now))!
        let weekday = (calendar.component(.weekday, from: start) + 5) % 7
        let count = calendar.range(of: .day, in: .month, for: now)?.count ?? 30
        return Array(repeating: nil, count: weekday) + (0..<count).map {
            calendar.date(byAdding: .day, value: $0, to: start)
        }
    }

    var body: some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text(Date.now.formatted(.dateTime.year().month(.wide))).font(.headline)
                    Spacer()
                    Text("本月学习").font(.caption).foregroundStyle(.secondary)
                }
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 7), spacing: 7) {
                    ForEach(["一", "二", "三", "四", "五", "六", "日"], id: \.self) {
                        Text($0).font(.caption2).foregroundStyle(.secondary)
                    }
                    ForEach(Array(days.enumerated()), id: \.offset) { _, day in
                        if let day {
                            let epoch = Int64(Calendar.current.startOfDay(for: day).timeIntervalSince1970 / 86_400)
                            let count = (store.daily[epoch]?.newCount ?? 0) + (store.daily[epoch]?.reviewCount ?? 0)
                            Text("\(Calendar.current.component(.day, from: day))")
                                .font(.caption2.monospacedDigit())
                                .frame(width: 28, height: 28)
                                .background(heat(count), in: Circle())
                        } else {
                            Color.clear.frame(width: 28, height: 28)
                        }
                    }
                }
            }
        }
    }

    private func heat(_ count: Int) -> Color {
        if count >= 30 { return MIearnPalette.purple.opacity(0.85) }
        if count >= 10 { return MIearnPalette.violet.opacity(0.55) }
        if count > 0 { return MIearnPalette.peach.opacity(0.48) }
        return Color.secondary.opacity(0.08)
    }
}

struct LearningDataView: View {
    @EnvironmentObject private var store: AppStore

    private var lastSeven: [(Int64, DailyStudyRecord?)] {
        (0..<7).reversed().map { offset in
            let day = AppStore.todayEpochDay - Int64(offset)
            return (day, store.daily[day])
        }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                HStack {
                    dataMetric("\(store.progress.values.filter { $0.study.firstLearnedEpochDay != nil }.count)", "累计学习")
                    dataMetric("\(store.progress.values.filter { $0.study.mastered }.count)", "已掌握")
                    dataMetric("\(store.wrongWords.count)", "当前错题")
                }
                SoftCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("近 7 天").font(.headline)
                        HStack(alignment: .bottom, spacing: 10) {
                            ForEach(lastSeven, id: \.0) { day, record in
                                let total = (record?.newCount ?? 0) + (record?.reviewCount ?? 0)
                                VStack {
                                    RoundedRectangle(cornerRadius: 6)
                                        .fill(MIearnPalette.purple.opacity(total == 0 ? 0.12 : 0.72))
                                        .frame(height: max(5, CGFloat(min(100, total * 4))))
                                    Text(shortWeekday(day)).font(.caption2)
                                }
                                .frame(maxWidth: .infinity)
                            }
                        }
                        .frame(height: 140, alignment: .bottom)
                    }
                }
                SoftCard {
                    let answers = store.daily.values.reduce(0) { $0 + $1.answerCount }
                    let correct = store.daily.values.reduce(0) { $0 + $1.correctFirstTry }
                    HStack {
                        Text("首次正确率")
                        Spacer()
                        Text(answers == 0 ? "—" : "\(Int(Double(correct) / Double(answers) * 100))%")
                            .font(.title2.bold())
                    }
                }
            }
            .padding(18)
        }
        .background(SoftBackground())
        .navigationTitle("学习数据")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func dataMetric(_ value: String, _ title: String) -> some View {
        VStack(spacing: 4) {
            Text(value).font(.title2.bold())
            Text(title).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
    }

    private func shortWeekday(_ epoch: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(epoch) * 86_400)
        return date.formatted(.dateTime.weekday(.narrow))
    }
}
