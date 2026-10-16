#!/usr/bin/env python3
"""
scripts/migrate_package.py
Скрипт автоматической миграции Android-проекта с com.example на целевой пакет (по умолчанию com.yourdomain.davidbudget).

Выполняет:
1. Переименование и перенос каталогов исходного кода в app/src/main/java, app/src/test/java, app/src/androidTest/java.
2. Замену package и import во всех Kotlin/Java файлах.
3. Обновление namespace и applicationId в app/build.gradle.kts.
4. Обновление AndroidManifest.xml (теги receiver, activity, action).
5. Обновление правил ProGuard/R8 в app/proguard-rules.pro.
6. Проверку путей FileProvider в app/src/main/res/xml/file_paths.xml.
"""

import os
import sys
import shutil
import re

OLD_PACKAGE = "com.example"
NEW_PACKAGE = "com.yourdomain.davidbudget"

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

def replace_in_file(file_path, old_str, new_str):
    if not os.path.isfile(file_path):
        return False
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    if old_str in content:
        new_content = content.replace(old_str, new_str)
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
        return True
    return False

def move_java_sources(src_root, old_pkg, new_pkg):
    old_rel = old_pkg.replace('.', os.sep)
    new_rel = new_pkg.replace('.', os.sep)
    old_dir = os.path.join(PROJECT_ROOT, src_root, old_rel)
    new_dir = os.path.join(PROJECT_ROOT, src_root, new_rel)

    if not os.path.exists(old_dir):
        print(f"Каталог {old_dir} не найден, пропускаем перемещение.")
        return

    print(f"Перемещение каталога:\n  откуда: {old_dir}\n  куда:   {new_dir}")
    os.makedirs(os.path.dirname(new_dir), exist_ok=True)
    
    # Если целевой каталог уже существует (например, при повторном запуске)
    if os.path.exists(new_dir):
        for item in os.listdir(old_dir):
            s = os.path.join(old_dir, item)
            d = os.path.join(new_dir, item)
            if os.path.isdir(s):
                shutil.copytree(s, d, dirs_exist_ok=True)
                shutil.rmtree(s)
            else:
                shutil.copy2(s, d)
                os.remove(s)
        shutil.rmtree(old_dir)
    else:
        shutil.move(old_dir, new_dir)

    # Очищаем пустые родительские каталоги старого пути
    parent = os.path.dirname(old_dir)
    while parent != os.path.join(PROJECT_ROOT, src_root) and os.path.exists(parent):
        if not os.listdir(parent):
            os.rmdir(parent)
            parent = os.path.dirname(parent)
        else:
            break

def update_code_imports_and_packages(new_pkg):
    print("Обновление package и import в Kotlin/Java файлах...")
    count = 0
    for root, _, files in os.walk(os.path.join(PROJECT_ROOT, "app", "src")):
        for f in files:
            if f.endswith((".kt", ".java")):
                path = os.path.join(root, f)
                if replace_in_file(path, OLD_PACKAGE, new_pkg):
                    count += 1
    print(f"Обновлено файлов исходного кода: {count}")

def update_build_gradle(new_pkg):
    gradle_file = os.path.join(PROJECT_ROOT, "app", "build.gradle.kts")
    print(f"Обновление {gradle_file}...")
    if os.path.isfile(gradle_file):
        with open(gradle_file, 'r', encoding='utf-8') as f:
            content = f.read()

        # Замена namespace
        content = re.sub(r'namespace\s*=\s*"[^"]*"', f'namespace = "{new_pkg}"', content)
        # Замена applicationId
        content = re.sub(r'applicationId\s*=\s*"[^"]*"', f'applicationId = "{new_pkg}"', content)

        with open(gradle_file, 'w', encoding='utf-8') as f:
            f.write(content)
        print("  ✓ namespace и applicationId обновлены")

def update_manifest(new_pkg):
    manifest_file = os.path.join(PROJECT_ROOT, "app", "src", "main", "AndroidManifest.xml")
    print(f"Обновление {manifest_file}...")
    if os.path.isfile(manifest_file):
        replace_in_file(manifest_file, f"{OLD_PACKAGE}.ACTION_DAILY_REMINDER", f"{new_pkg}.ACTION_DAILY_REMINDER")
        replace_in_file(manifest_file, f"{OLD_PACKAGE}.notifications.ReminderReceiver", f"{new_pkg}.notifications.ReminderReceiver")
        print("  ✓ AndroidManifest.xml обновлен")

def update_proguard(new_pkg):
    proguard_file = os.path.join(PROJECT_ROOT, "app", "proguard-rules.pro")
    print(f"Обновление {proguard_file}...")
    if os.path.isfile(proguard_file):
        replace_in_file(proguard_file, OLD_PACKAGE, new_pkg)
        print("  ✓ proguard-rules.pro обновлен")

def verify_file_provider():
    xml_path = os.path.join(PROJECT_ROOT, "app", "src", "main", "res", "xml", "file_paths.xml")
    print(f"Проверка {xml_path}...")
    if os.path.isfile(xml_path):
        with open(xml_path, 'r', encoding='utf-8') as f:
            content = f.read()
        if 'cache-path' in content and 'exports/' in content:
            print("  ✓ FileProvider пути корректны (<cache-path name=\"shared_exports\" path=\"exports/\" />)")

def main():
    target_package = sys.argv[1] if len(sys.argv) > 1 else NEW_PACKAGE
    print(f"=== Запуск миграции пакета: {OLD_PACKAGE} -> {target_package} ===")

    # 1. Перемещение исходников
    for src_dir in ["app/src/main/java", "app/src/test/java", "app/src/androidTest/java"]:
        move_java_sources(src_dir, OLD_PACKAGE, target_package)

    # 2. Обновление package и импортов
    update_code_imports_and_packages(target_package)

    # 3. Обновление build.gradle.kts
    update_build_gradle(target_package)

    # 4. Обновление AndroidManifest.xml
    update_manifest(target_package)

    # 5. Обновление ProGuard правил
    update_proguard(target_package)

    # 6. Проверка FileProvider
    verify_file_provider()

    print("\nМиграция успешно завершена!")

if __name__ == "__main__":
    main()
