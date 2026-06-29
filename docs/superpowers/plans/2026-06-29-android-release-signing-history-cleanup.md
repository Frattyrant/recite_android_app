# Android Release Signing and History Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the exposed keystore from local Git history, create a new local-only release identity, and add redacted CI/release automation without committing or pushing.

**Architecture:** Rewrite only the historical `milearn-release.jks` path, then keep the replacement identity behind existing ignored local configuration. Separate unprivileged Debug CI from a secret-gated signed Release workflow and enforce the repository contract with standard-library security configuration tests.

**Tech Stack:** Git, Gradle Kotlin DSL, Android SDK/JDK tools, GitHub Actions, Python `unittest`, PowerShell

---

### Task 1: Rewrite Local Git History

**Files:**
- Historical removal: `milearn-release.jks`
- Preserve untracked: `docs/superpowers/specs/2026-06-29-android-release-signing-history-cleanup-design.md`
- Preserve untracked: `docs/superpowers/plans/2026-06-29-android-release-signing-history-cleanup.md`

- [ ] **Step 1: Verify the tracked worktree is clean and only approved files are untracked**

Run:

```powershell
git diff --quiet
git diff --cached --quiet
git status --porcelain=v1 --untracked-files=all
```

Expected: both diff commands exit `0`; status lists only the design and plan.

- [ ] **Step 2: Rewrite all local refs with the exact-path index filter**

Run:

```powershell
git filter-branch --force --index-filter "git rm --cached --ignore-unmatch milearn-release.jks" --prune-empty --tag-name-filter cat -- --all
```

Expected: rewritten refs complete without touching working-tree files.

- [ ] **Step 3: Remove rewrite backup refs and expire local reflogs**

Run:

```powershell
$backupRefs = @(git for-each-ref --format='%(refname)' refs/original/)
foreach ($backupRef in $backupRefs) { git update-ref -d $backupRef }
git reflog expire --expire=now --all
git gc --prune=now
```

Expected: no refs remain under `refs/original/`.

- [ ] **Step 4: Verify the exposed path is unreachable**

Run:

```powershell
git log --all --oneline -- milearn-release.jks
git rev-list --objects --all | Select-String -SimpleMatch 'milearn-release.jks'
git ls-files | Select-String -Pattern '\.(jks|keystore|p12|pem)$|(^|/)key\.properties$'
```

Expected: all three checks produce no matches.

### Task 2: Add a Failing Security Contract Test

**Files:**
- Create: `tools/tests/test_release_security_config.py`

- [ ] **Step 1: Add tests for naming, redaction, and workflow behavior**

Create a standard-library `unittest` module that asserts:

```python
EXAMPLE = ROOT / "key.properties.example"
README = ROOT / "README.md"
CI_WORKFLOW = ROOT / ".github/workflows/android-ci.yml"
RELEASE_WORKFLOW = ROOT / ".github/workflows/android-release.yml"

def test_local_signing_file_names_are_consistent():
    assert "Copy this file to key.properties" in EXAMPLE.read_text(encoding="utf-8")
    assert "`key.properties.example` to `key.properties`" in README.read_text(encoding="utf-8")

def test_release_workflow_uses_only_secret_references_and_temp_keystore():
    workflow = RELEASE_WORKFLOW.read_text(encoding="utf-8")
    for name in REQUIRED_SECRET_NAMES:
        assert f"secrets.{name}" in workflow
    assert "runner.temp" in workflow
    assert "if: always()" in workflow
    assert "app-release.apk" in workflow

def test_ci_workflow_runs_repository_checks():
    workflow = CI_WORKFLOW.read_text(encoding="utf-8")
    for command in ("test", "lint", "assembleDebug"):
        assert command in workflow
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
python -m unittest tools.tests.test_release_security_config -v
```

Expected: failures because workflows are absent and the example/README names disagree.

### Task 3: Implement Redacted Repository Configuration

**Files:**
- Modify: `key.properties.example`
- Modify: `README.md`
- Create: `.github/workflows/android-ci.yml`
- Create: `.github/workflows/android-release.yml`

- [ ] **Step 1: Fix the canonical local signing names**

Use this template:

```properties
# Copy this file to key.properties and keep the real file local.
storeFile=.signing/miearn-release.jks
storePassword=replace-with-a-strong-local-secret
keyAlias=miearn
keyPassword=replace-with-a-different-strong-local-secret
```

