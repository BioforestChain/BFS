package org.dweb_browser.sys

import org.dweb_browser.helper.compose.Language
import org.dweb_browser.helper.compose.SimpleI18nResource

class SysI18nResource {
  companion object {
    val permission_title_request =
      SimpleI18nResource(Language.ZH to "申请权限", Language.EN to "Request Permission")

    val permission_button_refuse =
      SimpleI18nResource(Language.ZH to "拒绝", Language.EN to "Refuse")
    val permission_button_confirm =
      SimpleI18nResource(Language.ZH to "确定", Language.EN to "Confirm")
    val permission_button_authorize_all =
      SimpleI18nResource(Language.ZH to "授权全部", Language.EN to "Authorize All")

    val permission_tip_camera_title = SimpleI18nResource(
      Language.ZH to "相机权限使用说明",
      Language.EN to "Camera Permission Instructions"
    )
    val permission_tip_camera_message = SimpleI18nResource(
      Language.ZH to "DwebBrowser正在向您获取“相机”权限，同意后，将用于为您提供拍照服务",
      Language.EN to "DwebBrowser is asking you for \"Camera\" permissions, and if you agree, it will be used to take photos for you"
    )

    val permission_tip_location_title = SimpleI18nResource(
      Language.ZH to "位置权限使用说明",
      Language.EN to "Location Permission Instructions"
    )
    val permission_tip_location_message = SimpleI18nResource(
      Language.ZH to "DwebBrowser正在向您获取“位置”权限，同意后，将用于获取位置信息",
      Language.EN to "DwebBrowser is asking you for \"location\" permission, and if you agree, it will be used to get location information"
    )
  }
}