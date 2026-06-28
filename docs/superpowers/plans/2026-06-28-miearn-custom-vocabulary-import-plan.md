# MIearn Custom Vocabulary Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fully offline XLSX/CSV vocabulary import flow with local ECDICT enrichment, multi-source vocabulary membership, persistent progress, source management, and strict APK size gates.

**Architecture:** Room v3 stores canonical words separately from vocabulary sources and links them through a many-to-many cross-reference. A prepare Worker parses and enriches rows into an import draft; after the user chooses a conflict policy, a commit Worker applies the draft in one idempotent transaction. A separately packaged, gzip-compressed ECDICT SQLite database supplies phonetics, translations, and inflections without adding network permission.

**Tech Stack:** Kotlin, Jetpack Compose, Room 2.8.4, WorkManager 2.11.2, FastExcel Reader 0.20.2, Android Storage Access Framework, SQLite, Python content tooling, JUnit/Robolectric, Compose UI tests.

---

## Working Agreement

This workspace currently has no `.git` directory. Each task therefore ends with a verified checkpoint rather than a commit. If version control is initialized before execution, commit after every checkpoint using the commit message shown in that task.

Use the project-local environment for every Gradle command:

```powershell
$env:JAVA_HOME = 'D:\Android_Studio\jbr'
$env:ANDROID_HOME = (Resolve-Path '.android-sdk')
$env:GRADLE_USER_HOME = (Resolve-Path '.gradle-user-home')
```

Do not add `INTERNET`, `ACCESS_NETWORK_STATE`, broad storage permissions, or notification behavior for imports.

## File Map

### New production files

- `app/src/main/java/com/miearn/app/data/local/ImportEntities.kt` — Source, cross-reference, import job, and draft Room entities.
- `app/src/main/java/com/miearn/app/data/local/ImportDaos.kt` — Source and import job/draft queries.
- `app/src/main/java/com/miearn/app/importing/ImportModels.kt` — parser-neutral import models and status enums.
- `app/src/main/java/com/miearn/app/importing/ImportSanitizer.kt` — normalization, validation, deduplication, and header aliases.
- `app/src/main/java/com/miearn/app/importing/CsvVocabularyReader.kt` — streaming CSV reader and charset selection.
- `app/src/main/java/com/miearn/app/importing/XlsxVocabularyReader.kt` — FastExcel-backed XLSX reader.
- `app/src/main/java/com/miearn/app/importing/VocabularyFileReader.kt` — file signature detection and reader dispatch.
- `app/src/main/java/com/miearn/app/importing/CompactDictionary.kt` — ECDICT installation, hash validation, and lookup.
- `app/src/main/java/com/miearn/app/importing/ImportRepository.kt` — prepare/commit transaction and source lifecycle.
- `app/src/main/java/com/miearn/app/importing/PrepareImportWorker.kt` — parse, validate, enrich, and stage.
- `app/src/main/java/com/miearn/app/importing/CommitImportWorker.kt` — idempotent atomic commit.
- `app/src/main/java/com/miearn/app/importing/ImportWorkCoordinator.kt` — enqueue, observe, resume, and cancel API.
- `app/src/main/java/com/miearn/app/ui/importing/ImportUiModels.kt` — immutable wizard state.
- `app/src/main/java/com/miearn/app/ui/importing/ImportViewModel.kt` — SAF result, mapping, conflict, and Worker state.
- `app/src/main/java/com/miearn/app/ui/importing/ImportEntrySheet.kt` — compact format explanation and picker action.
- `app/src/main/java/com/miearn/app/ui/importing/ImportWizardScreen.kt` — preview, mapping, summary, progress, and completion.
- `app/src/main/java/com/miearn/app/ui/SourceManagerScreen.kt` — rename and delete custom sources.
- `tools/build_compact_ecdict.py` — deterministic ECDICT selection and compressed SQLite generation.
- `tools/tests/test_build_compact_ecdict.py` — dictionary build tests.
- `app/proguard-rules.pro` — FastExcel and app shrinker configuration.

### Modified production files

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/miearn/app/AppContainer.kt`
- `app/src/main/java/com/miearn/app/data/local/Entities.kt`
- `app/src/main/java/com/miearn/app/data/local/Daos.kt`
- `app/src/main/java/com/miearn/app/data/local/AppDatabase.kt`
- `app/src/main/java/com/miearn/app/data/MIearnRepository.kt`
- `app/src/main/java/com/miearn/app/data/seed/ContentSeeder.kt`
- `app/src/main/java/com/miearn/app/ui/UiModels.kt`
- `app/src/main/java/com/miearn/app/ui/MainViewModel.kt`
- `app/src/main/java/com/miearn/app/ui/MIearnApp.kt`
- `app/src/main/java/com/miearn/app/ui/V21LearningHomeScreen.kt`
- `app/src/main/java/com/miearn/app/ui/MineScreen.kt`
- `app/src/main/java/com/miearn/app/ui/StudyScreen.kt`
- `app/src/main/java/com/miearn/app/ui/QuizScreen.kt`
- `README.md`
- `THIRD_PARTY_NOTICES.md`

### New or modified tests

- `app/src/test/java/com/miearn/app/data/Migration2To3Test.kt`
- `app/src/test/java/com/miearn/app/importing/ImportSanitizerTest.kt`
- `app/src/test/java/com/miearn/app/importing/CsvVocabularyReaderTest.kt`
- `app/src/test/java/com/miearn/app/importing/XlsxVocabularyReaderTest.kt`
- `app/src/test/java/com/miearn/app/importing/CompactDictionaryTest.kt`
- `app/src/test/java/com/miearn/app/importing/ImportRepositoryTest.kt`
- `app/src/test/java/com/miearn/app/importing/ImportWorkersTest.kt`
- `app/src/test/java/com/miearn/app/data/AppDatabaseTest.kt`
- `app/src/androidTest/java/com/miearn/app/MainActivityTest.kt`

## Task 1: Lock the size baseline and dependency strategy

**Files:**

- Modify: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Test: Gradle dependency report and APK size tasks

- [ ] **Step 1: Record the current APK composition**

Run:

```powershell
Get-Item app/build/outputs/apk/debug/app-debug.apk |
    Select-Object FullName, Length
