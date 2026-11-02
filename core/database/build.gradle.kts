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

// В CI пропускаем упаковку автономного debug AAR для :core:database (~22 сек),
// так как модуль :app потребляет :core:database напрямую через compile/runtime jar & resources.
if (System.getenv("GITHUB_ACTIONS") == "true") {
  tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(":ciStageAFastChecks")
  }
  tasks.matching {
    it.name in setOf(
      "bundleDebugAar",
      "mergeDebugJavaResource",
      "bundleLibRuntimeToJarDebug",
      "syncDebugLibJars"
    )
  }.configureEach {
    enabled = false
  }
}
