/// <reference lib="dom"/>
declare global {
  interface Navigator {
    readonly userActivation: UserActivation;
  }
  interface UserActivation {
    /** [MDN Reference](https://developer.mozilla.org/docs/Web/API/UserActivation/hasBeenActive) */
    readonly hasBeenActive: boolean;
    /** [MDN Reference](https://developer.mozilla.org/docs/Web/API/UserActivation/hasBeenActive) */
    readonly isActive: boolean;
  }

  interface CloseWatcherKit {
    /**
     * 将一个url注册成token，在拦截到要打开此url的时候，覆盖原本open的行为，改成 创建一个 CloseWatcher
     * 基于 open 接口，原因是 CloseWatcher 的创建本身要基于浏览器的 UserActivation 中“消耗用户激活”的机制
     */
    registryToken(token: string): void;
    /**
     * 尝试关闭 CloseWatcher，会触发 close 事件
     */
    tryClose(id: string): void;
    /**
     * 尝试销毁 CloseWatcher，不会触发任何事件
     */
    tryDestroy(id: string): void;
    /**
     * 该对象由 web 侧负责写入，由 native 侧去触发事件
     */
    _watchers: Map<string, EventTarget>;
    /**
     * 该对象由 web 侧负责写入，由 native 侧去调用
     */
    _tasks: Map<string, (id: string) => void>;
  }

  interface CloseWatcherEventMap {
    close: CloseEvent;
    cancel: Event;
  }

  interface Window {
    __native_close_watcher_kit__: CloseWatcherKit;
    open(url: string): Window;

    CloseWatcher: typeof CloseWatcherShim;
  }
  const CloseWatcher: typeof CloseWatcherShim;
}
import type { default as CloseWatcherShim } from "./close-watcher.shim.ts";
