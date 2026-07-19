import MIearnCore
import SwiftUI

private enum MainTab: Hashable {
    case learn, test, mine
}

struct RootView: View {
    @EnvironmentObject private var store: AppStore
    @State private var tab: MainTab = .learn

    var body: some View {
        ZStack {
            SoftBackground()
            if store.isReady {
                TabView(selection: $tab) {
                    HomeView()
                        .tag(MainTab.learn)
                        .tabItem { Label("学习", systemImage: "book.pages") }
                    TestSetupView()
                        .tag(MainTab.test)
                        .tabItem { Label("测试", systemImage: "checkmark.square") }
                    MineView()
                        .tag(MainTab.mine)
                        .tabItem { Label("我的", systemImage: "person.crop.circle") }
                }
            } else if let message = store.errorMessage {
                EmptyState(icon: "exclamationmark.triangle", title: "无法启动", message: message)
            } else {
                ProgressView("正在载入离线词库…")
            }
        }
        .fullScreenCover(
            isPresented: Binding(
                get: { store.activeSession != nil },
                set: { if !$0 { store.closeSession() } }
            )
        ) {
            StudySessionView()
                .environmentObject(store)
        }
    }
}

struct HomeView: View {
    @EnvironmentObject private var store: AppStore
    @State private var showSearch = false
    @State private var showSettings = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    header
                    overview
                    if store.dueCount == 0 && store.learnedCount > 0 {
                        Label("今天的到期复习已完成", systemImage: "checkmark.circle.fill")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.green)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 4)
                    }
                    Spacer(minLength: 120)
                }
                .padding(18)
            }
            .background(SoftBackground())
            .toolbar(.hidden, for: .navigationBar)
            .safeAreaInset(edge: .bottom) {
                Button {
                    store.startLearning()
                } label: {
                    Label(
                        store.dueCount > 0 ? "开始复习" : "开始学习",
                        systemImage: "play.fill"
                    )
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(store.selectedWords.isEmpty)
                .padding(.horizontal, 18)
                .padding(.vertical, 10)
                .background(.ultraThinMaterial)
            }
            .sheet(isPresented: $showSearch) { WordSearchView() }
            .sheet(isPresented: $showSettings) { SettingsView() }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("MIearn")
                        .font(.system(size: 30, weight: .bold, design: .rounded))
                    Menu {
                        ForEach(store.categories, id: \.id) { item in
                            Button("\(item.label) · \(item.count)") {
                                store.selectedCategory = item.id
                            }
                        }
                    } label: {
                        HStack(spacing: 5) {
                            Text(store.categories.first { $0.id == store.selectedCategory }?.label ?? "选择词库")
                            Image(systemName: "chevron.down")
                        }
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(MIearnPalette.purple)
                    }
                }
                Spacer()
                Button { showSearch = true } label: {
                    Image(systemName: "magnifyingglass")
                }
                .buttonStyle(HeaderIconStyle())
                Button { showSettings = true } label: {
                    Image(systemName: "slider.horizontal.3")
                }
                .buttonStyle(HeaderIconStyle())
            }
        }
    }

    private var overview: some View {
        SoftCard {
            VStack(spacing: 18) {
                HStack {
                    metric("\(min(store.settings.dailyGoal, max(0, store.selectedWords.count - store.learnedCount)))", "新学")
                    Divider().frame(height: 38)
                    metric("\(store.dueCount)", "复习")
                    Divider().frame(height: 38)
                    metric("\(streak)", "连续")
                }
                ProgressView(
                    value: Double(store.learnedCount),
                    total: Double(max(1, store.selectedWords.count))
                )
                .tint(MIearnPalette.purple)
                HStack {
                    Text("\(store.learnedCount) / \(store.selectedWords.count) 已学习")
                    Spacer()
                    Text("\(store.masteredCount) 已掌握")
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }
        }
    }

    private func metric(_ value: String, _ label: String) -> some View {
        VStack(spacing: 3) {
            Text(value).font(.title2.bold())
            Text(label).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
    }

    private var streak: String {
        var day = AppStore.todayEpochDay
        var count = 0
        while let record = store.daily[day], record.newCount + record.reviewCount > 0 {
            count += 1
            day -= 1
        }
        return count == 0 ? "0天" : "\(count)天 " + (count >= 7 ? "🔥" : count >= 3 ? "♨️" : "˙")
    }
}

private struct HeaderIconStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 18, weight: .semibold))
            .foregroundStyle(.primary)
            .frame(width: 42, height: 42)
            .background(.thinMaterial, in: Circle())
            .scaleEffect(configuration.isPressed ? 0.94 : 1)
    }
}

