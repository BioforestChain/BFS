/// <reference lib="webworker"/>
/// 该文件是给 js-worker 用的，worker 中是纯粹的一个runtime，没有复杂的 import 功能，所以这里要极力克制使用外部包。
/// import 功能需要 chrome-80 才支持。我们明年再支持 import 吧，在此之前只能用 bundle 方案来解决问题
import type { $DWEB_DEEPLINK, $IpcSupportProtocols, $MicroModule, $MMID } from "../../../../core/types.ts";
import type { $RunMainConfig } from "../main/index.ts";

import { $normalizeRequestInitAsIpcRequestArgs } from "../../../../core/helper/ipcRequestHelper.ts";
import { $Callback, createSignal } from "../../../../helper/createSignal.ts";
import { fetchExtends } from "../../../../helper/fetchExtends/index.ts";
import { mapHelper } from "../../../../helper/mapHelper.ts";
import { normalizeFetchArgs } from "../../../../helper/normalizeFetchArgs.ts";
import { PromiseOut } from "../../../../helper/PromiseOut.ts";
import { updateUrlOrigin } from "../../../../helper/urlHelper.ts";

import * as core from "./std-dweb-core.ts";
import * as http from "./std-dweb-http.ts";

import {
  $OnIpcEventMessage,
  $OnIpcRequestMessage,
  Ipc,
  IPC_ROLE,
  IpcEvent,
  MessagePortIpc,
  MWEBVIEW_LIFECYCLE_EVENT,
} from "./std-dweb-core.ts";

declare global {
  type JsProcessMicroModuleContructor = JsProcessMicroModule;
  const JsProcessMicroModule: new (mmid: $MMID) => JsProcessMicroModuleContructor;

  interface DWebCore {
    jsProcess: JsProcessMicroModuleContructor;
    core: typeof core;
    ipc: typeof core;
    http: typeof http;
  }
  interface WorkerNavigator {
    readonly dweb: DWebCore;
  }
  interface Navigator {
    readonly dweb: DWebCore;
  }
}
const workerGlobal = self as DedicatedWorkerGlobalScope;

// import * as helper_createSignal from "../../helper/createSignal.ts";
// import * as helper_JsonlinesStream from "../../helper/stream/JsonlinesStream.ts";
// import * as helper_PromiseOut from "../../helper/PromiseOut.ts";
// import * as helper_readableStreamHelper from "../../helper/stream/readableStreamHelper.ts";

export class Metadata<T extends $Metadata = $Metadata> {
  constructor(readonly data: T, readonly env: Record<string, string>) {}
  envString(key: string) {
    const val = this.envStringOrNull(key);
    if (val == null) {
      throw new Error(`no found (string) ${key}`);
    }
    return val;
  }
  envStringOrNull(key: string) {
    const val = this.env[key];
    if (val == null) {
      return;
    }
    return val;
  }
  envBoolean(key: string) {
    const val = this.envBooleanOrNull(key);
    if (val == null) {
      throw new Error(`no found (boolean) ${key}`);
    }
    return val;
  }
  envBooleanOrNull(key: string) {
    const val = this.envStringOrNull(key);
    if (val == null) {
      return;
    }
    return val === "true";
  }
}

type $Metadata = {
  mmid: $MMID;
};

// const js_process_ipc_support_protocols =

/// 这个文件是给所有的 js-worker 用的，所以会重写全局的 fetch 函数，思路与 dns 模块一致
/// 如果是在原生的系统中，不需要重写fetch函数，因为底层那边可以直接捕捉 fetch
/// 虽然 nwjs 可以通过 chrome.webRequest 来捕捉请求，但没法自定义相应内容
/// 所以这里的方案还是对 fetch 进行重写
/// 拦截到的 ipc-message 通过 postMessage 转发到 html 层，再有 html 层

/**
 * 这个是虚假的 $MicroModule，这里只是一个影子，指代 native 那边的 micro_module
 */
export class JsProcessMicroModule implements $MicroModule {
  readonly ipc_support_protocols = (() => {
    const protocols = this.meta.envStringOrNull("ipc-support-protocols")?.split(/[\s\,]+/) ?? [];
    return {
      raw: protocols.includes("raw"),
      cbor: protocols.includes("cbor"),
      protobuf: protocols.includes("protobuf"),
    } satisfies $IpcSupportProtocols;
  })();
  readonly mmid: $MMID;
  readonly name: string;
  readonly host: string;
  readonly dweb_deeplinks: $DWEB_DEEPLINK[] = [];
  readonly categories: $MicroModule["categories"] = [];

