# Vocabulary File Compatibility and Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Extend offline vocabulary import to robustly accept XLSX, CSV, TSV and plain TXT files while making every copy, parse, worker and failure state visible and recoverable in the UI.

**Architecture:** Keep the existing SAF → internal cache → WorkManager → Room draft pipeline, but separate content detection from filename detection. Create the Room import job before copying so the UI can observe it immediately; use a content-signature detector, a shared text decoder, typed import failures and a persisted active-job query for process recovery. Do not add a production dependency or network permission.

**Tech Stack:** Kotlin, Jetpack Compose, Room, WorkManager, coroutines/Flow, SAX XML parsing, Java NIO charsets.

**Spec:** Sol architecture report from the 2026-08-24 import compatibility review.

## Global Constraints

- Support `.xlsx`, `.csv`, `.tsv`, and `.txt`; reject legacy binary `.xls`, encrypted workbooks, `.xlsm`, ordinary ZIP and unknown binary files with actionable messages.
- Accept UTF-8, UTF-8 BOM, UTF-16LE, UTF-16BE and GB18030 text.
- TXT with stable tabular columns uses tabs; otherwise each non-empty line is one complete vocabulary row. Never split TXT rows on semicolons.
- Detect content by signature first; filename and MIME are hints only.
- Create the import job before copying and expose copy/prepare/failure states immediately.
- Keep SAF-only file access, no `INTERNET`, no broad storage permission, 20 MB input limit and 20,000-row limit.
- Preserve Room user progress and custom vocabulary data; schema changes require an explicit migration test.
- No Apache POI, FastExcel, or other production dependency additions.

---

### Task 1: Content Detection and Text Decoding

**Files:**
- Create: `app/src/main/java/com/miearn/app/importing/VocabularyFileDetector.kt`
- Create: `app/src/main/java/com/miearn/app/importing/TextDocumentDecoder.kt`
- Create: `app/src/main/java/com/miearn/app/importing/DelimitedTextVocabularyReader.kt`
- Modify: `app/src/main/java/com/miearn/app/importing/ImportModels.kt`
- Modify: `app/src/main/java/com/miearn/app/importing/VocabularyFileReader.kt`
- Test: `app/src/test/java/com/miearn/app/importing/VocabularyFileDetectorTest.kt`
- Test: `app/src/test/java/com/miearn/app/importing/TextDocumentDecoderTest.kt`
- Test: `app/src/test/java/com/miearn/app/importing/DelimitedTextVocabularyReaderTest.kt`

**Interfaces:**
- `VocabularyFileDetector.detect(fileName: String, mimeType: String?, prefix: ByteArray): DetectedVocabularyFile`
- `TextDocumentDecoder.decode(bytes: ByteArray): DecodedText`
- `VocabularyFileReader.read(fileName: String, mimeType: String?, input: InputStream): VocabularyReadResult`

- [ ] Write failing tests for UTF-16 BOM, TSV, line-oriented TXT, OLE `.xls`, fake ZIP and unknown binary.
- [ ] Run the focused tests and record the expected unresolved-type failures.
- [ ] Implement signature-first detection and decoder fallback order.
- [ ] Implement bounded delimited text parsing with existing CSV quote semantics.
- [ ] Run focused tests and the existing CSV/XLSX reader tests.

### Task 2: Immediate Job Creation and Copy/Worker Failure Mapping

**Files:**
- Modify: `app/src/main/java/com/miearn/app/importing/ImportWorkCoordinator.kt`
- Modify: `app/src/main/java/com/miearn/app/importing/ImportFileStore.kt`
- Modify: `app/src/main/java/com/miearn/app/importing/PrepareImportWorker.kt`
- Modify: `app/src/main/java/com/miearn/app/importing/CommitImportWorker.kt`
- Modify: `app/src/main/java/com/miearn/app/importing/ImportModels.kt`
- Modify: `app/src/main/java/com/miearn/app/data/local/ImportDaos.kt`
- Modify: `app/src/main/java/com/miearn/app/data/local/ImportEntities.kt` only if copy byte counters are required; add a Room migration and schema test when needed.
- Test: `app/src/test/java/com/miearn/app/importing/ImportWorkCoordinatorTest.kt`
- Test: `app/src/test/java/com/miearn/app/importing/ImportFailureMappingTest.kt`

**Interfaces:**
- Create/import job before opening and copying the selected URI.
- Persist a stable failure code/message pair without logging URI or vocabulary contents.
- Keep internal files for retryable failures; delete them only on completed, cancelled, or non-retryable failure.

- [ ] Write failing tests for a slow input stream that must expose a job before copy completes, null stream, `SecurityException`, unsupported format, and worker parse failure.
- [ ] Run focused tests to verify the current late-job and silent-failure behavior.
- [ ] Implement bounded copy progress and typed error mapping.
- [ ] Add recent-active-job DAO observation and process-recovery behavior.
- [ ] Run coordinator, worker, Room and import repository tests.

### Task 3: Import UI State and Recovery UX

**Files:**
- Create: `app/src/main/java/com/miearn/app/ui/importing/ImportUiState.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/MIearnApp.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/importing/ImportWizardScreen.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/importing/ImportStepIndicator.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/V21LearningHomeScreen.kt`
- Test: `app/src/test/java/com/miearn/app/ui/ImportUiStateTest.kt`
- Test: `app/src/androidTest/java/com/miearn/app/ImportWizardFeedbackTest.kt`

**Interfaces:**
- Replace the split `job + localError` presentation with a state mapper that exposes Empty, Copying, Preparing, Mapping, Confirmation, Committing, Completed and Failed states.
- Failed state must show stage, cause, recovery hint and actions for retry/reselect/close.

- [ ] Write failing Compose/state tests for immediate copying feedback, TXT/XLSX copy text, persistent error card, retry and process recovery.
- [ ] Implement state mapping and automatic observation of the latest active job.
- [ ] Update picker to accept all files and let content detection provide the final error.
- [ ] Add clear TXT/TSV guidance and progress/error UI without changing the existing mapping or conflict behavior.
- [ ] Run Compose and ViewModel tests.

### Task 4: Regression, Documentation and Release Gates

**Files:**
- Modify: `长期记忆.md`
- Test: existing import, Room, WorkManager and Compose test suites.

- [ ] Add durable notes for SAF MIME/filename unreliability, UTF-16 Excel exports, create-job-before-copy ordering and retryable failures.
- [ ] Run `./gradlew test`.
- [ ] Run `./gradlew assembleDebug`.
- [ ] Run `./gradlew lint`.
- [ ] Run `./gradlew verifyDebugApkSize`.
- [ ] Verify no `INTERNET`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` or `MANAGE_EXTERNAL_STORAGE` permissions.
- [ ] Verify APK size remains below the current debug gate and leave version/tag/release unchanged until user acceptance.
