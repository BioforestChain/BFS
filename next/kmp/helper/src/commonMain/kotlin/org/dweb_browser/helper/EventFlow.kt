package org.dweb_browser.helper

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

typealias Callback<T> = suspend EventFlowController<T>.(args: T) -> Unit
typealias SimpleCallback = suspend EventFlowController<Unit>.(Unit) -> Unit


/** 控制器 */
class EventFlowController<T>(
  val args: T,
  val offListener: () -> Unit,
)

/**
 *scope 生命周期
 * isAwaitEmit 是否等待listen 全部触发完成才返回emit
 * tip 标识，用于调试
 */
open class EventFlow<T>(
  val scope: CoroutineScope,
  val tip: String = ""
) :
  SynchronizedObject() {
  //用于存储和发送事件
  private val eventEmitter = MutableSharedFlow<T>(
    replay = 0,//相当于粘性数据
    extraBufferCapacity = 0,//接受的慢时候，发送的入栈
    onBufferOverflow = BufferOverflow.SUSPEND // 缓冲区溢出的时候挂起 背压
  ) // 热流，在emit 之后去监听不会触发该新注册的监听

  // 管理所有的listen
  private val eventSet = mutableSetOf<Job>()

  // 等待全部的监听触发
  private var eventCollect = SafeInt(0)

  // 等待listen全部触发完成
  private var awaitEmit = CompletableDeferred<Unit>()

  suspend fun emitAndClear(event: T) {
    this.emit(event)
    this.clear()
  }

  suspend fun emit(event: T) {
    eventEmitter.emit(event)
    if (eventCollect.value > 0) {
//      println("🍄 emit-start $tip ${eventCollect.value}")
      awaitEmit.await()
//      println("🍄 emit-end  $tip ${eventCollect.value}")
    }
  }

  // 监听数据
  fun listen(collector: Callback<T>): EOffListener<T> = synchronized(this) {
    eventCollect++
    var job: Job? = null
    job = scope.launch(
      start = CoroutineStart.UNDISPATCHED
    ) {
      eventEmitter.collect {
        val ctx = EventFlowController(it) { job?.cancel() }
        collector.invoke(ctx, it)
        if (eventCollect.value > 0) {
          eventCollect--
          if (eventCollect.value == 0) {
            awaitEmit.complete(Unit)
          }
        }
      }
    }
    if (!job.isActive) {
      println("🍄 listen $tip $this isActive:${job.isActive} isCompleted:${job.isCompleted}")
    }
    eventSet.add(job)
    return EOffListener(this@EventFlow, job)
  }

  fun listenOnce(collector: Callback<T>) = synchronized(this) {
    var job: Job? = null
    job = scope.launch(
      start = CoroutineStart.UNDISPATCHED
    ) {
      eventEmitter.collect {
        val ctx = EventFlowController(it) { job?.cancel() }
        collector.invoke(ctx, it)
        job?.cancel()
      }
    }
  }

  fun toListener() = Listener(this)

  // 关闭某个listen事件
  internal fun off(job: Job) = synchronized(this) {
    eventSet.remove(job)
    job.cancel()
    if (eventCollect.value > 0) {
      eventCollect--
    }
  }

  fun clear() = synchronized(this) {
    eventSet.forEach {
      it.cancel()
    }
    eventSet.clear()
  }
}

class SimpleEventFlow(
  scope: CoroutineScope,
  tip: String = ""
) : EventFlow<Unit>(scope, tip) {
  suspend fun emit() {
    this.emit(Unit)
  }

  suspend fun emitAndClear() {
    this.emitAndClear(Unit)
  }
}

// 监听生成器
class Listener<Args>(private val eventFlow: EventFlow<Args>) {
  operator fun invoke(cb: Callback<Args>) = eventFlow.listen(cb)
}

// 返回关闭操作
class EOffListener<Args>(private val eventFlow: EventFlow<Args>, val job: Job) {
  operator fun invoke() = synchronized(eventFlow) { eventFlow.off(job) }

  /**
   * 触发自身的监听函数
   */
  suspend fun emitSelf(args: Args) = eventFlow.emit(args)
  fun removeWhen(listener: Listener<*>) = listener {
    this@EOffListener()
  }

  fun removeWhen(lifecycleScope: CoroutineScope) = lifecycleScope.launch {
    CompletableDeferred<Unit>().await()
  }.invokeOnCompletion {
    this@EOffListener()
  }
}

typealias Remover = () -> Boolean

fun Remover.removeWhen(listener: Signal.Listener<*>) = listener {
  this@removeWhen()
}

fun <T> Remover.removeWhen(listener: Listener<T>) = listener {
  this@removeWhen()
}

fun Remover.removeWhen(lifecycleScope: CoroutineScope) = lifecycleScope.launch {
  CompletableDeferred<Unit>().await()
}.invokeOnCompletion {
  this@removeWhen()
}
