plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

import java.io.File
import java.util.Properties
import java.util.Locale

// РУЧНОЕ ЗНАЧЕНИЕ: версия автоматически увеличивается при каждой сборке и сохраняется в файле version.properties в корне проекта
val versionPropsFile = file("${rootDir}/version.properties")
val versionProps = Properties()
var manualVersionCode = 2

if (versionPropsFile.exists()) {
    versionPropsFile.inputStream().use { versionProps.load(it) }
    manualVersionCode = versionProps.getProperty("VERSION_CODE", "2").toIntOrNull() ?: 2
} else {
    versionProps.setProperty("VERSION_CODE", "2")
    versionPropsFile.outputStream().use { versionProps.store(it, "Auto-generated build version. You can edit this file to change the version.") }
}

// Автоматически увеличиваем версию при сборке
val isBuilding = gradle.startParameter.taskNames.any { 
    it.contains("assemble", ignoreCase = true) || 
    it.contains("bundle", ignoreCase = true) || 
    it.contains("install", ignoreCase = true) || 
    it.contains("compile", ignoreCase = true)
}
if (isBuilding) {
    manualVersionCode += 1
    versionProps.setProperty("VERSION_CODE", manualVersionCode.toString())
    versionPropsFile.outputStream().use { versionProps.store(it, "Auto-incremented build version.") }
}

val buildNum = manualVersionCode

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
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
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
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)

  // Автоматический парсинг Markdown для Jetpack Compose
  implementation("com.github.jeziellago:compose-markdown:0.5.0")
  
  // Google Generative AI SDK для взаимодействия с Gemini
  implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

  // Локальное офлайн-распознавание речи VOSK
  implementation("com.alphacephei:vosk-android:0.3.47")
}
