from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
EXAMPLE = ROOT / "key.properties.example"
README = ROOT / "README.md"
GITIGNORE = ROOT / ".gitignore"
CI_WORKFLOW = ROOT / ".github" / "workflows" / "android-ci.yml"
RELEASE_WORKFLOW = ROOT / ".github" / "workflows" / "android-release.yml"
REQUIRED_SECRET_NAMES = (
    "MIEARN_KEYSTORE_BASE64",
    "MIEARN_KEYSTORE_PASSWORD",
    "MIEARN_KEY_ALIAS",
    "MIEARN_KEY_PASSWORD",
)


class ReleaseSecurityConfigTest(unittest.TestCase):
    def test_local_signing_file_names_are_consistent(self) -> None:
        example = EXAMPLE.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")

        self.assertIn("Copy this file to key.properties", example)
        self.assertIn("`key.properties.example` to `key.properties`", readme)
        self.assertNotIn("keystore.properties.example", readme)

    def test_signing_secrets_and_artifacts_are_ignored(self) -> None:
        gitignore = GITIGNORE.read_text(encoding="utf-8")

        for pattern in ("key.properties", ".signing/", "*.jks", "*.keystore"):
            self.assertIn(pattern, gitignore)

    def test_ci_workflow_runs_repository_checks(self) -> None:
        self.assertTrue(CI_WORKFLOW.is_file(), "Debug CI workflow is missing")
        workflow = CI_WORKFLOW.read_text(encoding="utf-8")

        for command in ("test", "lint", "assembleDebug"):
            self.assertIn(command, workflow)
        self.assertIn("app-debug.apk", workflow)
        self.assertIn("permissions:", workflow)
        self.assertIn("contents: read", workflow)

    def test_release_workflow_uses_only_secret_references_and_temp_keystore(
        self,
    ) -> None:
        self.assertTrue(
            RELEASE_WORKFLOW.is_file(),
            "Signed Release workflow is missing",
        )
        workflow = RELEASE_WORKFLOW.read_text(encoding="utf-8")

        for name in REQUIRED_SECRET_NAMES:
            self.assertIn(f"secrets.{name}", workflow)
        self.assertIn("runner.temp", workflow)
        self.assertIn("if: always()", workflow)
        self.assertIn("verifyReleaseApkSize", workflow)
        self.assertIn("apksigner verify", workflow)
        self.assertIn("app-release.apk", workflow)
        self.assertNotIn("storePassword=", workflow)
        self.assertNotIn("keyPassword=", workflow)

    def test_readme_lists_secret_names_without_values(self) -> None:
        readme = README.read_text(encoding="utf-8")

        for name in REQUIRED_SECRET_NAMES:
            self.assertIn(f"`{name}`", readme)
        self.assertIn("Base64", readme)
        self.assertIn("不要", readme)


if __name__ == "__main__":
    unittest.main()
