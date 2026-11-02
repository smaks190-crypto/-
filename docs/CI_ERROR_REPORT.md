# CI Error Report — Спецификация и протокол диагностики Argon4

## 1. Назначение

Система **CI Error Report** автоматически формирует и публикует структурированный диагностический отчёт (`ci-error-report.md` и `ci-error-report.json`) при каждом запуске GitHub Actions в репозитории **David Finance** (`smaks190-crypto/-`).

Отчёт является **первичным источником истины** для агента **Argon4** в рамках правила **CI Error Report First**:
- При падении любого обязательного этапа CI (Этап A: быстрые проверки конфигурации, Этап B: ранняя компиляция Kotlin/KSP, Этап C: сборка Android-ресурсов и упаковка APK, Этап D: Unit-тесты и Android Lint) система формирует отчёт и публикует его как GitHub Actions Artifact `ci-error-report`.
- При успешном прохождении CI формируется лаконичный отчёт со статусом `success` и `0` ошибок.
- Ошибка внутри самого генератора отчёта перехватывается защитным `fallback`-механизмом (код возврата генератора всегда `0`), поэтому генератор никогда не маскирует исходную причину падения сборки и не превращает неуспешный CI в успешный.

---

## 2. Публикация артефакта в GitHub Actions

- **Имя артефакта:** `ci-error-report`
- **Состав артефакта:**
  - `ci-error-report.md` — человекочитаемый отчёт с таблицей метаданных запуска, сводкой и группировкой ошибок, карточками каждой ошибки, фрагментами исходного кода (`±3` строки с указателем столбца `^`) и рекомендациями по исправлению;
  - `ci-error-report.json` — машиночитаемый отчёт строго по схеме `schemaVersion: "1.0"`.
- **Срок хранения (`retention-days`):** `14` дней.
- **Безопасность:** перед записью отчётов автоматически маскируются токены (`ghp_*`, `github_pat_*`, `ghs_*`, `Bearer ...`, `ACTIONS_RUNTIME_TOKEN`), пароли (`KEYSTORE_PASSWORD`, `KEY_PASSWORD`) и переменные секретов. Отчёт не публикуется автоматически в публичные комментарии PR.

---

## 3. Схема JSON-отчёта (`schemaVersion: "1.0"`)

```json
{
  "schemaVersion": "1.0",
  "workflow": {
    "repository": "smaks190-crypto/-",
    "name": "Android CI",
    "runId": "24099999999",
    "jobName": "Build Debug APK",
    "branch": "feature/example",
    "commitSha": "a1b2c3d4e5f6...",
    "commitMessage": "feat: example change",
    "timestampUtc": "2026-04-07T18:30:00Z",
    "status": "failure"
  },
  "errors": [
    {
      "id": "ERR-001",
      "file": "app/src/main/java/com/example/david/MainActivity.kt",
      "line": 42,
      "column": 17,
      "type": "Unresolved reference",
      "message": "Unresolved reference: brokenSymbol",
      "gradleTask": ":app:compileDebugKotlin",
      "ciStage": "Stage B: Kotlin Compilation",
      "context": "   39 |     override fun onCreate(savedInstanceState: Bundle?) {\n   40 |         super.onCreate(savedInstanceState)\n   41 |         val x = 1\n>  42 |         println(brokenSymbol)\n       |                 ^\n   43 |     }",
      "classification": "primary",
      "relatedErrors": [],
      "probableCause": "Идентификатор `brokenSymbol` не объявлен в текущей области видимости, переименован или отсутствует соответствующий import."
    }
  ],
  "groups": {
    "byFile": {
      "app/src/main/java/com/example/david/MainActivity.kt": ["ERR-001"]
    },
    "byGradleTask": {
      ":app:compileDebugKotlin": ["ERR-001"]
    },
    "byType": {
      "Unresolved reference": ["ERR-001"]
    }
  },
  "recommendations": {
    "summary": "Обнаружено ошибок: 1 (первичных: 1, вторичных: 0). Основной сбой произошёл на этапе Stage B: Kotlin Compilation (задача :app:compileDebugKotlin).",
    "fixFirst": ["ERR-001"],
    "additionalDataNeeded": "не требуются (координаты и контекст определены)"
  },
  "summary": {
    "totalErrors": 1,
    "primaryErrors": 1,
    "secondaryErrors": 0,
    "unclassifiedErrors": 0
  }
}
```

### Правила достоверности полей
- Если координаты ошибки отсутствуют в выводе инструмента, генератор **не придумывает** их:
  - в JSON: `"file": "unknown"`, `"line": null`, `"column": null`;
  - в Markdown: `unknown`.
- Если тип ошибки не распознан специализированными парсерами, устанавливается `"type": "unclassified"` с полным сохранением исходного текста ошибки.

---