```

Expected baseline from the approved design: approximately `95,549,866` bytes before size optimization.

- [ ] **Step 2: Add FastExcel Reader and explicit icon-core dependency**

Replace the extended icon dependency and add the reader:

```kotlin
implementation("androidx.compose.material:material-icons-core")
implementation("org.dhatim:fastexcel-reader:0.20.2")
testImplementation("org.dhatim:fastexcel:0.20.2")
testImplementation("androidx.work:work-testing:2.11.2")
```

Remove:

```kotlin
implementation("androidx.compose.material:material-icons-extended")
```

Map extended-only icons to icon-core equivalents:

| Existing | Replacement |
|---|---|
| `Icons.Rounded.BarChart` | `Icons.Default.Info` |
| `Icons.Rounded.WarningAmber` | `Icons.Default.Info` |
| `Icons.Rounded.Quiz` | `Icons.Default.Check` |
| `Icons.Rounded.Headphones` | `Icons.Default.PlayArrow` |
| `Icons.Rounded.ExpandMore` | `Icons.Default.KeyboardArrowDown` |
| `Icons.Rounded.Person` | `Icons.Default.AccountCircle` |
| Home/FavoriteBorder/PlayArrow/Search/Settings/Close/Refresh | Same-name `Icons.Default` icon |

- [ ] **Step 3: Enable deterministic shrinking for both deliverables**

Add to `android.buildTypes`:

```kotlin
buildTypes {
    debug {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro",
        )
    }
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro",
        )
    }
}
```

Create `app/proguard-rules.pro` with:

```proguard
-keepattributes Signature,InnerClasses,EnclosingMethod
-dontwarn org.apache.commons.compress.**
```

- [ ] **Step 4: Replace the old single size task with two approved gates**

```kotlin
fun registerApkSizeGate(
    taskName: String,
    assembleTask: String,
    relativeApk: String,
    maxBytes: Long,
) = tasks.register(taskName) {
    group = "verification"
    dependsOn(assembleTask)
    doLast {
        val apk = layout.buildDirectory.file(relativeApk).get().asFile
        check(apk.isFile) { "APK was not produced: $apk" }
        check(apk.length() <= maxBytes) {
            "${apk.name} is ${apk.length()} bytes; limit is $maxBytes bytes"
        }
    }
}

registerApkSizeGate(
    "verifyDebugApkSize",
    "assembleDebug",
    "outputs/apk/debug/app-debug.apk",
    65_000_000L,
)
registerApkSizeGate(
    "verifyReleaseApkSize",
    "assembleRelease",
    "outputs/apk/release/app-release-unsigned.apk",
    45_000_000L,
)
```

- [ ] **Step 5: Verify dependency resolution before feature code**

Run:

```powershell
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath --no-daemon
.\gradlew.bat compileDebugKotlin --no-daemon
```

Expected: FastExcel `0.20.2` resolves, `material-icons-extended` is absent, and compilation succeeds.

- [ ] **Step 6: Checkpoint**

Run:

```powershell
.\gradlew.bat assembleDebug --no-daemon
```

Expected: APK is materially smaller than the 95.55 MB baseline.

Commit message if Git exists: `build: prepare import dependencies and apk size gates`

## Task 2: Build the compact ECDICT asset

**Files:**

- Create: `tools/build_compact_ecdict.py`
- Create: `tools/tests/test_build_compact_ecdict.py`
- Create during build: `app/src/main/assets/dictionaries/ecdict_compact.db.gz`
- Create during build: `app/src/main/assets/dictionaries/ecdict_compact_manifest.json`
- Modify: `THIRD_PARTY_NOTICES.md`

- [ ] **Step 1: Write failing selection and determinism tests**

```python
from pathlib import Path
from tools.build_compact_ecdict import select_rows, build_database


def test_select_rows_prefers_exam_and_frequency_words():
    rows = [
        {"word": "fixture", "phonetic": "x", "translation": "夹具", "exchange": "", "tag": "cet4", "bnc": "500", "frq": "600"},
        {"word": "rare-token", "phonetic": "", "translation": "稀有词", "exchange": "", "tag": "", "bnc": "200000", "frq": "300000"},
    ]
    assert [row["word"] for row in select_rows(rows, limit=1)] == ["fixture"]


def test_build_is_deterministic(tmp_path: Path):
    rows = [
        {"word": "fixture", "phonetic": "/x/", "translation": "夹具", "exchange": "s:fixtures", "tag": "cet4", "bnc": "500", "frq": "600"},
    ]
    first = build_database(rows, tmp_path / "a.db")
    second = build_database(rows, tmp_path / "b.db")
    assert first.entry_count == 1
    assert first.logical_sha256 == second.logical_sha256
```

- [ ] **Step 2: Run tests and confirm RED**

Run:

```powershell
python -m unittest discover -s tools/tests -p 'test_build_compact_ecdict.py' -v
```

Expected: import failure because `build_compact_ecdict.py` does not exist.

- [ ] **Step 3: Implement deterministic selection**

Use the pinned ECDICT CSV input supplied to the tool through `--source`. Selection rules:

```python
def score(row: dict[str, str]) -> tuple[int, int, str]:
    tags = set(row.get("tag", "").split())
    exam = int(bool(tags & {"zk", "gk", "cet4", "cet6", "ky", "toefl", "ielts", "gre"}))
    ranks = [
        int(value)
        for value in (row.get("bnc", ""), row.get("frq", ""))
        if value.isdigit() and int(value) > 0
    ]
    rank = min(ranks, default=1_000_000_000)
    return (-exam, rank, row["word"].casefold())


def select_rows(rows, limit=120_000):
    usable = {
        row["word"].strip().casefold(): row
        for row in rows
        if row.get("word", "").strip()
        and row.get("translation", "").strip()
    }
    return sorted(usable.values(), key=score)[:limit]
```

Create a `WITHOUT ROWID` SQLite table:

```sql
CREATE TABLE entry (
    word TEXT PRIMARY KEY COLLATE NOCASE,
    phonetic TEXT NOT NULL,
    translation TEXT NOT NULL,
    exchange TEXT NOT NULL
) WITHOUT ROWID;
```

Insert in case-folded word order, run `VACUUM`, gzip with `mtime=0`, and write a manifest containing source URL, source commit, license, count, raw bytes, gzip bytes, database SHA-256, and gzip SHA-256.

- [ ] **Step 4: Acquire and record the official source revision**

```powershell
New-Item -ItemType Directory -Force tools\vendor | Out-Null
$commit = (Invoke-RestMethod `
  -Uri 'https://api.github.com/repos/skywind3000/ECDICT/commits/master' `
  -Headers @{ 'User-Agent' = 'MIearn-build' }).sha
