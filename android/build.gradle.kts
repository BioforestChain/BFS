// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
  id("com.google.devtools.ksp") version "1.8.10-1.0.9" apply false
}

buildscript {
  apply(from = "versions.gradle")
  repositories {
    google()
    mavenCentral()
    maven(
      "https://maven.pkg.jetbrains.space/public/p/ktor/eap"
    )
    maven("https://jitpack.io")
  }
  dependencies {
    classpath("com.android.tools.build:gradle:8.0.2")
    val kotlin_version = "1.8.10"
    classpath(kotlin("gradle-plugin", version = kotlin_version))
  }

}


tasks.register<Delete>("clean").configure {
  delete(rootProject.buildDir)
}

//
