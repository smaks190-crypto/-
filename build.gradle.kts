// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
}

import java.io.File
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.Failure
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent

// ============================================================================
// CI Error Report Diagnostics Collector (Stage A/B/C/D + Auto-Report Hook)
// ============================================================================
object CiDiagnosticsStore {
  val taskLogs = ConcurrentHashMap<String, MutableList<String>>()
  val taskOutcomes = ConcurrentHashMap<String, String>()
  val taskFailures = ConcurrentHashMap<String, MutableList<String>>()
  val reportGenerated = AtomicBoolean(false)

  fun appendLog(taskPath: String, chunk: CharSequence) {
    val lines = chunk.toString().lines()
    val list = taskLogs.computeIfAbsent(taskPath) { CopyOnWriteArrayList() }
    for (line in lines) {
      val trimmed = line.trimEnd()
      if (trimmed.isNotEmpty() && list.size < 500) {
        list.add(trimmed)
      }
    }
  }

  private fun jsonEscape(raw: String): String = buildString(raw.length + 16) {
    for (ch in raw) {
      when (ch) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
      }
    }
  }

  @Synchronized
  fun flushAndGenerateReport(projectRoot: File, forceOverwrite: Boolean = false) {
    if (!forceOverwrite && !reportGenerated.compareAndSet(false, true)) return
    reportGenerated.set(true)
    try {
      val hasFailedTask = taskOutcomes.values.any { it == "FAILED" }
      val buildStatus = if (hasFailedTask) "failure" else "success"
      val allPaths = (taskOutcomes.keys + taskLogs.keys + taskFailures.keys).toSortedSet()

      val diagDir = File(projectRoot, "build/ci-diagnostics")
      diagDir.mkdirs()
      val rawFile = File(diagDir, "raw-gradle-diagnostics.json")

      val jsonText = buildString {
        appendLine("{")
        appendLine("  \"status\": \"${jsonEscape(buildStatus)}\",")
        appendLine("  \"tasks\": [")
        allPaths.forEachIndexed { idx, path ->
          val outcome = taskOutcomes[path] ?: "UNKNOWN"
          val logs = taskLogs[path] ?: emptyList<String>()
          val fails = taskFailures[path] ?: emptyList<String>()
          appendLine("    {")
          appendLine("      \"path\": \"${jsonEscape(path)}\",")
          appendLine("      \"outcome\": \"${jsonEscape(outcome)}\",")
          append("      \"failureMessages\": [")
          fails.forEachIndexed { fIdx, msg ->
            if (fIdx > 0) append(", ")
            append("\"${jsonEscape(msg)}\"")
          }
          appendLine("],")
          append("      \"logLines\": [")
          logs.forEachIndexed { lIdx, ln ->
            if (lIdx > 0) append(", ")
            append("\"${jsonEscape(ln)}\"")
          }
          appendLine("]")
          append("    }")
          if (idx < allPaths.size - 1) appendLine(",") else appendLine()
        }
        appendLine("  ]")
        appendLine("}")
      }
      rawFile.writeText(jsonText, Charsets.UTF_8)

      val genScript = File(projectRoot, "ci/scripts/generate_ci_error_report.py")
      if (genScript.isFile) {
        val mdOut = File(projectRoot, "ci-error-report.md")
        val jsonOut = File(projectRoot, "ci-error-report.json")
        ProcessBuilder(
          "python3",
          genScript.absolutePath,
          "--repo-root", projectRoot.absolutePath,
          "--input", rawFile.absolutePath,
          "--output-md", mdOut.absolutePath,
          "--output-json", jsonOut.absolutePath,
          "--status", buildStatus,
          "--no-step-summary"
        )
          .directory(projectRoot)
          .redirectErrorStream(true)
          .start()
          .waitFor()
      }
    } catch (e: Exception) {
      System.err.println("::warning::CiDiagnosticsStore flush warning: ${e.message}")
    }
  }
}

abstract class CiDiagnosticsService :
  BuildService<CiDiagnosticsService.Params>,
  OperationCompletionListener,
  AutoCloseable {

  interface Params : BuildServiceParameters {
    val rootDirPath: Property<String>
  }

  private fun collectFailureMessages(failures: List<Failure>?, out: MutableList<String>) {
    if (failures == null) return
    for (f in failures) {
      f.message?.trim()?.takeIf { it.isNotEmpty() }?.let { if (!out.contains(it)) out.add(it) }
      f.description?.trim()?.takeIf { it.isNotEmpty() }?.let { if (!out.contains(it)) out.add(it) }
      collectFailureMessages(f.causes, out)
    }
  }

  override fun onFinish(event: FinishEvent?) {
    if (event is TaskFinishEvent) {
      val path = event.descriptor.taskPath
      val result = event.result
      if (result is TaskFailureResult) {
        CiDiagnosticsStore.taskOutcomes[path] = "FAILED"
        val msgs = CiDiagnosticsStore.taskFailures.computeIfAbsent(path) { CopyOnWriteArrayList() }
        collectFailureMessages(result.failures, msgs)
        CiDiagnosticsStore.flushAndGenerateReport(File(parameters.rootDirPath.get()), forceOverwrite = true)
      } else {
        CiDiagnosticsStore.taskOutcomes.putIfAbsent(path, "SUCCESS")
      }
    }
  }

  override fun close() {
    CiDiagnosticsStore.flushAndGenerateReport(File(parameters.rootDirPath.get()), forceOverwrite = true)
  }
}