Invoke-WebRequest `
  -Uri 'https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv' `
  -OutFile tools\vendor\ecdict.csv
Set-Content -LiteralPath tools\vendor\ecdict.revision.txt -Value $commit -NoNewline
```

The CSV is build input only and must not be packaged in the APK. The build script reads `ecdict.revision.txt` and writes that exact revision plus the CSV SHA-256 into the manifest.

- [ ] **Step 5: Build and inspect the asset**

Run:

```powershell
python tools/build_compact_ecdict.py `
  --source tools/vendor/ecdict.csv `
  --output app/src/main/assets/dictionaries/ecdict_compact.db.gz `
  --manifest app/src/main/assets/dictionaries/ecdict_compact_manifest.json `
  --limit 120000
```

Expected:

- Entry count is between 80,000 and 120,000.
- Gzip asset is at most 12,000,000 bytes.
- Two consecutive builds produce identical SHA-256 values.

- [ ] **Step 6: Add attribution**

Append ECDICT MIT attribution, source repository, selected source revision, and the fact that MIearn packages a reduced derivative database to `THIRD_PARTY_NOTICES.md`.

- [ ] **Step 7: Run Python tests**

```powershell
python -m unittest discover -s tools/tests -v
```

Expected: all content, audio, and compact dictionary tests pass.

Commit message if Git exists: `feat: build compact offline ecdict asset`

## Task 3: Add Room v3 source and import schema

**Files:**

- Modify: `app/src/main/java/com/miearn/app/data/local/Entities.kt`
- Create: `app/src/main/java/com/miearn/app/data/local/ImportEntities.kt`
- Modify: `app/src/main/java/com/miearn/app/data/local/AppDatabase.kt`
- Create: `app/src/test/java/com/miearn/app/data/Migration2To3Test.kt`
- Generated: `app/schemas/com.miearn.app.data.local.AppDatabase/3.json`

- [ ] **Step 1: Write the v2→v3 migration test**

The test must:

1. Create a v2 database with one word and learned progress.
2. Run `MIGRATION_2_3`.
3. Assert `words.isCustom = 0`.
4. Assert a built-in Source exists for the old category.
5. Assert one cross-reference exists.
6. Assert every progress field is unchanged.

Core assertion:

```kotlin
val migratedWord = database.wordDao().getById("mec_0001")!!
assertFalse(migratedWord.isCustom)
assertEquals("mechanical", database.sourceDao().getById("mechanical")?.sourceId)
assertEquals(
    listOf("mec_0001"),
    database.sourceDao().wordIds("mechanical"),
)
assertEquals(6, database.progressDao().getByWordId("mec_0001")?.intervalDays)
```

- [ ] **Step 2: Run the migration test and confirm RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests 'com.miearn.app.data.Migration2To3Test' --no-daemon
```

Expected: failure because v3 entities and migration are missing.

- [ ] **Step 3: Extend `WordEntity`**

Add:

```kotlin
val isCustom: Boolean = false,
```

Change the category/source index from a unique index to a normal index:

```kotlin
Index(value = ["category", "sourceIndex"])
```

Canonical imported words use `category = "custom"`; source membership and order live only in the cross-reference.

- [ ] **Step 4: Create import entities**

```kotlin
enum class SourceType { BUILTIN, CUSTOM }
enum class ImportJobStatus {
    COPYING, PREPARING, AWAITING_MAPPING, AWAITING_CONFIRMATION,
    COMMITTING, COMPLETED, FAILED, CANCELLED,
}
enum class ImportConflictPolicy { KEEP_EXISTING, UPDATE_NON_EMPTY }

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val sourceId: String,
    val displayName: String,
    val type: String,
    val originalFileName: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val wordCount: Int,
)

@Entity(
    tableName = "word_source",
    primaryKeys = ["sourceId", "wordId"],
    foreignKeys = [
        ForeignKey(SourceEntity::class, ["sourceId"], ["sourceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(WordEntity::class, ["id"], ["wordId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("wordId")],
)
data class WordSourceCrossRef(
    val sourceId: String,
    val wordId: String,
    val importOrder: Int,
)

@Entity(tableName = "import_jobs")
data class ImportJobEntity(
    @PrimaryKey val jobId: String,
    val sourceId: String,
    val sourceName: String,
    val originalFileName: String,
    val internalFilePath: String,
    val status: String,
    val processedRows: Int,
    val totalRows: Int,
    val validRows: Int,
    val invalidRows: Int,
    val duplicateRows: Int,
    val mappingJson: String,
    val headersJson: String,
    val previewRowsJson: String,
    val conflictPolicy: String?,
    val errorMessage: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "import_drafts",
    primaryKeys = ["jobId", "rowIndex"],
    foreignKeys = [
        ForeignKey(
            ImportJobEntity::class,
            ["jobId"],
            ["jobId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("jobId"), Index("normalizedEnglish")],
)
data class ImportDraftEntity(
    val jobId: String,
    val rowIndex: Int,
    val normalizedEnglish: String,
    val english: String,
    val primaryEnglish: String,
    val phonetic: String,
    val chinese: String,
    val note: String,
    val exampleEn: String,
    val exampleZh: String,
    val existingWordId: String?,
    val validationError: String?,
)
```

- [ ] **Step 5: Implement `MIGRATION_2_3`**

Execute in this order:

