plugins {
  alias(libs.plugins.kotlinxMultiplatform)
  alias(libs.plugins.androidLibrary)
  alias(libs.plugins.jetbrainsCompose)
  alias(libs.plugins.kotlinPluginSerialization)
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
        implementation(libs.jetbrains.compose.runtime)
        implementation(libs.jetbrains.compose.foundation)
        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        implementation(libs.jetbrains.compose.components.resources)

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
        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.activity)
        implementation(libs.androidx.activity.ktx)
        implementation(libs.androidx.appcompat)
        implementation(libs.androidx.animation.core.android)
        implementation(libs.androidx.lifecycle.runtime.ktx)
        implementation(libs.google.material)
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
        }
      }
    }
  }
}

android {
  namespace = "org.dweb_browser.helper.android"
  compileSdk = libs.versions.compileSdkVersion.get().toInt()
  defaultConfig {
    minSdk = libs.versions.minSdkVersion.get().toInt()
  }
}