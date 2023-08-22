import { cacheGetter } from "../../helper/cacheGetter.ts";
import { ListenerCallback } from "../base/BaseEvent.ts";
import { ServiceWorkerFetchEvent } from "./FetchEvent.ts";
import { DwebWorkerEventMap, UpdateControllerMap } from "./dweb-service-worker.type.ts";
import { dwebServiceWorkerPlugin } from "./dweb_service-worker.plugin.ts";
class DwebServiceWorker extends EventTarget {
  plugin = dwebServiceWorkerPlugin;
  ws: WebSocket | undefined;
  constructor() {
    super();
    this.plugin.ipcPromise.then(ipc=>{
      ipc.onFetch((event) => {
        this.dispatchEvent(new ServiceWorkerFetchEvent(event))
      })
    })
  }

  updateContoller = new UpdateController();

  @cacheGetter()
  get externalFetch() {
    return this.plugin.externalFetch;
  }

  @cacheGetter()
  get canOpenUrl() {
    return this.plugin.canOpenUrl;
  }

  @cacheGetter()
  get update() {
    return this.updateContoller;
  }

  @cacheGetter()
  get close() {
    return this.plugin.close;
  }

  @cacheGetter()
  get restart() {
    return this.plugin.restart;
  }

  /**
   *  dwebview 注册一个监听事件
   * @param eventName
   * @param listenerFunc
   * @returns
   */
  override addEventListener<K extends keyof DwebWorkerEventMap>(
    eventName: K,
    listenerFunc: ListenerCallback<DwebWorkerEventMap[K]>,
    options?: boolean | AddEventListenerOptions
  ) {
    return super.addEventListener(eventName, listenerFunc as EventListenerOrEventListenerObject, options);
  }

  /**移除监听器 */
  override removeEventListener<K extends keyof DwebWorkerEventMap>(
    eventName: K,
    listenerFunc: ListenerCallback<DwebWorkerEventMap[K]>,
    options?: boolean | EventListenerOptions
  ) {
    return super.removeEventListener(eventName, listenerFunc as EventListenerOrEventListenerObject, options);
  }
}

class UpdateController extends EventTarget {
  constructor() {
    super();
  }

  @cacheGetter()
  get download() {
    return dwebServiceWorkerPlugin.update().download;
  }

  // 暂停
  @cacheGetter()
  get pause() {
    return dwebServiceWorkerPlugin.update().pause;
  }
  // 重下
  @cacheGetter()
  get resume() {
    return dwebServiceWorkerPlugin.update().resume;
  }
  // 取消
  @cacheGetter()
  get cancel() {
    return dwebServiceWorkerPlugin.update().cancel;
  }
  /**
   *  dwebview 注册一个监听事件
   * @param eventName
   * @param listenerFunc
   * @returns
   */
  override addEventListener<K extends keyof UpdateControllerMap>(
    eventName: K,
    listenerFunc: ListenerCallback<UpdateControllerMap[K]>,
    options?: boolean | AddEventListenerOptions
  ) {
    return super.addEventListener(eventName, listenerFunc as EventListenerOrEventListenerObject, options);
  }

  /**移除监听器 */
  override removeEventListener<K extends keyof UpdateControllerMap>(
    eventName: K,
    listenerFunc: ListenerCallback<UpdateControllerMap[K]>,
    options?: boolean | EventListenerOptions
  ) {
    return super.removeEventListener(eventName, listenerFunc as EventListenerOrEventListenerObject, options);
  }
}

export const dwebServiceWorker = new DwebServiceWorker();

// // deno-lint-ignore no-explicit-any
// if (typeof (globalThis as any)["DwebServiceWorker"] === "undefined") {
//   Object.assign(globalThis, { DwebServiceWorker });
// }

// // deno-lint-ignore no-explicit-any
// if (typeof (globalThis as any)["UpdateController"] === "undefined") {
//   Object.assign(globalThis, { UpdateController });
// }
