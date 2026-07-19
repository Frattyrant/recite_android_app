import AVFoundation
import AudioToolbox
import Foundation
import MIearnCore
import UserNotifications

@MainActor
final class AudioService: NSObject, AVAudioPlayerDelegate, AVSpeechSynthesizerDelegate {
    static let shared = AudioService()

    private var player: AVAudioPlayer?
    private let synthesizer = AVSpeechSynthesizer()

    private override init() {
        super.init()
        synthesizer.delegate = self
    }

    func play(word: WordSeed) {
        stop()
        if playBundled(relativePath: word.audioAsset.replacingOccurrences(of: ".ogg", with: ".m4a")) {
            return
        }
        speak(EnglishVariantParser.parse(word.english, kind: word.kind))
    }

    func playVariant(word: WordSeed, index: Int) {
        stop()
        let variants = EnglishVariantParser.parse(word.english, kind: word.kind)
        guard variants.indices.contains(index) else { return }
        let path = "audio/variants/\(word.id)_\(String(format: "%02d", index)).m4a"
        if playBundled(relativePath: path) { return }
        speak([variants[index]])
    }

    func stop() {
        player?.stop()
        player = nil
        synthesizer.stopSpeaking(at: .immediate)
    }

    func answerFeedback(correct: Bool) {
        stop()
        AudioServicesPlaySystemSound(correct ? 1057 : 1073)
    }

    @discardableResult
    private func playBundled(relativePath: String) -> Bool {
        let path = relativePath as NSString
        let directory = path.deletingLastPathComponent
        let name = (path.lastPathComponent as NSString).deletingPathExtension
        let url = Bundle.main.url(
            forResource: name,
            withExtension: "m4a",
            subdirectory: directory.isEmpty ? nil : directory
        )
        guard let url else { return false }
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
            try AVAudioSession.sharedInstance().setActive(true)
            let next = try AVAudioPlayer(contentsOf: url)
            next.delegate = self
            next.prepareToPlay()
            player = next
            return next.play()
        } catch {
            return false
        }
    }

    private func speak(_ segments: [String]) {
        guard !segments.isEmpty else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
        try? AVAudioSession.sharedInstance().setActive(true)
        for (index, segment) in segments.enumerated() {
            let utterance = AVSpeechUtterance(string: EnglishVariantParser.toSpeechText(segment))
            utterance.voice = AVSpeechSynthesisVoice(language: "en-US")
            utterance.rate = 0.46
            utterance.preUtteranceDelay = index == 0 ? 0 : 0.5
            synthesizer.speak(utterance)
        }
    }
}

actor ReminderService {
    static let shared = ReminderService()
    private let identifier = "miearn.daily.study.v232"

    func reconcile(settings: AppSettings) async {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [identifier])
        guard settings.reminderEnabled else { return }
        do {
            let granted = try await center.requestAuthorization(options: [.alert, .badge, .sound])
            guard granted else { return }
            let content = UNMutableNotificationContent()
            content.title = "MIearn 学习提醒"
            content.body = "用几分钟完成今天的新词与复习。"
            content.sound = .default
            var components = DateComponents()
            components.hour = settings.reminderHour
            components.minute = settings.reminderMinute
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
            try await center.add(
                UNNotificationRequest(identifier: identifier, content: content, trigger: trigger)
            )
        } catch {
            // Settings UI reads the system permission state; scheduling remains fail-closed.
        }
    }

    func authorizationLabel() async -> String {
        let status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        switch status {
        case .authorized, .provisional, .ephemeral: return "系统通知已允许"
        case .denied: return "系统通知已关闭"
        case .notDetermined: return "开启时将请求通知权限"
        @unknown default: return "通知状态未知"
        }
    }
}
