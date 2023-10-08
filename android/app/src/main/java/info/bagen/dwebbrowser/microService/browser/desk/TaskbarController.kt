package info.bagen.dwebbrowser.microService.browser.desk

import android.content.res.Resources
import info.bagen.dwebbrowser.microService.browser.desk.types.DeskAppMetaData
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.dweb_browser.helper.ChangeableMap
import org.dweb_browser.helper.PromiseOut
import org.dweb_browser.helper.Signal
import org.dweb_browser.helper.SimpleSignal
import org.dweb_browser.helper.build
import org.dweb_browser.helper.resolvePath
import org.dweb_browser.microservice.help.types.MMID
import org.dweb_browser.microservice.ipc.Ipc
import org.dweb_browser.microservice.std.http.HttpDwebServer

class TaskbarController(
  val deskSessionId: String,
  val deskNMM: DeskNMM,
  private val desktopController: DesktopController,
  private val taskbarServer: HttpDwebServer,
  private val runningApps: ChangeableMap<MMID, Ipc>
) {
  val taskbarView = TaskbarView(this)

  /** 展示在taskbar中的应用列表 */
  private suspend fun getApps() = deskNMM.deskStore.getTaskbarApps()
  internal suspend fun getFocusApp() = getApps().firstOrNull()
  internal val updateSignal = SimpleSignal()
  val onUpdate = updateSignal.toListener()

  // 触发状态更新
  internal val stateSignal = Signal<TaskBarState>()
  val onStatus = stateSignal.toListener()

  init {
    /**
     * 绑定 runningApps 集合
     */
    deskNMM.ioAsyncScope.launch {
      val apps = getApps()
      runningApps.onChange { map ->
        /// 将新增的打开应用追加到列表签名
        for (mmid in map.origin.keys) {
          if (!apps.contains(mmid)) {
            apps.add(0, mmid) // 追加到第一个
          }
        }
        /// 保存到数据库
        deskNMM.deskStore.setTaskbarApps(apps)
        updateSignal.emit()
      }

      desktopController.onUpdate { updateSignal.emit() }
    }

  }

  suspend fun getTaskbarAppList(limit: Int): List<DeskAppMetaData> {
    val apps = mutableMapOf<MMID, DeskAppMetaData>()
    for (appId in getApps()) {
      if (apps.size >= limit) {
        break
      }
      if (appId == deskNMM.mmid || apps.containsKey(appId)) {
        continue
      }
      val metaData = deskNMM.bootstrapContext.dns.query(appId)
      if (metaData != null) {
        apps[appId] = DeskAppMetaData().apply {
          running = runningApps.contains(appId)
          winStates = desktopController.desktopWindowsManager.getWindowStates(metaData.mmid)
          //...复制metaData属性
          assign(metaData.manifest)
        }
      }
    }

    return apps.values.toList()
  }

  /**
   * 对Taskbar自身进行resize
   * 根据web元素的大小进行自适应调整
   *
   * @returns 如果视图发生了真实的改变（不论是否变成说要的结果），则返回 true
   */
  fun resize(reSize: ReSize) {
    taskbarView.state.layoutWidth = reSize.width
    taskbarView.state.layoutHeight = reSize.height
  }

  /**
   * 将其它视图临时最小化到 TaskbarView/TooggleDesktopButton 按钮里头，在此点击该按钮可以释放这些临时视图到原本的状态
   */
  suspend fun toggleDesktopView() {
    val allWindows = desktopController.desktopWindowsManager.allWindows.keys.toList()
    if (allWindows.find { it.isVisible() } != null) {
      allWindows.forEach { win ->
        win.toggleVisible(false)
      }
    } else {
      allWindows.forEach { win ->
        win.toggleVisible(true)
      }
    }
  }

  private var activityTask = PromiseOut<TaskbarActivity>()
  suspend fun waitActivityCreated() = activityTask.waitPromise()

  var activity: TaskbarActivity? = null
    set(value) {
      if (field == value) {
        return
      }
      field = value
      if (value == null) {
        activityTask = PromiseOut()
      } else {
        activityTask.resolve(value)
      }
    }

  fun getTaskbarUrl() =
    taskbarServer.startResult.urlInfo.buildInternalUrl().build {
      resolvePath("/taskbar.html")
      parameters["api-base"] = taskbarServer.startResult.urlInfo.buildPublicUrl().toString()
    }

  @Serializable
  data class ReSize(val width: Float, val height: Float)

  @Serializable
  data class TaskBarState(val focus: Boolean, val appId: String)

  private val density by lazy { Resources.getSystem().displayMetrics.density }

  private fun Int.toDpValue() = (this * density + 0.5f).toInt()
}
