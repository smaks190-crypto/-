#!/usr/bin/env python3
"""Русскоязычные заметки релиза без внешнего ИИ и дополнительных секретов.

Источник русского текста (в порядке приоритета):
1. Раздел «## Заметки релиза» в описании PR (см. .github/pull_request_template.md).
2. Строка `Release-Note: ...` в сообщении коммита, отправленного прямо в main.
3. Заголовок PR/коммита, если он уже написан по-русски.
4. Нейтральная русская строка «Технические изменения без отдельного описания: N».
   Английские заголовки коммитов в публичный список не попадают.

Ссылки на отдельные коммиты и PR в списке не выводятся; остаётся одна ссылка
«Полная история изменений». Заголовок «Что изменилось» не добавляется: в README он
уже есть, а у релиза на GitHub достаточно заголовков разделов.
"""
import json
import os
import re
import subprocess
import sys
from pathlib import Path

SECTIONS = ["Добавлено", "Изменено", "Исправлено", "Документация и сборка"]
EMPTY_TEXT = "В этом выпуске нет автоматически описанных изменений."
FALLBACK_TEXT = "Технические изменения без отдельного описания"

CYRILLIC = re.compile(r"[А-Яа-яЁё]")
PATTERNS = [
    ("Исправлено", re.compile(r"^(fix|bugfix|hotfix)(?:\b|[(:!])", re.I)),
    ("Добавлено", re.compile(r"^(feat|feature|add)(?:\b|[(:!])", re.I)),
    ("Документация и сборка", re.compile(r"^(ci|build|docs|chore|test|refactor)(?:\b|[(:!])", re.I)),
]
PREFIX_RE = re.compile(
    r"^(feat|feature|fix|bugfix|hotfix|ci|build|docs|chore|test|refactor|add)(?:\([^)]*\))?!?:\s*",
    re.I,
)
NOTE_HEADING_RE = re.compile(
    r"^#{1,6}\s*(заметки релиза|release notes(?:\s*\(ru\))?|что изменилось для пользователей?)\s*:?\s*$",
    re.I,
)
HEADING_RE = re.compile(r"^#{1,6}\s")
COMMENT_RE = re.compile(r"<!--.*?-->", re.S)
COMMIT_NOTE_RE = re.compile(r"^(?:release-note|заметка релиза|заметки релиза)\s*:\s*(.*)$", re.I)
CATEGORY_RE = re.compile(
    r"^(добавлено|изменено|исправлено|документация и сборка)\s*[:—–-]\s*(.+)$", re.I
)
CATEGORY_NAMES = {name.lower(): name for name in SECTIONS}
NO_NOTE = {"-", "—", "–", "нет", "не требуется", "n/a", "none", "пропустить", "skip"}
MAX_NOTES_PER_PR = 5


def has_cyrillic(text):
    return bool(CYRILLIC.search(text))


def classify(title):
    for section, pattern in PATTERNS:
        if pattern.search(title):
            return section
    return "Изменено"


def strip_markup(text):
    """Убирает ссылки, номера issue/PR и обратные кавычки из публичного текста."""
    text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)
    text = re.sub(r"https?://\S+", "", text)
    text = re.sub(r"\s*\(?#\d+\)?", "", text)
    text = text.replace("`", "")
    return re.sub(r"\s{2,}", " ", text).strip()


def clean(title):
    cleaned = PREFIX_RE.sub("", title).strip()
    cleaned = strip_markup(cleaned)
    if not cleaned:
        return strip_markup(title)
    return cleaned[0].upper() + cleaned[1:]


def is_no_note(text):
    return text.strip(" .").lower() in NO_NOTE


def extract_pr_notes(body):
    """None — раздела нет или он не заполнен; [] — автор явно отметил «нет»; иначе список строк."""
    body = COMMENT_RE.sub("", body or "")
    in_section = False
    explicit_skip = False
    notes = []
    for raw in body.splitlines():
        line = raw.strip()
        if not in_section:
            in_section = bool(NOTE_HEADING_RE.match(line))
            continue
        if HEADING_RE.match(line):
            break
        if not line:
            continue
        item = re.sub(r"^([-*+]|\d+[.)])\s+", "", line)
        item = re.sub(r"^\[[ xX]\]\s*", "", item)
        item = strip_markup(item)
        if not item:
            continue
        if is_no_note(item):
            explicit_skip = True
            continue
        if has_cyrillic(item):
            notes.append(item)
    if notes:
        return notes[:MAX_NOTES_PER_PR]
    return [] if explicit_skip else None


