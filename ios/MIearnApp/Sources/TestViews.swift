import MIearnCore
import SwiftUI

enum TestMode: String, CaseIterable, Identifiable {
    case enToZh = "英选中"
    case zhToEn = "中选英"
    case spelling = "拼写"
    case listening = "听音选词"
    case blank = "例句填空"
    var id: String { rawValue }
}

struct TestSetupView: View {
    @EnvironmentObject private var store: AppStore
    @State private var mode: TestMode = .enToZh
    @State private var count = 10
    @State private var learnedOnly = true
    @State private var wrongOnly = false
    @State private var testing = false

    private var pool: [WordSeed] {
        if wrongOnly { return store.wrongWords }
        if learnedOnly {
            return store.words.filter { store.progress[$0.id]?.study.firstLearnedEpochDay != nil }
        }
        return store.words
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("测试模式").font(.headline)
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 145), spacing: 12)], spacing: 12) {
                        ForEach(TestMode.allCases) { item in
                            Button {
                                mode = item
                            } label: {
                                Text(item.rawValue)
                                    .font(.subheadline.weight(.semibold))
                                    .frame(maxWidth: .infinity, minHeight: 58)
                                    .background(
                                        mode == item ? MIearnPalette.purple : MIearnPalette.violet.opacity(0.10),
                                        in: RoundedRectangle(cornerRadius: 16)
                                    )
                                    .foregroundStyle(mode == item ? .white : .primary)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    Text("题量").font(.headline)
                    Picker("题量", selection: $count) {
                        ForEach([10, 20, 30], id: \.self) { Text("\($0)").tag($0) }
                    }
                    .pickerStyle(.segmented)
                    SoftCard {
                        VStack(spacing: 12) {
                            Toggle("仅测试已学词条", isOn: $learnedOnly)
                            Toggle("错题重测", isOn: $wrongOnly)
                        }
                    }
                    Text("可用题目：\(pool.count)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Spacer(minLength: 90)
                }
                .padding(18)
            }
            .background(SoftBackground())
            .navigationTitle("测试")
            .safeAreaInset(edge: .bottom) {
                Button("开始测试") { testing = true }
                    .buttonStyle(PrimaryButtonStyle())
                    .disabled(pool.isEmpty)
                    .padding(18)
                    .background(.ultraThinMaterial)
            }
            .fullScreenCover(isPresented: $testing) {
                QuizSessionView(mode: mode, words: Array(pool.shuffled().prefix(count)))
            }
        }
    }
}

struct QuizSessionView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let mode: TestMode
    let words: [WordSeed]
    @State private var index = 0
    @State private var score = 0
    @State private var answered = false
    @State private var selected = ""
    @State private var input = ""

    private var current: WordSeed? { words.indices.contains(index) ? words[index] : nil }

    var body: some View {
        NavigationStack {
            ZStack {
                SoftBackground()
                if index >= words.count {
                    VStack(spacing: 18) {
                        Image(systemName: "checkmark.seal.fill").font(.system(size: 56)).foregroundStyle(MIearnPalette.purple)
                        Text("测试完成").font(.largeTitle.bold())
                        Text("\(score) / \(words.count)").font(.title2.monospacedDigit())
                        Button("返回") { dismiss() }.buttonStyle(PrimaryButtonStyle()).frame(maxWidth: 300)
                    }
                } else if let word = current {
                    VStack(spacing: 20) {
                        HStack {
                            Text(mode.rawValue).font(.headline)
                            Spacer()
                            Text("\(index + 1)/\(words.count)").monospacedDigit()
                        }
                        ProgressView(value: Double(index + 1), total: Double(max(1, words.count)))
                            .tint(MIearnPalette.purple)
                        Spacer()
                        question(word)
                        Spacer()
                    }
                    .padding(20)
                    .task(id: index) {
                        answered = false
                        selected = ""
                        input = ""
                        if mode == .listening { AudioService.shared.play(word: word) }
                    }
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { dismiss() } label: { Image(systemName: "xmark").frame(width: 44, height: 44) }
                }
            }
            .onDisappear { AudioService.shared.stop() }
        }
    }

    @ViewBuilder
    private func question(_ word: WordSeed) -> some View {
        switch mode {
        case .enToZh:
            choiceQuestion(prompt: word.english, answer: word.chinese, candidates: store.words.map(\.chinese), word: word)
        case .zhToEn:
            choiceQuestion(prompt: word.chinese, answer: word.primaryEnglish, candidates: store.words.map(\.primaryEnglish), word: word)
        case .listening:
            VStack(spacing: 20) {
                Button { AudioService.shared.play(word: word) } label: {
                    Image(systemName: "speaker.wave.2.fill").font(.system(size: 44))
                }
                choiceQuestion(prompt: "选择你听到的表达", answer: word.primaryEnglish, candidates: store.words.map(\.primaryEnglish), word: word)
            }
        case .spelling:
            typingQuestion(prompt: word.chinese, answer: word.primaryEnglish, word: word)
        case .blank:
            typingQuestion(
                prompt: QuizEngine.blankExample(word.exampleEn, primaryEnglish: word.primaryEnglish),
                answer: word.primaryEnglish,
                word: word
            )
        }
    }

    private func choiceQuestion(prompt: String, answer: String, candidates: [String], word: WordSeed) -> some View {
        let options = QuizEngine.choiceOptions(answer: answer, candidates: candidates, seed: word.id.hashValue)
        return VStack(spacing: 16) {
            Text(prompt).font(.system(size: 28, weight: .bold, design: .rounded)).multilineTextAlignment(.center)
            ForEach(options, id: \.self) { option in
                Button {
                    grade(option == answer, selection: option, word: word)
                } label: {
                    Text(option).frame(maxWidth: .infinity, alignment: .leading).padding(15)
                        .background(choiceColor(option, answer: answer), in: RoundedRectangle(cornerRadius: 16))
                }
                .buttonStyle(.plain)
                .disabled(answered)
            }
        }
    }

    private func typingQuestion(prompt: String, answer: String, word: WordSeed) -> some View {
        VStack(spacing: 18) {
            Text(prompt).font(.title2.bold()).multilineTextAlignment(.center)
            TextField("输入答案", text: $input)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textFieldStyle(.roundedBorder)
                .font(.title3)
            if answered { Text("正确答案：\(answer)").foregroundStyle(.secondary) }
            Button("确认") {
                grade(QuizEngine.isSpellingCorrect(answer: input, expected: answer), selection: input, word: word)
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || answered)
        }
    }

    private func grade(_ correct: Bool, selection: String, word: WordSeed) {
        guard !answered else { return }
        answered = true
        selected = selection
        if correct { score += 1 } else {
            var progress = store.progress[word.id] ?? StoredWordProgress()
            progress.wrongCount += 1
            store.progress[word.id] = progress
            store.persist()
        }
        AudioService.shared.answerFeedback(correct: correct)
        Task {
            try? await Task.sleep(for: .milliseconds(620))
            index += 1
        }
    }

    private func choiceColor(_ option: String, answer: String) -> Color {
        guard answered else { return MIearnPalette.violet.opacity(0.10) }
        if option == answer { return .green.opacity(0.18) }
        if option == selected { return .red.opacity(0.16) }
        return MIearnPalette.violet.opacity(0.05)
    }
}
