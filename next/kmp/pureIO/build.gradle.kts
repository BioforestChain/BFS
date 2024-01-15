plugins {
  id("kmp-library")
}

kotlin {
  kmpCommonTarget(project) {
    dependencies {
      api(libs.squareup.okio)

      implementation(projects.helper)
    }
  }
  kmpAndroidTarget(project) {
  }
  kmpIosTarget(project) {
  }
  kmpNodeJsTarget(project) {
    dependencies {
      implementation(libs.squareup.okio.nodefilesystem)
    }
  }
}