```sql
ALTER TABLE words ADD COLUMN isCustom INTEGER NOT NULL DEFAULT 0;
DROP INDEX IF EXISTS index_words_category_sourceIndex;
CREATE INDEX IF NOT EXISTS index_words_category_sourceIndex
    ON words(category, sourceIndex);

CREATE TABLE sources (
    sourceId TEXT NOT NULL PRIMARY KEY,
    displayName TEXT NOT NULL,
    type TEXT NOT NULL,
    originalFileName TEXT,
    createdAtEpochMillis INTEGER NOT NULL,
    updatedAtEpochMillis INTEGER NOT NULL,
    wordCount INTEGER NOT NULL
);
CREATE TABLE word_source (
    sourceId TEXT NOT NULL,
    wordId TEXT NOT NULL,
    importOrder INTEGER NOT NULL,
    PRIMARY KEY(sourceId, wordId),
    FOREIGN KEY(sourceId) REFERENCES sources(sourceId) ON DELETE CASCADE,
    FOREIGN KEY(wordId) REFERENCES words(id) ON DELETE CASCADE
);
CREATE INDEX index_word_source_wordId ON word_source(wordId);
CREATE TABLE import_jobs (
    jobId TEXT NOT NULL PRIMARY KEY,
    sourceId TEXT NOT NULL,
    sourceName TEXT NOT NULL,
    originalFileName TEXT NOT NULL,
    internalFilePath TEXT NOT NULL,
    status TEXT NOT NULL,
    processedRows INTEGER NOT NULL,
    totalRows INTEGER NOT NULL,
    validRows INTEGER NOT NULL,
    invalidRows INTEGER NOT NULL,
    duplicateRows INTEGER NOT NULL,
    mappingJson TEXT NOT NULL,
    headersJson TEXT NOT NULL,
    previewRowsJson TEXT NOT NULL,
    conflictPolicy TEXT,
    errorMessage TEXT,
    createdAtEpochMillis INTEGER NOT NULL,
    updatedAtEpochMillis INTEGER NOT NULL
);
CREATE TABLE import_drafts (
    jobId TEXT NOT NULL,
    rowIndex INTEGER NOT NULL,
    normalizedEnglish TEXT NOT NULL,
    english TEXT NOT NULL,
    primaryEnglish TEXT NOT NULL,
    phonetic TEXT NOT NULL,
    chinese TEXT NOT NULL,
    note TEXT NOT NULL,
    exampleEn TEXT NOT NULL,
    exampleZh TEXT NOT NULL,
    existingWordId TEXT,
    validationError TEXT,
    PRIMARY KEY(jobId, rowIndex),
    FOREIGN KEY(jobId) REFERENCES import_jobs(jobId) ON DELETE CASCADE
);
CREATE INDEX index_import_drafts_jobId ON import_drafts(jobId);
CREATE INDEX index_import_drafts_normalizedEnglish ON import_drafts(normalizedEnglish);
```

Seed built-in sources:

```sql
INSERT INTO sources (
    sourceId, displayName, type, originalFileName,
    createdAtEpochMillis, updatedAtEpochMillis, wordCount
)
SELECT category, MIN(categoryLabel), 'BUILTIN', NULL, 0, 0, COUNT(*)
FROM words
GROUP BY category;

INSERT INTO word_source(sourceId, wordId, importOrder)
SELECT category, id, sourceIndex FROM words;
```

- [ ] **Step 6: Register entities and migration**

Set Room version to `3`, add all four entities, add `sourceDao()` and `importDao()`, and register both migrations:

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3)
```

- [ ] **Step 7: Run migration and schema tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests 'com.miearn.app.data.Migration*' --no-daemon
```

Expected: migrations 1→2 and 2→3 pass; schema `3.json` is generated.

Commit message if Git exists: `feat: add room v3 vocabulary source schema`

## Task 4: Add source-aware DAOs and preserve learning behavior

**Files:**

- Create: `app/src/main/java/com/miearn/app/data/local/ImportDaos.kt`
- Modify: `app/src/main/java/com/miearn/app/data/local/Daos.kt`
- Modify: `app/src/main/java/com/miearn/app/data/MIearnRepository.kt`
- Modify: `app/src/main/java/com/miearn/app/data/seed/ContentSeeder.kt`
- Modify: `app/src/test/java/com/miearn/app/data/AppDatabaseTest.kt`

- [ ] **Step 1: Write failing source membership tests**

Test one canonical word linked to both `mechanical` and `custom-1`, then assert:

```kotlin
assertEquals(listOf("fixture"), dao.wordsForSource("mechanical", 10).map { it.english })
assertEquals(listOf("fixture"), dao.wordsForSource("custom-1", 10).map { it.english })
assertEquals(1, dao.observeCategoryStats().first().single { it.category == "custom-1" }.total)
```

Also verify due, unseen, learned, and quiz queries return the word through either source without duplicating progress.

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests 'com.miearn.app.data.AppDatabaseTest' --no-daemon
```

- [ ] **Step 3: Add `SourceDao` and `ImportDao`**

Required API:

```kotlin
@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY type, createdAtEpochMillis, displayName")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE sourceId = :sourceId")
    suspend fun getById(sourceId: String): SourceEntity?

    @Query("SELECT * FROM sources WHERE type = 'CUSTOM' AND displayName = :displayName")
    suspend fun customByName(displayName: String): SourceEntity?

    @Query("""
        SELECT w.id FROM words w
        JOIN word_source x ON x.wordId = w.id
        WHERE x.sourceId = :sourceId
        ORDER BY x.importOrder
    """)
    suspend fun wordIds(sourceId: String): List<String>

    @Upsert suspend fun upsert(source: SourceEntity)
    @Upsert suspend fun upsertLinks(links: List<WordSourceCrossRef>)

    @Query("UPDATE sources SET displayName = :name, updatedAtEpochMillis = :now WHERE sourceId = :sourceId AND type = 'CUSTOM'")
    suspend fun rename(sourceId: String, name: String, now: Long): Int

    @Query("DELETE FROM sources WHERE sourceId = :sourceId AND type = 'CUSTOM'")
    suspend fun deleteCustom(sourceId: String): Int
}
```

`ImportDao` must observe/get/upsert jobs, replace draft rows in batches, count valid/invalid/duplicates, retrieve drafts ordered by `rowIndex`, and delete job drafts.

- [ ] **Step 4: Rewrite category filters through `word_source`**

For every source-specific query use:

```sql
JOIN word_source x ON x.wordId = w.id
WHERE x.sourceId = :sourceId
ORDER BY x.importOrder
```

Rename Kotlin parameters from `category` to `sourceId`, but keep persisted settings and study-session JSON field names unchanged for migration compatibility.

Add a deterministic import lookup to `WordDao`:

```kotlin
@Query("""
    SELECT * FROM words
    WHERE LOWER(TRIM(english)) = :normalizedEnglish
    ORDER BY isCustom, rowid
    LIMIT 1
""")
suspend fun findCanonicalWord(normalizedEnglish: String): WordEntity?
```

- [ ] **Step 5: Seed source links transactionally**

After built-in JSON seeding, ensure the five built-in Source rows and all CrossRefs exist. Content upgrades may add new links but must not replace progress.

- [ ] **Step 6: Run repository and Room tests**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.miearn.app.data.AppDatabaseTest' `
  --tests 'com.miearn.app.data.MIearnRepositoryTest' `
  --tests 'com.miearn.app.data.ObjectiveLearningRepositoryTest' `
  --no-daemon