  constructor(readonly meta: Metadata, private nativeFetchPort: MessagePort) {
    const _beConnect = (event: MessageEvent) => {
      const data = event.data;
      if (Array.isArray(data) === false) {
        return;
      }
      if (data[0] === "ipc-connect") {
        const mmid = data[1];
        const port = event.ports[0];
        const env = JSON.parse(data[2] ?? "{}");
        const protocols = env["ipc-support-protocols"] ?? "";
        const ipc_support_protocols = {
          raw: protocols.includes("raw"),
          cbor: protocols.includes("cbor"),
          protobuf: protocols.includes("protobuf"),
        } satisfies $IpcSupportProtocols;
        let rote = IPC_ROLE.CLIENT as IPC_ROLE;
        const port_po = mapHelper.getOrPut(this._ipcConnectsMap, mmid, () => {
          rote = IPC_ROLE.SERVER;
          return new PromiseOut<Ipc>();
        });
        const ipc = new MessagePortIpc(
          port,
          {
            mmid,
            ipc_support_protocols,
            dweb_deeplinks: [],
            categories: [],
            name: this.name,
          },
          rote
        );
        port_po.resolve(ipc);
        workerGlobal.postMessage(["ipc-connect-ready", mmid]);
        /// 不论是连接方，还是被连接方，都需要触发事件
        this.beConnect(ipc);
      } else if (data[0] === "ipc-connect-fail") {
        const mmid = data[1];
        const reason = data[2];
        this._ipcConnectsMap.get(mmid)?.reject(reason);
      }
    };
    workerGlobal.addEventListener("message", _beConnect);

    this.mmid = meta.data.mmid;
    this.name = `js process of ${this.mmid}`;
    this.host = this.meta.envString("host");
    this.fetchIpc = new MessagePortIpc(this.nativeFetchPort, this, IPC_ROLE.SERVER);
  }

  /// 这个通道只能用于基础的通讯
  readonly fetchIpc: MessagePortIpc;

  private async _nativeFetch(url: RequestInfo | URL, init?: RequestInit): Promise<Response> {
    const args = normalizeFetchArgs(url, init);
    const hostName = args.parsed_url.hostname;
    if (!(hostName.endsWith(".dweb") && args.parsed_url.protocol === "file:")) {
      const ipc_response = await this._nativeRequest(args.parsed_url, args.request_init);
      return ipc_response.toResponse(args.parsed_url.href);
    }
    const ipc = await this.connect(hostName as $MMID);
    const ipc_req_init = await $normalizeRequestInitAsIpcRequestArgs(args.request_init);
    const ipc_response = await ipc.request(args.parsed_url.href, ipc_req_init);
    return ipc_response.toResponse(args.parsed_url.href);
  }

  /**
   * 模拟fetch的返回值
   * 这里的做fetch的时候需要先connect
   */
  nativeFetch(url: RequestInfo | URL, init?: RequestInit) {
    return Object.assign(this._nativeFetch(url, init), fetchExtends);
  }

  private async _nativeRequest(parsed_url: URL, request_init: RequestInit) {
    const ipc_req_init = await $normalizeRequestInitAsIpcRequestArgs(request_init);
    return await this.fetchIpc.request(parsed_url.href, ipc_req_init);
  }

  /** 同 ipc.request，只不过使用 fetch 接口的输入参数 */
  nativeRequest(url: RequestInfo | URL, init?: RequestInit) {
    const args = normalizeFetchArgs(url, init);
    return this._nativeRequest(args.parsed_url, args.request_init);
  }

