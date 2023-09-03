plugins {
  alias(libs.plugins.kotlinxMultiplatform)
  alias(libs.plugins.androidLibrary)
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
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(libs.versions.jvmTarget.get()))
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(kotlin("stdlib"))
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.atomicfu)

        api(libs.ktor.server.websockets)
        api(libs.ktor.server.cio)
        api(libs.ktor.client.cio)

        api(libs.ktor.server.websockets)
        api(libs.ktor.server.cio)
        api(libs.ktor.client.cio)
        api(libs.kotlin.serialization.json)

        implementation(project(":helper"))
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.test.kotlin.coroutines.test)
        implementation(libs.test.kotlin.coroutines.debug)
        implementation(libs.kotlinx.atomicfu)
      }
    }
    val androidMain by getting {
      dependencies {

        implementation(platform(libs.http4k.bom.get()))
        api(libs.http4k.core)
        api(libs.http4k.multipart)
        api(libs.http4k.client.apache)

        implementation(libs.data.moshi.pack)
        api(libs.data.gson)
      }
    }

  }
}

android {
  namespace = "org.dweb_browser.microservice"
  compileSdk = libs.versions.compileSdkVersion.get().toInt()
  defaultConfig {
    minSdk = libs.versions.minSdkVersion.get().toInt()
  }
}