```

Expected: source-aware and existing learning tests pass.

Commit message if Git exists: `feat: make learning queries vocabulary-source aware`

## Task 5: Implement import models, cleaning, CSV, and XLSX readers

**Files:**

- Create: `app/src/main/java/com/miearn/app/importing/ImportModels.kt`
- Create: `app/src/main/java/com/miearn/app/importing/ImportSanitizer.kt`
- Create: `app/src/main/java/com/miearn/app/importing/CsvVocabularyReader.kt`
- Create: `app/src/main/java/com/miearn/app/importing/XlsxVocabularyReader.kt`
- Create: `app/src/main/java/com/miearn/app/importing/VocabularyFileReader.kt`
- Create tests under: `app/src/test/java/com/miearn/app/importing/`

- [ ] **Step 1: Write sanitizer tests**

Cover:

```kotlin
assertEquals("flow drill screw", ImportSanitizer.normalizeEnglish("  Flow   Drill Screw "))
assertTrue(ImportSanitizer.isValidEnglish("fixture; jig"))
assertTrue(ImportSanitizer.isValidEnglish("end-of-arm tooling"))
assertFalse(ImportSanitizer.isValidEnglish("夹具"))
assertEquals(ColumnRole.ENGLISH, ImportSanitizer.detectHeader("单词"))
assertEquals(ColumnRole.EXAMPLE_ZH, ImportSanitizer.detectHeader("例句翻译"))
```

- [ ] **Step 2: Write CSV tests**

Fixtures must cover:

- UTF-8 BOM.
- GB18030 Chinese translation.
- Quoted comma.
- Quoted line break.
- Escaped quote.
- Empty lines.
- 20,001-row rejection.

Expected model:

```kotlin
data class RawVocabularyRow(
    val rowIndex: Int,
    val cells: List<String>,
)

interface VocabularyRowReader {
    fun rows(input: InputStream): Sequence<RawVocabularyRow>
}
```

- [ ] **Step 3: Write XLSX tests**

Create test workbooks with FastExcel Writer in test scope or package binary fixtures. Verify first non-empty sheet, cached formula values, empty sheet skipping, and corrupt ZIP rejection.

- [ ] **Step 4: Run RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests 'com.miearn.app.importing.*ReaderTest' --tests 'com.miearn.app.importing.ImportSanitizerTest' --no-daemon
```

- [ ] **Step 5: Implement models and sanitizer**

Use:

```kotlin
enum class ColumnRole {
    ENGLISH, CHINESE, PHONETIC, EXAMPLE_EN, EXAMPLE_ZH, NOTE, IGNORE,
}

data class ImportColumnMapping(val byIndex: Map<Int, ColumnRole>) {
    init {
        require(byIndex.values.count { it == ColumnRole.ENGLISH } == 1)
    }
}
```

Normalize with `Normalizer.normalize(value, Normalizer.Form.NFKC)`, trim, collapse `\\s+`, and case-fold with `Locale.ROOT`. Validation requires at least one `[A-Za-z]` and permits letters, digits, spaces, apostrophes, hyphens, slashes, ampersands, parentheses, commas, periods, colons, and both semicolons.

- [ ] **Step 6: Implement RFC-4180-compatible CSV streaming**

Use a state machine with `inQuotes`, `pendingQuote`, and current row/cell builders. Do not split lines with `String.split`.

Detect encoding:

1. UTF-8 BOM.
2. Strict UTF-8 decoder with `CodingErrorAction.REPORT`.
3. GB18030 fallback.

Stop copying/reading after 20,000 rows and throw `ImportLimitException`.

- [ ] **Step 7: Implement XLSX and dispatch**

`XlsxVocabularyReader` uses:

```kotlin
ReadableWorkbook(input).use { workbook ->
    val sheet = workbook.sheets
        .firstOrNull { candidate -> candidate.openStream().use { it.findAny().isPresent } }
        ?: throw EmptyVocabularyFileException()
    sheet.openStream().use { rows ->
        rows.asSequence().map { row ->
            RawVocabularyRow(
                rowIndex = row.rowNum + 1,
                cells = row.cells.map { it.rawValue ?: it.text ?: "" },
            )
        }
    }
}
```

Because Java streams cannot escape a closed workbook, expose a callback-based API:

```kotlin
fun read(input: InputStream, consume: (RawVocabularyRow) -> Unit)
```

Dispatch XLSX when the first four bytes are `PK\u0003\u0004`; dispatch CSV otherwise when extension/MIME is accepted.

- [ ] **Step 8: Run GREEN**

```powershell
.\gradlew.bat testDebugUnitTest --tests 'com.miearn.app.importing.*' --no-daemon
```

Commit message if Git exists: `feat: parse and sanitize imported vocabulary files`

## Task 6: Install and query the compact dictionary

**Files:**

- Create: `app/src/main/java/com/miearn/app/importing/CompactDictionary.kt`
- Create: `app/src/test/java/com/miearn/app/importing/CompactDictionaryTest.kt`
- Modify: `app/src/main/java/com/miearn/app/AppContainer.kt`

- [ ] **Step 1: Write failing install and lookup tests**

Verify:

- Gzip is decompressed once.
- Manifest SHA-256 mismatch deletes the partial database and fails closed.
- Second open reuses the verified file.
- `Fixture` resolves case-insensitively.
- Missing word returns `null`.

```kotlin
assertEquals(
    DictionaryEntry("fixture", "/ˈfɪkstʃə/", "夹具", "s:fixtures"),
    dictionary.lookup("Fixture"),
)
```

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests 'com.miearn.app.importing.CompactDictionaryTest' --no-daemon
```

- [ ] **Step 3: Implement installer and read-only query**

Public API:

```kotlin
data class DictionaryEntry(
    val word: String,
    val phonetic: String,
    val translation: String,
    val exchange: String,
)

