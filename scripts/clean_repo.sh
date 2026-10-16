#!/bin/bash
# ==============================================================================
# clean_repo.sh - Очистка временных скриптов автопатчинга из корня репозитория
# ==============================================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=== Очистка корня проекта: $ROOT_DIR ==="

mkdir -p "$ROOT_DIR/scripts"

# Перенос полезных сборочных скриптов в scripts/
if [ -f "$ROOT_DIR/check_style.sh" ] && [ "$ROOT_DIR/check_style.sh" != "$ROOT_DIR/scripts/check_style.sh" ]; then
    echo "-> Перенос check_style.sh в scripts/"
    mv "$ROOT_DIR/check_style.sh" "$ROOT_DIR/scripts/check_style.sh"
    chmod +x "$ROOT_DIR/scripts/check_style.sh"
fi

# Удаление временных одноразовых скриптов автопатчинга
PATTERNS=(
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
for pattern in "${PATTERNS[@]}"; do
    for f in $ROOT_DIR/$pattern; do
        if [ -f "$f" ]; then
            rm -f "$f"
            DELETED_COUNT=$((DELETED_COUNT + 1))
        fi
    done
done

echo "=== Очистка завершена. Удалено временных файлов: $DELETED_COUNT ==="
