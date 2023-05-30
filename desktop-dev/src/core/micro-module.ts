import { fetchExtends } from "../helper/$makeFetchExtends.ts";
import { createSignal } from "../helper/createSignal.ts";
import { normalizeFetchArgs } from "../helper/normalizeFetchArgs.ts";
import { PromiseOut } from "../helper/PromiseOut.ts";
import type {
  $DWEB_DEEPLINK,
  $IpcSupportProtocols,
  $MicroModule,
  $MMID,
} from "../helper/types.ts";
import { nativeFetchAdaptersManager } from "../sys/dns/nativeFetch.ts";
import type { $BootstrapContext } from "./bootstrapContext.ts";
import type { Ipc } from "./ipc/index.ts";
export abstract class MicroModule implements $MicroModule {
  abstract ipc_support_protocols: $IpcSupportProtocols;
  abstract dweb_deeplinks: $DWEB_DEEPLINK[];
  abstract mmid: $MMID;
  protected abstract _bootstrap(context: $BootstrapContext): unknown;
  protected abstract _shutdown(): unknown;

  private _running_state_lock = PromiseOut.resolve(false);
  protected readonly _after_shutdown_signal = createSignal<() => unknown>();
  protected _ipcSet = new Set<Ipc>();
  /**
   * 内部程序与外部程序通讯的方法
   * TODO 这里应该是可以是多个
   */
  private readonly _connectSignal = createSignal<$OnIpcConnect>();
  get isRunning() {
    return this._running_state_lock.promise;
  }

  protected context?: $BootstrapContext;

  protected async before_bootstrap(context: $BootstrapContext) {
    if (await this._running_state_lock.promise) {
      throw new Error(`module ${this.mmid} alreay running`);
    }
    this._running_state_lock = new PromiseOut();
    this.context = context;
  }

  protected after_bootstrap(_context: $BootstrapContext) {
    this._running_state_lock.resolve(true);
  }

  async bootstrap(context: $BootstrapContext) {
    await this.before_bootstrap(context);
    try {
      await this._bootstrap(context);
    } finally {
      this.after_bootstrap(context);
    }
  }

  protected async before_shutdown() {
    if (false === (await this._running_state_lock.promise)) {
      throw new Error(`module ${this.mmid} already shutdown`);
    }
    this._running_state_lock = new PromiseOut();
    this.context = undefined;
  }

  protected after_shutdown() {
    this._after_shutdown_signal.emit();
    this._after_shutdown_signal.clear();
    this._running_state_lock.resolve(false);
  }

  async shutdown() {
    await this.before_shutdown();
    try {
      await this._shutdown();
    } finally {
      this.after_shutdown();
    }
  }

  /**
   * 给内部程序自己使用的 onConnect，外部与内部建立连接时使用
   * 因为 NativeMicroModule 的内部程序在这里编写代码，所以这里会提供 onConnect 方法
   * 如果时 JsMicroModule 这个 onConnect 就是写在 WebWorker 那边了
   */
  protected onConnect(cb: $OnIpcConnect) {
    return this._connectSignal.listen(cb);
  }

  beConnect(ipc: Ipc, reason: Request) {
    this._ipcSet.add(ipc);
    ipc.onClose(() => {
      this._ipcSet.delete(ipc);
    });
    this._connectSignal.emit(ipc, reason);
  }

  private async _nativeFetch(url: RequestInfo | URL, init?: RequestInit) {
    const args = normalizeFetchArgs(url, init);
    for (const adapter of nativeFetchAdaptersManager.adapters) {
      const response = await adapter(this, args.parsed_url, args.request_init);
      if (response !== undefined) {
        return response;
      }
    }
    return fetch(args.parsed_url, args.request_init);
  }

  nativeFetch(url: RequestInfo | URL, init?: RequestInit) {
    if (init?.body instanceof ReadableStream) {
      Reflect.set(init, "duplex", "half");
    }
    return Object.assign(this._nativeFetch(url, init), fetchExtends);
  }
}

type $OnIpcConnect = (ipc: Ipc, reason: Request) => unknown;
