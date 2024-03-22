plugins {
  id("kmp-compose")
}
dependencies {
  implementation(project(":core"))
}

kotlin {
  kmpCommonTarget(project) {
    applyHierarchy {
      common {
        group("mobile") {
          withAndroidTarget()
          withIosTarget()
        }
      }
    }
  }
  val mobileMain by sourceSets.creating {
    dependencies {
      // ssl代理。因为桌面端直接支持 ssl 服务，所以不需要这一层 rust 提供ssl代理转发
      implementation(projects.libReverseProxy)
    }
  }
  kmpComposeTarget(project) {
    dependencies {
      implementation(projects.helper)
      implementation(projects.helperPlatform)
      implementation(projects.helperCompose)
      implementation(projects.pureIO)
      implementation(projects.pureHttp)
      implementation(projects.pureImage)
      implementation(projects.core)
      implementation(projects.sys)
    }
  }
  kmpAndroidTarget(project) {
    dependencies {
      implementation(libs.androidx.webkit)
      implementation(libs.google.material)
      implementation(libs.accompanist.webview)
      implementation(libs.compose.ui)
    }
//    instrumentedTestDependsOn(commonMain.get())
//    instrumentedTestDependsOn(androidMain.get())
//    instrumentedTestSourceSets{
//      add(androidMain.get())
//    }
    instrumentedTestDependencies {
      implementation(libs.androidx.test.core)
      implementation(libs.androidx.compose.ui.test)
      implementation(libs.androidx.compose.ui.test.junit4)
      implementation(libs.androidx.compose.ui.test.manifest)
    }
  }
  kmpIosTarget(project)
  kmpDesktopTarget(project)
}

