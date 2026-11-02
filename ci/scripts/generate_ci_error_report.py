#!/usr/bin/env python3
"""
CI Error Report Generator for David Finance AI.

Generates structured `ci-error-report.md` and `ci-error-report.json` files
from Gradle build diagnostics, Kotlin compiler output, JUnit XML reports,
Android Lint XML reports, and task execution results.

Safety guarantee:
- This script NEVER exits with a non-zero exit code during CI report generation,
  ensuring that an internal parser/generator issue can never mask the original
  CI build status or turn a failed build into a false success.
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import traceback
import xml.etree.ElementTree as ET
from typing import Any, Dict, List, Optional, Tuple


SCHEMA_VERSION = "1.0"

# Patterns for redacting secrets, tokens, keys, and passwords
SECRET_PATTERNS: List[Tuple[re.Pattern[str], str]] = [
    (re.compile(r"AIza[0-9A-Za-z_\-]{20,}"), "[REDACTED_GOOGLE_API_KEY]"),
    (re.compile(r"ghp_[0-9A-Za-z]{20,}"), "[REDACTED_GITHUB_TOKEN]"),
    (re.compile(r"ghs_[0-9A-Za-z]{20,}"), "[REDACTED_GITHUB_TOKEN]"),
    (re.compile(r"github_pat_[0-9A-Za-z_]{20,}"), "[REDACTED_GITHUB_TOKEN]"),
    (re.compile(r"(?i)(Bearer\s+)[0-9A-Za-z._\-]{16,}"), r"\1[REDACTED_TOKEN]"),
    (
        re.compile(
            r"(?i)((?:GEMINI_API_KEY|STORE_PASSWORD|KEY_PASSWORD|RELEASE_KEYSTORE_BASE64|GH_TOKEN|GITHUB_TOKEN|ACTIONS_RUNTIME_TOKEN|password|storePassword|keyPassword)\s*[:=]\s*)([^\s\"',;]+)"
        ),
        r"\1[REDACTED]",
    ),
]

# Kotlin compiler error line:
# e: file:///path/to/File.kt:142:18 Initializer type mismatch: ...
# or e: /path/to/File.kt: (142, 18): ...
KOTLIN_ERROR_RE = re.compile(
    r"^e:\s+(?:file://)?(?P<file>[^\s:]+?\.kts?):(?:(?P<line>\d+):(?:(?P<col>\d+):?)?|\s*\((?P<line2>\d+),\s*(?P<col2>\d+)\):?)\s*(?P<msg>.+)$"
)

# Gradle script compilation error:
# Script compilation error: /path/build.gradle.kts:42:10: ...
GRADLE_KTS_ERROR_RE = re.compile(
    r"^(?:Script compilation error|e):\s*(?:file://)?(?P<file>[^\s:]+?\.gradle\.kts):(?P<line>\d+)(?::(?P<col>\d+))?:\s*(?P<msg>.+)$"
)

# GitHub Actions workflow command error:
# ::error file=version.properties,line=1,col=1::Invalid VERSION_CODE
GH_ANNOTATION_ERROR_RE = re.compile(
    r"^::error\s+(?P<attrs>[^:]+)::(?P<msg>.+)$"
)

# AAPT / Android resource error:
# ERROR: /path/to/res/values/strings.xml:15:5-22: AAPT: error: ...
AAPT_ERROR_RE = re.compile(
    r"^(?:ERROR:\s*)?(?P<file>[^\s:]+\.xml):(?P<line>\d+)(?::(?P<col>\d+)(?:-\d+)?)?:\s*(?:AAPT:\s*error:\s*|error:\s*)(?P<msg>.+)$"
)


def redact_secrets(text: Optional[str], extra_secrets: Optional[List[str]] = None) -> str:
    if not text:
        return ""
    result = str(text)
    if extra_secrets:
        for secret in extra_secrets:
            if secret and len(secret) >= 6:
                result = result.replace(secret, "[REDACTED]")
    for pattern, replacement in SECRET_PATTERNS:
        result = pattern.sub(replacement, result)
    return result


def collect_env_secrets() -> List[str]:
    secret_env_keys = (
        "GEMINI_API_KEY",
        "SECRET_ENV_FILE",
        "ENV_FILE",
        "STORE_PASSWORD",
        "KEY_PASSWORD",
        "RELEASE_KEYSTORE_BASE64",
        "GH_TOKEN",
        "GITHUB_TOKEN",
        "ACTIONS_RUNTIME_TOKEN",
    )
    secrets: List[str] = []
    for key in secret_env_keys:
        val = os.environ.get(key, "").strip()
        if val and len(val) >= 6:
            secrets.append(val)
            for line in val.splitlines():
                if "=" in line:
                    _, rhs = line.split("=", 1)
                    if len(rhs.strip()) >= 6:
                        secrets.append(rhs.strip())
    return secrets


def map_task_to_ci_stage(task_path: Optional[str]) -> str:
    if not task_path or task_path == "unknown":
        return "unknown"
    t = task_path.lower()
    if "cistageafastchecks" in t or "check_style" in t or "localize_release" in t:
        return "Stage A: Fast Checks & Change Detection"
    if (
        "compile" in t
        or "ksp" in t
        or "cistagebcompilekotlin" in t
    ):
        return "Stage B: Kotlin Compilation"
    if "test" in t:
        return "Stage D: Automated Testing"
    if (
        "dex" in t
        or "package" in t
        or "assemble" in t
        or "bundle" in t
        or "merge" in t
        or "resource" in t
        or "manifest" in t
        or "lint" in t
        or "sign" in t
    ):
        return "Stage C: Full Build & Packaging"
    return "Stage C: Full Build & Packaging"


def classify_kotlin_error_type(message: str) -> Tuple[str, Optional[str]]:
    msg = message.strip()
    lower = msg.lower()
    if "unresolved reference" in lower:
        m = re.search(r"Unresolved reference[:\s]+'?(?P<sym>[A-Za-z0-9_]+)'?", msg)
        sym = m.group("sym") if m else None
        cause = (
            f"Идентификатор `{sym}` не объявлен в текущей области видимости, переименован или отсутствует соответствующий import."
            if sym
            else "Обращение к необъявленному символу, методу или свойству (либо отсутствует import)."
        )
        return ("Unresolved reference", cause)
    if "type mismatch" in lower:
        return (
            "Type mismatch",
            "Тип переданного значения или выражения не совпадает с ожидаемым типом сигнатуры/переменной.",
        )
    if (
        "expecting" in lower
        or "unexpected tokens" in lower
        or "syntax error" in lower
    ):
        return (
            "Syntax error",
            "Синтаксическая ошибка в исходном коде Kotlin (пропущена скобка, токен или нарушена структура выражения).",
        )
    if "overload resolution ambiguity" in lower:
        return (
            "Overload resolution ambiguity",
            "Компилятор не может однозначно выбрать перегрузку функции для переданных аргументов.",
        )
    if "no value passed for parameter" in lower:
        return (
            "No value passed for parameter",
            "При вызове функции или конструктора не передан обязательный параметр.",
        )
    if "only safe (?.) or non-null asserted (!!.) calls are allowed" in lower:
        return (
            "Nullability error",
            "Обращение к nullable-типу без безопасного вызова `?.` или проверки на `null`.",
        )
    if "'when' expression must be exhaustive" in lower:
        return (
            "Non-exhaustive 'when' expression",
            "Выражение `when` не покрывает все возможные ветки enum/sealed-типа и не содержит ветки `else`.",
        )
    if "is not abstract and does not implement" in lower:
        return (
            "Unimplemented abstract member",
            "Класс не реализует все обязательные методы/свойства интерфейса или абстрактного базового класса.",
        )
    if "redeclaration" in lower or "conflicting overloads" in lower:
        return (
            "Redeclaration / Conflicting overloads",
            "Повторное объявление символа с тем же именем и сигнатурой в одной области видимости.",
        )
    return ("Kotlin compilation error", None)


def normalize_repo_path(raw_path: Optional[str], repo_root: Path) -> str:
    if not raw_path or raw_path == "unknown":
        return "unknown"
    cleaned = raw_path.strip()
    if cleaned.startswith("file://"):
        cleaned = cleaned[len("file://") :]
    try:
        p = Path(cleaned)
        if p.is_absolute():
            try:
                return p.resolve().relative_to(repo_root.resolve()).as_posix()
            except ValueError:
                # Match known repo top-level directories if path came from another runner root
                parts = p.parts
                for marker in ("app", "core", "gradle", ".github", "ci", "scripts"):
                    if marker in parts:
                        idx = parts.index(marker)
                        return Path(*parts[idx:]).as_posix()
                if len(parts) >= 2 and parts[-1].endswith((".kt", ".kts", ".xml", ".properties", ".toml")):
                    cand = repo_root / parts[-1]
                    if cand.is_file():
                        return parts[-1]
                return Path(cleaned).as_posix()
        return p.as_posix()
    except Exception:
        return cleaned


def extract_source_context(
    repo_root: Path,
    rel_file: str,
    line: Optional[int],
    column: Optional[int],
    radius: int = 3,
) -> Optional[str]:
    if not rel_file or rel_file == "unknown" or line is None or line <= 0:
        return None
    target = repo_root / rel_file
    if not target.is_file():
        return None
    try:
        lines = target.read_text(encoding="utf-8", errors="replace").splitlines()
        if line > len(lines):
            return None
        start = max(1, line - radius)
        end = min(len(lines), line + radius)
        out: List[str] = []
        for ln in range(start, end + 1):
            prefix = ">" if ln == line else " "
            out.append(f"{prefix} {ln:4d} | {lines[ln - 1]}")
            if ln == line and column is not None and column > 0:
                caret_pad = max(0, column - 1)
                out.append(f"       | {' ' * caret_pad}^")
        return "\n".join(out)
    except Exception:
        return None


def locate_source_file_by_name(repo_root: Path, filename: str, package_hint: Optional[str] = None) -> str:
    if not filename:
        return "unknown"
    candidates: List[Path] = []
    for search_dir in ("app/src/test", "app/src/main", "app/src/androidTest", "core/database/src"):
        base = repo_root / search_dir
        if base.is_dir():
            candidates.extend(base.rglob(filename))
    if not candidates:
        return "unknown"
    if package_hint:
        pkg_path = package_hint.replace(".", "/")
        for c in candidates:
            if pkg_path in c.as_posix():
                return c.relative_to(repo_root).as_posix()
    return candidates[0].relative_to(repo_root).as_posix()


def parse_junit_xml_reports(repo_root: Path, extra_secrets: List[str]) -> List[Dict[str, Any]]:
    errors: List[Dict[str, Any]] = []
    xml_files: List[Path] = []
    for pattern in (
        "app/build/test-results/**/*.xml",
        "core/database/build/test-results/**/*.xml",
    ):
        xml_files.extend(sorted(repo_root.glob(pattern)))

    for xml_path in xml_files:
        try:
            tree = ET.parse(xml_path)
            root = tree.getroot()
        except Exception:
            continue

        # Determine Gradle task from parent directory name (e.g. testDebugUnitTest)
        parent_name = xml_path.parent.name
        gradle_task = f":app:{parent_name}" if parent_name.startswith("test") else ":app:testDebugUnitTest"

        testcases = root.findall(".//testcase")
        for tc in testcases:
            classname = tc.attrib.get("classname", "unknown")
            testname = tc.attrib.get("name", "unknown")
            failure_el = tc.find("failure")
            error_el = tc.find("error")
            node = failure_el if failure_el is not None else error_el
            if node is None:
                continue

            raw_msg = node.attrib.get("message") or ""
            raw_type = node.attrib.get("type") or "UnitTestFailure"
            stacktrace = (node.text or "").strip()

            short_type = raw_type.split(".")[-1] if "." in raw_type else raw_type
            error_type = f"Unit Test Failure ({short_type})"

            # Find first stack frame in project package
            rel_file = "unknown"
            line_num: Optional[int] = None
            frame_re = re.compile(r"at\s+(?P<cls>[A-Za-z0-9_$.]+)\.(?P<method>[^(\n]+)\((?P<file>[A-Za-z0-9_]+\.kt):(?P<line>\d+)\)")
            for m in frame_re.finditer(stacktrace):
                cls_full = m.group("cls")
                pkg_parts = cls_full.split(".")[:-1]
                pkg_hint = ".".join(pkg_parts)
                candidate_file = locate_source_file_by_name(repo_root, m.group("file"), pkg_hint)
                if candidate_file != "unknown":
                    rel_file = candidate_file
                    line_num = int(m.group("line"))
                    break
            if rel_file != "unknown" and line_num is not None:
                pass
            else:
                # Fallback: locate test class file
                simple_cls = classname.split(".")[-1].split("$")[0]
                pkg_hint = ".".join(classname.split(".")[:-1])
                located = locate_source_file_by_name(repo_root, f"{simple_cls}.kt", pkg_hint)
                if located != "unknown":
                    rel_file = located

            src_ctx = extract_source_context(repo_root, rel_file, line_num, None)
            if not src_ctx and stacktrace:
                src_ctx = "\n".join(stacktrace.splitlines()[:10])

            full_message = f"{classname}.{testname}: {raw_msg}".strip(": ")
            if not raw_msg and stacktrace:
                full_message = f"{classname}.{testname}: {stacktrace.splitlines()[0]}"

            probable_cause = (
                f"Тест `{classname}.{testname}` завершился с исключением `{short_type}`."
                if short_type
                else None
            )

            errors.append(
                {
                    "file": rel_file,
                    "line": line_num,
                    "column": None,
                    "type": error_type,
                    "message": redact_secrets(full_message, extra_secrets),
                    "gradleTask": gradle_task,
                    "ciStage": "Stage D: Automated Testing",
                    "context": redact_secrets(src_ctx, extra_secrets) if src_ctx else None,
                    "probableCause": probable_cause,
                }
            )
    return errors


def parse_lint_xml_reports(repo_root: Path, extra_secrets: List[str]) -> List[Dict[str, Any]]:
    errors: List[Dict[str, Any]] = []
    for xml_path in sorted(repo_root.glob("**/build/reports/lint-results*.xml")):
        try:
            tree = ET.parse(xml_path)
            root = tree.getroot()
        except Exception:
            continue
        for issue in root.findall(".//issue"):
            severity = issue.attrib.get("severity", "")
            if severity.lower() not in ("error", "fatal"):
                continue
            issue_id = issue.attrib.get("id", "LintError")
            msg = issue.attrib.get("message", "Android Lint Error")
            loc = issue.find("location")
            rel_file = "unknown"
            line_num: Optional[int] = None
            col_num: Optional[int] = None
            if loc is not None:
                rel_file = normalize_repo_path(loc.attrib.get("file"), repo_root)
                if loc.attrib.get("line", "").isdigit():
                    line_num = int(loc.attrib["line"])
                if loc.attrib.get("column", "").isdigit():
                    col_num = int(loc.attrib["column"])
            src_ctx = extract_source_context(repo_root, rel_file, line_num, col_num)
            errors.append(
                {
                    "file": rel_file,
                    "line": line_num,
                    "column": col_num,
                    "type": f"Android Lint Error ({issue_id})",
                    "message": redact_secrets(msg, extra_secrets),
                    "gradleTask": ":app:lintDebug",
                    "ciStage": "Stage C: Full Build & Packaging",
                    "context": redact_secrets(src_ctx, extra_secrets) if src_ctx else None,
                    "probableCause": f"Проверка Android Lint обнаружила критическую проблему `{issue_id}`.",
                }
            )
    return errors


def parse_task_logs_and_failures(
    repo_root: Path,
    raw_diagnostics: Dict[str, Any],
    extra_secrets: List[str],
) -> List[Dict[str, Any]]:
    extracted: List[Dict[str, Any]] = []
    tasks_data = raw_diagnostics.get("tasks", [])
    global_lines = raw_diagnostics.get("globalLogLines", [])

    ansi_re = re.compile(r"\x1b\[[0-9;]*[a-zA-Z]")
    ts_prefix_re = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z\s?")
    task_header_re = re.compile(r"^>\s*Task\s+(:[A-Za-z0-9_:-]+)(?:\s+(FAILED|UP-TO-DATE|FROM-CACHE|SKIPPED|NO-SOURCE))?\s*$")
    exec_failed_re = re.compile(r"Execution failed for task \x27(:[A-Za-z0-9_:-]+)\x27")

    def scan_lines_for_task(lines: List[str], default_task_path: str) -> List[Dict[str, Any]]:
        task_errors: List[Dict[str, Any]] = []
        active_task = default_task_path
        for raw_line in lines:
            clean_line = ansi_re.sub("", ts_prefix_re.sub("", raw_line)).strip()
            if not clean_line:
                continue
            tm = task_header_re.match(clean_line)
            if tm:
                if default_task_path == "unknown":
                    active_task = tm.group(1)
                continue
            em = exec_failed_re.search(clean_line)
            if em and em.group(1) and default_task_path == "unknown":
                active_task = em.group(1)
            task_path = active_task
            stage = map_task_to_ci_stage(task_path)
            stripped = clean_line

            # 1. Kotlin compiler diagnostic
            km = KOTLIN_ERROR_RE.match(stripped)
            if km:
                raw_file = km.group("file")
                ln_str = km.group("line") or km.group("line2")
                col_str = km.group("col") or km.group("col2")
                msg = km.group("msg").strip()
                ln = int(ln_str) if ln_str and ln_str.isdigit() else None
                col = int(col_str) if col_str and col_str.isdigit() else None
                rel_file = normalize_repo_path(raw_file, repo_root)
                err_type, cause = classify_kotlin_error_type(msg)
                src_ctx = extract_source_context(repo_root, rel_file, ln, col)
                task_errors.append(
                    {
                        "file": rel_file,
                        "line": ln,
                        "column": col,
                        "type": err_type,
                        "message": redact_secrets(msg, extra_secrets),
                        "gradleTask": task_path,
                        "ciStage": stage if stage != "unknown" else "Stage B: Kotlin Compilation",
                        "context": redact_secrets(src_ctx, extra_secrets) if src_ctx else None,
                        "probableCause": cause,
                    }
                )
                continue

            # 2. Gradle Kotlin DSL script error
            gm = GRADLE_KTS_ERROR_RE.match(stripped)
            if gm:
                rel_file = normalize_repo_path(gm.group("file"), repo_root)
                ln = int(gm.group("line")) if gm.group("line") else None
                col = int(gm.group("col")) if gm.group("col") else None
                msg = gm.group("msg").strip()
                src_ctx = extract_source_context(repo_root, rel_file, ln, col)
                task_errors.append(
                    {
                        "file": rel_file,
                        "line": ln,
                        "column": col,
                        "type": "Gradle Script Compilation Error",
                        "message": redact_secrets(msg, extra_secrets),
                        "gradleTask": task_path,
                        "ciStage": "Stage A: Fast Checks & Change Detection",
                        "context": redact_secrets(src_ctx, extra_secrets) if src_ctx else None,
                        "probableCause": "Синтаксическая или типовая ошибка в конфигурационном файле Gradle Kotlin DSL.",
                    }
                )
                continue

            # 3. GitHub Actions ::error annotation from Stage A fast checks
            am = GH_ANNOTATION_ERROR_RE.match(stripped)
            if am:
                attrs_raw = am.group("attrs")
                msg = am.group("msg").strip()
                attrs: Dict[str, str] = {}
                for part in attrs_raw.split(","):
                    if "=" in part:
                        k, v = part.split("=", 1)
                        attrs[k.strip()] = v.strip()
                rel_file = normalize_repo_path(attrs.get("file", "unknown"), repo_root)
                ln = int(attrs["line"]) if attrs.get("line", "").isdigit() else None
                col = int(attrs["col"]) if attrs.get("col", "").isdigit() else None
                src_ctx = extract_source_context(repo_root, rel_file, ln, col)
                task_errors.append(
                    {
                        "file": rel_file,
                        "line": ln,
                        "column": col,
                        "type": "Configuration Validation Error",
                        "message": redact_secrets(msg, extra_secrets),
                        "gradleTask": task_path if task_path != "unknown" else ":ciStageAFastChecks",
                        "ciStage": "Stage A: Fast Checks & Change Detection",
                        "context": redact_secrets(src_ctx, extra_secrets) if src_ctx else None,
                        "probableCause": "Ошибка быстрой валидации конфигурации проекта на Этапе A.",
                    }
                )
                continue

            # 4. Android AAPT / XML resource error
            rm = AAPT_ERROR_RE.match(stripped)
            if rm:
                rel_file = normalize_repo_path(rm.group("file"), repo_root)
                ln = int(rm.group("line")) if rm.group("line") else None
                col = int(rm.group("col")) if rm.group("col") else None
                msg = rm.group("msg").strip()
                src_ctx = extract_source_context(repo_root, rel_file, ln, col)
                task_errors.append(
                    {
                        "file": rel_file,
                        "line": ln,
                        "column": col,
                        "type": "Android Resource / Manifest Error",
                        "message": redact_secrets(msg, extra_secrets),
                        "gradleTask": task_path,
                        "ciStage": "Stage C: Full Build & Packaging",
                        "context": redact_secrets(src_ctx, extra_secrets) if src_ctx else None,
                        "probableCause": "Ошибка компиляции или слияния Android-ресурсов / AndroidManifest.xml.",
                    }
                )
                continue

        return task_errors

    for t in tasks_data:
        t_path = t.get("path") or "unknown"
        t_outcome = t.get("outcome") or ""
        t_lines = t.get("logLines") or []
        t_failures = t.get("failureMessages") or []

        found_in_logs = scan_lines_for_task(t_lines, t_path)
        extracted.extend(found_in_logs)

        # If task failed but had no structured compiler/resource lines (and is not a test task already covered by JUnit XML)
        if t_outcome == "FAILED" and not found_in_logs:
            # Skip generic test failure wrapper if JUnit XML errors will be parsed separately
            if "test" in t_path.lower() and any("There were failing tests" in fm for fm in t_failures):
                continue

            combined_msg = " | ".join(m.strip() for m in t_failures if m and m.strip())
            if not combined_msg:
                combined_msg = f"Execution failed for task '{t_path}'"

            # Check if message contains file:line coordinates
            inline_errors = scan_lines_for_task(combined_msg.splitlines(), t_path)
            if inline_errors:
                extracted.extend(inline_errors)
                continue

            # Determine if known configuration error vs unclassified
            err_type = "unclassified"
            cause: Optional[str] = None
            if "ciStageAFastChecks" in t_path or "version.properties" in combined_msg or "libs.versions.toml" in combined_msg:
                err_type = "Configuration Validation Error"
                cause = "Нарушена структура конфигурационного файла проекта или провалились тесты релизных скриптов на Этапе A."
            elif "OutOfMemoryError" in combined_msg or "Java heap space" in combined_msg:
                err_type = "JVM OutOfMemoryError"
                cause = "Нехватка памяти JVM (Heap/Metaspace) при выполнении Gradle-задачи."

            ctx_lines = t_lines[-15:] if t_lines else t_failures
            ctx_str = "\n".join(ctx_lines).strip() if ctx_lines else None

            extracted.append(
                {
                    "file": "unknown",
                    "line": None,
                    "column": None,
                    "type": err_type,
                    "message": redact_secrets(combined_msg, extra_secrets),
                    "gradleTask": t_path,
                    "ciStage": map_task_to_ci_stage(t_path),
                    "context": redact_secrets(ctx_str, extra_secrets) if ctx_str else None,
                    "probableCause": cause,
                }
            )

    if global_lines:
        extracted.extend(scan_lines_for_task(global_lines, raw_diagnostics.get("defaultTask", "unknown")))

    if not extracted and not raw_diagnostics.get("skipFilesystemScan"):
        diag_pages_dir = Path("/home/runner/_diag/pages")
        if diag_pages_dir.is_dir():
            try:
                log_files = sorted(diag_pages_dir.glob("*.log"), key=lambda f: f.stat().st_mtime)
                runner_lines: List[str] = []
                for lf in log_files[-6:]:
                    runner_lines.extend(lf.read_text(encoding="utf-8", errors="replace").splitlines())
                if runner_lines:
                    extracted.extend(scan_lines_for_task(runner_lines, "unknown"))
            except Exception:
                pass

    # Fallback: if buildStatus is failure and no task-level errors were recorded, check top-level buildFailureMessages
    build_failures = raw_diagnostics.get("buildFailureMessages") or []
    if not extracted and build_failures:
        for bf in build_failures:
            inline = scan_lines_for_task(str(bf).splitlines(), "unknown")
            if inline:
                extracted.extend(inline)
            else:
                extracted.append(
                    {
                        "file": "unknown",
                        "line": None,
                        "column": None,
                        "type": "unclassified",
                        "message": redact_secrets(str(bf).strip(), extra_secrets),
                        "gradleTask": "unknown",
                        "ciStage": "unknown",
                        "context": redact_secrets(str(bf).strip(), extra_secrets),
                        "probableCause": None,
                    }
                )

    return extracted


def deduplicate_and_classify_errors(raw_errors: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    # 1. Deduplicate identical (file, line, column, type, message, gradleTask)
    seen = set()
    unique_errors: List[Dict[str, Any]] = []
    for err in raw_errors:
        key = (
            err.get("file", "unknown"),
            err.get("line"),
            err.get("column"),
            err.get("type", "unclassified"),
            err.get("message", ""),
            err.get("gradleTask", "unknown"),
        )
        if key not in seen:
            seen.add(key)
            unique_errors.append(dict(err))

    # Assign sequential IDs: ERR-001, ERR-002, ...
    for idx, err in enumerate(unique_errors, start=1):
        err["id"] = f"ERR-{idx:03d}"
        err["classification"] = "primary"
        err["relatedErrors"] = []

    if len(unique_errors) <= 1:
        return unique_errors

    # 2. Identify primary vs secondary (cascading) errors:
    # Rule A: If :core:database compilation/KSP failed, downstream :app compilation/test errors are secondary
    core_primary_ids = [
        e["id"]
        for e in unique_errors
        if str(e.get("gradleTask", "")).startswith(":core:database")
    ]

    # Rule B: Within the same file, if an earlier error is a Syntax error or primary declaration/unresolved error,
    # subsequent errors in the same file (or Type mismatch on the same/later line) are linked
    first_error_by_file: Dict[str, Dict[str, Any]] = {}
    first_compile_error_id: Optional[str] = None
    for e in unique_errors:
        if "Stage B" in str(e.get("ciStage", "")) and first_compile_error_id is None:
            first_compile_error_id = e["id"]
        f = e.get("file", "unknown")
        if f != "unknown" and f not in first_error_by_file:
            first_error_by_file[f] = e

    for e in unique_errors:
        eid = e["id"]
        task = str(e.get("gradleTask", ""))
        f = e.get("file", "unknown")
        etype = str(e.get("type", ""))
        msg = str(e.get("message", ""))

        # Cascade from :core:database failure to :app
        if core_primary_ids and task.startswith(":app") and eid not in core_primary_ids:
            e["classification"] = "secondary"
            for cid in core_primary_ids:
                if cid not in e["relatedErrors"]:
                    e["relatedErrors"].append(cid)
            continue

        # Cascade from compile failure to unit test NoClassDefFoundError / ClassNotFoundException
        if (
            first_compile_error_id
            and eid != first_compile_error_id
            and ("NoClassDefFoundError" in msg or "ClassNotFoundException" in msg or "Compilation error" in msg)
        ):
            e["classification"] = "secondary"
            e["relatedErrors"].append(first_compile_error_id)
            continue

        # Same-file cascade: if the first error in this file is a Syntax error or an earlier line error
        # and this error is on the same or later line and is Type mismatch / Unresolved reference caused by broken syntax/declaration
        first_in_file = first_error_by_file.get(f)
        if first_in_file and first_in_file["id"] != eid:
            first_type = str(first_in_file.get("type", ""))
            first_line = first_in_file.get("line")
            cur_line = e.get("line")
            if first_type == "Syntax error" or (
                first_line is not None
                and cur_line is not None
                and cur_line >= first_line
                and first_type in ("Unresolved reference", "Redeclaration / Conflicting overloads")
                and etype in ("Type mismatch", "Overload resolution ambiguity")
            ):
                e["classification"] = "secondary"
                e["relatedErrors"].append(first_in_file["id"])

    # Populate reciprocal relatedErrors on primary errors
    by_id = {e["id"]: e for e in unique_errors}
    for e in unique_errors:
        for rel_id in list(e["relatedErrors"]):
            if rel_id in by_id and e["id"] not in by_id[rel_id]["relatedErrors"]:
                by_id[rel_id]["relatedErrors"].append(e["id"])

    return unique_errors


def resolve_git_metadata(repo_root: Path, overrides: Optional[Dict[str, Any]] = None) -> Dict[str, str]:
    overrides = overrides or {}

    def git_cmd(*args: str) -> str:
        try:
            proc = subprocess.run(
                ["git", *args],
                cwd=str(repo_root),
                capture_output=True,
                text=True,
                timeout=5,
                check=False,
            )
            return proc.stdout.strip() if proc.returncode == 0 else ""
        except Exception:
            return ""

    event_name = overrides.get("eventName") or os.environ.get("GITHUB_EVENT_NAME") or "local"
    workflow_sha = overrides.get("workflowSha") or os.environ.get("GITHUB_SHA") or git_cmd("rev-parse", "HEAD") or "unknown"

    # On pull_request, GITHUB_SHA is the temporary merge commit (HEAD) where HEAD^2 is the actual PR branch HEAD commit!
    branch_head_sha = overrides.get("commitSha") or os.environ.get("CI_REPORT_COMMIT_SHA") or ""
    if not branch_head_sha:
        if event_name == "pull_request":
            second_parent = git_cmd("rev-parse", "HEAD^2")
            branch_head_sha = second_parent if second_parent else workflow_sha
        else:
            branch_head_sha = git_cmd("rev-parse", "HEAD") or workflow_sha

    commit_msg = overrides.get("commitMessage") or git_cmd("log", "-1", "--pretty=%s", branch_head_sha) or git_cmd("log", "-1", "--pretty=%s") or "unknown"
    branch = (
        overrides.get("branch")
        or os.environ.get("GITHUB_HEAD_REF")
        or os.environ.get("GITHUB_REF_NAME")
        or git_cmd("rev-parse", "--abbrev-ref", "HEAD")
        or "unknown"
    )
    repo = overrides.get("repository") or os.environ.get("GITHUB_REPOSITORY") or "smaks190-crypto/-"
    workflow_name = overrides.get("workflowName") or os.environ.get("GITHUB_WORKFLOW") or "Build Android APK & AAB"
    run_id = str(overrides.get("runId") or os.environ.get("GITHUB_RUN_ID") or "local")
    job_name = overrides.get("jobName") or os.environ.get("GITHUB_JOB") or "build"
    timestamp_utc = overrides.get("timestampUtc") or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    return {
        "repository": repo,
        "name": workflow_name,
        "runId": run_id,
        "jobName": job_name,
        "branch": branch,
        "commitSha": branch_head_sha,
        "workflowSha": workflow_sha,
        "commitMessage": commit_msg,
        "timestampUtc": timestamp_utc,
    }


def build_recommendations(errors: List[Dict[str, Any]], status: str) -> Dict[str, Any]:
    if status == "success" and not errors:
        return {
            "summary": "Ошибок не обнаружено. Все обязательные этапы CI завершились успешно.",
            "fixFirst": [],
            "additionalDataNeeded": "Дополнительная диагностика не требуется.",
        }

    primary = [e for e in errors if e.get("classification") == "primary"]
    secondary = [e for e in errors if e.get("classification") == "secondary"]
    unclassified = [e for e in errors if e.get("type") == "unclassified" or e.get("file") == "unknown"]

    fix_first = [e["id"] for e in primary] or [e["id"] for e in errors[:1]]
    stages = sorted({e.get("ciStage", "unknown") for e in primary or errors})
    summary = (
        f"Обнаружено ошибок: {len(errors)} (первичных: {len(primary)}, вторичных/каскадных: {len(secondary)}) "
        f"на этапе {', '.join(stages)}."
    )

    if not unclassified:
        additional = "Полный лог CI не требуется: для всех ошибок извлечены точные координаты (файл, строка) и контекст исходного кода."
    else:
        tasks = sorted({e.get("gradleTask", "unknown") for e in unclassified})
        additional = (
            f"Для ошибок без координат ({', '.join(e['id'] for e in unclassified)}) при необходимости "
            f"проверьте точечный фрагмент job log вокруг задач: {', '.join(tasks)}."
        )

    return {
        "summary": summary,
        "fixFirst": fix_first,
        "additionalDataNeeded": additional,
    }


def render_markdown_report(report: Dict[str, Any]) -> str:
    wf = report["workflow"]
    summary = report["summary"]
    errors: List[Dict[str, Any]] = report["errors"]
    recs = report["recommendations"]
    groups = report["groups"]

    out: List[str] = []
    status_badge = "✅ SUCCESS" if wf["status"] == "success" else "❌ FAILURE"
    out.append(f"# 🩺 CI Error Report — {status_badge}")
    out.append("")
    out.append("## 1. Информация о запуске")
    out.append("")
    out.append("| Поле | Значение |")
    out.append("|---|---|")
    out.append(f"| **Repository** | `{wf['repository']}` |")
    out.append(f"| **Workflow name** | `{wf['name']}` |")
    out.append(f"| **Workflow run ID** | `{wf['runId']}` |")
    out.append(f"| **Job name** | `{wf['jobName']}` |")
    out.append(f"| **Branch** | `{wf['branch']}` |")
    out.append(f"| **Commit SHA** | `{wf['commitSha']}` |")
    if wf.get("workflowSha") and wf["workflowSha"] != wf["commitSha"]:
        out.append(f"| **Workflow Merge SHA** | `{wf['workflowSha']}` |")
    out.append(f"| **Commit message** | {wf['commitMessage']} |")
    out.append(f"| **Дата и время запуска (UTC)** | `{wf['timestampUtc']}` |")
    out.append(f"| **Итоговый статус workflow** | **`{wf['status']}`** |")
    out.append("")

    out.append("## 2. Сводка и группировка ошибок")
    out.append("")
    out.append(
        f"- **Всего ошибок:** `{summary['totalErrors']}` "
        f"(Первичных: `{summary['primaryErrors']}`, Вторичных/каскадных: `{summary['secondaryErrors']}`, "
        f"Неклассифицированных: `{summary['unclassifiedErrors']}`)"
    )
    out.append("")

    if not errors:
        out.append("> ✅ **Ошибок не обнаружено.** Все предусмотренные этапы сборки и тестирования завершились успешно.")
        out.append("")
    else:
        if len(errors) > 1:
            out.append("### Группировка по файлам")
            for f_path, e_ids in groups["byFile"].items():
                out.append(f"- `{f_path}`: {', '.join(f'`{i}`' for i in e_ids)}")
            out.append("")
            out.append("### Группировка по Gradle-задачам")
            for g_task, e_ids in groups["byGradleTask"].items():
                out.append(f"- `{g_task}`: {', '.join(f'`{i}`' for i in e_ids)}")
            out.append("")
            out.append("### Группировка по типам / причинам")
            for e_type, e_ids in groups["byType"].items():
                out.append(f"- `{e_type}`: {', '.join(f'`{i}`' for i in e_ids)}")
            out.append("")

        out.append("## 3. Детальная информация об ошибках")
        out.append("")
        for err in errors:
            file_str = err["file"] if err.get("file") else "unknown"
            line_str = str(err["line"]) if err.get("line") is not None else "unknown"
            col_str = str(err["column"]) if err.get("column") is not None else "unknown"
            rel_str = ", ".join(f"`{r}`" for r in err.get("relatedErrors", [])) or "none"
            cause_str = err.get("probableCause") or "unknown (требуется проверка контекста)"

            out.append(f"### [{err['id']}] {err['type']} ({err['classification'].upper()})")
            out.append("")
            out.append(f"- **Error ID:** `{err['id']}`")
            out.append(f"- **Файл:** `{file_str}`")
            out.append(f"- **Строка:** `{line_str}`")
            out.append(f"- **Столбец:** `{col_str}`")
            out.append(f"- **Тип ошибки:** `{err['type']}`")
            out.append(f"- **Gradle task:** `{err['gradleTask']}`")
            out.append(f"- **Этап CI:** `{err['ciStage']}`")
            out.append(f"- **Категория:** `{err['classification']}`")
            out.append(f"- **Связанные ошибки:** {rel_str}")
            out.append(f"- **Предполагаемая причина:** {cause_str}")
            out.append(f"- **Полный текст ошибки:**")
            out.append("```text")
            out.append(err["message"])
            out.append("```")
            if err.get("context"):
                out.append("- **Фрагмент исходного кода / диагностический контекст:**")
                out.append("```kotlin")
                out.append(err["context"])
                out.append("```")
            out.append("")

    out.append("## 4. Рекомендации для диагностики и исправления")
    out.append("")
    out.append(f"- **Краткое описание проблемы:** {recs['summary']}")
    fix_first_str = ", ".join(f"`{i}`" for i in recs["fixFirst"]) if recs["fixFirst"] else "нет ошибок"
    out.append(f"- **Исправить в первую очередь (Primary Errors):** {fix_first_str}")
    out.append(f"- **Дополнительные данные для диагностики:** {recs['additionalDataNeeded']}")
    out.append("")
    return "\n".join(out)


def generate_reports(
    repo_root: Path,
    raw_diagnostics: Dict[str, Any],
    output_md: Path,
    output_json: Path,
    append_step_summary: bool = True,
) -> Dict[str, Any]:
    extra_secrets = collect_env_secrets()

    if raw_diagnostics.get("simulateGeneratorInternalError"):
        raise RuntimeError("Simulated internal error in CI Error Report generator")

    wf_meta = resolve_git_metadata(repo_root, raw_diagnostics.get("workflow"))

    # Collect errors from all structured sources
    task_and_compiler_errors = parse_task_logs_and_failures(repo_root, raw_diagnostics, extra_secrets)
    junit_errors = parse_junit_xml_reports(repo_root, extra_secrets) if not raw_diagnostics.get("skipFilesystemScan") else []
    lint_errors = parse_lint_xml_reports(repo_root, extra_secrets) if not raw_diagnostics.get("skipFilesystemScan") else []
    explicit_errors = raw_diagnostics.get("explicitErrors") or []

    all_raw_errors = task_and_compiler_errors + junit_errors + lint_errors + explicit_errors
    classified_errors = deduplicate_and_classify_errors(all_raw_errors)

    raw_status = str(raw_diagnostics.get("status", "")).lower()
    if raw_status in ("failure", "failed"):
        final_status = "failure"
    elif raw_status in ("success", "succeeded"):
        final_status = "failure" if classified_errors else "success"
    else:
        final_status = "failure" if classified_errors else "success"

    wf_meta["status"] = final_status

    # Grouping
    by_file: Dict[str, List[str]] = {}
    by_task: Dict[str, List[str]] = {}
    by_type: Dict[str, List[str]] = {}
    for e in classified_errors:
        by_file.setdefault(e.get("file") or "unknown", []).append(e["id"])
        by_task.setdefault(e.get("gradleTask") or "unknown", []).append(e["id"])
        by_type.setdefault(e.get("type") or "unclassified", []).append(e["id"])

    primary_count = sum(1 for e in classified_errors if e.get("classification") == "primary")
    secondary_count = sum(1 for e in classified_errors if e.get("classification") == "secondary")
    unclassified_count = sum(1 for e in classified_errors if e.get("type") == "unclassified")

    report: Dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "workflow": wf_meta,
        "errors": classified_errors,
        "groups": {
            "byFile": by_file,
            "byGradleTask": by_task,
            "byType": by_type,
        },
        "recommendations": build_recommendations(classified_errors, final_status),
        "summary": {
            "totalErrors": len(classified_errors),
            "primaryErrors": primary_count,
            "secondaryErrors": secondary_count,
            "unclassifiedErrors": unclassified_count,
        },
    }

    md_text = render_markdown_report(report)
    json_text = json.dumps(report, ensure_ascii=False, indent=2)

    output_md.parent.mkdir(parents=True, exist_ok=True)
    output_json.parent.mkdir(parents=True, exist_ok=True)
    output_md.write_text(md_text, encoding="utf-8")
    output_json.write_text(json_text, encoding="utf-8")

    if append_step_summary:
        summary_file = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary_file:
            try:
                with open(summary_file, "a", encoding="utf-8") as sf:
                    sf.write("\n\n" + md_text + "\n")
            except Exception:
                pass

    return report


def write_fallback_report(
    repo_root: Path,
    output_md: Path,
    output_json: Path,
    generator_exc: BaseException,
    original_status: str = "failure",
) -> Dict[str, Any]:
    extra_secrets = collect_env_secrets()
    wf_meta = resolve_git_metadata(repo_root)
    wf_meta["status"] = original_status
    err_msg = redact_secrets(f"CI Error Report generator internal fallback: {generator_exc}", extra_secrets)
    fallback: Dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "workflow": wf_meta,
        "errors": [
            {
                "id": "ERR-001",
                "file": "unknown",
                "line": None,
                "column": None,
                "type": "unclassified",
                "message": err_msg,
                "gradleTask": "unknown",
                "ciStage": "unknown",
                "context": redact_secrets(traceback.format_exc(), extra_secrets),
                "classification": "primary",
                "relatedErrors": [],
                "probableCause": "Внутреннее исключение генератора отчёта; обратитесь к job log для первичной ошибки сборки.",
            }
        ],
        "groups": {
            "byFile": {"unknown": ["ERR-001"]},
            "byGradleTask": {"unknown": ["ERR-001"]},
            "byType": {"unclassified": ["ERR-001"]},
        },
        "recommendations": {
            "summary": "Сработал защитный fallback генератора CI Error Report.",
            "fixFirst": ["ERR-001"],
            "additionalDataNeeded": "Проверьте фрагмент job log упавшего шага GitHub Actions.",
        },
        "summary": {
            "totalErrors": 1,
            "primaryErrors": 1,
            "secondaryErrors": 0,
            "unclassifiedErrors": 1,
        },
    }
    output_md.parent.mkdir(parents=True, exist_ok=True)
    output_json.parent.mkdir(parents=True, exist_ok=True)
    output_md.write_text(render_markdown_report(fallback), encoding="utf-8")
    output_json.write_text(json.dumps(fallback, ensure_ascii=False, indent=2), encoding="utf-8")
    return fallback


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Generate structured CI Error Report (Markdown + JSON).")
    parser.add_argument("--repo-root", default=".", help="Path to repository root")
    parser.add_argument("--input", default="", help="Path to raw diagnostics JSON file")
    parser.add_argument("--output-md", default="ci-error-report.md", help="Output Markdown path")
    parser.add_argument("--output-json", default="ci-error-report.json", help="Output JSON path")
    parser.add_argument("--status", default="", help="Explicit build status override (success/failure)")
    parser.add_argument("--no-step-summary", action="store_true", help="Do not append to GITHUB_STEP_SUMMARY")
    args = parser.parse_args(argv)

    repo_root = Path(args.repo_root).resolve()
    out_md = Path(args.output_md)
    out_json = Path(args.output_json)

    raw_diag: Dict[str, Any] = {}
    try:
        if args.input and Path(args.input).is_file():
            raw_diag = json.loads(Path(args.input).read_text(encoding="utf-8"))
        if args.status:
            raw_diag["status"] = args.status

        generate_reports(
            repo_root=repo_root,
            raw_diagnostics=raw_diag,
            output_md=out_md,
            output_json=out_json,
            append_step_summary=not args.no_step_summary,
        )
    except Exception as exc:
        print(f"::warning::CI Error Report generator caught internal exception: {exc}", file=sys.stderr)
        try:
            write_fallback_report(
                repo_root=repo_root,
                output_md=out_md,
                output_json=out_json,
                generator_exc=exc,
                original_status=args.status or raw_diag.get("status", "failure") or "failure",
            )
        except Exception as inner_exc:
            print(f"::warning::Fallback report writer failed: {inner_exc}", file=sys.stderr)
    # Always return 0 so report generation never masks or overrides the CI build exit code
    return 0


if __name__ == "__main__":
    sys.exit(main())
