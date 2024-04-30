import { Mutex } from "@dweb-browser/helper/Mutex.ts";
import { Producer } from "@dweb-browser/helper/Producer.ts";
import { PromiseOut } from "@dweb-browser/helper/PromiseOut.ts";
import { once } from "@dweb-browser/helper/decorator/$once.ts";
import { fetchExtends } from "@dweb-browser/helper/fetchExtends/index.ts";
import { mapHelper } from "@dweb-browser/helper/fun/mapHelper.ts";
import { setHelper } from "@dweb-browser/helper/fun/setHelper.ts";
import { logger } from "@dweb-browser/helper/logger.ts";
import { normalizeFetchArgs } from "@dweb-browser/helper/normalizeFetchArgs.ts";
import { promiseAsSignalListener } from "@dweb-browser/helper/promiseSignal.ts";
import type { $BootstrapContext } from "./bootstrapContext.ts";
import { $normalizeRequestInitAsIpcRequestArgs } from "./ipc/helper/ipcRequestHelper.ts";
import { type $IpcEvent, type Ipc } from "./ipc/index.ts";
import type { MICRO_MODULE_CATEGORY } from "./type/category.const.ts";
import type {
  $DWEB_DEEPLINK,
  $IpcSupportProtocols,
  $MMID,
  $MicroModuleManifest,
  $MicroModuleRuntime,
} from "./types.ts";

const enum MMState {
  BOOTSTRAP,
  SHUTDOWN,
}
export abstract class MicroModule {
  abstract manifest: $MicroModuleManifest;

  protected abstract createRuntime(context: $BootstrapContext): MicroModuleRuntime;

  @once()
  get console() {
    return logger(this);
  }
  toString() {
    return `MicroModule(${this.manifest.mmid})`;
  }

  get isRunning() {
    return this.#runtime?.isRunning === true;
  }

  #runtime?: MicroModuleRuntime;
  get runtime() {
    const runtime = this.#runtime;
    if (runtime === undefined) {
      throw new Error(`${this.manifest.mmid} is no running`);
    }
    return runtime;
  }
  async bootstrap(bootstrapContext: $BootstrapContext) {
    if (this.#runtime === undefined) {
      const runtime = this.createRuntime(bootstrapContext);
      runtime.onShutdown(() => {
        this.#runtime = undefined;
      });
      await runtime.bootstrap();
      this.#runtime = runtime;
    }
    return this.#runtime;
  }
}
export abstract class MicroModuleRuntime implements $MicroModuleRuntime {
  abstract mmid: $MMID;
  abstract ipc_support_protocols: $IpcSupportProtocols;
  abstract dweb_deeplinks: $DWEB_DEEPLINK[];
  abstract categories: MICRO_MODULE_CATEGORY[];
  abstract dir: $MicroModuleRuntime["dir"];
  abstract lang: $MicroModuleRuntime["lang"];
  abstract name: $MicroModuleRuntime["name"];
  abstract short_name: $MicroModuleRuntime["short_name"];
  abstract description: $MicroModuleRuntime["description"];
  abstract icons: $MicroModuleRuntime["icons"];
  abstract screenshots: $MicroModuleRuntime["screenshots"];
  abstract display: $MicroModuleRuntime["display"];
  abstract orientation: $MicroModuleRuntime["orientation"];
  abstract theme_color: $MicroModuleRuntime["theme_color"];
  abstract background_color: $MicroModuleRuntime["background_color"];
  abstract shortcuts: $MicroModuleRuntime["shortcuts"];
  abstract bootstrapContext: $BootstrapContext;
  abstract microModule: MicroModule;

  protected abstract _bootstrap(): unknown;
  protected abstract _shutdown(): unknown;

  private readonly stateLock = new Mutex();
  private state = MMState.SHUTDOWN;

  protected connectionLinks = new Set<Ipc>();
  get connectedIpcs() {
    return this.connectionLinks as ReadonlySet<Ipc>;
  }
  protected connectionMap = new Map<$MMID, PromiseOut<Ipc>>();

  @once()
  get console() {
    return this.microModule.console;
  }