val ciDiagServiceProvider = gradle.sharedServices.registerIfAbsent(
  "ciDiagnosticsService",
  CiDiagnosticsService::class.java
) {
  parameters.rootDirPath.set(rootDir.absolutePath)
}

@Suppress("UnstableApiUsage")
gradle.services.get(BuildEventsListenerRegistry::class.java).onTaskCompletion(ciDiagServiceProvider)

Runtime.getRuntime().addShutdownHook(
  Thread {
    CiDiagnosticsStore.flushAndGenerateReport(rootDir)
  }
)

allprojects {
  tasks.configureEach {
    val tPath = path
    logging.addStandardOutputListener { text ->
      CiDiagnosticsStore.appendLog(tPath, text)
    }
    logging.addStandardErrorListener { text ->
      CiDiagnosticsStore.appendLog(tPath, text)
    }
  }
}

// ============================================================================
// ЭТАП A: Быстрые проверки конфигурации и определение затронутых файлов (CI)
// ============================================================================
fun detectCiChangedFiles(rootDir: File): List<String> {
  if (System.getenv("GITHUB_ACTIONS") != "true") return emptyList()
  fun runGitDiff(vararg args: String): List<String> {
    val proc = ProcessBuilder(listOf("git", "diff", "--name-only") + args.toList())
      .directory(rootDir)
      .redirectErrorStream(true)
      .start()
    val output = proc.inputStream.bufferedReader().readText()
    val code = proc.waitFor()
    if (code != 0) return emptyList()
    return output.lineSequence()
      .map { it.trim() }
      .filter { it.isNotEmpty() && !it.startsWith("fatal:") && !it.startsWith("warning:") }
      .toList()
  }

  return try {
    val baseRef = (System.getenv("GITHUB_BASE_REF") ?: "main").ifBlank { "main" }
    val fromMergeParent = runGitDiff("HEAD^1", "HEAD")
    if (fromMergeParent.isNotEmpty()) {
      fromMergeParent
    } else {
      ProcessBuilder(
        "git", "fetch", "--no-tags", "--depth=50", "origin",
        "+refs/heads/$baseRef:refs/remotes/origin/$baseRef"
      ).directory(rootDir).redirectErrorStream(true).start().waitFor()
      val fromOriginBase = runGitDiff("origin/$baseRef", "HEAD")
      if (fromOriginBase.isNotEmpty()) fromOriginBase else runGitDiff("HEAD~1", "HEAD")
    }
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
    val hookInstaller = file("${rootDir}/ci/scripts/install_ci_post_hook.py")
    if (hookInstaller.isFile && System.getenv("GITHUB_ACTIONS") == "true") {
      ProcessBuilder("python3", hookInstaller.absolutePath)
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
        .waitFor()
    }

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

    val ciReportTestScript = file("${rootDir}/ci/scripts/test_generate_ci_error_report.py")
    if (ciReportTestScript.isFile) {
      val proc = ProcessBuilder("python3", ciReportTestScript.absolutePath)
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
      val out = proc.inputStream.bufferedReader().readText()
      val exit = proc.waitFor()
      if (exit != 0) {
        throw GradleException("CI Error Report generator unit tests failed:\n$out")
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
          appendLine("| **Stage A (Fast Checks)** | `PASSED` (`version.properties`, `libs.versions.toml`, `.kts`, Python & CI Report tests) |")
          appendLine("| **Stage B (Early Kotlin Compile)** | `:core:database:compileDebugKotlin`, `:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin`" +
            (if (ciHasAndroidTestChanges) ", `:app:compileDebugAndroidTestKotlin`" else "") + " |")
          appendLine("| **Stage C (APK Packaging)** | `:app:assembleDebug` (skipping unused `:core:database:bundleDebugAar`) |")
          appendLine("| **Stage D (Unit Testing)** | `${if (ciChangeScope == "docs_only" || ciChangeScope == "android_test_only") "SKIPPED (not affected)" else ":app:testDebugUnitTest (parallel forks)"}` |")
          appendLine("| **CI Error Report Artifact** | `ci-error-report` (`ci-error-report.md` + `ci-error-report.json`) |")
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