interface LocalDictionary {
    suspend fun lookup(word: String): DictionaryEntry?
}
```

Install into:

```kotlin
context.noBackupFilesDir.resolve("dictionaries/ecdict_compact.db")
```

Write to `.part`, stream through `GZIPInputStream`, verify SHA-256 from the packaged manifest, then atomically rename. Open with:

```kotlin
SQLiteDatabase.openDatabase(
    path.absolutePath,
    null,
    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
)
```

Query:

```sql
SELECT word, phonetic, translation, exchange
FROM entry
WHERE word = ? COLLATE NOCASE
LIMIT 1
```

- [ ] **Step 4: Register in `AppContainer`**

```kotlin
val compactDictionary: LocalDictionary = CompactDictionary(context)
```

- [ ] **Step 5: Run GREEN**

```powershell
.\gradlew.bat testDebugUnitTest --tests 'com.miearn.app.importing.CompactDictionaryTest' --no-daemon
```

Commit message if Git exists: `feat: add compact offline dictionary lookup`

## Task 7: Implement prepare and atomic commit repository logic

**Files:**

- Create: `app/src/main/java/com/miearn/app/importing/ImportRepository.kt`
- Create: `app/src/test/java/com/miearn/app/importing/ImportRepositoryTest.kt`
- Modify: `app/src/main/java/com/miearn/app/AppContainer.kt`

- [ ] **Step 1: Write failing enrichment-priority tests**

Required assertions:

```kotlin
// User value wins.
assertEquals("用户翻译", enriched.chinese)
// Empty user phonetic is filled locally.
assertEquals("/ˈfɪkstʃə/", enriched.phonetic)
// ECDICT never fabricates examples.
assertEquals("", enriched.exampleEn)
```

Test duplicate policies:

- KEEP_EXISTING retains existing content and creates a CrossRef.
- UPDATE_NON_EMPTY updates only non-empty imported fields and creates a CrossRef.
- Both preserve the exact ProgressEntity.
- Two commit calls with the same job ID produce one Source and one CrossRef.

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests 'com.miearn.app.importing.ImportRepositoryTest' --no-daemon
```

- [ ] **Step 3: Implement stable IDs**

```kotlin
fun importedSourceId(): String = "usr_source_${UUID.randomUUID()}"

fun importedWordId(normalizedEnglish: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(normalizedEnglish.toByteArray(Charsets.UTF_8))
        .take(8)
        .joinToString("") { "%02x".format(it) }
    return "usr_$digest"
}
```

Resolve existing words by normalized English before creating an ID. Imported words use:

```kotlin
WordEntity(
    id = importedWordId(normalized),
    category = "custom",
    categoryLabel = "自定义词条",
    sourceIndex = normalized.hashCode(),
    kind = "TERM",
    section = "",
    english = english,
    primaryEnglish = EnglishVariantParser.parse(english).first(),
    phonetic = phonetic,
    chinese = chinese,
    note = note,
    exampleEn = exampleEn,
    exampleZh = exampleZh,
    audioText = english,
    audioAsset = "",
    isCustom = true,
)
```

- [ ] **Step 4: Implement preparation**

For each valid row:

1. Normalize English.
2. Deduplicate within the same file, retaining the first import order and merging later non-empty cells.
3. Find an existing canonical word.
4. Query ECDICT only for empty phonetic/chinese fields; retain exchange data inside the dictionary layer for inflection lookup.
5. Batch upsert drafts every 250 rows.
6. Update ImportJob counts in the same coroutine.

- [ ] **Step 5: Implement atomic commit**

Use `database.withTransaction`:

```kotlin
if (sourceDao.getById(job.sourceId) == null) {
    sourceDao.upsert(job.toSourceEntity(validDrafts.size))
}
for (draft in validDrafts) {
    val word = mergeDraft(draft, policy)
    wordDao.upsertAll(listOf(word))
    sourceDao.upsertLinks(
        listOf(WordSourceCrossRef(job.sourceId, word.id, draft.rowIndex)),
    )
}
importDao.markCompleted(job.jobId, now)
importDao.deleteDrafts(job.jobId)
```

The transaction checks `status == COMMITTING || status == COMPLETED`; `COMPLETED` returns without writes.

- [ ] **Step 6: Implement safe custom source deletion**

In one transaction:

1. Reject non-custom Source.
2. Save its linked word IDs.
3. Delete Source, cascading its CrossRefs.
4. Delete words where `isCustom = 1` and no CrossRef remains.
5. Progress and review events cascade only for those true orphan words.

- [ ] **Step 7: Run GREEN**

```powershell
.\gradlew.bat testDebugUnitTest --tests 'com.miearn.app.importing.ImportRepositoryTest' --no-daemon
```

Commit message if Git exists: `feat: stage and atomically commit imported words`

## Task 8: Add WorkManager orchestration

**Files:**

- Create: `app/src/main/java/com/miearn/app/importing/PrepareImportWorker.kt`
- Create: `app/src/main/java/com/miearn/app/importing/CommitImportWorker.kt`
- Create: `app/src/main/java/com/miearn/app/importing/ImportWorkCoordinator.kt`
- Create: `app/src/test/java/com/miearn/app/importing/ImportWorkersTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Write Worker tests**

Use `TestListenableWorkerBuilder`. Verify:

- Prepare success ends at `AWAITING_CONFIRMATION`.
- Progress reaches the final row.
- Corrupt file ends at `FAILED` with a user-safe message.
- ECDICT exception still succeeds with empty enrichment.
- Commit success ends at `COMPLETED`.
- Re-running commit is idempotent.
- Cancel deletes the internal copy and draft rows.

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests 'com.miearn.app.importing.ImportWorkersTest' --no-daemon
```

- [ ] **Step 3: Implement prepare Worker**

Input data contains only `jobId`; all other state comes from Room.

```kotlin
override suspend fun doWork(): Result = runCatching {
    repository.prepare(
        jobId = inputData.getString(KEY_JOB_ID)!!,
        onProgress = { processed, total ->
            setProgress(workDataOf("processed" to processed, "total" to total))
        },
    )
    Result.success()
}.getOrElse { error ->
    repository.fail(jobId, error.toUserMessage())
    Result.failure()
}
```

- [ ] **Step 4: Implement commit Worker**

Read the policy from `ImportJobEntity`; reject a null policy. Call the idempotent transaction, then delete the copied file after successful commit.

- [ ] **Step 5: Implement coordinator**

Public API:

```kotlin
interface ImportWorkCoordinator {
    suspend fun createJob(
        originalFileName: String,
        displayName: String,
        input: InputStream,
    ): String
    fun observe(jobId: String): Flow<ImportJobEntity?>
    fun prepare(jobId: String)
    suspend fun setMappingAndPrepare(jobId: String, mapping: ImportColumnMapping)
    suspend fun confirmAndCommit(jobId: String, policy: ImportConflictPolicy)
    suspend fun cancel(jobId: String)
}
```

