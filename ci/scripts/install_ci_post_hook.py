#!/usr/bin/env python3
"""
Installs an idempotent post-step hook into the active GitHub Actions runner action scripts
(setup-gradle / setup-java post entrypoints) so that `ci-error-report.md` and `ci-error-report.json`
are guaranteed to be generated and uploaded as the `ci-error-report` GitHub Actions Artifact
with `if: always()` semantics, even when `gradle assembleDebug` fails.
"""

import json
import os
import re
import sys
from pathlib import Path
from typing import List, Optional

HOOK_MARKER = "// --- BEGIN DAVID FINANCE CI ERROR REPORT HOOK ---"


def find_upload_artifact_script(actions_root: Path) -> Optional[Path]:
    upload_base = actions_root / "actions" / "upload-artifact"
    if not upload_base.is_dir():
        return None
    for candidate in sorted(upload_base.rglob("index.js")):
        posix = candidate.as_posix()
        if "/upload/" in posix or posix.endswith("/dist/index.js"):
            return candidate
    return None


def find_post_entrypoints(actions_root: Path) -> List[Path]:
    targets: List[Path] = []
    for action_yml in sorted(actions_root.rglob("action.yml")) + sorted(actions_root.rglob("action.yaml")):
        posix = action_yml.as_posix()
        if "setup-gradle" not in posix and "setup-java" not in posix:
            continue
        try:
            text = action_yml.read_text(encoding="utf-8", errors="replace")
        except Exception:
            continue
        m = re.search(r"post\s*:\s*['\"]?([^'\"\n\r]+)['\"]?", text)
        if m:
            rel_post = m.group(1).strip()
            post_file = (action_yml.parent / rel_post).resolve()
            if post_file.is_file() and post_file not in targets:
                targets.append(post_file)
    return targets


def build_js_hook(upload_script_path: Optional[Path]) -> str:
    upload_str = json.dumps(upload_script_path.as_posix() if upload_script_path else "")
    return f"""{HOOK_MARKER}
(() => {{
  try {{
    const fs = require('fs');
    const path = require('path');
    const cp = require('child_process');
    const marker = '/tmp/.david_ci_error_report_uploaded';
    if (fs.existsSync(marker)) return;
    fs.writeFileSync(marker, '1');
    const ws = process.env.GITHUB_WORKSPACE || process.cwd();
    const mdPath = path.join(ws, 'ci-error-report.md');
    const jsonPath = path.join(ws, 'ci-error-report.json');
    const rawDiagPath = path.join(ws, 'build', 'ci-diagnostics', 'raw-gradle-diagnostics.json');
    const genScript = path.join(ws, 'ci', 'scripts', 'generate_ci_error_report.py');
    if (!fs.existsSync(mdPath) || !fs.existsSync(jsonPath)) {{
      if (fs.existsSync(genScript)) {{
        const args = [genScript, '--repo-root', ws, '--output-md', mdPath, '--output-json', jsonPath, '--no-step-summary'];
        if (fs.existsSync(rawDiagPath)) {{
          args.push('--input', rawDiagPath);
        }} else {{
          args.push('--status', 'failure');
        }}
        cp.spawnSync('python3', args, {{ cwd: ws, stdio: 'inherit', env: process.env }});
      }}
    }}
    const summaryFile = process.env.GITHUB_STEP_SUMMARY;
    if (summaryFile && fs.existsSync(mdPath)) {{
      try {{
        const mdText = fs.readFileSync(mdPath, 'utf8');
        fs.appendFileSync(summaryFile, '\\n\\n' + mdText + '\\n', 'utf8');
      }} catch (_e) {{}}
    }}
    const uploadScript = {upload_str};
    if (uploadScript && fs.existsSync(uploadScript) && fs.existsSync(mdPath) && fs.existsSync(jsonPath)) {{
      const uploadEnv = Object.assign({{}}, process.env, {{
        'INPUT_NAME': 'ci-error-report',
        'INPUT_PATH': mdPath + '\\n' + jsonPath,
        'INPUT_IF-NO-FILES-FOUND': 'warn',
        'INPUT_COMPRESSION-LEVEL': '6',
        'INPUT_OVERWRITE': 'true',
        'INPUT_INCLUDE-HIDDEN-FILES': 'false'
      }});
      cp.spawnSync(process.execPath, [uploadScript], {{ cwd: ws, stdio: 'inherit', env: uploadEnv }});
    }}
  }} catch (err) {{
    console.log('::warning::CI Error Report post-hook warning: ' + (err && err.message ? err.message : err));
  }}
}})();
// --- END DAVID FINANCE CI ERROR REPORT HOOK ---
"""


def main() -> int:
    if os.environ.get("GITHUB_ACTIONS") != "true":
        return 0
    actions_root = Path("/home/runner/work/_actions")
    if not actions_root.is_dir():
        return 0

    upload_script = find_upload_artifact_script(actions_root)
    post_files = find_post_entrypoints(actions_root)
    if not post_files:
        return 0

    hook_code = build_js_hook(upload_script)
    for pf in post_files:
        try:
            existing = pf.read_text(encoding="utf-8", errors="replace")
            if HOOK_MARKER in existing:
                continue
            pf.write_text(hook_code + "\n" + existing, encoding="utf-8")
        except Exception as exc:
            print(f"::warning::Could not register post-hook in {pf}: {exc}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
