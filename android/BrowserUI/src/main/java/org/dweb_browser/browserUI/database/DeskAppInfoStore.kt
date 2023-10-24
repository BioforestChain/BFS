package org.dweb_browser.browserUI.database

import android.content.Context
import android.graphics.Bitmap
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dweb_browser.browserUI.util.BitmapUtil
import org.dweb_browser.browserUI.util.BrowserUIApp
import org.dweb_browser.helper.ImageResource
import org.dweb_browser.helper.ioAsyncExceptionHandler
import org.dweb_browser.microservice.help.types.JmmAppInstallManifest
import org.dweb_browser.microservice.help.types.MMID

enum class AppType {
  MetaData, URL;

  fun createId() = "${this.name}${System.currentTimeMillis()}.dweb"
}

private const val DeskWebLinkStart = "file:///local_icons/"

fun createDeskWebLink(title: String, url: String, bitmap: Bitmap?): DeskWebLink {
  val imageResource = bitmap?.let {
    BitmapUtil.saveBitmapToIcons(it)?.let { src ->
      ImageResource(src = "$DeskWebLinkStart$src")
    }
  }
  return DeskWebLink(
    id = AppType.URL.createId(),
    title = title,
    url = url,
    icon = imageResource ?: ImageResource(src = "file:///sys/browser/web/logo.svg")
  )
}

@Serializable
data class DeskWebLink(
  val id: String,
  val title: String,
  val url: String,
  val icon: ImageResource
) {
  fun deleteIconFile() {
    if (icon.src.startsWith(DeskWebLinkStart)) {
      BitmapUtil.deleteIconsFile(icon.src.replaceFirst(DeskWebLinkStart, ""))
    }
  }
}

@Serializable
data class DeskAppInfo(
  val appType: AppType,
  val metadata: JmmAppInstallManifest? = null,
  val weblink: DeskWebLink? = null,
)

object DeskAppInfoStore {
  private const val PREFERENCE_NAME = "DeskAppInfo"
  private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = PREFERENCE_NAME)

  suspend fun saveAppInfo(mmid: MMID, metadata: JmmAppInstallManifest) =
    runBlocking(ioAsyncExceptionHandler) {
      // edit 函数需要在挂起环境中执行
      BrowserUIApp.Instance.appContext.dataStore.edit { pref ->
        pref[stringPreferencesKey("${AppType.MetaData}$mmid")] = Json.encodeToString(metadata)
      }
    }

  suspend fun saveWebLink(item: DeskWebLink) =
    runBlocking(ioAsyncExceptionHandler) {
      // edit 函数需要在挂起环境中执行
      BrowserUIApp.Instance.appContext.dataStore.edit { pref ->
        pref[stringPreferencesKey(item.id)] = Json.encodeToString(item)
      }
    }

  suspend fun getWebLinkByUrl(url: String) =
    BrowserUIApp.Instance.appContext.dataStore.data.map { pref ->
      var item: DeskWebLink? = null
      pref.asMap().forEach { (key, value) ->
        if (key.name.startsWith(AppType.URL.name)) {
          val data = Json.decodeFromString<DeskWebLink>(value as String)
          if (data.url == url) {
            item = data
            return@forEach
          }
        }
      }
      item
    }.first()

  suspend fun deleteDeskAppInfo(mmid: MMID) {
    BrowserUIApp.Instance.appContext.dataStore.edit { pref ->
      val name = if (mmid.startsWith(AppType.URL.name)) mmid else "${AppType.MetaData}$mmid"
      pref.remove(stringPreferencesKey(name))
    }
  }

  suspend fun queryDeskAppInfoList(): Flow<MutableList<DeskAppInfo>> {
    return BrowserUIApp.Instance.appContext.dataStore.data.catch { e ->  // Flow 中发生异常可使用这种方式捕获，catch 块是可选的
      if (e is IOException) {
        e.printStackTrace()
        emit(emptyPreferences())
      } else {
        throw e
      }
    }.map { pref ->
      val list = mutableListOf<DeskAppInfo>()
      pref.asMap().forEach { (key, value) ->
        val item = if (key.name.startsWith(AppType.MetaData.name)) {
          DeskAppInfo(
            AppType.MetaData,
            metadata = Json.decodeFromString<JmmAppInstallManifest>(value as String)
          )
        } else if (key.name.startsWith(AppType.URL.name)) {
          DeskAppInfo(AppType.URL, weblink = Json.decodeFromString<DeskWebLink>(value as String))
        } else {
          null
        }
        item?.let { list.add(it) }
      }
      list
    }
  }
}