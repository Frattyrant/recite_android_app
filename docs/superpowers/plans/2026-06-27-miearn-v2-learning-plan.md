# MIearn V2 Two-pass Learning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace subjective new-word ratings with review → browse → consolidate, backed by SM-2,
wrong-answer reinforcement, and process-death-safe sessions.

**Architecture:** Pure Kotlin schedulers and a session state machine define behavior independently
of Compose and Room. Room v2 stores SM-2 state, immutable review events, and the current session.
The ViewModel translates state-machine output into repository writes and a compact task UI.

**Tech Stack:** Kotlin, Room 2.8.4, coroutines/Flow, Jetpack Compose, JUnit 4, Robolectric.

---

The workspace is not a Git repository. Use verification checkpoints in place of commits.

### Task 1: Implement objective SM-2

**Files:**
- Replace: `app/src/main/java/com/miearn/app/domain/ReviewScheduler.kt`
- Modify: `app/src/main/java/com/miearn/app/domain/ReviewModels.kt`
- Replace: `app/src/test/java/com/miearn/app/domain/ReviewSchedulerTest.kt`

- [ ] **Step 1: Write RED tests for qualities 2 and 5**

```kotlin
class ReviewSchedulerTest {
    @Test fun firstCorrectSchedulesTomorrow() {
        val result = ReviewScheduler.grade(StudyProgress(), quality = 5, todayEpochDay = 100)
        assertEquals(1, result.progress.repetitions)
        assertEquals(1, result.progress.intervalDays)
        assertEquals(101, result.progress.nextReviewEpochDay)
    }

    @Test fun secondCorrectSchedulesSixDays() {
        val current = StudyProgress(repetitions = 1, intervalDays = 1, easeFactor = 2.5)
        val result = ReviewScheduler.grade(current, quality = 5, todayEpochDay = 101)
        assertEquals(2, result.progress.repetitions)
        assertEquals(6, result.progress.intervalDays)
        assertEquals(107, result.progress.nextReviewEpochDay)
    }

    @Test fun wrongAnswerResetsRepetitionsAndAddsLapse() {
        val current = StudyProgress(repetitions = 4, intervalDays = 20, lapseCount = 2)
        val result = ReviewScheduler.grade(current, quality = 2, todayEpochDay = 200)
        assertEquals(0, result.progress.repetitions)
        assertEquals(1, result.progress.intervalDays)
        assertEquals(3, result.progress.lapseCount)
        assertEquals(201, result.progress.nextReviewEpochDay)
    }

    @Test fun easeFactorNeverFallsBelowOnePointThree() {
        var progress = StudyProgress(easeFactor = 1.3)
        repeat(10) { progress = ReviewScheduler.grade(progress, 2, 300L + it).progress }
        assertEquals(1.3, progress.easeFactor, 0.0001)
    }
}
```

Run the focused test. Expected: compilation failure because the quality-based model does not exist.

- [ ] **Step 2: Implement the SM-2 formula**

`StudyProgress` contains `easeFactor`, `intervalDays`, `repetitions`, `lapseCount`,
`nextReviewEpochDay`, `lastReviewedEpochDay`, `firstLearnedEpochDay`, and `mastered`.

```kotlin
data class StudyProgress(
    val easeFactor: Double = 2.5,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val lapseCount: Int = 0,
    val nextReviewEpochDay: Long = 0,
    val lastReviewedEpochDay: Long? = null,
    val firstLearnedEpochDay: Long? = null,
    val mastered: Boolean = false,
)

data class ReviewOutcome(val progress: StudyProgress)

object ReviewScheduler {
    fun grade(current: StudyProgress, quality: Int, todayEpochDay: Long): ReviewOutcome {
        require(quality in 0..5)
        val nextEase = maxOf(
            1.3,
            current.easeFactor + 0.1 -
                (5 - quality) * (0.08 + (5 - quality) * 0.02),
        )
        val repetitions = if (quality < 3) 0 else current.repetitions + 1
        val interval = when {
            quality < 3 -> 1
            repetitions == 1 -> 1
            repetitions == 2 -> 6
            else -> maxOf(1, (current.intervalDays * nextEase).roundToInt())
        }
        val firstLearned = current.firstLearnedEpochDay ?: todayEpochDay
        return ReviewOutcome(
            progress = current.copy(
                easeFactor = nextEase,
                intervalDays = interval,
                repetitions = repetitions,
                lapseCount = current.lapseCount + if (quality < 3) 1 else 0,
                nextReviewEpochDay = todayEpochDay + interval,
                lastReviewedEpochDay = todayEpochDay,
                firstLearnedEpochDay = firstLearned,
                mastered = repetitions >= 3 && interval >= 21,
            ),
        )
    }
}
```

- [ ] **Step 3: Run focused and full tests**

Expected: focused tests pass. Update old call sites only after the scheduler is green.

### Task 2: Add Room v1→v2 migration and review history

