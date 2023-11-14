package org.dweb_browser.browser.desk

import org.dweb_browser.browser.desk.types.DeskAppMetaData
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.dweb_browser.core.help.types.MMID
import org.dweb_browser.core.std.http.HttpDwebServer
import org.dweb_browser.helper.ChangeableMap
import org.dweb_browser.helper.PromiseOut
import org.dweb_browser.helper.Signal
import org.dweb_browser.helper.SimpleSignal
import org.dweb_browser.helper.build
import org.dweb_browser.helper.platform.IPlatformViewController
import org.dweb_browser.helper.resolvePath


class TaskbarController private constructor(
  open val deskNMM: DeskNMM,
  open val deskSessionId: String,
  private val desktopController: IDesktopController,
  private val taskbarServer: HttpDwebServer,
  private val runningApps: ChangeableMap<MMID, RunningApp>,
) {
  companion object {

    suspend fun create(
      deskSessionId: String,
      deskNMM: DeskNMM,
      desktopController: IDesktopController,
      taskbarServer: HttpDwebServer,
      runningApps: ChangeableMap<MMID, RunningApp>
    ) =
      TaskbarController(
        deskNMM,
        deskSessionId,
        desktopController,
        taskbarServer,
        runningApps
      ).also {
        it._taskbarView = ITaskbarView.create(it)
      }
  }

  private lateinit var _taskbarView: ITaskbarView
  val taskbarView get() = _taskbarView
  val taskbarStore = TaskbarStore(deskNMM)

  /** 展示在taskbar中的应用列表 */
  private suspend fun getTaskbarShowAppList() = taskbarStore.getApps()
  internal suspend fun getFocusApp() = getTaskbarShowAppList().firstOrNull()
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
      val apps = getTaskbarShowAppList()
      runningApps.onChange { map ->
        /// 将新增的打开应用追加到列表签名
        for (mmid in map.origin.keys) {
          apps.remove(mmid)
          apps.add(0, mmid) // 追加到第一个
        }
        // 只展示4个，结合返回桌面的一个tarBar有5个图标
        if (apps.size > 4) {
          apps.removeLastOrNull()
        }

        /// 保存到数据库
        taskbarStore.setApps(apps)
        updateSignal.emit()
      }

      desktopController.onUpdate { updateSignal.emit() }
    }

  }

  suspend fun getTaskbarAppList(limit: Int): List<DeskAppMetaData> {
    val apps = mutableMapOf<MMID, DeskAppMetaData>()
    for (appId in getTaskbarShowAppList()) {
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

  private var activityTask = PromiseOut<IPlatformViewController>()
  suspend fun waitActivityCreated() = activityTask.waitPromise()

  var platformContext: IPlatformViewController? = null
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
}