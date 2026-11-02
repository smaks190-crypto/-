// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
}

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties

// ============================================================================
// ЭТАП A: Быстрые проверки конфигурации и определение затронутых файлов (CI)
// ============================================================================
fun detectCiChangedFiles(rootDir: File): List<String> {
  if (System.getenv("GITHUB_ACTIONS") != "true") return emptyList()
  return try {
    val eventName = System.getenv("GITHUB_EVENT_NAME") ?: ""
    val baseRef = System.getenv("GITHUB_BASE_REF") ?: "main"
    if (eventName == "pull_request" && baseRef.isNotBlank()) {
      ProcessBuilder("git", "fetch", "origin", baseRef, "--depth=50")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
        .waitFor()
    }
    val diffTarget = if (eventName == "pull_request" && baseRef.isNotBlank()) {
      "origin/$baseRef...HEAD"
    } else {
      "HEAD~1...HEAD"
    }
    val proc = ProcessBuilder("git", "diff", "--name-only", diffTarget)
      .directory(rootDir)
      .redirectErrorStream(true)
      .start()
    val output = proc.inputStream.bufferedReader().readText()
    proc.waitFor()
    output.lineSequence()
      .map { it.trim() }
      .filter { it.isNotEmpty() && !it.startsWith("fatal:") && !it.startsWith("warning:") }
      .toList()
  } catch (_: Exception) {
    emptyList()
  }
}

fun classifyCiChangeScope(changedFiles: List<String>): String {
  if (changedFiles.isEmpty()) return "full"
  val isDoc: (String) -> Boolean = { f ->
    f.endsWith(".md") || f.startsWith("docs/") || f == "LICENSE" || f == ".gitignore"
  }
  if (changedFiles.all(isDoc)) return "docs_only"

  val isBuildOrCiConfig: (String) -> Boolean = { f ->
    f in setOf(
      "build.gradle.kts",
      "settings.gradle.kts",
      "gradle.properties",
      "version.properties",
      ".env.example",
      "app/build.gradle.kts",
      "core/database/build.gradle.kts"
    ) || f.startsWith("gradle/") || f.startsWith(".github/") || f.startsWith("ci/")
  }
  val hasBuildCfg = changedFiles.any(isBuildOrCiConfig)
  val hasCore = changedFiles.any { it.startsWith("core/database/") }
  val hasAppMain = changedFiles.any { it.startsWith("app/src/main/") }
  val hasUnitTest = changedFiles.any { it.startsWith("app/src/test/") }
  val hasAndroidTest = changedFiles.any { it.startsWith("app/src/androidTest/") }

  return when {
    hasBuildCfg || hasCore -> "core_or_build"
    hasAppMain -> "app_only"
    hasUnitTest && !hasAndroidTest -> "unit_test_only"
    hasAndroidTest && !hasUnitTest -> "android_test_only"
    hasUnitTest && hasAndroidTest -> "tests_only"
    else -> "full"
  }
}

val ciChangedFiles = detectCiChangedFiles(rootDir)
val ciChangeScope = classifyCiChangeScope(ciChangedFiles)
val ciHasAndroidTestChanges = ciChangedFiles.any { it.startsWith("app/src/androidTest/") }
extra["ciChangedFiles"] = ciChangedFiles
extra["ciChangeScope"] = ciChangeScope
extra["ciHasAndroidTestChanges"] = ciHasAndroidTestChanges

tasks.register("ciStageAFastChecks") {
  group = "verification"
  description = "Stage A: Fast configuration validation, script unit tests, and change scope analysis."
  doLast {
    println("=== [Stage A: Fast Checks] Validating project configuration & scripts ===")
    val versionFile = file("${rootDir}/version.properties")
    check(versionFile.isFile && versionFile.length() > 0L) {
      "Missing or empty version.properties"
    }
    val props = Properties().apply { versionFile.inputStream().use { load(it) } }
    val versionCode = props.getProperty("VERSION_CODE")?.trim()?.toIntOrNull()
    check(versionCode != null && versionCode > 0) {
      "Invalid VERSION_CODE in version.properties: ${props.getProperty("VERSION_CODE")}"
    }

    val requiredFiles = listOf(
      "settings.gradle.kts",
      "build.gradle.kts",
      "app/build.gradle.kts",
      "core/database/build.gradle.kts",
      "gradle.properties",
      "gradle/libs.versions.toml",
      "gradle/wrapper/gradle-wrapper.properties"
    )
    for (rel in requiredFiles) {
      val f = file("${rootDir}/$rel")
      check(f.isFile && f.length() > 0L) { "Required configuration file missing or empty: $rel" }
    }

    val tomlText = file("${rootDir}/gradle/libs.versions.toml").readText()
    for (section in listOf("[versions]", "[libraries]", "[plugins]")) {
      check(tomlText.contains(section)) { "Missing $section in gradle/libs.versions.toml" }
    }

    val testScript = file("${rootDir}/.github/scripts/test_localize_release_notes.py")
    if (testScript.isFile) {
      val proc = ProcessBuilder("python3", testScript.absolutePath)
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
      val out = proc.inputStream.bufferedReader().readText()
      val exit = proc.waitFor()
      if (exit != 0) {
        throw GradleException("Release notes script unit test failed:\n$out")
      }
    }

    println("=== [Stage A: Change Detection] Scope=$ciChangeScope, ChangedFiles=${ciChangedFiles.size} ===")
    ciChangedFiles.forEach { println("  - $it") }

    val summaryPath = System.getenv("GITHUB_STEP_SUMMARY")
    if (!summaryPath.isNullOrBlank()) {
      File(summaryPath).appendText(
        buildString {
          appendLine("### ⚡ Multi-Stage CI Pipeline Summary")
          appendLine()
          appendLine("| Параметр | Значение |")
          appendLine("|---|---|")
          appendLine("| **Detected Change Scope** | `$ciChangeScope` |")
          appendLine("| **Changed Files Count** | `${ciChangedFiles.size}` |")
          appendLine("| **Stage A (Fast Checks)** | `PASSED` (`version.properties`, `libs.versions.toml`, `.kts`, Python tests) |")
          appendLine("| **Stage B (Early Kotlin Compile)** | `:core:database:compileDebugKotlin`, `:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin`" +
            (if (ciHasAndroidTestChanges) ", `:app:compileDebugAndroidTestKotlin`" else "") + " |")
          appendLine("| **Stage C (APK Packaging)** | `:app:assembleDebug` (skipping unused `:core:database:bundleDebugAar`) |")
          appendLine("| **Stage D (Unit Testing)** | `${if (ciChangeScope == "docs_only" || ciChangeScope == "android_test_only") "SKIPPED (not affected)" else ":app:testDebugUnitTest (parallel forks)"}` |")
          if (ciChangedFiles.isNotEmpty()) {
            appendLine()
            appendLine("<details><summary>Изменённые файлы (${ciChangedFiles.size})</summary>")
            appendLine()
            ciChangedFiles.take(50).forEach { appendLine("- `$it`") }
            appendLine()
            appendLine("</details>")
          }
        }
      )
    }
  }
}