**Files:**
- Modify: `app/src/main/java/com/miearn/app/data/local/Entities.kt`
- Modify: `app/src/main/java/com/miearn/app/data/local/Daos.kt`
- Modify: `app/src/main/java/com/miearn/app/data/local/AppDatabase.kt`
- Create: `app/src/androidTest/java/com/miearn/app/data/Migration1To2Test.kt`
- Modify: `app/schemas/com.miearn.app.data.local.AppDatabase/2.json`

- [ ] **Step 1: Write a failing migration test**

Create a version-1 database containing one word and progress row with `isFavorite = 1`,
`wrongCount = 2`, and `lastStudiedEpochDay = 20000`. Migrate it with `MIGRATION_1_2`, then assert:

```kotlin
assertEquals(true, progress.isFavorite)
assertEquals(2, progress.wrongCount)
assertEquals(20_000L, progress.lastStudiedEpochDay)
assertEquals(2.5, progress.easeFactor, 0.0001)
assertEquals(0, progress.repetitions)
```

Run `compileDebugAndroidTestKotlin`. Expected: compilation failure because `MIGRATION_1_2` and the
new fields do not exist.

- [ ] **Step 2: Extend progress and add event/session entities**

```kotlin
data class ProgressEntity(
    @PrimaryKey val wordId: String,
    val easeFactor: Double = 2.5,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val lapseCount: Int = 0,
    val nextReviewEpochDay: Long = 0,
    val mastered: Boolean = false,
    val isFavorite: Boolean = false,
    val wrongCount: Int = 0,
    val lastStudiedEpochDay: Long? = null,
    val lastReviewedEpochDay: Long? = null,
    val firstLearnedEpochDay: Long? = null,
)

@Entity(tableName = "review_events", indices = [Index("wordId"), Index("epochDay")])
data class ReviewEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: String,
    val category: String,
    val epochMillis: Long,
    val epochDay: Long,
    val phase: String,
    val firstCorrect: Boolean,
    val quality: Int,
    val responseMillis: Long,
    val scheduledIntervalDays: Int,
    val nextReviewEpochDay: Long,
)

@Entity(tableName = "study_session")
data class StudySessionEntity(
    @PrimaryKey val slot: Int = 1,
    val epochDay: Long,
    val category: String,
    val phase: String,
    val reviewIdsJson: String,
    val newIdsJson: String,
    val reinforcementIdsJson: String,
    val index: Int,
    val completedNew: Int,
    val completedReview: Int,
    val correctFirstTry: Int,
    val answeredFirstTry: Int,
    val cardExpanded: Boolean,
)
```

- [ ] **Step 3: Implement `MIGRATION_1_2`**

Use explicit `ALTER TABLE progress ADD COLUMN` statements with non-null defaults for SM-2 fields,
then create `review_events`, its indices, and `study_session`. Set database version to 2 and register
the migration in `AppDatabase.create()`. Do not use destructive migration.

- [ ] **Step 4: Compile the migration test and run Room unit tests**

Run:

```powershell
.\gradlew.bat compileDebugAndroidTestKotlin testDebugUnitTest --offline
```

Expected: build succeeds and Room schema 2 is exported.

### Task 3: Build the three-stage session state machine

**Files:**
- Create: `app/src/main/java/com/miearn/app/domain/LearningSession.kt`
- Create: `app/src/test/java/com/miearn/app/domain/LearningSessionTest.kt`
- Remove use of: `app/src/main/java/com/miearn/app/domain/SessionQueue.kt`

- [ ] **Step 1: Write RED transition tests**

```kotlin
class LearningSessionTest {
    @Test fun sessionRunsReviewThenBrowseThenConsolidate() {
        var state = LearningSession.start(listOf("r1"), listOf("n1", "n2"))
        assertEquals(LearningPhase.REVIEW, state.phase)
        state = state.answer(firstCorrect = true)
        assertEquals(LearningPhase.BROWSE, state.phase)
        state = state.next()
        state = state.next()
        assertEquals(LearningPhase.CONSOLIDATE, state.phase)
    }

    @Test fun wrongAnswerAppearsOnceAtEndAndDoesNotCountTwice() {
        var state = LearningSession.start(listOf("r1"), emptyList())
        state = state.answer(firstCorrect = false)
        assertEquals(LearningPhase.REINFORCEMENT, state.phase)
        assertEquals("r1", state.currentId)
        state = state.answer(firstCorrect = true)
        assertEquals(1, state.answeredFirstTry)
        assertEquals(0, state.correctFirstTry)
        assertEquals(LearningPhase.COMPLETE, state.phase)
    }
}
```

Expected RED: learning types do not exist.

- [ ] **Step 2: Implement immutable session state**

