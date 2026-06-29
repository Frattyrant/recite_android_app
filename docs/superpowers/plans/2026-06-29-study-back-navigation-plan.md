# Study Back Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Android system back and full-screen edge-swipe close every active study state and return to the learning home.

**Architecture:** A focused Compose back-handler wrapper is enabled whenever `StudyUiState` is not idle and delegates to the existing `MainViewModel.closeStudy()`. An instrumentation test drives the real `OnBackPressedDispatcher` so the behavior is tested at the Android/Compose boundary.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Activity Compose, JUnit4, Android instrumentation tests

---

### Task 1: Add System Back Coverage for Study

**Files:**
- Create: `app/src/androidTest/java/com/miearn/app/ui/StudyBackHandlerTest.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/MIearnApp.kt`

- [ ] **Step 1: Write the failing instrumentation test**

```kotlin
package com.miearn.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyBackHandlerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun systemBackClosesVisibleStudy() {
        var closes = 0
        composeRule.setContent {
            val enabled = remember { mutableIntStateOf(1) }
            StudyBackHandler(enabled = enabled.intValue == 1) {
                closes += 1
            }
            Text("study")
        }

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.runOnIdle {
            assertEquals(1, closes)
        }
    }
}
```

- [ ] **Step 2: Compile the test to verify RED**

Run:

```powershell
.\gradlew.bat compileDebugAndroidTestKotlin --no-daemon --max-workers=1
```

Expected: compilation fails because `StudyBackHandler` does not exist.

- [ ] **Step 3: Add the minimal Compose handler and wire it to study state**

```kotlin
import androidx.activity.compose.BackHandler

@Composable
internal fun StudyBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled, onBack = onBack)
}
```

Inside the non-idle study branch:

```kotlin
StudyBackHandler(enabled = true, onBack = viewModel::closeStudy)
StudyScreen(
    // existing arguments
)
```

- [ ] **Step 4: Compile and run the focused Android test**

Run:

```powershell
.\gradlew.bat compileDebugAndroidTestKotlin --no-daemon --max-workers=1
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.miearn.app.ui.StudyBackHandlerTest --no-daemon --max-workers=1
```

Expected: compilation succeeds and the focused test passes when an emulator is available. If no emulator is available, preserve the successful compile result and report the device limitation.

- [ ] **Step 5: Commit the navigation change**

```powershell
git add app/src/main/java/com/miearn/app/ui/MIearnApp.kt app/src/androidTest/java/com/miearn/app/ui/StudyBackHandlerTest.kt
git commit -m "fix: handle system back during study"
```
