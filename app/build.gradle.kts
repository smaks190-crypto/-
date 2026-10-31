plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  // alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

import java.io.File
import java.util.Properties
import java.util.Locale

// Version is stable for Debug builds. CI supplies a release-only version code.
val versionProps = Properties()
val versionPropsFile = file("${rootDir}/version.properties")
if (versionPropsFile.exists()) {
    versionPropsFile.inputStream().use { versionProps.load(it) }
}
val baseVersionCode = versionProps.getProperty("VERSION_CODE", "809").toIntOrNull()
    ?.takeIf { it > 0 } ?: error("Invalid VERSION_CODE in version.properties")
val ciVersionCode = System.getenv("CI_VERSION_CODE")?.takeIf { it.isNotBlank() }?.toIntOrNull()
    ?.takeIf { it > 0 }
val buildNum = ciVersionCode ?: baseVersionCode

// Функция расчета: x.xx (до 100, после 99 идет 0)
fun getVersionLetterName(number: Int): String {
    val major = 1 + (number / 100)
    val minor = number % 100
    return String.format(Locale.US, "%d.%02d", major, minor)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "ru.personalbudget.app.aadece"
    minSdk = 24
    targetSdk = 36
    versionCode = buildNum
    versionName = getVersionLetterName(buildNum)

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystoreFile = file("${rootDir}/my-upload-key.jks")
      if (keystoreFile.exists() && keystoreFile.length() > 0L) {
        storeFile = keystoreFile
        val storePass = System.getenv("STORE_PASSWORD")?.takeIf { it.isNotBlank() }
        val keyPass = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() }
        val alias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "upload"

        storePassword = storePass ?: keyPass
        keyAlias = alias
        keyPassword = keyPass ?: storePass
      } else {
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
      isCrunchPngs = false
      isMinifyEnabled = false
      isShrinkResources = false
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

configurations.all {
  exclude(group = "androidx.compose.compiler", module = "compiler")
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(project(":core:database"))
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  // testImplementation(libs.roborazzi)
  // testImplementation(libs.roborazzi.compose)
  // testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.core)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  // "ksp"(libs.moshi.kotlin.codegen) // Не используется (применяется рефлексивный KotlinJsonAdapterFactory)

  // Автоматический парсинг Markdown для Jetpack Compose
  implementation("com.github.jeziellago:compose-markdown:0.5.0")
  
  // Google Generative AI SDK отключен, т.к. запросы идут напрямую через Retrofit
  // implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

  // Локальное офлайн-распознавание речи VOSK отключено (используется прямая отправка аудио в Gemini)
  // implementation("com.alphacephei:vosk-android:0.3.47")
}

// Автоматическая верификация Unit-тестов Developer Console, David Live и компиляции Release/AndroidTest Source Sets при сборке Debug APK
tasks.withType<Test>().configureEach {
  filter {
    includeTestsMatching("com.example.utils.GlobalConsoleLoggerTest")
    includeTestsMatching("com.example.ExampleUnitTest")
    includeTestsMatching("com.example.davidapp.live.DavidLiveUnitTest")
    includeTestsMatching("com.example.data.FinancialSemanticsCalculatorTest")
    includeTestsMatching("com.example.data.safetospend.SafeToSpendCalculatorTest")
  }
}

val startCiAndroidEmulator = tasks.register<Exec>("startCiAndroidEmulator") {
  workingDir = rootProject.projectDir
  commandLine("bash", "${rootProject.projectDir}/ci/start_emulator.sh")
  mustRunAfter("packageDebugAndroidTest", "packageDebug")
}

val collectCiEmulatorArtifacts = tasks.register<Exec>("collectCiEmulatorArtifacts") {
  workingDir = rootProject.projectDir
  commandLine("bash", "${rootProject.projectDir}/ci/collect_artifacts.sh")
}

if (System.getenv("GITHUB_ACTIONS") == "true") {
  tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
    dependsOn(startCiAndroidEmulator)
    finalizedBy(collectCiEmulatorArtifacts)
  }
}

// В Fast CI при сборке Debug APK запускаем только Debug Unit-тесты (без дублирующей компиляции Release и AndroidTest)
if (System.getenv("GITHUB_ACTIONS") == "true") {
  tasks.matching { it.name == "assembleDebug" }.configureEach {
    dependsOn("testDebugUnitTest")
  }
}

// Полная проверка всех Source Sets (Release + AndroidTest + UnitTest) доступна через отдельную задачу
tasks.register("verifyAllSourceSets") {
  group = "verification"
  description = "Compiles Release and AndroidTest source sets and runs Debug unit tests."
  dependsOn("compileReleaseKotlin", "compileDebugAndroidTestKotlin", "testDebugUnitTest")
}


