/// <reference lib="dom"/>
import { PromiseOut } from "../../helper/PromiseOut.ts";
import "./close-watcher.type.ts";

let native_close_watcher_kit = self.__native_close_watcher_kit__;

if (native_close_watcher_kit) {
  native_close_watcher_kit._watchers ??= new Map();
  native_close_watcher_kit._tasks ??= new Map();
} else if ("webkit" in globalThis) {
  // 仅适用于iOS平台
  Object.assign(globalThis, {
    __native_close_watcher_kit__: {
      registryToken(token: string) {
        try {
          // deno-lint-ignore no-explicit-any
          (globalThis as any).webkit.messageHandlers.closeWatcher.postMessage({
            token,
          });
        } catch {
          // 非iOS平台才有可能会触发
        }
      },

      tryClose(id: string) {
        try {
          // deno-lint-ignore no-explicit-any
          (globalThis as any).webkit.messageHandlers.closeWatcher.postMessage({
            id,
          });
        } catch {
          // 非iOS平台才有可能会触发
        }
      },
      _watchers: new Map(),
      _tasks: new Map(),
    },
  });
  native_close_watcher_kit = self.__native_close_watcher_kit__;
} else {
  /// 桌面 平台使用 esc 按钮作为返回键
  const consuming = new Set<string>();
  const watchers = new Array<Watcher>();
  class Watcher {
    static #acc_id = 0;
    private _destoryed = false;
    readonly id = `#cw-${Watcher.#acc_id++}`;
    tryClose() {
      if (this._destoryed) {
        return false;
      }
      native_close_watcher_kit._watchers.get(this.id)?.dispatchEvent(new CloseEvent("close"));
      this._destoryed = true;
      return true;
    }
  }
  const closeWatcherController = new (class CloseWatcher {
    /**
     * 申请一个 CloseWatcher
     */
    apply(isUserGesture: boolean) {
      if (isUserGesture || watchers.length === 0) {
        const watcher = new Watcher();
        watchers.push(watcher);
      }
      return watchers.at(-1)!;
    }
    resolveToken(consumeToken: string, watcher: Watcher) {
      native_close_watcher_kit._tasks.get(consumeToken)?.(watcher.id);
    }
    get canClose() {
      return watchers.length > 0;
    }
    close(watcher = watchers.at(-1)) {
      if (watcher?.tryClose()) {
        const index = watchers.indexOf(watcher);
        if (index !== -1) {
          watchers.splice(index, 1);
          return true;
        }
      }
      return false;
    }
  })();
  // @ts-ignore
  if (typeof navigation === "object") {
    // @ts-ignore
    navigation.addEventListener("navigate", (event) => {
      if (closeWatcherController.canClose) {
        event.intercept({
          async hanlder() {
            closeWatcherController.close();
          },
        });
      }
    });
  }
  if (typeof document === "object")
    document.addEventListener("keypress", (event) => {
      if (event.key === "Escape") {
        console.log("Esc键被按下");
        closeWatcherController.close();
      }
    });

  Object.assign(globalThis, {
    __native_close_watcher_kit__: {
      async registryToken(token: string) {
        consuming.add(token);
        /// 模拟移动端的 open(token)
        if ((await (await fetch(token)).text()) === "create-close-watcher") {
          const watcher = closeWatcherController.apply(navigator.userActivation.isActive);
          closeWatcherController.resolveToken(token, watcher);
        }
      },

      tryClose(id: string) {
        for (const w of watchers.slice()) {
          if (w.id === id) {
            closeWatcherController.close(w);
          }
        }
      },
      _watchers: new Map(),
      _tasks: new Map(),
    },
  });
  native_close_watcher_kit = self.__native_close_watcher_kit__;
}
export class CloseWatcherShim extends EventTarget {
  constructor() {
    super();
    void this.#init();
  }
  #id = new PromiseOut<string>();
  async #init() {
    const token = URL.createObjectURL(new Blob(["create-close-watcher"], { type: "text/html" }));

    const native_close_watcher_kit = self.__native_close_watcher_kit__;
    const tasks = native_close_watcher_kit._tasks;
    const po = this.#id;
    // 注册回调
    tasks.set(token, po.resolve);
    // 注册指令，如果在移动端，会发起 self.open(token) ，从而获得
    native_close_watcher_kit.registryToken(token);

    // 等待响应
    const id = await po.promise;
    // 等到响应，删除注册的回掉
    tasks.delete(token);
    // 注册实例
    native_close_watcher_kit._watchers.set(id, this);
    console.log("close watcher created");

    this.addEventListener(
      "close",
      (event) => {
        console.log("close watcher closed");
        this.#onclose?.(event);
        native_close_watcher_kit._watchers.delete(id);
        this._closed = true;
      },
      { once: true }
    );
  }
  private _closed = false;
  async close() {
    if (this._closed) {
      return;
    }
    const id = await this.#id.promise;
    const native_close_watcher_kit = self.__native_close_watcher_kit__;
    native_close_watcher_kit.tryClose(id);
  }
  #onclose?: (event: CloseEvent) => void;
  public get onclose() {
    return this.#onclose ?? null;
  }
  public set onclose(value) {
    if (typeof value === "function") {
      this.#onclose = value;
    } else {
      this.#onclose = undefined;
    }
  }
  override addEventListener<K extends keyof CloseWatcherEventMap>(
    type: K,
    // deno-lint-ignore no-explicit-any
    listener: (this: CloseWatcherShim, ev: CloseWatcherEventMap[K]) => any,
    options?: boolean | AddEventListenerOptions
  ): void;
  override addEventListener(
    type: string,
    listener: EventListenerOrEventListenerObject,
    options?: boolean | AddEventListenerOptions
  ): void;
  override addEventListener() {
    // deno-lint-ignore no-explicit-any
    return (super.addEventListener as any)(...arguments);
  }
  override removeEventListener<K extends keyof CloseWatcherEventMap>(
    type: K,
    // deno-lint-ignore no-explicit-any
    listener: (this: CloseWatcherShim, ev: CloseWatcherEventMap[K]) => any,
    options?: boolean | EventListenerOptions
  ): void;
  override removeEventListener(
    type: string,
    listener: EventListenerOrEventListenerObject,
    options?: boolean | EventListenerOptions
  ): void;
  override removeEventListener() {
    // deno-lint-ignore no-explicit-any
    return (super.addEventListener as any)(...arguments);
  }
}
interface CloseWatcherEventMap {
  close: CloseEvent;
}

if (typeof self.CloseWatcher === "undefined") {
  Object.assign(self, { CloseWatcher: CloseWatcherShim });
}

export const CloseWatcher = self.CloseWatcher;
