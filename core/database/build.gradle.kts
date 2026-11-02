plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.example.core.database"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    minSdk = 24
    consumerProguardFiles("consumer-rules.pro")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  api(libs.androidx.room.runtime)
  api(libs.androidx.room.ktx)
  implementation(libs.androidx.core.ktx)
  implementation(libs.kotlinx.coroutines.core)
  "ksp"(libs.androidx.room.compiler)
}

// В CI пропускаем только финальную упаковку автономного .aar архива (bundleDebugAar),
// сохраняя bundleLibCompileToJarDebug и bundleLibRuntimeToJarDebug для :app и Unit-тестов.
if (System.getenv("GITHUB_ACTIONS") == "true") {
  tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(":ciStageAFastChecks")
  }
  tasks.matching { it.name == "bundleDebugAar" }.configureEach {
    enabled = false
  }
}
