#!/usr/bin/env python3
"""
Unit & Scenario Test Suite for CI Error Report Generator (David Finance).
Covers all 7 mandatory verification scenarios + secret redaction + schema validation.
"""

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

import generate_ci_error_report as gen


class TestCIErrorReportGenerator(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.out_md = self.root / "ci-error-report.md"
        self.out_json = self.root / "ci-error-report.json"

        # Create sample Kotlin source files for context extraction tests
        app_src = self.root / "app" / "src" / "main" / "java" / "com" / "david" / "finance"
        app_src.mkdir(parents=True, exist_ok=True)
        self.sample_kt = app_src / "MainViewModel.kt"
        self.sample_kt.write_text(
            "package com.david.finance\n"
            "\n"
            "class MainViewModel {\n"
            "    fun calculateTotal(items: List<Int>): Int {\n"
            "        val sum = items.sumOf { it }\n"
            "        return brokenReference + sum\n"
            "    }\n"
            "}\n",
            encoding="utf-8",
        )

        core_db = self.root / "core" / "database" / "src" / "main" / "java" / "com" / "david" / "core" / "db"
        core_db.mkdir(parents=True, exist_ok=True)
        self.sample_dao_kt = core_db / "TransactionDao.kt"
        self.sample_dao_kt.write_text(
            "package com.david.core.db\n"
            "\n"
            "interface TransactionDao {\n"
            "    fun getAll(): UnknownEntityList\n"
            "}\n",
            encoding="utf-8",
        )

        test_src = self.root / "app" / "src" / "test" / "java" / "com" / "david" / "finance"
        test_src.mkdir(parents=True, exist_ok=True)
        self.sample_test_kt = test_src / "BudgetCalculatorTest.kt"
        self.sample_test_kt.write_text(
            "package com.david.finance\n"
            "\n"
            "import org.junit.Assert.assertEquals\n"
            "import org.junit.Test\n"
            "\n"
            "class BudgetCalculatorTest {\n"
            "    @Test\n"
            "    fun `should compute remaining budget accurately`() {\n"
            "        val expected = 500\n"
            "        val actual = 450\n"
            "        assertEquals(expected, actual)\n"
            "    }\n"
            "}\n",
            encoding="utf-8",
        )

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def _base_workflow_meta(self, status: str = "failure") -> dict:
        return {
            "repository": "smaks190-crypto/-",
            "name": "Android CI",
            "runId": "999888777",
            "jobName": "build",
            "branch": "ci/error-report-generator",
            "commitSha": "1234567890abcdef1234567890abcdef12345678",
            "commitMessage": "test: verify CI error report generation",
            "timestampUtc": "2026-04-01T12:00:00Z",
            "status": status,
        }

    def test_scenario_1_kotlin_compile_error_with_coordinates(self) -> None:
        """Scenario 1: Ошибка компиляции Kotlin с известными координатами."""
        raw_data = {
            "status": "failure",
            "workflow": self._base_workflow_meta("failure"),
            "tasks": [
                {
                    "path": ":app:compileDebugKotlin",
                    "outcome": "FAILED",
                    "failureMessages": ["Compilation error. See log for more details"],
                    "logLines": [
                        f"e: file://{self.sample_kt}:6:16 Unresolved reference 'brokenReference'."
                    ],
                }
            ],
        }
        payload = gen.generate_reports(
            repo_root=self.root,
            raw_diagnostics=raw_data,
            output_md=self.out_md,
            output_json=self.out_json,
            append_step_summary=False,
        )
        self.assertEqual(payload["schemaVersion"], "1.0")
        self.assertEqual(payload["summary"]["totalErrors"], 1)
        self.assertEqual(payload["summary"]["primaryErrors"], 1)

        err = payload["errors"][0]
        self.assertEqual(err["id"], "ERR-001")
        self.assertEqual(err["file"], "app/src/main/java/com/david/finance/MainViewModel.kt")
        self.assertEqual(err["line"], 6)
        self.assertEqual(err["column"], 16)
        self.assertEqual(err["type"], "Unresolved reference")
        self.assertEqual(err["gradleTask"], ":app:compileDebugKotlin")
        self.assertEqual(err["ciStage"], "Stage B: Kotlin Compilation")
        self.assertEqual(err["classification"], "primary")
        self.assertIn("return brokenReference + sum", err["context"])
        self.assertIn("^", err["context"])

    def test_scenario_2_unit_test_failure(self) -> None:
        """Scenario 2: Ошибка Unit Test с извлечением координат из JUnit XML и стектрейса."""
        junit_dir = self.root / "app" / "build" / "test-results" / "testDebugUnitTest"
        junit_dir.mkdir(parents=True, exist_ok=True)
        junit_xml = junit_dir / "TEST-com.david.finance.BudgetCalculatorTest.xml"
        junit_xml.write_text(
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<testsuite name="com.david.finance.BudgetCalculatorTest" tests="1" failures="1" errors="0">\n'
            '  <testcase name="should compute remaining budget accurately" classname="com.david.finance.BudgetCalculatorTest" time="0.012">\n'
            '    <failure message="java.lang.AssertionError: expected:&lt;500&gt; but was:&lt;450&gt;" type="java.lang.AssertionError">\n'
            "java.lang.AssertionError: expected:&lt;500&gt; but was:&lt;450&gt;\n"
            "\tat org.junit.Assert.fail(Assert.java:89)\n"
            "\tat com.david.finance.BudgetCalculatorTest.should compute remaining budget accurately(BudgetCalculatorTest.kt:11)\n"
            "    </failure>\n"
            "  </testcase>\n"
            "</testsuite>\n",
            encoding="utf-8",
        )

        raw_data = {
            "status": "failure",
            "workflow": self._base_workflow_meta("failure"),
            "tasks": [
                {
                    "path": ":app:testDebugUnitTest",
                    "outcome": "FAILED",
                    "failureMessages": ["There were failing tests."],
                    "logLines": [],
                }
            ],
        }
        payload = gen.generate_reports(
            repo_root=self.root,
            raw_diagnostics=raw_data,
            output_md=self.out_md,
            output_json=self.out_json,
            append_step_summary=False,
        )
        self.assertEqual(payload["summary"]["totalErrors"], 1)
        err = payload["errors"][0]
        self.assertIn("Unit Test Failure", err["type"])
        self.assertEqual(err["file"], "app/src/test/java/com/david/finance/BudgetCalculatorTest.kt")
        self.assertEqual(err["line"], 11)
        self.assertIsNone(err["column"])
        self.assertEqual(err["gradleTask"], ":app:testDebugUnitTest")
        self.assertEqual(err["ciStage"], "Stage D: Automated Testing")
        self.assertIn("assertEquals(expected, actual)", err["context"])

    def test_scenario_3_error_without_coordinates(self) -> None:
        """Scenario 3: Ошибка без координат (file='unknown', line=null, column=null)."""
        raw_data = {
            "status": "failure",
            "workflow": self._base_workflow_meta("failure"),
            "tasks": [
                {
                    "path": ":app:mergeDebugResources",
                    "outcome": "FAILED",
                    "failureMessages": [
                        "Execution failed for task ':app:mergeDebugResources'. AAPT2 aapt2-8.7.0 Daemon #0: Unexpected error during resource table merge"
                    ],
                    "logLines": [],
                }
            ],
        }
        payload = gen.generate_reports(
            repo_root=self.root,
            raw_diagnostics=raw_data,
            output_md=self.out_md,
            output_json=self.out_json,
            append_step_summary=False,
        )
        self.assertEqual(payload["summary"]["totalErrors"], 1)
        err = payload["errors"][0]
        self.assertEqual(err["file"], "unknown")
        self.assertIsNone(err["line"])
        self.assertIsNone(err["column"])
        self.assertEqual(err["ciStage"], "Stage C: Full Build & Packaging")
        self.assertNotEqual(payload["recommendations"]["additionalDataNeeded"], "не требуются (координаты и контекст определены)")

        md = gen.render_markdown_report(payload)
        self.assertIn("- **Файл:** `unknown`", md)
        self.assertIn("- **Строка:** `unknown`", md)
        self.assertIn("- **Столбец:** `unknown`", md)

    def test_scenario_4_multiple_errors_primary_and_secondary_grouping(self) -> None:
        """Scenario 4: Несколько ошибок в одном запуске (первичные и вторичные + группировка)."""
        raw_data = {
            "status": "failure",
            "workflow": self._base_workflow_meta("failure"),
            "tasks": [
                {
                    "path": ":core:database:compileDebugKotlin",
                    "outcome": "FAILED",
                    "failureMessages": ["Compilation error"],
                    "logLines": [
                        f"e: file://{self.sample_dao_kt}:4:19 Unresolved reference 'UnknownEntityList'."
                    ],
                },
                {
                    "path": ":app:compileDebugKotlin",
                    "outcome": "FAILED",
                    "failureMessages": ["Compilation error"],
                    "logLines": [
                        f"e: file://{self.sample_kt}:6:16 Unresolved reference 'brokenReference'."
                    ],
                },
            ],
        }
        payload = gen.generate_reports(
            repo_root=self.root,
            raw_diagnostics=raw_data,
            output_md=self.out_md,
            output_json=self.out_json,
            append_step_summary=False,
        )
        self.assertEqual(payload["summary"]["totalErrors"], 2)
        self.assertEqual(payload["summary"]["primaryErrors"], 1)
        self.assertEqual(payload["summary"]["secondaryErrors"], 1)

        err1, err2 = payload["errors"][0], payload["errors"][1]
        self.assertEqual(err1["id"], "ERR-001")
        self.assertEqual(err1["classification"], "primary")
        self.assertEqual(err1["gradleTask"], ":core:database:compileDebugKotlin")
        self.assertIn("ERR-002", err1["relatedErrors"])

        self.assertEqual(err2["id"], "ERR-002")
        self.assertEqual(err2["classification"], "secondary")
        self.assertEqual(err2["gradleTask"], ":app:compileDebugKotlin")
        self.assertIn("ERR-001", err2["relatedErrors"])

        self.assertIn("core/database/src/main/java/com/david/core/db/TransactionDao.kt", payload["groups"]["byFile"])
        self.assertIn(":core:database:compileDebugKotlin", payload["groups"]["byGradleTask"])
        self.assertIn("Unresolved reference", payload["groups"]["byType"])

    def test_scenario_5_unknown_error_type_unclassified(self) -> None:
        """Scenario 5: Ошибка неизвестного типа (type='unclassified')."""
        raw_data = {
            "status": "failure",
            "workflow": self._base_workflow_meta("failure"),
            "tasks": [
                {
                    "path": ":app:customOpaqueVerificationTask",
                    "outcome": "FAILED",
                    "failureMessages": [
                        "Opaque subsystem state mismatch code 0x9942 in custom verification runner"
                    ],
                    "logLines": [],
                }
            ],
        }
        payload = gen.generate_reports(
            repo_root=self.root,
            raw_diagnostics=raw_data,
            output_md=self.out_md,
            output_json=self.out_json,
            append_step_summary=False,
        )
        self.assertEqual(payload["summary"]["totalErrors"], 1)
        self.assertEqual(payload["summary"]["unclassifiedErrors"], 1)
        err = payload["errors"][0]
        self.assertEqual(err["type"], "unclassified")
        self.assertIn("Opaque subsystem state mismatch code 0x9942", err["message"])

    def test_scenario_6_successful_run_without_errors(self) -> None:
        """Scenario 6: Успешный запуск без ошибок (status='success', totalErrors=0)."""
        raw_data = {
            "status": "success",
            "workflow": self._base_workflow_meta("success"),
            "tasks": [
                {
                    "path": ":ciStageAFastChecks",
                    "outcome": "SUCCESS",
                    "failureMessages": [],
                    "logLines": [],
                },
                {
                    "path": ":app:assembleDebug",
                    "outcome": "SUCCESS",
                    "failureMessages": [],
                    "logLines": [],
                },
            ],
        }
        payload = gen.generate_reports(
            repo_root=self.root,
            raw_diagnostics=raw_data,
            output_md=self.out_md,
            output_json=self.out_json,
            append_step_summary=False,
        )
        self.assertEqual(payload["workflow"]["status"], "success")
        self.assertEqual(payload["summary"]["totalErrors"], 0)
        self.assertEqual(payload["summary"]["primaryErrors"], 0)
        self.assertEqual(payload["summary"]["secondaryErrors"], 0)
        self.assertEqual(payload["summary"]["unclassifiedErrors"], 0)

        md = gen.render_markdown_report(payload)
        self.assertIn("Ошибок не обнаружено", md)

    def test_scenario_7_generator_internal_error_fallback_and_secret_redaction(self) -> None:
        """Scenario 7: Ошибка внутри самого генератора отчёта (fallback + exit 0 + маскирование секретов)."""
        raw_path = self.root / "raw-broken.json"
        out_md = self.root / "fallback-report.md"
        out_json = self.root / "fallback-report.json"

        raw_path.write_text(
            json.dumps(
                {
                    "simulateGeneratorInternalError": True,
                    "workflow": self._base_workflow_meta("failure"),
                }
            ),
            encoding="utf-8",
        )

        proc = subprocess.run(
            [
                sys.executable,
                str(SCRIPT_DIR / "generate_ci_error_report.py"),
                "--repo-root",
                str(self.root),
                "--input",
                str(raw_path),
                "--output-md",
                str(out_md),
                "--output-json",
                str(out_json),
                "--status",
                "failure",
                "--no-step-summary",
            ],
            capture_output=True,
            text=True,
            check=False,
        )
        # Generator MUST exit with 0 so it never masks original CI failure or breaks post-steps
        self.assertEqual(proc.returncode, 0)
        self.assertTrue(out_md.is_file())
        self.assertTrue(out_json.is_file())

        fallback_data = json.loads(out_json.read_text(encoding="utf-8"))
        self.assertEqual(fallback_data["schemaVersion"], "1.0")
        self.assertEqual(fallback_data["workflow"]["status"], "failure")
        self.assertEqual(fallback_data["errors"][0]["type"], "unclassified")

        # Secret redaction verification
        secret_sample = (
            "Failed with token ghp_1234567890abcdefghijklmnopqrstuvwxyzABCD "
            "and Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.secret.sig "
            "and password=mySuperSecretPassword123"
        )
        redacted = gen.redact_secrets(secret_sample)
        self.assertNotIn("ghp_1234567890", redacted)
        self.assertNotIn("mySuperSecretPassword123", redacted)
        self.assertIn("[REDACTED]", redacted)


if __name__ == "__main__":
    unittest.main()
