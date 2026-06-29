# Android Release Signing and History Cleanup Design

## Goal

Remove the exposed Android release keystore from local Git history, replace it
with a new local-only signing identity, add safe GitHub Actions workflows, and
leave all new repository changes uncommitted for the owner to review.

## Scope

- Rewrite local Git history to remove `milearn-release.jks` from every ref.
- Do not push rewritten history or create a new feature/configuration commit.
- Generate a new keystore under the ignored `.signing/` directory.
- Store local signing paths and credentials only in ignored `key.properties`.
- Keep `key.properties.example`, README instructions, and Gradle configuration
  consistent.
- Add CI for tests, lint, and Debug APK assembly.
- Add a manually or tag-triggered signed Release APK workflow.
- Delete the stale unsigned Release APK.
- Verify builds and verify that the old keystore path is absent from reachable
  local Git history.

## Security and Redaction Requirements

- Never print passwords, private-key material, complete `key.properties`
  contents, or keystore Base64 to terminal output or assistant messages.
- Generate independent cryptographically random store and key passwords.
- Write secrets directly to ignored local files; do not pass them through
  command-line arguments when a safer response-file or protected process
  mechanism is available.
- Do not add the new keystore, `key.properties`, generated Base64, build
  artifacts, or secret-bearing temporary files to Git.
- GitHub Actions must consume secrets through `${{ secrets.* }}` and must not
  echo them.
- Temporary CI keystore data must live under the runner temporary directory and
  be removed in an `always()` cleanup step.
- Validate examples and documentation using placeholders only.
- Before handoff, inspect the Git diff for secret-shaped values and list tracked
  files to confirm no signing secret is staged or tracked.

## History Cleanup

Preferred implementation uses `git filter-repo` with an inverted path filter
for `milearn-release.jks`. If it is unavailable, use `git filter-branch` with
an index filter limited to that exact path. Preserve the configured remote URL
locally if the selected tool removes the remote, but do not fetch or push.

The rewrite must run while the worktree is clean. After rewriting, verify:

- `git log --all -- milearn-release.jks` returns no commits.
- `git rev-list --objects --all` contains no `milearn-release.jks` path.
- the current tracked tree does not contain signing secrets.

Local reflogs and backup refs created by the rewrite must be removed so the old
object is no longer reachable through normal local refs. Object pruning may be
performed locally after verification. Remote history remains unchanged until
the owner force-pushes the rewritten branch.

## Local Signing Identity

Generate `.signing/miearn-release.jks` using the JDK `keytool` available from
the Android/Gradle toolchain. Use alias `miearn`, a long validity period, and a
non-identifying distinguished name such as `CN=MIearn Release, O=MIearn,
C=CN`.

Generate two independent random passwords. Save them only in ignored
`key.properties` together with the absolute or project-relative keystore path.
Do not display their values. The existing Gradle configuration continues to
prefer `key.properties` values and falls back to the four `MIEARN_*`
environment variables.

## Repository Configuration

`key.properties.example` remains the canonical committed template. Its comment
must instruct users to copy it to `key.properties`. README uses the same names
and documents both local signing and GitHub secret setup.

The Release size gate must continue to support unsigned local builds when no
signing configuration exists, while producing `app-release.apk` when signing
is configured.

## GitHub Actions

Create two workflows:

1. `android-ci.yml` runs on pushes and pull requests. It checks out the
   repository, installs a supported JDK, restores Gradle caching, runs unit
   tests and lint, assembles the Debug APK, and uploads the Debug APK.
2. `android-release.yml` runs on version tags and manual dispatch. It validates
   the four required secrets, decodes `MIEARN_KEYSTORE_BASE64` into the runner
   temporary directory, exports the existing `MIEARN_*` signing variables,
   builds and size-checks the signed Release APK, verifies its signature, and
   uploads the signed APK artifact. It removes the temporary keystore in an
   `always()` step.

The required GitHub secrets are:

- `MIEARN_KEYSTORE_BASE64`
- `MIEARN_KEYSTORE_PASSWORD`
- `MIEARN_KEY_ALIAS`
- `MIEARN_KEY_PASSWORD`

No secret values are stored in workflow YAML or documentation.

## Error Handling

- Abort history rewriting if the worktree is dirty.
- Abort signing setup if no usable `keytool` is available.
- Abort Release CI before Gradle execution if any required secret is missing.
- Abort local verification if the signed APK is absent or signature
  verification fails.
- Preserve local credentials and keystore if a build fails; delete only stale
  unsigned build output and temporary, non-secret verification artifacts.

## Verification

Run the repository-required checks:

- `gradlew.bat assembleDebug`
- `gradlew.bat test`
- `gradlew.bat lint`

Then run the signed Release size gate and signature verification. Finally,
check Git status, ignored/tracked secret paths, workflow syntax, documentation
name consistency, stale unsigned APK absence, and historical object reachability.

## Handoff

Leave workflow, documentation, example, and any necessary Gradle changes
uncommitted. Report the rewritten local branch state and explain that updating
the remote requires an owner-reviewed force push. Do not expose local secret
values in the handoff.
