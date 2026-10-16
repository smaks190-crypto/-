#!/usr/bin/env bash
# scripts/cleanup_root.sh
# Безопасный скрипт очистки корня репозитория от временных автопатч-скриптов

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
echo "Очистка временных скриптов в каталоге: $ROOT_DIR"

# Перемещаем полезные утилиты в scripts/ (если они всё ещё в корне)
if [ -f "$ROOT_DIR/check_style.sh" ]; then
    echo "Перенос check_style.sh в scripts/..."
    mv "$ROOT_DIR/check_style.sh" "$ROOT_DIR/scripts/check_style.sh"
    chmod +x "$ROOT_DIR/scripts/check_style.sh"
fi

# Список шаблонов одноразовых патч-скриптов
TEMPORARY_PATTERNS=(
    "fix*.py"
    "patch*.py"
    "update*.py"
    "add_imports*.py"
    "clean_nested.py"
    "count_braces.py"
    "implement_single_animated_content.py"
    "rewrite_chat.py"
    "test_icon.py"
    "test_typing.py"
)

DELETED_COUNT=0
for pattern in "${TEMPORARY_PATTERNS[@]}"; do
    for file in "$ROOT_DIR"/$pattern; do
        if [ -f "$file" ]; then
            echo "Удаление временного файла: $(basename "$file")"
            rm -f "$file"
            DELETED_COUNT=$((DELETED_COUNT + 1))
        fi
    done
done

echo "Очистка завершена. Удалено временных файлов: $DELETED_COUNT"