- [ ] **Step 2: Document local and GitHub signing without values**

README must name the four `MIEARN_*` secrets, explain that the keystore is Base64-encoded locally, warn users not to paste it into files or logs, and use `key.properties.example`.

- [ ] **Step 3: Add Debug CI**

Create `.github/workflows/android-ci.yml` using:

```yaml
permissions:
  contents: read
steps:
  - uses: actions/checkout@v5
  - uses: actions/setup-java@v5
    with:
      distribution: temurin
      java-version: "17"
  - uses: gradle/actions/setup-gradle@v4
  - run: ./gradlew test lint assembleDebug
  - uses: actions/upload-artifact@v4
    with:
      name: miearn-debug-apk
      path: app/build/outputs/apk/debug/app-debug.apk
      if-no-files-found: error
```

- [ ] **Step 4: Add signed Release automation**

Create `.github/workflows/android-release.yml` that validates the four secrets, decodes `MIEARN_KEYSTORE_BASE64` into `${{ runner.temp }}`, exposes only the existing Gradle environment variable names, runs `./gradlew verifyReleaseApkSize`, verifies with `apksigner verify`, uploads `app-release.apk`, and deletes the temporary keystore under `if: always()`.

- [ ] **Step 5: Run the contract test and verify GREEN**

Run:

```powershell
python -m unittest tools.tests.test_release_security_config -v
```

Expected: all tests pass.

### Task 4: Generate the Local-Only Signing Identity

**Files:**
- Create (ignored): `.signing/miearn-release.jks`
- Create (ignored): `key.properties`

- [ ] **Step 1: Locate the active JDK keytool**

Run:

```powershell
$java = (Get-Command java -ErrorAction Stop).Source
$keytool = Join-Path (Split-Path (Split-Path $java)) 'bin\keytool.exe'
Test-Path $keytool
```

Expected: `True`; otherwise use Android Studio's bundled JBR path.

- [ ] **Step 2: Generate independent random credentials without printing them**

Use `System.Security.Cryptography.RandomNumberGenerator` to generate two independent 36-byte Base64url passwords in memory. Do not return or log them.

- [ ] **Step 3: Generate the keystore via environment-backed password options**

Run `keytool -genkeypair` with alias `miearn`, RSA 4096, SHA256withRSA, validity 10000 days, and:

```text
-storepass:env MIEARN_GENERATED_STORE_PASSWORD
-keypass:env MIEARN_GENERATED_KEY_PASSWORD
-dname "CN=MIearn Release, O=MIearn, C=CN"
```

- [ ] **Step 4: Write ignored local properties without displaying them**

Write these keys directly from in-memory variables:

```properties
storeFile=.signing/miearn-release.jks
storePassword=<generated store password>
keyAlias=miearn
keyPassword=<generated key password>
```

- [ ] **Step 5: Verify ignore and tracking state**

Run:

```powershell
git check-ignore -v key.properties .signing/miearn-release.jks
git ls-files --error-unmatch key.properties
git ls-files --error-unmatch .signing/miearn-release.jks
```

Expected: ignore rules match; both `git ls-files` calls fail because the files are untracked.

### Task 5: Delete Stale Output and Verify

**Files:**
- Delete: `app/build/outputs/apk/release/app-release-unsigned.apk`
- Produce (ignored): `app/build/outputs/apk/release/app-release.apk`

- [ ] **Step 1: Delete only the verified stale unsigned APK**

Resolve the path, confirm it is under the workspace release output directory, then remove it with `Remove-Item -LiteralPath`.

- [ ] **Step 2: Run repository-required verification**

Run:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat verifyReleaseApkSize
```

Expected: each command exits `0`.

- [ ] **Step 3: Verify the signed APK**

Run the Android SDK `apksigner` against:

```text
app/build/outputs/apk/release/app-release.apk
```

Expected: signature verification exits `0`.

- [ ] **Step 4: Perform final redaction and scope checks**

Run the security contract test, inspect `git diff --check`, inspect `git status --short`, scan tracked/diff content for secret-shaped assignments, confirm no unsigned Release APK remains, and repeat the historical path checks.

Expected: only intended source/docs/workflow/test files are uncommitted; no secret value or signing artifact is tracked or shown.