Create `LearningPhase { REVIEW, BROWSE, CONSOLIDATE, REINFORCEMENT, COMPLETE }` and an immutable
`LearningSession` data class containing review IDs, new IDs, reinforcement IDs, index, first-try
counters, and expanded state. `answer()` adds the current ID to reinforcement only on a first-try
wrong answer. Reinforcement answers never change first-try counters.

- [ ] **Step 3: Cover empty stages and resumption**

Add tests for: no due reviews, no new words, all lists empty, restored reinforcement, and toggling
card expansion only in browse. Run the focused suite until green.

### Task 4: Persist objective grading exactly once

**Files:**
- Modify: `app/src/main/java/com/miearn/app/data/MIearnRepository.kt`
- Modify: `app/src/main/java/com/miearn/app/data/local/Daos.kt`
- Create: `app/src/test/java/com/miearn/app/data/ObjectiveReviewRepositoryTest.kt`

- [ ] **Step 1: Write RED repository tests**

Test that a first wrong answer:

```kotlin
repository.recordFirstAnswer(word, LearningPhase.REVIEW, false, 1200, today)
val progress = progressDao.getByWordId(word.id)!!
assertEquals(1, progress.lapseCount)
assertEquals(1, progress.wrongCount)
assertEquals(1, eventDao.countForWord(word.id))
```

Then call `recordReinforcementAnswer(word.id, true)` and assert lapse, wrong count, event count, and
daily activity did not increment again.

- [ ] **Step 2: Implement transaction boundaries**

`recordFirstAnswer()` uses `quality = 5` for correct and `quality = 2` for wrong, updates SM-2,
updates wrong weight, inserts exactly one event, and increments daily new/review counts once.
`recordReinforcementAnswer()` only advances the session.

Correct first answers decrement `wrongCount` by one without going below zero. Wrong first answers
increment it by one. DAO queries order due items by `wrongCount DESC`, due day, then source order.

- [ ] **Step 3: Add session save/restore methods**

Implement `saveSession`, `loadSession`, and `clearSession` using `StudySessionEntity`. JSON arrays
use `org.json.JSONArray`, which is available on Android and Robolectric; no new serialization
dependency is required.

- [ ] **Step 4: Run data and domain suites**

Expected: all existing and new tests pass.

### Task 5: Replace the rating UI with browse and choice cards

**Files:**
- Modify: `app/src/main/java/com/miearn/app/ui/UiModels.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/MainViewModel.kt`
- Replace: `app/src/main/java/com/miearn/app/ui/StudyScreen.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/MIearnApp.kt`
- Create: `app/src/androidTest/java/com/miearn/app/TwoPassLearningTest.kt`

- [ ] **Step 1: Write failing Compose assertions**

The test starts a session with two new words and asserts:

```kotlin
composeRule.onNodeWithText("浏览 1/2").assertIsDisplayed()
composeRule.onNodeWithText("不认识").assertDoesNotExist()
composeRule.onNodeWithText("模糊").assertDoesNotExist()
composeRule.onNodeWithText("认识").assertDoesNotExist()
composeRule.onNodeWithContentDescription("展开释义").performClick()
composeRule.onNodeWithText(firstWord.chinese).assertIsDisplayed()
```

After browse completes, assert four Chinese options appear. On a wrong choice, assert
`"已加入本轮强化"` and that the word reappears after the remaining questions.

- [ ] **Step 2: Define the UI state**

`StudyUiState.Active` contains:

```kotlin
data class Active(
    val phase: LearningPhase,
    val word: WordEntity,
    val index: Int,
    val total: Int,
    val expanded: Boolean,
    val options: List<String>,
    val selectedAnswer: String? = null,
    val correctAnswer: String? = null,
    val firstCorrect: Boolean? = null,
    val pronunciationStatus: PronunciationStatus,
)
```

- [ ] **Step 3: Implement browse gestures and consolidation choices**

Use `HorizontalPager` for browse cards. Every page settles by calling one ViewModel method that
persists the index and plays the current word. Card click toggles full details. Keep play and favorite
buttons visible in both states.

Review and consolidation use a static card with four unique Chinese options. Disable choices after
the first tap, show concise feedback, then expose one large “继续” button.

- [ ] **Step 4: Ensure autoplay is keyed to the actual current word**

Use one event from the ViewModel after persisted state changes. Avoid both a ViewModel play call and
a Compose `LaunchedEffect` for the same transition, which would double-play.

- [ ] **Step 5: Compile device tests and run all unit tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin --offline
```

Expected: build succeeds.

### Task 6: Learning verification checkpoint

- [ ] Run clean build and lint:

```powershell
.\gradlew.bat clean testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug lintDebug --offline
```

- [ ] Install `emulator`, `system-images;android-29;google_apis;x86_64`, and
  `system-images;android-36;google_apis;x86_64`; create AVDs named `miearn_api29` and
  `miearn_api36`; run `connectedDebugAndroidTest` once against each AVD and record both results.
- [ ] Record test totals, migration result, and unresolved device checks in
  `output/reports/v2_verification_report.md`.
