package org.dweb_browser.sys.keychain

import kotlinx.coroutines.launch
import org.dweb_browser.core.module.MicroModule
import org.dweb_browser.helper.SuspendOnce
import org.dweb_browser.helper.base64String
import org.dweb_browser.helper.globalDefaultScope
import org.dweb_browser.helper.now
import org.dweb_browser.helper.platform.DeviceKeyValueStore
import org.dweb_browser.helper.utf8Binary
import org.dweb_browser.helper.utf8String
import java.security.KeyStore

internal const val ANDROID_KEY_STORE = "AndroidKeyStore"
internal val deviceKeyStore = DeviceKeyValueStore(ANDROID_KEY_STORE)
internal val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }


actual class KeychainStore actual constructor(val runtime: MicroModule.Runtime) {


  //  private val cryptKey = createKey()
  companion object {


    private val getOrRecoveryList = listOf(EncryptKeyV1.getOrRecoveryKey)
    private val currentGenerator = EncryptKeyV1.generateKey
    private suspend fun getOrRecoveryOrCreateKey(runtime: MicroModule.Runtime): EncryptKey {
      for (getter in getOrRecoveryList) {
        getter(runtime)?.also {
          return it
        }
      }
      return currentGenerator(runtime)
    }
  }

  private val encryptKey = SuspendOnce { getOrRecoveryOrCreateKey(runtime) }


  /**
   * 数据加密
   */
  suspend fun encryptData(remoteMmid: String, sourceData: ByteArray): ByteArray {
    val encryptKey = encryptKey()
    return encryptKey.encryptData(runtime, remoteMmid, sourceData)
  }

  /**
   * 数据解密
   */
  suspend fun decryptData(remoteMmid: String, encryptedBytes: ByteArray): ByteArray {
    val encryptKey = encryptKey()
    return encryptKey.decryptData(runtime, remoteMmid, encryptedBytes)
  }

  init {
    globalDefaultScope.launch {
      getItem("keychain.sys.dweb", "test-crypto")?.also { encryptBytes ->
        println("QAQ test-crypto/start: ${encryptBytes.base64String}")
        runCatching {
          println("QAQ test-crypto/load: ${decryptData(runtime.mmid, encryptBytes).utf8String}")
        }.getOrElse { err ->
          println(err.stackTraceToString().split('\n')
            .joinToString("\n") { "QAQ test-crypto/load-error: $it" })
        }
      }
      val data = "Time: ${now()}"
      println("QAQ test-crypto/save: $data")
      runCatching {
        val encryptBytes = encryptData(runtime.mmid, data.utf8Binary);
        setItem("keychain.sys.dweb", "test-crypto", encryptBytes)
        println("QAQ test-crypto/done: ${encryptBytes.base64String}")
      }.getOrElse { err ->
        println(err.stackTraceToString().split('\n')
          .joinToString("\n") { "QAQ test-crypto/save-error: $it" })
      }
    }
  }

  actual suspend fun getItem(remoteMmid: String, key: String): ByteArray? {
    val store = DeviceKeyValueStore(remoteMmid)
    return store.getRawItem(key.utf8Binary)
  }

  actual suspend fun setItem(remoteMmid: String, key: String, value: ByteArray): Boolean {
    val store = DeviceKeyValueStore(remoteMmid)
    return runCatching { store.setRawItem(key.utf8Binary, value);true }.getOrDefault(false)
  }

  actual suspend fun hasItem(remoteMmid: String, key: String): Boolean {
    val store = DeviceKeyValueStore(remoteMmid)
    return store.hasKey(key)
  }

  actual suspend fun deleteItem(remoteMmid: String, key: String): Boolean {
    val store = DeviceKeyValueStore(remoteMmid)
    return store.removeKey(key)
  }

  actual suspend fun supportEnumKeys(): Boolean {
    return true
  }

  actual suspend fun keys(remoteMmid: String): List<String> {
    val store = DeviceKeyValueStore(remoteMmid)
    return store.getKeys()
  }
}
