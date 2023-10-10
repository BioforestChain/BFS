plugins {
  alias(libs.plugins.kotlinxMultiplatform)
  alias(libs.plugins.androidLibrary)
  alias(libs.plugins.jetbrainsCompose)
  alias(libs.plugins.kotlinPluginSerialization)
  id ("com.google.osdetector") version "1.7.3"
}

kotlin {
  androidTarget {
    compilations.all {
      kotlinOptions {
        jvmTarget = libs.versions.jvmTarget.get()
      }
    }
  }

  jvm("desktop")
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(libs.versions.jvmTarget.get()))
  }

  listOf(
    iosX64(),
    iosArm64(),
    iosSimulatorArm64()
  ).forEach {
    it.binaries.framework {
      baseName = "DwebHelperCompose"
      isStatic = true
    }
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(libs.jetbrains.compose.runtime)
        api(libs.jetbrains.compose.foundation)
        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        api(libs.jetbrains.compose.components.resources)
        api(libs.kotlinx.atomicfu)
        api(libs.ktor.server.cio)
        api(libs.ktor.client.cio)
        api(libs.ktor.client.encoding)
        api(libs.ktor.server.websockets)

        implementation(libs.jetbrains.compose.material3)

        implementation(project(":helper"))
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
      }
    }
    val androidMain by getting {
      dependencies {
        // Android Runtime
        api(libs.androidx.core.ktx)
        api(libs.androidx.activity)
        api(libs.androidx.activity.ktx)
        api(libs.androidx.activity.compose)
        api(libs.androidx.appcompat)
        api(libs.androidx.animation.core.android)
        api(libs.androidx.lifecycle.runtime.ktx)
        api(libs.google.material)
        // 加载图片 coil
        api(libs.coil.core)
        api(libs.coil.compose)
        api(libs.coil.svg)
        api(libs.coil.video)
        api(libs.coil.gif)
      }
    }
    val androidUnitTest by getting
    val iosX64Main by getting
    val iosArm64Main by getting
    val iosSimulatorArm64Main by getting
    val iosMain by creating {
      dependsOn(commonMain)
      iosX64Main.dependsOn(this)
      iosArm64Main.dependsOn(this)
      iosSimulatorArm64Main.dependsOn(this)
    }
    val iosX64Test by getting
    val iosArm64Test by getting
    val iosSimulatorArm64Test by getting
    val iosTest by creating {
      dependsOn(commonTest)
      iosX64Test.dependsOn(this)
      iosArm64Test.dependsOn(this)
      iosSimulatorArm64Test.dependsOn(this)
    }

    val desktopMain by getting {
      dependencies {
        dependencies {
          implementation(compose.desktop.common)
          // https://stackoverflow.com/questions/73187027/use-javafx-in-kotlin-multiplatform
          // As JavaFX have platform-specific dependencies, we need to add them manually
          val fxSuffix = when (osdetector.classifier) {
            "linux-x86_64" -> "linux"
            "linux-aarch_64" -> "linux-aarch64"
            "windows-x86_64" -> "win"
            "osx-x86_64" -> "mac"
            "osx-aarch_64" -> "mac-aarch64"
            else -> throw IllegalStateException("Unknown OS: ${osdetector.classifier}")
          }
          implementation("org.openjfx:javafx-base:19:${fxSuffix}")
          implementation("org.openjfx:javafx-graphics:19:${fxSuffix}")
          implementation("org.openjfx:javafx-controls:19:${fxSuffix}")
          implementation("org.openjfx:javafx-swing:19:${fxSuffix}")
          implementation("org.openjfx:javafx-web:19:${fxSuffix}")
          implementation("org.openjfx:javafx-media:19:${fxSuffix}")
        }
      }
    }
  }
}

android {
  namespace = "org.dweb_browser.helper.platform"
  compileSdk = libs.versions.compileSdkVersion.get().toInt()
  defaultConfig {
    minSdk = libs.versions.minSdkVersion.get().toInt()
  }
  sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
}