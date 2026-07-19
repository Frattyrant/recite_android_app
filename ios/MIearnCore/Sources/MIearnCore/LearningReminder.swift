import Foundation

public enum ReminderSchedule {
    public static func nextDate(
        now: Date,
        calendar: Calendar,
        hour: Int,
        minute: Int
    ) -> Date? {
        guard (0...23).contains(hour), (0...59).contains(minute) else { return nil }
        return calendar.nextDate(
            after: now,
            matching: DateComponents(hour: hour, minute: minute, second: 0),
            matchingPolicy: .nextTime,
            repeatedTimePolicy: .first,
            direction: .forward
        )
    }
}

#if canImport(UserNotifications)
import UserNotifications

public actor LearningReminderService {
    public static let requestIdentifier = "com.miearn.app.daily-learning"
    public static let testRequestIdentifier = "com.miearn.app.test-learning"

    private let center: UNUserNotificationCenter

    public init(center: UNUserNotificationCenter = .current()) {
        self.center = center
    }

    public func requestAuthorization() async throws -> Bool {
        try await center.requestAuthorization(options: [.alert, .sound, .badge])
    }

    public func authorizationStatus() async -> UNAuthorizationStatus {
        await center.notificationSettings().authorizationStatus
    }

    public func reconcile(enabled: Bool, hour: Int, minute: Int) async throws {
        center.removePendingNotificationRequests(withIdentifiers: [Self.requestIdentifier])
        guard enabled, (0...23).contains(hour), (0...59).contains(minute) else { return }

        let content = UNMutableNotificationContent()
        content.title = "MIearn 今日学习"
        content.body = "新词与到期复习已经准备好，花几分钟继续积累。"
        content.sound = .default
        content.categoryIdentifier = "LEARNING_REMINDER"

        let trigger = UNCalendarNotificationTrigger(
            dateMatching: DateComponents(hour: hour, minute: minute),
            repeats: true
        )
        try await center.add(
            UNNotificationRequest(
                identifier: Self.requestIdentifier,
                content: content,
                trigger: trigger
            )
        )
    }

    public func sendTest() async throws {
        center.removePendingNotificationRequests(withIdentifiers: [Self.testRequestIdentifier])
        let content = UNMutableNotificationContent()
        content.title = "MIearn 提醒测试"
        content.body = "通知可以正常送达。"
        content.sound = .default
        try await center.add(
            UNNotificationRequest(
                identifier: Self.testRequestIdentifier,
                content: content,
                trigger: UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)
            )
        )
    }

    public func disable() {
        center.removePendingNotificationRequests(withIdentifiers: [Self.requestIdentifier])
    }
}
#endif
