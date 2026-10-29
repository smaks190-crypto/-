#!/usr/bin/env python3
"""Russian release notes from GitHub-generated PR entries, without external AI or extra secrets.

Keeps the real PR titles (only strips the conventional-commit prefix) and, for changes
pushed straight to main without a PR, adds commit subjects from the compare range.
"""
import json
import os
import re
import subprocess
import sys
from pathlib import Path

original = Path(sys.argv[1]).read_text(encoding="utf-8")
sections = {
    "Добавлено": [],
    "Изменено": [],
    "Исправлено": [],
    "Документация и сборка": [],
}
patterns = [
    ("Исправлено", re.compile(r"^(fix|bugfix|hotfix)(?:\b|[(:!])", re.I)),
    ("Добавлено", re.compile(r"^(feat|feature|add)(?:\b|[(:!])", re.I)),
    ("Документация и сборка", re.compile(r"^(ci|build|docs|chore|test|refactor)(?:\b|[(:!])", re.I)),
]
prefix_re = re.compile(
    r"^(feat|feature|fix|bugfix|hotfix|ci|build|docs|chore|test|refactor|add)(?:\([^)]*\))?!?:\s*",
    re.I,
)


def classify(title):
    for section, pattern in patterns:
        if pattern.search(title):
            return section
    return "Изменено"


def clean(title):
    cleaned = prefix_re.sub("", title).strip()
    cleaned = re.sub(r"\s*\(#\d+\)\s*$", "", cleaned).strip()
    if not cleaned:
        return title.strip()
    return cleaned[0].upper() + cleaned[1:]


listed_prs = set()
for line in original.splitlines():
    if not line.startswith("* "):
        continue
    match = re.search(r"#(\d+)\)", line)
    if not match:
        continue
    number = match.group(1)
    listed_prs.add(number)
    title = line[2:].split(" by @", 1)[0].strip()
    sections[classify(title)].append(f"- {clean(title)} ([PR #{number}]({match.group(0)}))")

comparison = re.search(r"https://github\.com/[^\s)]+/compare/([^\s)]+)", original)

# Commits pushed directly to main (without a PR) are not listed by GitHub's PR-based notes.
repo = os.environ.get("GITHUB_REPOSITORY", "")
if comparison and repo:
    try:
        out = subprocess.run(
            ["gh", "api", f"repos/{repo}/compare/{comparison.group(1)}", "--paginate",
             "--jq", ".commits[] | {sha: .sha, msg: .commit.message, parents: (.parents | length)}"],
            capture_output=True, text=True, check=True, timeout=60,
        ).stdout
        for raw in out.splitlines():
            raw = raw.strip()
            if not raw:
                continue
            commit = json.loads(raw)
            subject = commit["msg"].splitlines()[0].strip()
            if commit["parents"] > 1 or not subject:
                continue  # merge commits are already covered by PR entries
            if "[skip ci]" in subject or subject.lower().startswith("docs: update latest release"):
                continue  # bot commits that update README
            refs = set(re.findall(r"#(\d+)", subject))
            if refs & listed_prs:
                continue  # squash-merged PR already listed
            sha = commit["sha"]
            sections[classify(subject)].append(
                f"- {clean(subject)} ([{sha[:7]}](https://github.com/{repo}/commit/{sha}))"
            )
    except Exception as error:  # never fail the release because of notes
        print(f"Could not read commits: {error}", file=sys.stderr)

output = ["## Что изменилось", ""]
for heading, entries in sections.items():
    if entries:
        output.extend([f"### {heading}", *entries, ""])
if not any(sections.values()):
    output.extend(["В этом выпуске нет автоматически описанных изменений.", ""])
if comparison:
    output.append(f"**Полная история изменений:** {comparison.group(0)}")
Path(sys.argv[2]).write_text("\n".join(output).rstrip() + "\n", encoding="utf-8")