struct StudySessionView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var detailWord: WordSeed?
    @State private var selectedAnswer: String?

    private var session: LearningSession? { store.activeSession }
    private var word: WordSeed? { store.word(id: session?.currentId) }

    var body: some View {
        NavigationStack {
            ZStack {
                SoftBackground()
                if let session, session.isComplete {
                    completion
                } else if let session, let word {
                    VStack(spacing: 16) {
                        progressHeader(session)
                        Spacer(minLength: 8)
                        if session.phase == .browse {
                            browseCard(word: word, session: session)
                        } else {
                            answerCard(word: word, session: session)
                        }
                        Spacer(minLength: 8)
                    }
                    .padding(18)
                    .task(id: word.id + session.phase.rawValue) {
                        selectedAnswer = nil
                        if store.settings.autoPronounce { AudioService.shared.play(word: word) }
                    }
                } else {
                    EmptyState(icon: "tray", title: "暂无任务", message: "返回首页选择词库后开始学习。")
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        store.closeSession()
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                            .frame(width: 44, height: 44)
                    }
                }
            }
            .sheet(item: $detailWord) { WordDetailView(word: $0) }
            .onDisappear { AudioService.shared.stop() }
        }
    }

    private func progressHeader(_ session: LearningSession) -> some View {
        VStack(spacing: 8) {
            HStack {
                Text(phaseTitle(session.phase)).font(.headline)
                Spacer()
                Text("\(min(session.index + 1, max(1, session.phaseTotal)))/\(max(1, session.phaseTotal))")
                    .font(.subheadline.monospacedDigit())
            }
            ProgressView(value: Double(session.index + 1), total: Double(max(1, session.phaseTotal)))
                .tint(MIearnPalette.purple)
        }
    }

    private func browseCard(word: WordSeed, session: LearningSession) -> some View {
        VStack(spacing: 18) {
            SoftCard {
                VStack(spacing: 22) {
                    VariantChips(word: word, detailWord: $detailWord)
                    HStack(spacing: 24) {
                        Button { AudioService.shared.play(word: word) } label: {
                            Label("完整发音", systemImage: "speaker.wave.2.fill")
                        }
                        Button { store.toggleFavorite(word.id) } label: {
                            Image(systemName: store.progress[word.id]?.favorite == true ? "heart.fill" : "heart")
                        }
                    }
                    .foregroundStyle(MIearnPalette.purple)
                    if session.cardExpanded {
                        Divider()
                        Text(word.phonetic).font(.title3).foregroundStyle(.secondary)
                        Text(word.chinese).font(.title2.bold())
                        VStack(alignment: .leading, spacing: 6) {
                            Text(word.exampleEn)
                            Text(word.exampleZh).foregroundStyle(.secondary)
                        }
                        .font(.subheadline)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    } else {
                        Text("点击卡片查看音标、释义与例句")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity, minHeight: 360)
                .contentShape(Rectangle())
                .onTapGesture { store.toggleCard() }
            }
            HStack(spacing: 14) {
                Button("上一个") { store.previousBrowse() }
                    .buttonStyle(.bordered)
                    .disabled(session.index == 0)
                Button("下一个") { store.nextBrowse() }
                    .buttonStyle(.borderedProminent)
                    .tint(MIearnPalette.purple)
            }
            .controlSize(.large)
        }
    }

    private func answerCard(word: WordSeed, session: LearningSession) -> some View {
        let options = QuizEngine.choiceOptions(
            answer: word.chinese,
            candidates: store.selectedWords.map(\.chinese),
            seed: word.id.hashValue
        )
        return VStack(spacing: 18) {
            SoftCard {
                VStack(spacing: 18) {
                    VariantChips(word: word, detailWord: $detailWord)
                    Text(word.phonetic).font(.subheadline).foregroundStyle(.secondary)
                    Button { AudioService.shared.play(word: word) } label: {
                        Image(systemName: "speaker.wave.2.fill").font(.title2)
                    }
                    .accessibilityLabel("播放完整发音")
                }
                .frame(maxWidth: .infinity, minHeight: 210)
            }
            VStack(spacing: 10) {
                ForEach(options, id: \.self) { option in
                    Button {
                        guard selectedAnswer == nil else { return }
                        selectedAnswer = option
                        let correct = option == word.chinese
                        AudioService.shared.answerFeedback(correct: correct)
                        store.submitAnswer(correct: correct)
                        Task {
                            try? await Task.sleep(for: .milliseconds(520))
                            store.continueAfterAnswer()
                        }
                    } label: {
                        HStack {
                            Text(option).multilineTextAlignment(.leading)
                            Spacer()
                            if selectedAnswer == option {
                                Image(systemName: option == word.chinese ? "checkmark.circle.fill" : "xmark.circle.fill")
                            }
                        }
                        .padding(15)
                        .frame(maxWidth: .infinity)
                        .background(answerColor(option, correct: word.chinese), in: RoundedRectangle(cornerRadius: 16))
                    }
                    .buttonStyle(.plain)
                    .disabled(selectedAnswer != nil)
                }
            }
        }
    }

    private func answerColor(_ option: String, correct: String) -> Color {
        guard let selectedAnswer else { return MIearnPalette.violet.opacity(0.10) }
        if option == correct { return .green.opacity(0.18) }
        if option == selectedAnswer { return .red.opacity(0.16) }
        return MIearnPalette.violet.opacity(0.06)
    }

    private var completion: some View {
        VStack(spacing: 18) {
            Image(systemName: "checkmark.seal.fill")
                .font(.system(size: 58))
                .foregroundStyle(MIearnPalette.purple)
            Text("今天的任务完成")
                .font(.largeTitle.bold())
            if let session {
                Text("新学 \(session.completedNew) · 复习 \(session.completedReview) · 首次正确 \(session.correctFirstTry)/\(session.answeredFirstTry)")
                    .foregroundStyle(.secondary)
            }
            Button("返回首页") {
                store.closeSession()
                dismiss()
            }
            .buttonStyle(PrimaryButtonStyle())
            .frame(maxWidth: 320)
        }
        .padding(24)
    }

    private func phaseTitle(_ phase: LearningPhase) -> String {
        switch phase {
        case .review: return "到期复习"
        case .browse: return "第一遍 · 浏览"
        case .consolidate: return "第二遍 · 巩固"
        case .reinforcement: return "错题强化"
        case .complete: return "已完成"
        }
    }
}

struct VariantChips: View {
    let word: WordSeed
    @Binding var detailWord: WordSeed?

    var body: some View {
        FlowLayout(spacing: 9) {
            ForEach(Array(EnglishVariantParser.parse(word.english, kind: word.kind).enumerated()), id: \.offset) { index, variant in
                HStack(spacing: 7) {
                    Button {
                        detailWord = word
                    } label: {
                        Text(variant)
                            .font(.system(size: word.kind == "PHRASE" ? 22 : 30, weight: .bold, design: .rounded))
                    }
                    Button {
                        AudioService.shared.playVariant(word: word, index: index)
                    } label: {
                        Image(systemName: "speaker.wave.1.fill")
                    }
                    .accessibilityLabel("播放 \(variant)")
                }
                .foregroundStyle(MIearnPalette.purple)
                .padding(.horizontal, 13)
                .padding(.vertical, 9)
                .background(MIearnPalette.violet.opacity(0.12), in: RoundedRectangle(cornerRadius: 14))
            }
        }
    }
}

struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) -> CGSize {
        let width = proposal.width ?? 320
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > width {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        return CGSize(width: width, height: y + rowHeight)
    }

    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > bounds.minX && x + size.width > bounds.maxX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            subview.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