Use unique names `import-prepare-$jobId` and `import-commit-$jobId` with `ExistingWorkPolicy.KEEP`.

Copy at most 20 MB to `cacheDir/imports/$jobId.source`; delete partial copies on failure.

- [ ] **Step 6: Confirm permission surface**

SAF requires no storage permission. Ensure Manifest still contains neither `READ_EXTERNAL_STORAGE` nor `MANAGE_EXTERNAL_STORAGE`.

- [ ] **Step 7: Run GREEN**

```powershell
.\gradlew.bat testDebugUnitTest --tests 'com.miearn.app.importing.ImportWorkersTest' --no-daemon
```

Commit message if Git exists: `feat: run vocabulary imports with workmanager`

## Task 9: Build the import wizard UI

**Files:**

- Create all files under: `app/src/main/java/com/miearn/app/ui/importing/`
- Modify: `app/src/main/java/com/miearn/app/ui/MIearnApp.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/MainViewModel.kt`
- Modify: `app/src/androidTest/java/com/miearn/app/MainActivityTest.kt`

- [ ] **Step 1: Add failing Compose tests**

Tests must assert:

- Import icon is visible from the learning home.
- Entry sheet shows XLSX/CSV help and “选择文件”.
- Picker result opens the wizard.
- Confident mapping skips manual mapping.
- Ambiguous mapping shows one selector per required column.
- Summary shows valid/invalid/duplicate counts.
- Both conflict actions are available.
- Progress text includes processed/total.
- Completion has exactly “开始学习” and “返回首页”.

Use concrete assertions such as:

```kotlin
@Test
fun homeImportEntryAndSummaryAreReachable() {
    composeRule.onNodeWithContentDescription("导入词库").performClick()
    composeRule.onNodeWithTag("import-entry").assertIsDisplayed()
    composeRule.onNodeWithTag("import-pick-file").assertHasClickAction()

    fakeImportCoordinator.emit(
        ImportUiState.Summary("job-1", valid = 96, invalid = 2, duplicates = 2),
    )
    composeRule.onNodeWithTag("import-summary").assertIsDisplayed()
    composeRule.onNodeWithText("有效 96").assertIsDisplayed()
    composeRule.onNodeWithTag("import-policy-keep").assertHasClickAction()
    composeRule.onNodeWithTag("import-policy-update").assertHasClickAction()
}
```

- [ ] **Step 2: Run Compose test compile and confirm missing UI**

```powershell
.\gradlew.bat compileDebugAndroidTestKotlin --no-daemon
```

Expected: test compilation fails until import UI test tags and destinations exist.

- [ ] **Step 3: Define immutable UI state**

```kotlin
sealed interface ImportUiState {
    data object Closed : ImportUiState
    data object Entry : ImportUiState
    data class Inspecting(val fileName: String) : ImportUiState
    data class Mapping(
        val jobId: String,
        val headers: List<String>,
        val previewRows: List<List<String>>,
        val suggested: ImportColumnMapping?,
    ) : ImportUiState
    data class Preparing(val jobId: String, val processed: Int, val total: Int) : ImportUiState
    data class Summary(
        val jobId: String,
        val valid: Int,
        val invalid: Int,
        val duplicates: Int,
    ) : ImportUiState
    data class Committing(val jobId: String) : ImportUiState
    data class Complete(val sourceId: String, val imported: Int) : ImportUiState
    data class Error(val jobId: String?, val message: String) : ImportUiState
}
```

- [ ] **Step 4: Add SAF picker**

Use:

```kotlin
rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
) { uri ->
    uri?.let(viewModel::acceptImportUri)
}
```

Launch with:

```kotlin
arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "text/csv",
    "text/comma-separated-values",
    "application/octet-stream",
)
```

- [ ] **Step 5: Build the minimal entry and wizard screens**

Use a ModalBottomSheet for entry and full-screen destination for the wizard. Every step has one primary action. Add stable test tags:

- `import-entry`
- `import-pick-file`
- `import-source-name`
- `import-mapping`
- `import-summary`
- `import-policy-keep`
- `import-policy-update`
- `import-progress`
- `import-complete`

Do not display ECDICT internals, Worker IDs, raw exceptions, or technical file paths.

- [ ] **Step 6: Wire lifecycle-safe observation**

`ImportViewModel` observes the active job from Room and converts status to `ImportUiState`. Reopening the app resumes the appropriate wizard step. `onCleared()` must not cancel active import work.

- [ ] **Step 7: Compile UI tests**

```powershell
.\gradlew.bat compileDebugKotlin compileDebugAndroidTestKotlin --no-daemon
```

Expected: both tasks pass.

Commit message if Git exists: `feat: add offline vocabulary import wizard`

## Task 10: Integrate sources into home, Mine, learning, and tests

**Files:**

- Modify: `app/src/main/java/com/miearn/app/ui/V21LearningHomeScreen.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/MineScreen.kt`
- Create: `app/src/main/java/com/miearn/app/ui/SourceManagerScreen.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/MIearnApp.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/StudyScreen.kt`
- Modify: `app/src/main/java/com/miearn/app/ui/QuizScreen.kt`
- Modify: `app/src/main/java/com/miearn/app/audio/AudioPronouncer.kt`

- [ ] **Step 1: Add failing navigation and source tests**

Assert:

- Home header contains the compact import icon.
- Source dropdown has separate “内置词库” and “自定义词库” sections.
- An active import shows one compact progress row.
- Mine contains “词库管理”.
- Built-in sources have no delete action.
- Custom source rename and delete actions work.
- Delete requires confirmation.

- [ ] **Step 2: Expose sources from ViewModel**

