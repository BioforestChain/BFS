package org.dweb_browser.browser.desk.version

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.dweb_browser.browser.desk.DeskNMM
import org.dweb_browser.browser.desk.DesktopController
import org.dweb_browser.browser.desk.debugDesk
import org.dweb_browser.browser.download.DownloadState
import org.dweb_browser.browser.download.DownloadTask
import org.dweb_browser.browser.download.ext.createChannelOfDownload
import org.dweb_browser.browser.download.ext.createDownloadTask
import org.dweb_browser.browser.download.ext.existsDownload
import org.dweb_browser.browser.download.ext.pauseDownload
import org.dweb_browser.browser.download.ext.removeDownload
import org.dweb_browser.browser.download.ext.startDownload
import org.dweb_browser.core.std.file.ext.realFile
import org.dweb_browser.helper.compose.Language
import org.dweb_browser.helper.compose.SimpleI18nResource
import org.dweb_browser.helper.debounce
import org.dweb_browser.helper.isGreaterThan
import org.dweb_browser.pure.http.PureTextFrame
import org.dweb_browser.sys.device.ext.getDeviceAppVersion
import org.dweb_browser.sys.permission.SystemPermissionName
import org.dweb_browser.sys.permission.ext.requestSystemPermission
import org.dweb_browser.sys.toast.ext.showToast

object NewVersionI18nResource {
  val toast_message_download_fail =
    SimpleI18nResource(Language.ZH to "下载失败", Language.EN to "Download Fail")

  val toast_message_storage_fail =
    SimpleI18nResource(
      Language.ZH to "文件存储失败，请重新下载",
      Language.EN to "File storage failed, please download again"
    )

  val toast_message_permission_fail =
    SimpleI18nResource(Language.ZH to "授权失败", Language.EN to "authorization failed")

  val request_permission_title_install =
    SimpleI18nResource(
      Language.ZH to "请求安装应用权限",
      Language.EN to "Request permission to install the application"
    )
  val request_permission_message_install =
    SimpleI18nResource(
      Language.ZH to "安装应用需要请求安装应用权限，请手动设置",
      Language.EN to "To install an application, you need to request the permission to install the application"
    )

  val request_permission_title_storage =
    SimpleI18nResource(
      Language.ZH to "请求外部存储权限",
      Language.EN to "Request external storage permissions"
    )
  val request_permission_message_storage =
    SimpleI18nResource(
      Language.ZH to "DwebBrowser正在向您获取“存储”权限，同意后，将用于存储下载的应用",
      Language.EN to "DwebBrowser is asking you to \"store\" permission, if you agree, it will be used to store the downloaded application"
    )
}

enum class NewVersionType {
  Hide, NewVersion, Download, Install
  ;
}

class NewVersionController(private val deskNMM: DeskNMM, val desktopController: DesktopController) {
  private val store = NewVersionStore(deskNMM)
  private val manage = NewVersionManage()
  var newVersionItem: NewVersionItem? = null
  val newVersionType = mutableStateOf(NewVersionType.Hide) // 用于显示新版本提醒的控制
  var openAgain: Boolean = false // 默认不需要重新打开，只有在授权后返回时，才需要重新打开

  fun updateVersionType(type: NewVersionType) {
    newVersionType.value = type
  }

  init {
    deskNMM.ioAsyncScope.launch { initNewVersionItem() }
  }

