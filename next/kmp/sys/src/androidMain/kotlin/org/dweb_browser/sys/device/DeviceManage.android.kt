package org.dweb_browser.sys.device

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.runBlocking
import org.dweb_browser.core.std.permission.AuthorizationStatus
import org.dweb_browser.helper.getAppContextUnsafe
import org.dweb_browser.helper.randomUUID
import org.dweb_browser.sys.permission.AndroidPermissionTask
import org.dweb_browser.sys.permission.PermissionActivity
import org.dweb_browser.sys.permission.SystemPermissionAdapterManager
import org.dweb_browser.sys.permission.SystemPermissionName
import java.io.File

actual object DeviceManage {
  init {
    SystemPermissionAdapterManager.append {
      when (task.name) {
        SystemPermissionName.PHONE -> {
          PermissionActivity.launchAndroidSystemPermissionRequester(
            microModule, AndroidPermissionTask(
              listOf(Manifest.permission.READ_PHONE_STATE), task.title, task.description
            )
          ).values.firstOrNull()
        }

        SystemPermissionName.STORAGE -> {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) { // Android 6.0 (API等级23) 到 Android 10 (API等级29):WRITE_EXTERNAL_STORAGE和READ_EXTERNAL_STORAGE是危险权限
            PermissionActivity.launchAndroidSystemPermissionRequester(
              microModule, AndroidPermissionTask(
                listOf(
                  Manifest.permission.WRITE_EXTERNAL_STORAGE,
                  Manifest.permission.READ_EXTERNAL_STORAGE
                ), task.title, task.description
              )
            ).values.firstOrNull()
          } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2) { // Android 11 (API等级30) 及以上: WRITE_EXTERNAL_STORAGE标记为已废弃。READ_EXTERNAL_STORAGE仍是一个危险权限
            PermissionActivity.launchAndroidSystemPermissionRequester(
              microModule, AndroidPermissionTask(
                listOf(
                  Manifest.permission.READ_EXTERNAL_STORAGE
                ), task.title, task.description
              )
            ).values.firstOrNull()
          } else {
            AuthorizationStatus.GRANTED
          }
        }

        else -> null
      }
    }
  }

  private const val PREFIX = ".dweb_" // 文件夹起始内容
  private val rootFile by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
  }

  /**
   * 由于使用 MediaStore 存储，卸载apk后分区存储信息会被清空，导致重新安装无法后台直接获取文件内容（权限异常）
   *
   */
  private val deviceUUID by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    runBlocking {
      val uuid = rootFile.list()?.find { file ->
        file.startsWith(PREFIX)
      }?.substringAfter(PREFIX)
      debugDevice("deviceUUID", "uuid=$uuid")
      uuid ?: randomUUID().also { newUUID ->
        val mkdirs = File(rootFile, "$PREFIX$newUUID").mkdirs()
        debugDevice("deviceUUID", "randomUUID=$newUUID, create directory => $mkdirs")
      }
    }
  }

  actual fun deviceUUID(uuid: String?): String {
    // 如果传入的uuid不为空，理论上需要创建一个和uuid一样的文件夹。如果uuid为空的话，同样是直接返回 deviceUUID
    return uuid?.also { saveUUID ->
      // 创建之前，把目录下面所有前缀符合的文件夹删除
      rootFile.listFiles()?.iterator()?.forEach { file ->
        if (file.name.startsWith(PREFIX)) {
          file.deleteRecursively()
        }
      }
      val mkdirs = File(rootFile, "$PREFIX$saveUUID").mkdirs()
      debugDevice("deviceUUID", "uuid=$saveUUID, create directory => $mkdirs")
    } ?: deviceUUID
  }

  actual fun deviceAppVersion(): String {
    val packageManager: PackageManager = getAppContextUnsafe().packageManager
    val packageName: String = getAppContextUnsafe().packageName
    return packageManager.getPackageInfo(packageName, 0).versionName
  }
}