```kotlin
val sources: StateFlow<List<SourceEntity>> =
    repository.observeSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

Selecting a source continues to store its ID in the existing `activeCategory` preference, avoiding a DataStore migration. If the active custom Source is deleted, immediately switch the preference to `mechanical` before returning home.

- [ ] **Step 3: Update the header**

Place an import icon button immediately after the source dropdown and before search/settings. Use content description `导入词库`. Keep the enlarged bottom “开始学习” button unchanged.

- [ ] **Step 4: Implement source manager**

Rows display source name and word count. Built-in rows are read-only. Custom rows expose rename and delete. Delete dialog text must state that orphan custom words and their learning history will be removed.

- [ ] **Step 5: Make blank enriched fields safe**

In study and detail screens:

```kotlin
if (word.phonetic.isNotBlank()) { Text(word.phonetic) }
if (word.chinese.isNotBlank()) { Text(word.chinese) }
if (word.exampleEn.isNotBlank()) {
    Text(word.exampleEn)
    if (word.exampleZh.isNotBlank()) Text(word.exampleZh)
}
```

If an imported word has no Chinese translation, the second pass shows `暂缺释义` and excludes it from Chinese-choice questions until edited or enriched.

- [ ] **Step 6: Preserve TTS fallback**

For `audioAsset.isBlank()`, skip the asset lookup and immediately request TTS:

```kotlin
executor.play(SpeechRequestFactory.full(word), assetAvailable = false)
```

Do not add an MP3 cache.

- [ ] **Step 7: Support small custom sources in quiz**

When source-local distractors are fewer than three, fill from globally distinct candidates. Never use the expected answer twice. If the expected Chinese value is blank, omit that word from EN_TO_ZH and FILL_BLANK modes.

- [ ] **Step 8: Run focused tests**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.miearn.app.domain.*' `
  --tests 'com.miearn.app.data.*' `
  --no-daemon
.\gradlew.bat compileDebugAndroidTestKotlin --no-daemon
```

Commit message if Git exists: `feat: integrate custom sources with learning and mine`

## Task 11: Device, migration, offline, and recovery acceptance

**Files:**

- Modify: `app/src/androidTest/java/com/miearn/app/MainActivityTest.kt`
- Add fixtures: `app/src/androidTest/assets/import/valid.csv`
- Add fixtures: `app/src/androidTest/assets/import/valid.xlsx`
- Add fixtures: `app/src/androidTest/assets/import/invalid.csv`

- [ ] **Step 1: Complete Compose instrumentation coverage**

Add named tests for the full happy path, manual mapping, conflict summary, cancellation, process-state restoration, source selection, rename, and delete:

```kotlin
@Test fun importCsvHappyPathCreatesSelectableSource()
@Test fun ambiguousHeadersRequireManualMapping()
@Test fun updatePolicyPreservesExistingProgress()
@Test fun cancellingImportRemovesProgressBanner()
@Test fun reopeningActivityRestoresPreparingJob()
@Test fun renamingCustomSourceUpdatesDropdown()
@Test fun deletingCustomSourceRequiresConfirmation()
@Test fun builtInSourceCannotBeDeleted()
```

Each test must assert the final Room state in addition to the visible Compose node.

- [ ] **Step 2: Run API 29 device suite**

Create the missing API 29 AVD once, then run it:

```powershell
& .android-sdk\cmdline-tools\latest\bin\sdkmanager.bat `
  'system-images;android-29;google_apis;x86_64'
'no' | & .android-sdk\cmdline-tools\latest\bin\avdmanager.bat create avd `
  --force --name MIearn_API_29 `
  --package 'system-images;android-29;google_apis;x86_64' `
  --device 'pixel_4'
Start-Process .android-sdk\emulator\emulator.exe `
  -ArgumentList '-avd','MIearn_API_29','-no-snapshot','-no-boot-anim' `
  -WindowStyle Hidden
.\gradlew.bat connectedDebugAndroidTest --no-daemon
```

Expected: all instrumentation tests pass on API 29.

- [ ] **Step 3: Run API 36 device suite**

```powershell
.\start_pixel_5.ps1
.\gradlew.bat connectedDebugAndroidTest --no-daemon
```

Expected: all instrumentation tests pass on API 36.

- [ ] **Step 4: Perform offline manual acceptance**

Disable Wi-Fi and mobile data, then verify:

1. Import 100-row CSV.
2. Import matching XLSX with UPDATE_NON_EMPTY.
3. Background the app during preparation.
4. Force-stop and reopen.
5. Start learning from the imported source.
6. Play an imported word through TTS.
7. Run a 10-question quiz.
8. Rename and delete the source.

Expected: no network error, no duplicate words, progress resumes, and deletion follows orphan rules.

- [ ] **Step 5: Stress test**

Import a generated 10,000-row CSV and assert:

- Progress updates at least every 100 rows.
- UI remains responsive.
- Peak process memory stays below 256 MB.
- Worker completes without foreground-service notification.

Commit message if Git exists: `test: cover offline vocabulary import lifecycle`

## Task 12: Final gates, documentation, and deliverables

**Files:**

- Modify: `README.md`
- Modify: `THIRD_PARTY_NOTICES.md`
- Create: `output/reports/import_v22_verification_report.md`

- [ ] **Step 1: Run all Python pipeline tests**

```powershell
python -m unittest discover -s tools/tests -v
```

Expected: all tests pass.

- [ ] **Step 2: Run all JVM, Room, and lint gates**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug --no-daemon
```

Expected: all unit tests pass and Lint reports zero errors.

- [ ] **Step 3: Build and enforce both APK gates**

```powershell
.\gradlew.bat verifyDebugApkSize verifyReleaseApkSize --no-daemon
```

Expected:

- Debug APK ≤65,000,000 bytes.
- Unsigned Release APK ≤45,000,000 bytes.

- [ ] **Step 4: Verify Manifest permissions**

```powershell
$aapt = Get-ChildItem .android-sdk\build-tools -Recurse -Filter aapt2.exe |
    Sort-Object FullName -Descending |
    Select-Object -First 1
& $aapt.FullName dump permissions app\build\outputs\apk\debug\app-debug.apk
```

Expected: no `android.permission.INTERNET`, `READ_EXTERNAL_STORAGE`, or `MANAGE_EXTERNAL_STORAGE`.

- [ ] **Step 5: Update documentation**

README must document:

- Supported formats and recommended headers.
- 20 MB/20,000-row limits.
- Offline ECDICT enrichment fields.
- Conflict policy semantics.
- Source deletion semantics.
- TTS behavior for imported words.
- Debug and Release APK paths.

- [ ] **Step 6: Write verification report**

Record:

- Test counts and commands.
- API 29/API 36 device IDs and results.
- Compact ECDICT count and hashes.
- Debug/Release byte sizes.
- Manifest permission dump.
- 10,000-row import duration and peak memory.

- [ ] **Step 7: Final deliverables**

Confirm:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
app/src/main/assets/dictionaries/ecdict_compact.db.gz
output/reports/import_v22_verification_report.md
```

Commit message if Git exists: `docs: complete miearn v2.2 import verification`
