import { cacheGetter } from "../../helper/cacheGetter.ts";
import { streamRead } from "../../helper/readableStreamHelper.ts";
import { toRequest } from "../../helper/request.ts";
import { BaseEvent, ListenerCallback, WindowListenerHandle } from "../base/BaseEvent.ts";
import { BasePlugin } from "../base/BasePlugin.ts";
import { configPlugin } from "../index.ts";
import { DwebWorkerEventMap, IpcRequest, UpdateControllerMap } from "./dweb-service-worker.type.ts";
import { dwebServiceWorkerPlugin } from "./dweb_service-worker.plugin.ts";
import { $FetchEventType, FetchEvent } from "./FetchEvent.ts";

declare namespace globalThis {
  const __app_upgrade_watcher_kit__: {
    /**
     * 该对象由 web 侧负责写入，由 native 侧去触发事件
     */
    // deno-lint-ignore no-explicit-any
    _listeners: { [eventName: string]: ListenerCallback<any>[] };
    _windowListeners: { [eventName: string]: WindowListenerHandle };
  };
}
// deno-lint-ignore no-explicit-any
(globalThis as any).__app_upgrade_watcher_kit__ = {};

const app_upgrade_watcher_kit = globalThis.__app_upgrade_watcher_kit__;

if (app_upgrade_watcher_kit) {
  app_upgrade_watcher_kit._listeners ??= {};
  app_upgrade_watcher_kit._windowListeners ??= {};
}

class DwebServiceWorker extends BaseEvent<keyof DwebWorkerEventMap> {
  plugin = dwebServiceWorkerPlugin;

  constructor() {
    super(app_upgrade_watcher_kit);
  }

  updateContoller = new UpdateController();

  @cacheGetter()
  get externalFetch() {
    return this.plugin.externalFetch;
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
  private decodeFetch = (ipcRequest: IpcRequest) => {
    return new FetchEvent("fetch", {
      request: toRequest(ipcRequest),
      clientId: ipcRequest.req_id.toString(),
    });
  };

  private async *registerEvent(eventName: $FetchEventType, options?: { signal?: AbortSignal }) {
    let pub = await BasePlugin.public_url;
    if (pub === "") {
      pub = await configPlugin.updatePublicUrl();
    }
    pub = pub.replace("X-Dweb-Host=api", "X-Dweb-Host=external");
    const X_Plaoc_Public_Url = new URL(location.href).searchParams.get("X-Plaoc-External-Url");
    const jsonlines = await this.plugin
      .buildExternalApiRequest(`/${X_Plaoc_Public_Url}`, {
        search: { mmid: this.plugin.mmid, action: "listen" },
        base: pub,
      })
      .fetch()
      .jsonlines(this.decodeFetch);
    for await (const onfetchString of streamRead(jsonlines, options)) {
      this.notifyListeners(eventName, onfetchString);
      yield onfetchString;
    }
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
  ): EventTarget {
    // 用户需要的时候再去注册
    if (eventName === "fetch") {
      (async () => {
        for await (const _info of this.registerEvent(eventName)) {
          // console.log("registerFetch", _info);
        }
      })();
    }
    return super.addEventListener(eventName, listenerFunc, options);
  }

  /**移除监听器 */
  override removeEventListener<K extends keyof DwebWorkerEventMap>(
    eventName: K,
    listenerFunc: ListenerCallback<DwebWorkerEventMap[K]>,
    options?: boolean | EventListenerOptions
  ) {
    return super.removeEventListener(eventName, listenerFunc, options);
  }
}

class UpdateController extends BaseEvent<keyof UpdateControllerMap> {
  constructor() {
    super(app_upgrade_watcher_kit);
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
  // @cacheGetter()
  // get progress() {
  //   return dwebServiceWorkerPlugin.update().progress
  // }

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
  ): EventTarget {
    return super.addEventListener(eventName, listenerFunc, options);
  }

  /**移除监听器 */
  override removeEventListener<K extends keyof UpdateControllerMap>(
    eventName: K,
    listenerFunc: ListenerCallback<UpdateControllerMap[K]>,
    options?: boolean | EventListenerOptions
  ) {
    return super.removeEventListener(eventName, listenerFunc, options);
  }
}

export const dwebServiceWorker = new DwebServiceWorker();

// deno-lint-ignore no-explicit-any
if (typeof (globalThis as any)["DwebServiceWorker"] === "undefined") {
  Object.assign(globalThis, { DwebServiceWorker });
}

// deno-lint-ignore no-explicit-any
if (typeof (globalThis as any)["UpdateController"] === "undefined") {
  Object.assign(globalThis, { UpdateController });
}
