#!/usr/bin/env python3
"""Russian release notes from GitHub-generated PR entries, without external AI or extra secrets."""
import re
import sys
from pathlib import Path

original = Path(sys.argv[1]).read_text(encoding="utf-8")
sections = {
    "Добавлено": [],
    "Исправлено": [],
    "Изменено": [],
    "Документация и сборка": [],
}
patterns = [
    ("Исправлено", re.compile(r"^(fix|bugfix|hotfix)(?:\\b|[(:])", re.I), "Исправления ошибок"),
    ("Добавлено", re.compile(r"^(feat|feature|add)(?:\\b|[(:])", re.I), "Новые возможности"),
    ("Документация и сборка", re.compile(r"^(ci|build|docs|chore|test|refactor)(?:\\b|[(:])", re.I), "Обновления проекта и сборки"),
]
for line in original.splitlines():
    if not line.startswith("* "):
        continue
    match = re.search(r"https://github\\.com/[^\\s)]+/pull/(\\d+)", line)
    if not match:
        continue
    number = match.group(1)
    title = line[2:].split(" by @", 1)[0].strip()
    category, summary = "Изменено", "Изменения в приложении"
    for section, pattern, label in patterns:
        if pattern.search(title):
            category, summary = section, label
            break
    # Keep already Russian PR descriptions. For English titles, avoid pretending to translate
    # them: show a Russian category and link to the source PR instead.
    if re.search(r"[А-Яа-яЁё]", title):
        title = re.sub(r"^(feat|fix|ci|build|docs|chore|test|refactor)(?:\\([^)]*\\))?:\\s*", "", title, flags=re.I)
        summary = title or summary
    sections[category].append(f"- {summary} ([PR #{number}]({match.group(0)}))")

output = ["## Что изменилось", ""]
for heading, entries in sections.items():
    if entries:
        output.extend([f"### {heading}", *entries, ""])
if not any(sections.values()):
    output.extend(["Изменения этого выпуска не содержат описанных pull request.", ""])
comparison = re.search(r"https://github\\.com/[^\\s)]+/compare/[^\\s)]+", original)
if comparison:
    output.append(f"**Полная история изменений:** {comparison.group(0)}")
Path(sys.argv[2]).write_text("\\n".join(output).rstrip() + "\\n", encoding="utf-8")
