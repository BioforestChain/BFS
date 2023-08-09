package org.dweb_browser.helper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

@OptIn(DelicateCoroutinesApi::class)
class ChangeableList<T>(context: CoroutineContext = ioAsyncExceptionHandler) :
  ArrayList<T>() {
  private val changeable = Changeable(this, context)
  val onChange = changeable.onChange
  suspend fun emitChange() = changeable.emitChange()

  override fun clear() {
    return super.clear().also { changeable.emitChangeSync() }
  }

  override fun addAll(elements: Collection<T>): Boolean {
    return super.addAll(elements).also { if (it) changeable.emitChangeSync() }
  }

  override fun addAll(index: Int, elements: Collection<T>): Boolean {
    return super.addAll(index, elements).also { if (it) changeable.emitChangeSync() }
  }

  override fun add(index: Int, element: T) {
    return super.add(index, element).also { changeable.emitChangeSync() }
  }

  override fun add(element: T): Boolean {
    return super.add(element).also { if (it) changeable.emitChangeSync() }
  }

  fun lastOrNull(): T? {
    return if (isEmpty()) null else this[size - 1]
  }

  override fun removeAt(index: Int): T {
    return super.removeAt(index).also { changeable.emitChangeSync() }
  }

  override fun set(index: Int, element: T): T {
    return super.set(index, element).also { changeable.emitChangeSync() }
  }

  override fun retainAll(elements: Collection<T>): Boolean {
    return super.retainAll(elements).also { if (it) changeable.emitChangeSync() }
  }

  override fun removeAll(elements: Collection<T>): Boolean {
    return super.removeAll(elements).also { if (it) changeable.emitChangeSync() }
  }

  override fun remove(element: T): Boolean {
    return super.remove(element).also { if (it) changeable.emitChangeSync() }
  }

  /** 重置 清空所有的事件监听，清空所有的数据  */
  fun reset() {
    changeable.signal.clear()
    this.clear()
  }
}