  /**重启 */
  restart() {
    this.fetchIpc.postMessage(IpcEvent.fromText("restart", "")); // 发送指令
  }
  // 激活信号
  private _activitySignal = createSignal<$OnIpcEventMessage>();
  // app关闭信号
  private _onCloseSignal = createSignal<$OnIpcEventMessage>();
  // 外部request信号
  private _onRequestSignal = createSignal<$OnIpcRequestMessage>();
  private _on_activity_inited = false;
  onActivity(cb: $OnIpcEventMessage) {
    if (this._on_activity_inited === false) {
      this._on_activity_inited = true;
      this.onConnect((ipc) => {
        ipc.onEvent((ipcEvent, ipc) => {
          console.log("js-process.worker onEvent=> ", ipcEvent.name);
          if (ipcEvent.name === MWEBVIEW_LIFECYCLE_EVENT.Activity) {
            return this._activitySignal.emit(ipcEvent, ipc);
          }
          if (ipcEvent.name === MWEBVIEW_LIFECYCLE_EVENT.Close) {
            return this._onCloseSignal.emit(ipcEvent, ipc);
          }
        });
        ipc.onRequest((ipcRequest, ipc) => this._onRequestSignal.emit(ipcRequest, ipc));
      });
    }
    return this._activitySignal.listen(cb);
  }

  onRequest(request: $OnIpcRequestMessage) {
    return this._onRequestSignal.listen(request);
  }

  onClose(cb: $OnIpcEventMessage) {
    return this._onCloseSignal.listen(cb);
  }

  private _ipcConnectsMap = new Map<$MMID, PromiseOut<Ipc>>();
  connect(mmid: $MMID) {
    return mapHelper.getOrPut(this._ipcConnectsMap, mmid, () => {
      const ipc_po = new PromiseOut<Ipc>();
      // 发送指令
      this.fetchIpc.postMessage(IpcEvent.fromText("dns/connect", JSON.stringify({ mmid })));
      ipc_po.onSuccess((ipc) => {
        ipc.onClose(() => {
          this._ipcConnectsMap.delete(ipc.remote.mmid);
        });
      });
      return ipc_po;
    }).promise;
  }

  private _ipcSet = new Set<Ipc>();
  addToIpcSet(ipc: Ipc) {
    this._ipcSet.add(ipc);
    ipc.onClose(() => {
      this._ipcSet.delete(ipc);
    });
  }

  private _connectSignal = createSignal<$Callback<[Ipc]>>(false);
  beConnect(ipc: Ipc) {
    this.addToIpcSet(ipc);
    this._connectSignal.emit(ipc);
  }
  onConnect(cb: $Callback<[Ipc]>) {
    return this._connectSignal.listen(cb);
  }
}

/// 消息通道构造器
const waitFetchPort = () => {
  return new Promise<MessagePort>((resolve) => {
    workerGlobal.addEventListener("message", function onFetchIpcChannel(event) {
      const data = event.data;
      if (Array.isArray(event.data) === false) {
        return;
      }
      /// 这是来自 原生接口 WebMessageChannel 创建出来的通道
      /// 由 web 主线程代理传递过来
      if (data[0] === "fetch-ipc-channel") {
        resolve(data[1]);
        workerGlobal.removeEventListener("message", onFetchIpcChannel);
      }
    });
  });
};

/**
 * 安装上下文
 */
export const installEnv = async (metadata: Metadata) => {
  const jsProcess = new JsProcessMicroModule(metadata, await waitFetchPort());

  const dweb = {
    jsProcess,
    core,
    ipc: core,
    http,
  } satisfies DWebCore;
  // Object.assign(globalThis, dweb);
  Object.assign(navigator, { dweb });
  /// 安装完成，告知外部
  workerGlobal.postMessage(["env-ready"]);

  workerGlobal.addEventListener("message", async function runMain(event) {
    const data = event.data;
    if (Array.isArray(event.data) === false) {
      return;
    }
    if (data[0] === "run-main") {
      const config = data[1] as $RunMainConfig;
      const main_parsed_url = updateUrlOrigin(config.main_url, `http://${jsProcess.host}`);
      const location = {
        hash: main_parsed_url.hash,
        host: main_parsed_url.host,
        hostname: main_parsed_url.hostname,
        href: main_parsed_url.href,
        origin: main_parsed_url.origin,
        pathname: main_parsed_url.pathname,
        port: main_parsed_url.port,
        protocol: main_parsed_url.protocol,
        search: main_parsed_url.search,
        toString() {
          return main_parsed_url.href;
        },
      };
      Object.setPrototypeOf(location, WorkerLocation.prototype);
      Object.freeze(location);

      Object.defineProperty(workerGlobal, "location", {
        value: location,
        configurable: false,
        enumerable: false,
        writable: false,
      });

      await import(config.main_url);
      workerGlobal.removeEventListener("message", runMain);
    }
  });
  return jsProcess;
};