def extract_commit_notes(message):
    explicit_skip = False
    notes = []
    for raw in message.splitlines()[1:]:
        match = COMMIT_NOTE_RE.match(raw.strip())
        if not match:
            continue
        item = strip_markup(match.group(1))
        if not item:
            continue
        if is_no_note(item):
            explicit_skip = True
        elif has_cyrillic(item):
            notes.append(item)
    if notes:
        return notes[:MAX_NOTES_PER_PR]
    return [] if explicit_skip else None


def split_category(note, default):
    match = CATEGORY_RE.match(note)
    if match:
        return CATEGORY_NAMES[match.group(1).lower()], match.group(2).strip()
    return default, note


def gh_api(path, jq):
    return subprocess.run(
        ["gh", "api", path, "--jq", jq],
        capture_output=True, text=True, check=True, timeout=60,
    ).stdout


def fetch_pr_body(repo, number):
    if not repo:
        return ""
    try:
        return gh_api(f"repos/{repo}/pulls/{number}", '.body // ""')
    except Exception as error:  # заметки не должны ломать релиз
        print(f"Could not read PR #{number}: {error}", file=sys.stderr)
        return ""


def fetch_commits(repo, compare_ref):
    if not repo:
        return []
    try:
        out = gh_api(
            f"repos/{repo}/compare/{compare_ref}",
            ".commits[] | {sha: .sha, msg: .commit.message, parents: (.parents | length)}",
        )
    except Exception as error:
        print(f"Could not read commits: {error}", file=sys.stderr)
        return []
    commits = []
    for raw in out.splitlines():
        raw = raw.strip()
        if raw:
            commits.append(json.loads(raw))
    return commits


def build_notes(original, repo="", pr_body_loader=fetch_pr_body, commit_loader=fetch_commits):
    sections = {name: [] for name in SECTIONS}
    fallback = {name: 0 for name in SECTIONS}
    seen = set()

    def add(section, text):
        text = text.strip()
        if not text:
            return
        text = text[0].upper() + text[1:]
        key = text.casefold()
        if key in seen:
            return
        seen.add(key)
        sections[section].append(f"- {text}")

    def register(default, title, notes):
        if notes is None:
            if has_cyrillic(title):
                add(default, clean(title))
            else:
                fallback[default] += 1
            return
        for note in notes:
            section, text = split_category(note, default)
            add(section, text)

    listed_prs = set()
    for line in original.splitlines():
        if not line.startswith("* ") or "made their first contribution" in line:
            continue
        number_match = re.search(r"/pull/(\d+)", line) or re.search(r"#(\d+)\)", line)
        if not number_match:
            continue
        number = number_match.group(1)
        listed_prs.add(number)
        title = line[2:].split(" by @", 1)[0].strip()
        register(classify(title), title, extract_pr_notes(pr_body_loader(repo, number)))

    comparison = re.search(r"https://github\.com/[^\s)]+/compare/([^\s)]+)", original)

    # Коммиты прямо в main (без PR) в заметках GitHub по PR не отражаются.
    if comparison and repo:
        for commit in commit_loader(repo, comparison.group(1)):
            message = commit["msg"]
            subject = message.splitlines()[0].strip() if message.strip() else ""
            if commit["parents"] > 1 or not subject:
                continue  # merge-коммиты уже покрыты записями PR
            if "[skip ci]" in subject or subject.lower().startswith("docs: update latest release"):
                continue  # служебные коммиты обновления README
            if set(re.findall(r"#(\d+)", subject)) & listed_prs:
                continue  # squash-merge PR уже в списке
            register(classify(subject), subject, extract_commit_notes(message))

    for name, count in fallback.items():
        if count:
            sections[name].append(f"- {FALLBACK_TEXT}: {count}")

    output = []
    for heading, entries in sections.items():
        if entries:
            output.extend([f"### {heading}", *entries, ""])
    if not any(sections.values()):
        output.extend([EMPTY_TEXT, ""])
    if comparison:
        output.append(f"**Полная история изменений:** {comparison.group(0)}")
    return "\n".join(output).rstrip() + "\n"


def main():
    original = Path(sys.argv[1]).read_text(encoding="utf-8")
    result = build_notes(original, os.environ.get("GITHUB_REPOSITORY", ""))
    Path(sys.argv[2]).write_text(result, encoding="utf-8")


if __name__ == "__main__":
    main()
