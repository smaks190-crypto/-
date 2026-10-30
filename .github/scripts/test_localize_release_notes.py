import re
import unittest

import localize_release_notes as notes

REPO = "owner/repo"
COMPARE = "https://github.com/owner/repo/compare/v11.05-build.240...v11.06-build.247"


def original(*lines):
    body = ["## What's Changed", *lines, "", f"**Full Changelog**: {COMPARE}"]
    return "\n".join(body)


def build(text, pr_bodies=None, commits=None):
    pr_bodies = pr_bodies or {}
    commits = commits or []
    return notes.build_notes(
        text,
        REPO,
        pr_body_loader=lambda repo, number: pr_bodies.get(str(number), ""),
        commit_loader=lambda repo, ref: commits,
    )


class ReleaseNotesTest(unittest.TestCase):
    def test_russian_section_from_pr_body_is_used(self):
        text = original("* feat(ui): add dark mode by @dev in https://github.com/owner/repo/pull/10")
        body = "Описание\n\n## Заметки релиза\n- Добавлена тёмная тема\n- Исправлен контраст [#9](https://x.y)\n\n## Тесты\nok"
        result = build(text, {"10": body})
        self.assertIn("### Добавлено", result)
        self.assertIn("- Добавлена тёмная тема", result)
        self.assertIn("- Исправлен контраст", result)
        self.assertNotIn("dark mode", result)
        self.assertNotIn("/pull/", result)

    def test_english_title_without_note_becomes_neutral_fallback(self):
        text = original(
            "* fix(live): resolve ping timeout by @dev in https://github.com/owner/repo/pull/11",
            "* fix: another english fix by @dev in https://github.com/owner/repo/pull/12",
        )
        result = build(text)
        self.assertIn("- Технические изменения без отдельного описания: 2", result)
        self.assertNotRegex(result, r"ping timeout|english fix")

    def test_russian_title_is_cleaned_and_used(self):
        text = original("* fix: Исправлен скачок графика (#49) by @dev in https://github.com/owner/repo/pull/13")
        result = build(text)
        self.assertIn("### Исправлено", result)
        self.assertIn("- Исправлен скачок графика", result)
        self.assertNotIn("#49", result)

    def test_explicit_skip_hides_internal_pr(self):
        text = original("* chore: bump deps by @dev in https://github.com/owner/repo/pull/14")
        result = build(text, {"14": "## Заметки релиза\nнет"})
        self.assertIn(notes.EMPTY_TEXT, result)
        self.assertNotIn(notes.FALLBACK_TEXT, result)

    def test_empty_template_section_falls_back(self):
        text = original("* feat: english only by @dev in https://github.com/owner/repo/pull/15")
        body = "## Заметки релиза\n<!-- Опишите по-русски -->\n\n## Тесты"
        result = build(text, {"15": body})
        self.assertIn(f"{notes.FALLBACK_TEXT}: 1", result)

    def test_category_prefix_in_note(self):
        text = original("* feat: english by @dev in https://github.com/owner/repo/pull/16")
        result = build(text, {"16": "## Заметки релиза\n- Исправлено: Давид больше не зависает"})
        self.assertIn("### Исправлено\n- Давид больше не зависает", result)

    def test_direct_commits(self):
        text = original()
        commits = [
            {"sha": "a" * 40, "msg": "fix: english subject\n\nRelease-Note: Исправлен вылет при старте", "parents": 1},
            {"sha": "b" * 40, "msg": "chore: english only", "parents": 1},
            {"sha": "c" * 40, "msg": "Merge pull request #1", "parents": 2},
            {"sha": "d" * 40, "msg": "docs: update latest release information [skip ci]", "parents": 1},
        ]
        result = build(text, commits=commits)
        self.assertIn("- Исправлен вылет при старте", result)
        self.assertIn(f"- {notes.FALLBACK_TEXT}: 1", result)
        self.assertNotIn("english", result)
        self.assertNotRegex(result, r"/commit/|[0-9a-f]{7}")

    def test_squash_commit_not_duplicated(self):
        text = original("* feat: Новая вкладка (#20) by @dev in https://github.com/owner/repo/pull/20")
        commits = [{"sha": "e" * 40, "msg": "feat: Новая вкладка (#20)", "parents": 1}]
        result = build(text, commits=commits)
        self.assertEqual(result.count("Новая вкладка"), 1)

    def test_first_contribution_line_is_ignored(self):
        text = original("* @newbie made their first contribution in https://github.com/owner/repo/pull/21")
        result = build(text)
        self.assertIn(notes.EMPTY_TEXT, result)
        self.assertNotIn("newbie", result)

    def test_duplicates_are_removed(self):
        text = original(
            "* feat: a by @dev in https://github.com/owner/repo/pull/30",
            "* feat: b by @dev in https://github.com/owner/repo/pull/31",
        )
        body = "## Заметки релиза\n- Добавлена тёмная тема"
        result = build(text, {"30": body, "31": body})
        self.assertEqual(result.count("Добавлена тёмная тема"), 1)

    def test_empty_release(self):
        result = build(original())
        self.assertNotIn("## Что изменилось", result)
        self.assertIn(notes.EMPTY_TEXT, result)

    def test_single_compare_link_only(self):
        text = original("* feat: english by @dev in https://github.com/owner/repo/pull/40")
        result = build(text, {"40": "## Заметки релиза\n- Что-то новое"})
        self.assertEqual(len(re.findall(r"https?://", result)), 1)
        self.assertIn(f"**Полная история изменений:** {COMPARE}", result)

    def test_no_english_technical_list_in_mixed_release(self):
        text = original(
            "* feat: add voice picker by @dev in https://github.com/owner/repo/pull/50",
            "* fix: Исправлена загрузка by @dev in https://github.com/owner/repo/pull/51",
        )
        result = build(text)
        self.assertNotIn("voice picker", result)
        self.assertIn("- Исправлена загрузка", result)


if __name__ == "__main__":
    unittest.main()
