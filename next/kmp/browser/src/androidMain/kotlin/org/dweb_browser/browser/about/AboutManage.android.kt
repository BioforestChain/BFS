package org.dweb_browser.browser.about

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.dweb_browser.helper.UUID
import org.dweb_browser.helper.compose.hex
import org.dweb_browser.sys.device.DeviceManage
import org.dweb_browser.sys.device.model.Battery
import org.dweb_browser.sys.device.model.DeviceData
import org.dweb_browser.sys.device.model.DeviceInfo
import org.dweb_browser.sys.window.core.windowAdapterManager
import org.dweb_browser.sys.window.render.LocalWindowControllerTheme

data class AndroidSystemInfo(
  val os: String = "Android",
  val osVersion: String,
//  val deviceName: String,
  val sdkInt: Int
)

actual suspend fun AboutNMM.AboutRuntime.openAboutPage(id: UUID) {
  val deviceData = DeviceInfo.deviceData
  val batteryInfo = DeviceInfo.getBatteryInfo()
  val androidSystemInfo = AndroidSystemInfo(
    osVersion = DeviceInfo.osVersion,
//    deviceName = deviceData.deviceName,
    sdkInt = DeviceInfo.sdkInt
  )
  val appInfo = AboutAppInfo(
    appVersion = DeviceManage.deviceAppVersion(),
    webviewVersion = WebView.getCurrentWebViewPackage()?.versionName ?: "Unknown"
  )
  windowAdapterManager.provideRender(id) { modifier ->
    AboutRender(
      modifier = modifier,
      appInfo = appInfo,
      androidSystemInfo = androidSystemInfo,
      deviceData = deviceData,
      batteryInfo = batteryInfo
    )
  }
}

@Composable
fun AboutRender(
  modifier: Modifier,
  appInfo: AboutAppInfo,
  androidSystemInfo: AndroidSystemInfo,
  deviceData: DeviceData,
  batteryInfo: Battery,
) {
  Box(
    modifier = modifier.fillMaxSize()
      .background(
        if (LocalWindowControllerTheme.current.isDark) Color.Black else (Color.hex("#F5F5FA")
          ?: Color.Gray)
      )
  ) {
    Column(
      modifier = Modifier.verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.Start,
      verticalArrangement = Arrangement.Top
    ) {
      Text(
        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        text = AboutI18nResource.app.text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      AboutAppInfoRender(appInfo)
      Spacer(Modifier.padding(8.dp))
      Text(
        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        text = AboutI18nResource.system.text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().background(
          color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(8.dp)
        )
      ) {
        AboutDetailsItem(
          labelName = AboutI18nResource.os.text, text = androidSystemInfo.os
        )
        AboutDetailsItem(
          labelName = AboutI18nResource.osVersion.text, text = androidSystemInfo.osVersion
        )
        AboutDetailsItem(
          labelName = AboutI18nResource.sdkInt.text, text = androidSystemInfo.sdkInt.toString()
        )
//        AboutDetailsItem(
//          labelName = AboutI18nResource.deviceName.text, text = androidSystemInfo.deviceName
//        )
      }
      Spacer(Modifier.padding(8.dp))
      Text(
        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        text = AboutI18nResource.hardware.text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().background(
          color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(8.dp)
        )
      ) {
        AboutDetailsItem(
          labelName = AboutI18nResource.brand.text, text = deviceData.brand
        )
        AboutDetailsItem(
          labelName = AboutI18nResource.modelName.text, text = deviceData.deviceModel
        )
        AboutDetailsItem(
          labelName = AboutI18nResource.hardware.text, text = deviceData.hardware
        )
        AboutDetailsItem(
          labelName = AboutI18nResource.supportAbis.text, text = deviceData.supportAbis
        )
        AboutDetailsItem(
          labelName = "ID", text = deviceData.id
        )
        AboutDetailsItem(
          labelName = "DISPLAY", text = deviceData.display
        )
        AboutDetailsItem(
          labelName = "BOARD", text = deviceData.board
        )
        AboutDetailsItem(
          labelName = AboutI18nResource.manufacturer.text, text = deviceData.manufacturer
        )
        AboutDetailsItem(
          labelName = AboutI18nResource.display.text, text = deviceData.screenSizeInches
        )
        AboutDetailsItem(
          labelName = AboutI18nResource.resolution.text, text = deviceData.resolution
        )
        AboutDetailsItem(
          labelName = AboutI18nResource.density.text, text = deviceData.density
        )
        AboutDetailsItem(
          labelName = AboutI18nResource.refreshRate.text, text = deviceData.refreshRate
        )
        AboutDetailsItem(
          labelName = AboutI18nResource.memory.text,
          text = "${deviceData.memory!!.usage}/${deviceData.memory!!.total}"
        )
        AboutDetailsItem(
          labelName = AboutI18nResource.storage.text,
          text = "${deviceData.storage!!.internalUsageSize}/${deviceData.storage!!.internalTotalSize}"
        )
      }
      Spacer(Modifier.padding(8.dp))
      Text(
        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        text = AboutI18nResource.battery.text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().background(
          color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(8.dp)
        )
      ) {
        AboutDetailsItem(
          labelName = AboutI18nResource.status.text,
          text = if (batteryInfo.isPhoneCharging) AboutI18nResource.charging.text else AboutI18nResource.discharging.text
        )
        AboutDetailsItem(
          labelName = AboutI18nResource.health.text,
          text = batteryInfo.batteryHealth ?: "Unknown"
        )
        AboutDetailsItem(
          labelName = AboutI18nResource.percent.text,
          text = "${batteryInfo.batteryPercent}%"
        )
      }
    }
  }
}