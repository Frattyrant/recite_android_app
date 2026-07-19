import Testing
import Foundation
@testable import MIearnCore

@Suite struct ReminderScheduleTests {
    @Test func schedulesLaterTodayAndThenTomorrow() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try #require(TimeZone(identifier: "Asia/Shanghai"))
        let today = try #require(
            calendar.date(from: DateComponents(year: 2026, month: 7, day: 15, hour: 9, minute: 30))
        )
        let afterTime = try #require(
            calendar.date(from: DateComponents(year: 2026, month: 7, day: 15, hour: 10, minute: 1))
        )

        let laterToday = try #require(
            ReminderSchedule.nextDate(now: today, calendar: calendar, hour: 10, minute: 0)
        )
        let tomorrow = try #require(
            ReminderSchedule.nextDate(now: afterTime, calendar: calendar, hour: 10, minute: 0)
        )

        #expect(calendar.component(.day, from: laterToday) == 15)
        #expect(calendar.component(.day, from: tomorrow) == 16)
        #expect(calendar.component(.hour, from: tomorrow) == 10)
    }

    @Test func rejectsInvalidTime() {
        #expect(
            ReminderSchedule.nextDate(
                now: Date(),
                calendar: .current,
                hour: 24,
                minute: 0
            ) == nil
        )
    }
}