  /**
   * 内部程序与外部程序通讯的方法
   * TODO 这里应该是可以是多个
   */
  readonly #ipcConnectedProducer = new Producer<Ipc>("ipcConnect");
  /**
   * 给内部程序自己使用的 onConnect，外部与内部建立连接时使用
   * 因为 NativeMicroModule 的内部程序在这里编写代码，所以这里会提供 onConnect 方法
   * 如果时 JsMicroModule 这个 onConnect 就是写在 WebWorker 那边了
   */
  readonly onConnect = this.#ipcConnectedProducer.consumer("for-internal");

  get isRunning() {
    return this.state === MMState.BOOTSTRAP;
  }

  bootstrap() {
    return this.stateLock.withLock(async () => {
      if (this.state != MMState.BOOTSTRAP) {
        this.console.debug("bootstrap-start");
        await this._bootstrap();
        this.console.debug("bootstrap-end");
      } else {
        this.console.debug("bootstrap", `${this.mmid} already running`);
      }
      this.state = MMState.BOOTSTRAP;
    });
  }

  @once()
  private get beforeShotdownPo() {
    return new PromiseOut();
  }
  @once()
  get onBeforeShutdown() {
    return promiseAsSignalListener(this.beforeShotdownPo.promise);
  }
  @once()
  private get shutdownPo() {
    return new PromiseOut();
  }
  // get awaitShutdown(){return  this.shutdownPo.promise}
  @once()
  get onShutdown() {
    return promiseAsSignalListener(this.shutdownPo.promise);
  }

  shutdown() {
    return this.stateLock.withLock(async () => {
      this.beforeShotdownPo.resolve(undefined);
      await this._shutdown();
      this.shutdownPo.resolve(undefined);
      this.#ipcConnectedProducer.close();
    });
  }

  /**
   * 尝试连接到指定对象
   */
  connect(mmid: $MMID, auto_start = true) {
    return mapHelper.getOrPut(this.connectionMap, mmid, () => {
      const po = new PromiseOut<Ipc>();
      po.resolve(
        (async () => {
          const ipc = await this.bootstrapContext.dns.connect(mmid);
          if (auto_start) {
            void ipc.start();
          }
          return ipc;
        })()
      );
      return po;
    }).promise;
  }

  /**
   * 收到一个连接，触发相关事件
   */

  // deno-lint-ignore require-await
  async beConnect(ipc: Ipc, _reason?: Request) {
    if (setHelper.add(this.connectionLinks, ipc)) {
      this.console.debug("beConnect", ipc);
      ipc.onFork("beConnect").collect(async (forkEvent) => {
        ipc.console.debug("onFork", forkEvent.data);
        await this.beConnect(forkEvent.consume());
      });
      this.onBeforeShutdown(() => {
        return ipc.close();
      });
      ipc.onClosed(() => {
        this.connectionLinks.delete(ipc);
      });

      // 尝试保存到双向连接索引中
      if (this.connectionMap.has(ipc.remote.mmid) === false) {
        this.connectionMap.set(ipc.remote.mmid, PromiseOut.resolve(ipc));
      }
      this.#ipcConnectedProducer.send(ipc);
    }
  }
  getConnected(mmid: $MMID) {
    return this.connectionMap.get(mmid)?.promise;
  }

  // private async _nativeFetch(url: RequestInfo | URL, init?: RequestInit) {
  //   const { parsed_url, request_init } = normalizeFetchArgs(url, init);
  //   if (parsed_url.protocol === "file:") {
  //     const ipc = this.connect(parsed_url.hostname as $MMID);
  //     const reasonRequest = buildRequestX(parsed_url, request_init);
  //     return (await (await ipc).request(parsed_url.href, reasonRequest)).toResponse();
  //   }
  //   return fetch(parsed_url, request_init);
  // }

  // nativeFetch(url: RequestInfo | URL, init?: RequestInit) {
  //   if (init?.body instanceof ReadableStream) {
  //     Reflect.set(init, "duplex", "half");
  //   }
  //   return Object.assign(this._nativeFetch(url, init), fetchExtends);
  // }

  // protected abstract _nativeRequest(parsed_url: URL, request_init: RequestInit): Promise<IpcResponse>;

  // /** 同 ipc.request，只不过使用 fetch 接口的输入参数 */
  // nativeRequest(url: RequestInfo | URL, init?: RequestInit) {
  //   const args = normalizeFetchArgs(url, init);
  //   return this._nativeRequest(args.parsed_url, args.request_init);
  // }

