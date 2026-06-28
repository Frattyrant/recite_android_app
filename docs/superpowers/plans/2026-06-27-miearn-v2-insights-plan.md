# MIearn V2 Minimal Home and Insights Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce navigation to Learning and Test, put the primary action in thumb reach, and add
useful statistics, retention estimates, and an opt-in 10:00 reminder.

**Architecture:** The learning home combines dashboard and library discovery without duplicating
state. A pure statistics calculator consumes Room projections. A pure reminder-time calculator is
wrapped by WorkManager, while DataStore holds opt-in and time preferences.

**Tech Stack:** Jetpack Compose, Room, DataStore, WorkManager, Android notifications, JUnit 4.

---

The workspace is not a Git repository. Use verification checkpoints in place of commits.

### Task 1: Calculate statistics and memory curve

**Files:**
- Create: `app/src/main/java/com/miearn/app/domain/LearningInsights.kt`
- Create: `app/src/test/java/com/miearn/app/domain/LearningInsightsTest.kt`
- Modify: `app/src/main/java/com/miearn/app/data/local/Daos.kt`
- Modify: `app/src/main/java/com/miearn/app/data/MIearnRepository.kt`

- [ ] **Step 1: Write RED tests**

```kotlin
class LearningInsightsTest {
    @Test fun retentionUsesElapsedOverInterval() {
        assertEquals(exp(-0.5), LearningInsights.retention(5, 10), 0.0001)
        assertEquals(1.0, LearningInsights.retention(0, 10), 0.0001)
    }

    @Test fun sevenDaySeriesFillsMissingDaysWithZero() {
        val points = LearningInsights.dailySeries(
            events = listOf(DayCount(epochDay = 100, count = 3)),
            endDay = 102,
            days = 7,
        )
        assertEquals(7, points.size)
        assertEquals(3, points.first { it.epochDay == 100L }.count)
        assertEquals(0, points.first { it.epochDay == 101L }.count)
    }

    @Test fun firstTryAccuracyHandlesEmptyHistory() {
        assertEquals(0f, LearningInsights.accuracy(correct = 0, total = 0))
        assertEquals(0.75f, LearningInsights.accuracy(correct = 3, total = 4))
    }
}
```

Expected RED: insights types are absent.

- [ ] **Step 2: Implement pure calculations**

```kotlin
object LearningInsights {
    fun retention(elapsedDays: Int, intervalDays: Int): Double =
        exp(-elapsedDays.toDouble() / maxOf(1, intervalDays))

    fun accuracy(correct: Int, total: Int): Float =
        if (total == 0) 0f else correct.toFloat() / total

    fun dailySeries(events: List<DayCount>, endDay: Long, days: Int): List<DayCount> {
        val counts = events.associate { it.epochDay to it.count }
        return ((endDay - days + 1)..endDay).map { DayCount(it, counts[it] ?: 0) }
    }
}
```

- [ ] **Step 3: Add Room aggregate projections**

DAO queries return daily new/review counts, first-try correct/total, future due counts grouped by
day, and per-word interval/last-review values. Repository combines them into `InsightsSnapshot`
without loading all words.

- [ ] **Step 4: Run domain and Room tests**

Expected: all tests pass.

### Task 2: Implement opt-in 10:00 reminders

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/miearn/app/data/settings/SettingsRepository.kt`
- Create: `app/src/main/java/com/miearn/app/reminder/ReminderTime.kt`
- Create: `app/src/main/java/com/miearn/app/reminder/LearningReminderWorker.kt`
- Create: `app/src/main/java/com/miearn/app/reminder/ReminderScheduler.kt`
- Create: `app/src/test/java/com/miearn/app/reminder/ReminderTimeTest.kt`

- [ ] **Step 1: Write RED time-calculation tests**

```kotlin
class ReminderTimeTest {
    @Test fun beforeTenSchedulesToday() {
        val now = LocalDateTime.of(2026, 6, 27, 9, 30)
        assertEquals(
            LocalDateTime.of(2026, 6, 27, 10, 0),
            ReminderTime.next(now, 10, 0),
        )
    }

    @Test fun atOrAfterTenSchedulesTomorrow() {
        val now = LocalDateTime.of(2026, 6, 27, 10, 0)
        assertEquals(
            LocalDateTime.of(2026, 6, 28, 10, 0),
            ReminderTime.next(now, 10, 0),
        )
    }
}
```

- [ ] **Step 2: Implement the pure calculator**

```kotlin
object ReminderTime {
    fun next(now: LocalDateTime, hour: Int, minute: Int): LocalDateTime {
        require(hour in 0..23 && minute in 0..59)
        val today = now.toLocalDate().atTime(hour, minute)
        return if (now.isBefore(today)) today else today.plusDays(1)
    }
}
```

- [ ] **Step 3: Add WorkManager**

Add `implementation("androidx.work:work-runtime:2.11.2")` and
`androidTestImplementation("androidx.work:work-testing:2.11.2")`. Add `POST_NOTIFICATIONS` with
`tools:targetApi="33"`; do not add `INTERNET`,
`SCHEDULE_EXACT_ALARM`, or `USE_EXACT_ALARM`.

`ReminderScheduler` enqueues unique work named `miearn_daily_reminder` with the duration until
`ReminderTime.next()`. The worker:

1. Reads settings and exits without a notification when disabled.
2. Checks whether today's task is already complete.
3. Creates a low-importance `learning_reminder` channel.
4. Posts one notification opening `MainActivity`.
5. Enqueues the next one-time reminder.

- [ ] **Step 4: Extend DataStore settings**

Add `reminderEnabled = false`, `reminderHour = 10`, `reminderMinute = 0`, and
`reminderPromptShown = false`. Enabling or changing the time reschedules work; disabling cancels it.

- [ ] **Step 5: Run reminder tests and compile**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.miearn.app.reminder.ReminderTimeTest"
.\gradlew.bat compileDebugKotlin
```

