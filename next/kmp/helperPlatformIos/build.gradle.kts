import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.ByteArrayOutputStream
import org.gradle.internal.os.OperatingSystem

plugins {
  id("mobile-compose-target")
}

kotlin {
  listOf(
    iosX64(),
    iosArm64(),
    iosSimulatorArm64()
  ).forEach {
    it.configureIos()
  }

  sourceSets.commonMain.dependencies {
    api(libs.jetbrains.compose.runtime)
    api(libs.jetbrains.compose.foundation)
    api(libs.jetbrains.compose.components.resources)
    api(libs.kotlinx.atomicfu)
    api(libs.ktor.server.cio)
    api(libs.ktor.client.cio)
    api(libs.ktor.client.encoding)
    api(libs.ktor.server.websockets)

    implementation(libs.jetbrains.compose.material3)

    implementation(projects.helper)
  }
  sourceSets.commonTest.dependencies {
    implementation(kotlin("test"))
  }
  sourceSets.iosMain.dependencies {
    api(libs.ktor.client.darwin)
  }
}

android {
  namespace = "org.dweb_browser.helper.platform.ios"
  compileSdk = libs.versions.compileSdkVersion.get().toInt()
  defaultConfig {
    minSdk = libs.versions.minSdkVersion.get().toInt()
  }
}
fun File.resolveArchPath(target: KonanTarget): File? {
  return resolve(
    if (target is KonanTarget.IOS_ARM64) "ios-arm64" else "ios-arm64_x86_64-simulator"
  )
}

fun org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget.configureIos() {
  val frameworkName = "DwebPlatformIosKit"
  val xcPath =
    projectDir.resolve("src/nativeInterop/cinterop/xcframeworks/$frameworkName.xcframework")
      .resolveArchPath(
        konanTarget,
      )
  println("xcPath: $xcPath")

  compilations.getByName("main") {
    val xc = cinterops.create(frameworkName) {
      defFile("src/nativeInterop/cinterop/$frameworkName.def")

      compilerOpts("-framework", frameworkName, "-F$xcPath/")
      extraOpts += listOf("-compiler-option", "-fmodules")
    }
    println("xc:$xc")
  }
  println("compilations.asMap:${compilations.asMap}")

  binaries.all {
    linkerOpts(
      "-framework", frameworkName, "-F$xcPath/",// "-rpath", "$xcPath", "-ObjC"
    )
  }
}
tasks.register("cinteropSync") {
  dependsOn(
    "cinteropDwebPlatformIosKitIosArm64",
    "cinteropDwebPlatformIosKitIosSimulatorArm64",
    "cinteropDwebPlatformIosKitIosX64",
    "commonizeCInterop",
    "copyCommonizeCInteropForIde",
    "commonize",
    "transformNativeMainCInteropDependenciesMetadataForIde",
    "transformAppleMainCInteropDependenciesMetadataForIde",
    "transformIosMainCInteropDependenciesMetadataForIde",
    "transformNativeTestCInteropDependenciesMetadataForIde",
    "transformAppleTestCInteropDependenciesMetadataForIde",
    "transformIosTestCInteropDependenciesMetadataForIde",
    "prepareKotlinIdeaImport",
  )
}

/// 项目一开始要做sync的时候
gradle.projectsEvaluated {
  println("beforeProject: $name")

  if(OperatingSystem.current().isMacOsX) {
    val output = ByteArrayOutputStream()

    exec {
      commandLine("which", "deno")
      standardOutput = output
      isIgnoreExitValue = true
    }

    val denoBinPath = output.toString().trim()
    val userHome = System.getProperty("user.home")
    var deno = denoBinPath.ifBlank { "$userHome/.deno/bin/deno" }

    if(!file(deno).exists()) {
      deno = "deno"
    }

    exec {
      workingDir = projectDir.resolve("./src/nativeInterop/cinterop")
      commandLine(deno, "run", "-A", "./build.ts")
    }
  }
}