  protected async _getIpcForFetch(url: URL): Promise<Ipc | undefined> {
    return await this.connect(url.hostname as $MMID);
  }

  protected async _nativeRequest(parsed_url: URL, request_init: RequestInit) {
    if (parsed_url.protocol === "file:") {
      const ipc = await this._getIpcForFetch(parsed_url);
      if (ipc) {
        //  hostName.endsWith(".dweb")?await this._getIpcForFetch(parsed_url):this.fetch
        // if (hostName.endsWith(".dweb")) {
        // }
        // // const tmp = this._ipcConnectsMap.get(hostName as $MMID);
        //   // console.log("🧊 connect=> ", hostName, tmp?.is_finished, tmp);
        //   const ipc = await this.connect(hostName as $MMID);
        const ipc_req_init = await $normalizeRequestInitAsIpcRequestArgs(request_init);
        // console.log("🧊 connect request=> ", ipc.isActivity, ipc.channelId, parsed_url.href);
        let ipc_response = await ipc.request(parsed_url.href, ipc_req_init);
        // console.log("🧊 connect response => ", ipc_response.statusCode, ipc.isActivity, parsed_url.href);
        if (ipc_response.statusCode === 401) {
          /// 尝试进行授权请求
          try {
            const permissions = await ipc_response.body.text();
            if (permissions && (await this.requestDwebPermissions(permissions))) {
              /// 如果授权完全成功，那么重新进行请求
              ipc_response = await ipc.request(parsed_url.href, ipc_req_init);
            }
          } catch (e) {
            console.error("fail to request permission:", e);
          }
        }
        return ipc_response;
      }
    }
  }

  /**
   * 同 ipc.request，只不过使用 fetch 接口的输入参数
   * 与 nativeFetch 的差别在于
   * nativeFetch 返回 Response
   * nativeRequest 返回 IpcResponse
   */
  nativeRequest(url: RequestInfo | URL, init?: RequestInit) {
    const args = normalizeFetchArgs(url, init);
    const response = this._nativeRequest(args.parsed_url, args.request_init);
    if (!response) {
      throw new Error("fail to ipc-request");
    }
    return response;
  }

  protected async _nativeFetch(url: RequestInfo | URL, init?: RequestInit): Promise<Response> {
    const { parsed_url, request_init } = normalizeFetchArgs(url, init);
    const ipcResponse = await this._nativeRequest(parsed_url, request_init);
    if (ipcResponse) {
      return ipcResponse.toResponse(parsed_url.href);
    }
    return fetch(parsed_url, request_init);
  }
  /**
   * 模拟fetch的返回值
   * 这里的做fetch的时候需要先connect
   */
  nativeFetch(url: RequestInfo | URL, init?: RequestInit) {
    return Object.assign(this._nativeFetch(url, init), fetchExtends);
  }

  async requestDwebPermissions(permissions: string) {
    const res = await (
      await this.nativeFetch(
        new URL(`file://permission.std.dweb/request?permissions=${encodeURIComponent(permissions)}`)
      )
    ).text();
    const requestPermissionResult: Record<string, string> = JSON.parse(res);
    return Object.values(requestPermissionResult).every((status) => status === "granted");
  }

  @once()
  private get _manifest() {
    return {
      mmid: this.mmid,
      name: this.name,
      short_name: this.short_name,
      ipc_support_protocols: this.ipc_support_protocols,
      dweb_deeplinks: this.dweb_deeplinks,
      categories: this.categories,
      dir: this.dir,
      lang: this.lang,
      description: this.description,
      icons: this.icons,
      screenshots: this.screenshots,
      display: this.display,
      orientation: this.orientation,
      theme_color: this.theme_color,
      background_color: this.background_color,
      shortcuts: this.shortcuts,
    } satisfies $MicroModuleManifest;
  }
  toManifest() {
    return this._manifest;
  }
}
type $OnIpcConnect = (ipc: Ipc, reason: Request) => unknown;
type $OnActivity = (event: $IpcEvent, ipc: Ipc) => unknown;
type $OnRenderer = (event: $IpcEvent, ipc: Ipc) => unknown;