Expected: both succeed.

### Task 3: Merge Home and Library into a minimal Learning tab

**Files:**
- Modify: `app/src/main/java/com/miearn/app/ui/UiModels.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/MIearnApp.kt`
- Replace: `app/src/main/java/com/miearn/app/ui/HomeScreen.kt`
- Remove routing use of: `app/src/main/java/com/miearn/app/ui/LibraryScreen.kt`
- Create: `app/src/main/java/com/miearn/app/ui/LearningHomeScreen.kt`
- Create: `app/src/androidTest/java/com/miearn/app/MinimalHomeTest.kt`

- [ ] **Step 1: Write failing navigation and reachability assertions**

```kotlin
composeRule.onNodeWithText("学习").assertIsDisplayed()
composeRule.onNodeWithText("测试").assertIsDisplayed()
composeRule.onNodeWithText("词库").assertDoesNotExist()
composeRule.onNodeWithText("开始学习").assertIsDisplayed()
val rootBounds = composeRule.onRoot().getUnclippedBoundsInRoot()
val action = composeRule.onNodeWithTag("primary-study-action")
action.assertHeightIsAtLeast(56.dp)
val actionBounds = action.getUnclippedBoundsInRoot()
assertTrue(actionBounds.bottom < rootBounds.bottom)
```

Also assert the screen contains today-new, due-review, streak, current category, search, favorite,
wrong, and insights entry semantics.

- [ ] **Step 2: Reduce `MainTab`**

```kotlin
enum class MainTab(val label: String) {
    LEARNING("学习"),
    QUIZ("测试"),
}
```

Remove the separate library route. Keep library mode and query state in the ViewModel so the merged
screen can expand search/filter content inline.

- [ ] **Step 3: Implement the thumb-reachable layout**

Use a `Scaffold` with:

- a compact top app bar;
- one scrollable content column for metrics and secondary actions;
- a bottom action area above the two-item navigation bar;
- a full-width `Button` tagged `primary-study-action`, minimum 56dp tall, 16dp horizontal padding,
  and 16–24dp spacing above navigation.

Do not put the primary button inside `LazyColumn`; it must remain reachable regardless of scroll.
Secondary entries use text/icon rows rather than promotional cards.

- [ ] **Step 4: Run Compose compilation**

Expected: device tests compile and existing primary-navigation test is updated from three tabs to
two.

### Task 4: Add the insights and reminder settings screens

**Files:**
- Create: `app/src/main/java/com/miearn/app/ui/InsightsScreen.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/SettingsDialog.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/MainViewModel.kt`
- Create: `app/src/androidTest/java/com/miearn/app/InsightsAndReminderTest.kt`

- [ ] **Step 1: Write failing UI tests**

Assert 7/30-day selector, first-try accuracy, future seven-day review labels, “预计保持率”, reminder
switch, default “10:00”, and notification permission request only after enabling.

- [ ] **Step 2: Implement a dependency-free chart**

Draw bars and retention line with Compose `Canvas`; use Material theme colors, content descriptions,
and adjacent text summaries. Do not add a chart library.

- [ ] **Step 3: Add reminder controls**

Settings show a switch and time picker row. The permission request occurs only after the user turns
the switch on. If denied, immediately restore the switch to off and show one compact explanation.

- [ ] **Step 4: Show the one-time post-session invitation**

After the first completed task, show a small bottom sheet offering “每天 10:00 提醒我” and “暂不”.
Persist `reminderPromptShown` for either choice so it never appears again automatically.

- [ ] **Step 5: Run tests and compile**

Run:

```powershell
.\gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin --offline
```

Expected: success.

### Task 5: Final V2 verification checkpoint

**Files:**
- Modify: `README.md`
- Modify: `output/reports/v2_verification_report.md`

- [ ] Run all content, audio, Kotlin, Compose compilation, and lint checks:

```powershell
& "C:\Users\LENOVO\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" `
  -m unittest tools.tests.test_content_pipeline tools.tests.test_audio_manifest -v
& "C:\Users\LENOVO\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" `
  tools\validate_audio.py --all
.\gradlew.bat clean testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug lintDebug --offline
```

- [ ] Inspect the final APK permission list and SHA-256.
- [ ] Install and run on API 29 and API 36, then repeat core learning with networking disabled.
- [ ] Verify Android 13+ notification permission is requested only after opt-in.
- [ ] Verify the large start button on a compact viewport and at 200% font scale.
- [ ] Update README with the two-tab workflow, two-pass learning, objective SM-2, default 10:00
  reminder, audio fallback behavior, and installation instructions.
