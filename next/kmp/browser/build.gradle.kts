plugins {
  id("kmp-compose")
}

kotlin {
  kmpComposeTarget(project) {
    dependencies {
      implementation(projects.helper)
      implementation(projects.helperCompose)
      implementation(projects.helperPlatform)
      implementation(projects.pureIO)
      implementation(projects.pureCrypto)
      implementation(projects.pureHttp)
      implementation(projects.core)
      implementation(projects.sys)
      implementation(projects.dwebview)
      implementation(projects.window)
    }
  }
  kmpAndroidTarget(project) {
    dependencies {
      // AndroidX Compose
      implementation(libs.compose.material3)
      implementation(libs.compose.material3.window)
      // Google Accompanist
      implementation(libs.accompanist.webview)
      implementation(libs.accompanist.navigation.material)
      implementation(libs.accompanist.navigation.animation)
      implementation(libs.accompanist.systemui.controller)
      implementation(libs.accompanist.permissions)
      implementation(libs.accompanist.insets.ui)
      implementation(libs.compose.ui.preview)
      //扫码核心库
      implementation(libs.camera.core)
      implementation(libs.camera.view)
      implementation(libs.camera.camera2)
      implementation(libs.camera.lifecycle)
      implementation(libs.camera.barcode)
      //解压缩
      implementation(libs.commons.compress)
      // 图像
      implementation(libs.coil.core)
      implementation(libs.coil.compose)
      // 七牛
      implementation(libs.qiniu.upload)
    }
  }
  kmpIosTarget(project) {
    dependencies {
      //解压缩
      implementation(projects.ziplib)
    }
  }
}