## 4. Поддерживаемые источники и типы ошибок

| Этап CI | Источник данных | Распознаваемые типы ошибок |
| :--- | :--- | :--- |
| **Stage A: Fast Checks & Change Detection** | Вывод `:ciStageAFastChecks`, аннотации `::error`, `check_style.py`, `test_localize_release_notes.py`, проверка `.kts` | `Configuration Validation Error`, `Style Check Error`, `Gradle Script Error` |
| **Stage B: Kotlin Compilation** | Диагностика компилятора Kotlin (`e: file://...:line:col ...`) и KSP по всем модулям (`:core:database`, `:app`) | `Unresolved reference`, `Type mismatch`, `Syntax error`, `Overload resolution ambiguity`, `No value passed for parameter`, `Nullability error`, `Non-exhaustive 'when' expression`, `Unimplemented abstract member`, `Redeclaration / Conflicting overloads`, `Kotlin compilation error` |
| **Stage C: Full Build & Packaging** | Вывод AAPT2 / слияния ресурсов / `AndroidManifest.xml` / упаковки APK | `Android Resource / Manifest Error`, `JVM OutOfMemoryError` |
| **Stage D: Automated Testing & Lint** | JUnit XML (`**/build/test-results/**/*.xml`) сопоставленный со стектрейсом `.kt`, и Android Lint XML (`**/build/reports/lint-results*.xml`) | `Unit Test Failure (<ExceptionType>)`, `Android Lint Error (<IssueId>)` |
| **Fallback / Любой этап** | Исключения задач Gradle (`TaskExecutionException`) и `buildFailureMessages` | `unclassified` (с сохранением точного текста исключения и имени задачи) |

### Классификация первичных (`primary`) и вторичных (`secondary`) ошибок
1. **Межмодульный каскад (`:core:database` → `:app`):** если упала компиляция или KSP в модуле `:core:database`, последующие ошибки компиляции в `:app` помечаются как `secondary` и связываются через `relatedErrors` с первичной ошибкой в `:core:database`.
2. **Внутрифайловый каскад:** если первая ошибка в файле является `Syntax error` (или `Unresolved reference` / `Redeclaration`), а последующие ошибки в том же файле на той же или более поздних строках представляют собой `Type mismatch` или `Overload resolution ambiguity`, они помечаются как `secondary`.
3. **Каскад компиляции в тесты:** ошибки `NoClassDefFoundError` / `ClassNotFoundException` при наличии первичной ошибки компиляции помечаются как `secondary`.

---

## 5. Протокол получения отчёта для Argon4 (CI Error Report First)

При обнаружении неуспешного запуска GitHub Actions агент **Argon4** обязан сначала получить `ci-error-report` с помощью утилиты `ci/scripts/fetch_ci_error_report.py`, не скачивая полный лог сборки:

```bash
python3 ci/scripts/fetch_ci_error_report.py \
  --repo smaks190-crypto/- \
  --branch <рабочая_ветка> \
  --commit-sha <ожидаемый_HEAD_SHA> \
  --output-dir /tmp/ci-error-report-fetched \
  --print-md \
  --print-json
```

### Алгоритм проверки для Argon4:
1. Запустить `ci/scripts/fetch_ci_error_report.py` с указанием `--branch` и `--commit-sha` текущего проверяемого коммита.
2. Скрипт автоматически проверяет:
   - что найденный запуск workflow относится к указанному `commitSha` (`head_sha`);
   - что внутри `ci-error-report.json` поле `workflow.commitSha` совпадает с проверяемым коммитом.
3. Изучить массив `errors` и список `recommendations.fixFirst`:
   - сначала устранить все ошибки с `"classification": "primary"`;
   - не исправлять `"secondary"` ошибки в отрыве от первичной причины.
4. Обращаться к полному логу job в GitHub Actions разрешается **только** если:
   - артефакт `ci-error-report` отсутствует (например, инфраструктурный сбой ранера до старта шагов сборки);
   - в отчёте указано `"type": "unclassified"` или `"file": "unknown"`, а для исправления необходимы дополнительные детали из лога конкретного упавшего шага.

---

## 6. Известные ограничения парсера

- Если сбой произошёл на уровне инфраструктуры GitHub Actions **до** запуска шага Gradle (например, ошибка `actions/checkout` или недоступность сети при скачивании JDK в `actions/setup-java`), Gradle-коллектор не запускается. Для таких случаев в шаблоне workflow (`ci/workflows/build.yml`) предусмотрен шаг `if: always()`, вызывающий `generate_ci_error_report.py` напрямую.
- Для ошибок AAPT2 в бинарных или сгенерированных ресурсах без текстовых координат в исходниках поле `file` принимает значение `unknown`, а в `recommendations.additionalDataNeeded` указывается рекомендация проверить вывод задачи `:app:mergeDebugResources`.