  private suspend fun initNewVersionItem() {
    val currentVersion = deskNMM.getDeviceAppVersion() // 获取当前系统的 app 版本
    val saveVersionItem = store.getNewVersion() // 获取之前下载存储的版本
    val loadVersionItem = manage.loadNewVersion() // 获取服务器最新的版本
    // 直接判断 load 和 save 版本是否高于系统版本
    val loadHigher = loadVersionItem?.let {
      loadVersionItem.versionName.isGreaterThan(currentVersion)
    } ?: false
    val saveHigher = saveVersionItem?.let {
      saveVersionItem.versionName.isGreaterThan(currentVersion)
    } ?: false

    // 根据 loadHigher 和 saveHigher 赋值 newVersionItem
    newVersionItem = if (loadHigher && saveHigher) {
      if (loadVersionItem!!.versionName.isGreaterThan(saveVersionItem!!.versionName)) {
        saveVersionItem.taskId?.let { taskId -> deskNMM.removeDownload(taskId) }
        loadVersionItem
      } else {
        saveVersionItem
      }
    } else if (loadHigher) {
      saveVersionItem?.taskId?.let { taskId -> deskNMM.removeDownload(taskId) }
      loadVersionItem
    } else if (saveHigher) {
      saveVersionItem
    } else {
      null
    }

    // 如果 newVersionItem 为空的话，那么就不需要显示了；如果不为空，判断状态进行指定跳转
    newVersionItem?.let { newVersion ->
      if (newVersion.status.state == DownloadState.Completed) {
        updateVersionType(NewVersionType.Install)
      } else {
        updateVersionType(NewVersionType.NewVersion)
      }
    }
    debugDesk("NewVersion", "hasNew=${newVersionType.value} => $newVersionItem")
  }

  private suspend fun watchProcess(newVersionItem: NewVersionItem) = newVersionItem.taskId?.let {
    deskNMM.ioAsyncScope.launch {
      val ret = deskNMM.createChannelOfDownload(it) {
        when (downloadTask.status.state) {
          DownloadState.Completed -> {
            newVersionItem.updateDownloadTask(downloadTask, store)
            // 关闭watchProcess
            channel.close()
            newVersionItem.pauseFlag = false
            // 跳转到安装界面
            if (checkInstallPermission()) { // 先判断是否有权限
              val realPath = deskNMM.realFile(downloadTask.filepath)
              newVersionType.value = NewVersionType.Hide
              manage.installApk(realPath)
              // 清除保存的新版本信息
              store.clear()
              // deskNMM.removeDownload(downloadTask.id) 不能在这边删除
            } else {
              debugDesk("NewVersion", "no Install Apk Permission")
              updateVersionType(NewVersionType.Install)
            }
          }

          else -> {
            newVersionItem.updateDownloadTask(downloadTask, store)
          }
        }
      }
      debugDesk("NewVersion", "watch process error=>$ret")
    }
  }

  suspend fun downloadApp() = debounce(
    scope = deskNMM.ioAsyncScope,
    action = {
      val grant = deskNMM.requestSystemPermission(
        name = SystemPermissionName.STORAGE,
        title = NewVersionI18nResource.request_permission_title_storage.text,
        description = NewVersionI18nResource.request_permission_message_storage.text
      )
      if (!grant) {
        deskNMM.showToast(NewVersionI18nResource.toast_message_permission_fail.text)
        return@debounce
      }

      newVersionItem?.let { newVersion ->
        val exists = newVersion.taskId?.let { deskNMM.existsDownload(it) } ?: false
        if (!exists) {
          val taskId = deskNMM.createDownloadTask(url = newVersion.originUrl, external = true)
          newVersion.updateTaskId(taskId, store)
        }
        start()
      }
    }
  )

  suspend fun start() = newVersionItem?.let { newVersionItem ->
    newVersionItem.taskId?.let { taskId ->
      if (deskNMM.startDownload(taskId)) {
        newVersionItem.updateState(DownloadState.Downloading, store)
        if (!newVersionItem.pauseFlag) {
          newVersionItem.pauseFlag = true
          watchProcess(newVersionItem)
        }
        true
      } else {
        deskNMM.showToast(NewVersionI18nResource.toast_message_download_fail.text)
        newVersionItem.updateState(DownloadState.Failed, store)
      }
    } ?: false
  } ?: false

  suspend fun pause() = newVersionItem?.taskId?.let { deskNMM.pauseDownload(it) } ?: false

  private suspend fun checkInstallPermission() = deskNMM.requestSystemPermission(
    name = SystemPermissionName.InstallSystemApp,
    title = NewVersionI18nResource.request_permission_message_install.text,
    description = NewVersionI18nResource.request_permission_message_install.text
  )

  fun openSystemInstallSetting() = run {
    openAgain = true // 为了使得返回的时候重新判断是否安装
    manage.openSystemInstallSetting()
  }
}