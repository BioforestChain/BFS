package org.dweb_browser.sys.keychain

import keychainstore.keychainDeleteItem
import keychainstore.keychainGetItem
import keychainstore.keychainHasItem
import keychainstore.keychainSetItem
import keychainstore.keychainSupportEnumKeys
import org.dweb_browser.core.help.types.MMID
import org.dweb_browser.core.module.MicroModule
import org.dweb_browser.helper.trueAlso

actual class KeychainStore actual constructor(val runtime: MicroModule.Runtime) {
  companion object {
    private val supportEnumKeys = keychainSupportEnumKeys()
    private val enumKeys = when {
      supportEnumKeys -> EnumKeys()
      else -> EnumKeysPolyfill()
    }
  }

  actual suspend fun getItem(remoteMmid: MMID, key: String): ByteArray? {
    return keychainGetItem("Dweb $remoteMmid", key)
  }

  actual suspend fun setItem(
    remoteMmid: MMID,
    key: String,
    value: ByteArray,
  ): Boolean {
    return keychainSetItem("Dweb $remoteMmid", key, value).trueAlso {
      enumKeys.addKey(remoteMmid, key)
    }
  }


  actual suspend fun hasItem(remoteMmid: MMID, key: String): Boolean {
    return keychainHasItem("Dweb $remoteMmid", key)
  }

  actual suspend fun deleteItem(remoteMmid: MMID, key: String): Boolean {
    return keychainDeleteItem("Dweb $remoteMmid", key).trueAlso {
      enumKeys.removeKey(remoteMmid, key)
    }
  }

  actual suspend fun keys(remoteMmid: MMID): List<String> {
    return enumKeys.getKeys(remoteMmid).toList()
  }

  actual suspend fun mmids(): List<MMID> {
    return enumKeys.getMmids()
  }
}