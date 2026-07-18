import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var local = AppSettings()
    @State private var permissionLabel = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("每日学习") {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("每日新词")
                            Spacer()
                            Text("\(local.dailyGoal)").font(.headline.monospacedDigit())
                        }
                        Slider(
                            value: Binding(
                                get: { Double(local.dailyGoal) },
                                set: { local.dailyGoal = Int(($0 / 5).rounded()) * 5 }
                            ),
                            in: 5...200,
                            step: 5
                        )
                        HStack {
                            Text("5")
                            Spacer()
                            Text("200")
                        }
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    }
                    Toggle("卡片自动发音", isOn: $local.autoPronounce)
                }

                Section("学习提醒") {
                    Toggle("每日提醒", isOn: $local.reminderEnabled)
                    DatePicker(
                        "提醒时间",
                        selection: reminderDate,
                        displayedComponents: .hourAndMinute
                    )
                    .datePickerStyle(.wheel)
                    .labelsHidden()
                    .frame(maxHeight: 170)
                    Text(permissionLabel).font(.caption).foregroundStyle(.secondary)
                }

                Section {
                    Text("全部内容与音频均可离线使用；应用不申请网络权限。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .scrollContentBackground(.hidden)
            .background(SoftBackground())
            .navigationTitle("设置")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("保存") {
                        Task {
                            await store.updateSettings { $0 = local }
                            dismiss()
                        }
                    }
                    .fontWeight(.semibold)
                }
            }
            .task {
                local = store.settings
                permissionLabel = await ReminderService.shared.authorizationLabel()
            }
        }
    }

    private var reminderDate: Binding<Date> {
        Binding(
            get: {
                Calendar.current.date(
                    from: DateComponents(hour: local.reminderHour, minute: local.reminderMinute)
                ) ?? Date()
            },
            set: { value in
                local.reminderHour = Calendar.current.component(.hour, from: value)
                local.reminderMinute = Calendar.current.component(.minute, from: value)
            }
        )
    }
}
