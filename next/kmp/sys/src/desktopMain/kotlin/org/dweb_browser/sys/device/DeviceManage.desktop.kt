package org.dweb_browser.sys.device

import org.dweb_browser.core.std.file.FileNMM
import org.dweb_browser.core.std.file.getApplicationRootDir
import org.dweb_browser.helper.randomUUID
import org.dweb_browser.platform.desktop.os.OsType

actual class DeviceManage actual constructor() {
  val runtime by lazy { Runtime.getRuntime() }
  val uuid by lazy {
    runCatching {
      when (OsType.current) {
        OsType.MacOS -> runtime.exec("system_profiler SPHardwareDataType | awk '/UUID/ { print $3; }'")
        OsType.Windows -> runtime.exec("wmic csproduct get UUID")
        else -> runtime.exec("cat /sys/class/dmi/id/product_uuid")
      }.inputStream.readAllBytes().decodeToString()
    }.getOrElse {
      FileNMM.getApplicationRootDir().resolve(".os").toFile().apply { mkdirs() }.resolve("uuid")
        .run {
          if (exists()) readText()
          else randomUUID().also { writeText(it) }
        }
    }
  }

  actual fun deviceUUID(): String {
    return uuid
  }

  actual fun deviceAppVersion(): String {
    return DeviceManage::class.java.`package`?.implementationVersion ?: ""
  }
}