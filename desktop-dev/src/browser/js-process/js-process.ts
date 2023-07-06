import { transfer, type Remote } from "comlink";
import { $ReqMatcher, $isMatchReq } from "../../core/helper/$ReqMatcher.ts";
import type { $PromiseMaybe } from "../../core/helper/types.ts";
import { MessagePortIpc } from "../../core/ipc-web/MessagePortIpc.ts";
import { ReadableStreamIpc } from "../../core/ipc-web/ReadableStreamIpc.ts";
import { IpcHeaders } from "../../core/ipc/IpcHeaders.ts";
import {
  IPC_ROLE,
  Ipc,
  IpcRequest,
  IpcResponse,
} from "../../core/ipc/index.ts";
import { NativeMicroModule } from "../../core/micro-module.native.ts";
import { $MMID } from "../../core/types.ts";
import { once } from "../../helper/$once.ts";
import { PromiseOut } from "../../helper/PromiseOut.ts";
import { mapHelper } from "../../helper/mapHelper.ts";
import { parseQuery, z, zq } from "../../helper/zodHelper.ts";
import {
  closeHttpDwebServer,
  createHttpDwebServer,
} from "../../std/http/helper/$createHttpDwebServer.ts";
import { saveNative2JsIpcPort } from "./ipc.native2js.ts";
import type { $NWW } from "./js-process.openWindow.ts";
import { jsProcessOpenWindow } from "./js-process.openWindow.ts";

type $APIS = typeof import("./assets/js-process.web.ts")["APIS"];

class ImportLinker {
  constructor(
    readonly origin: string,
    /** 我们将托管用户的代码，响应虚拟环境中的 import 请求 */
    readonly importMaps: Array<{
      pathMatcher: $ReqMatcher;
      handler: (parsed_url: URL) => $PromiseMaybe<$Code>;
    }> = []
  ) {}

  link(url: string) {
    const parsed_url = new URL(url, this.origin);
    for (const item of this.importMaps) {
      if ($isMatchReq(item.pathMatcher, parsed_url.pathname)) {
        return item.handler(parsed_url);
      }
    }
  }
}

interface $Code {
  mime: string;
  data: Uint8Array | string;
}

/** 响应错误信息 */
const _ipcErrorResponse = (
  requestMessage: IpcRequest,
  ipc: Ipc,
  statusCode: number,
  errorMessage: string
) => {
  const headers = new IpcHeaders(CORS_HEADERS);
  ipc.postMessage(
    IpcResponse.fromText(
      requestMessage.req_id,
      statusCode,
      headers,
      errorMessage,
      ipc
    )
  );
};

/** 响应实体内容 */
const _ipcSuccessResponse = (
  requestMessage: IpcRequest,
  ipc: Ipc,
  code: $Code
) => {
  const headers = new IpcHeaders(CORS_HEADERS);
  headers.set("Content-Type", code.mime);
  ipc.postMessage(
    typeof code.data === "string"
      ? IpcResponse.fromText(
          requestMessage.req_id,
          200,
          headers,
          code.data,
          ipc
        )
      : IpcResponse.fromBinary(
          requestMessage.req_id,
          200,
          headers,
          code.data,
          ipc
        )
  );
};

/** 基于 importLinker 响应内容 */
const _ipcResponseFromImportLinker = async (
  ipc: Ipc,
  importLinker: ImportLinker,
  request: IpcRequest
) => {
  const code = await importLinker.link(request.url);
  if (code === undefined) {
    _ipcErrorResponse(request, ipc, 404, "// No Found");
  } else {
    _ipcSuccessResponse(request, ipc, code);
  }
};
const CORS_HEADERS = [
  ["Content-Type", "text/javascript"],
  ["Access-Control-Allow-Origin", "*"],
  ["Access-Control-Allow-Headers", "*"], // 要支持 X-Dweb-Host
  ["Access-Control-Allow-Methods", "*"],
] satisfies HeadersInit;

/**
 * 将指定的js运行在后台的一个管理器，
 * 注意它们共享一个域，所以要么就关闭
 *
 * 功能：
 * 用来创建 woker.js 线程
 * 用来中转  woker.js 同匹配的 JsMicroModule 通信
 */
export class JsProcessNMM extends NativeMicroModule {
  override mmid = `js.browser.dweb` as const;

  private JS_PROCESS_WORKER_CODE = once(() => {
    return this.nativeFetch(
      "file:///sys/browser/js-process/worker-thread/js-process.worker.js"
    ).text();
  });

  private INTERNAL_PATH = encodeURI("/<internal>");

  async _bootstrap() {
    const mainServer = await createHttpDwebServer(this, {});
    {
      const serverIpc = await mainServer.listen();
      serverIpc.onFetch(async (event) => {
        const { pathname } = event.url;
        /// TODO：这里应该使用更好的设计
        if (pathname.endsWith("/bootstrap.js")) {
          return new Response(await this.JS_PROCESS_WORKER_CODE(), {
            headers: {
              "Content-Type": "text/javascript,charset=utf8",
            },
            status: 200,
          });
        }
        return await this.nativeFetch(
          "file:///sys/browser/js-process/main-thread" + pathname
        );
      });
    }

    const bootstrap_url = mainServer.startResult.urlInfo.buildInternalUrl(
      (url) => {
        url.pathname = `${this.INTERNAL_PATH}/bootstrap.js`;
      }
    ).href;

    this._after_shutdown_signal.listen(mainServer.close);

    let nww: $NWW | undefined;
    // 从 渲染进程的 主线程中获取到 暴露的 apis
    const apis = await (async () => {
      const urlInfo = mainServer.startResult.urlInfo;
      nww = await jsProcessOpenWindow(
        // 如果下面的 show === false 那么这个窗口是不会出现的
        mainServer.startResult.urlInfo.buildInternalUrl((url) => {
          url.pathname = "/index.html";
        }).href,
        {
          // 是否需要显示 js-process 的窗口
          show: true, //process.argv.includes("--inspect") ? true : false, // require.main?.filename.endsWith(".html"),
        },
        { userAgent: (userAgent) => userAgent + ` dweb-host/${urlInfo.host}` }
      );
      this._after_shutdown_signal.listen(() => {
        nww!.close();
      });
      return nww.getApis<$APIS>();
    })();

    const ipcProcessIdMap = new Map<$MMID, Map<string, PromiseOut<number>>>();
    /// 创建 web worker
    this.registerCommonIpcOnMessageHandler({
      method: "POST",
      pathname: "/create-process",
      matchMode: "full",
      input: { entry: "string", process_id: "string" },
      output: "object",
      handler: async (args, ipc, requestMessage) => {
        const processIdMap = mapHelper.getOrPut(
          ipcProcessIdMap,
          ipc.remote.mmid,
          () => new Map()
        );

        if (processIdMap.has(args.process_id)) {
          throw new Error(
            `ipc:${ipc.remote.mmid}/processId:${args.process_id} has already using`
          );
        }

        // // 关闭代码通道
        // closeHttpDwebServer(this, { port: 80, subdomain: ipc.remote.mmid });

        const po = new PromiseOut<number>();
        processIdMap.set(args.process_id, po);
        const result = await this.createProcessAndRun(
          ipc,
          apis,
          bootstrap_url,
          args.entry,
          requestMessage
        );
        po.resolve(result.processInfo.process_id);

        /// 创建成功了，注册销毁函数
        ipc.onClose(() => {
          if (processIdMap.delete(args.process_id)) {
            ipcProcessIdMap.delete(ipc.remote.mmid);
            if (processIdMap.size === 0) {
              apis.destroyProcess(result.processInfo.process_id);
            }
          }
        });

        return result.streamIpc.stream;
      },
    });

    /// 创建 web 通讯管道
    this.registerCommonIpcOnMessageHandler({
      pathname: "/create-ipc",
      matchMode: "full",
      input: {
        process_id: "string",
        /**
         * 虽然 mmid 是从远程直接传来的，但风险与jsProcess无关，
         * 因为首先我们是基于 ipc 来得到 processId 的，所以这个 mmid 属于 ipc 自己的定义
         */
        mmid: "string",
      },
      output: "number",
      handler: async (args, ipc) => {
        const process_id_po = ipcProcessIdMap
          .get(ipc.remote.mmid)
          ?.get(args.process_id);
        if (process_id_po === undefined) {
          throw new Error(
            `ipc:${ipc.remote.mmid}/processId:${args.process_id} invalid`
          );
        }
        const process_id = await process_id_po.promise;
        const port_id = await this.createIpc(ipc, apis, process_id, args.mmid);
        return port_id;
      },
    });

    const query_createIpc = z.object({
      process_id: zq.string(),
      mmid: zq.string(),
    });
    const query_createIpcFail = query_createIpc.and(
      z.object({ reason: zq.string() })
    );

    this.onFetch(async (event) => {
      if (event.pathname === "/create-ipc-fail") {
        const { ipc } = event;
        const args = parseQuery(event.searchParams, query_createIpcFail);
        const process_id_po = ipcProcessIdMap
          .get(ipc.remote.mmid)
          ?.get(args.process_id);
        if (process_id_po === undefined) {
          throw new Error(
            `ipc:${ipc.remote.mmid}/processId:${args.process_id} invalid`
          );
        }
        const process_id = await process_id_po.promise;
        await apis.createIpcFail(process_id, args.mmid, args.reason);
        return Response.json(true);
      }
    });

    this.registerCommonIpcOnMessageHandler({
      pathname: "/close-all-process",
      matchMode: "full",
      input: {},
      output: "boolean",
      handler: async (args, ipc, request) => {
        if (nww === undefined) return false;
        const processMap = ipcProcessIdMap.get(ipc.remote.mmid);
        if (processMap == null) return true;
        // 关闭程序
        processMap.forEach(async (po, _processId) => {
          const processId = await po.promise;
          apis.destroyProcess(processId);
        });
        // 关闭代码通道
        closeHttpDwebServer(this, { port: 80, subdomain: ipc.remote.mmid });
        return true;
      },
    });
  }
  async _shutdown() {}

  private async createProcessAndRun(
    ipc: Ipc,
    apis: Remote<$APIS>,
    bootstrap_url: string,
    entry = "/index.js",
    requestMessage: IpcRequest
  ) {
    /**
     * 用自己的域名的权限为它创建一个子域名
     * 这个服务专门一年来提供 woker.js 代码的服务
     * 包括 js-process.wekrer.mts 代码
     * 和 匹配 JsMicroModule 的 worker.js 代码
     */
    const httpDwebServer = await createHttpDwebServer(this, {
      subdomain: ipc.remote.mmid,
    });

    /**
     * 远端是代码服务，所以这里是 client 的身份
     */
    const streamIpc = new ReadableStreamIpc(ipc.remote, IPC_ROLE.CLIENT);
    void streamIpc.bindIncomeStream(requestMessage.body.stream());
    this.addToIpcSet(streamIpc);
    /**
     * 让远端提供 esm 模块代码
     * 这里我们将请求转发给对方，要求对方以一定的格式提供代码回来，
     * 我们会对回来的代码进行处理，然后再执行
     */
    const importLinker = new ImportLinker(
      httpDwebServer.startResult.urlInfo.internal_origin,
      [
        {
          pathMatcher: {
            pathname: "/",
            matchMode: "prefix",
          },
          handler: async (url) => {
            // <internal>开头的是特殊路径：交由内部处理，不会推给远端处理
            if (url.pathname.startsWith(this.INTERNAL_PATH)) {
              url.pathname = url.pathname.substring(this.INTERNAL_PATH.length);
              if (url.pathname === "/bootstrap.js") {
                // 这里的 bootstrap.js 好像还没有使用
                // 可能是通过 这个进程在启动进程的时候会使用的到
                const code = await this.JS_PROCESS_WORKER_CODE();
                return {
                  mime: "text/javascript",
                  data: code,
                };
              }
            }

            /// TODO 对代码进行翻译处理
            // 暂时是使用来同 woker.js 功能
            const response = await streamIpc.request(url.href);
            const preStr = "";
            const data = `${preStr};${await response.body.text()}`;
            return {
              /// TODO 默认只是js，未来会支持 WASM/JSON 等模块
              mime: "text/javascript",
              data: data,
            };
          },
        },
      ]
    );

    const httpStreamIpc = await httpDwebServer.listen();
    httpStreamIpc.onRequest((request, ipc) => {
      void _ipcResponseFromImportLinker(ipc, importLinker, request);
    });

    /// TODO 需要传过来，而不是自己构建
    const metadata = JSON.stringify({ mmid: ipc.remote.mmid });
    /// TODO env 允许远端传过来扩展
    const env = JSON.stringify({
      host: httpDwebServer.startResult.urlInfo.host,
      debug: "true",
      "ipc-support-protocols": "raw message_pack",
    } satisfies Record<string, string>);

    /**
     * 创建一个通往 worker 的消息通道
     */
    const channel_for_worker = new MessageChannel();
    const processInfo = await apis.createProcess(
      bootstrap_url,
      metadata,
      env,
      // JSON.stringify(metadata),
      // JSON.stringify(env),
      transfer(channel_for_worker.port2, [channel_for_worker.port2])
    );

    /**
     * 将 js-worker 中的请求进行中转代理
     * 在Android和IOS中的Webview默认只能传输字符串
     */
    const ipc_to_worker = new MessagePortIpc(
      channel_for_worker.port1,
      ipc.remote,
      IPC_ROLE.CLIENT
    );

    ipc_to_worker.onMessage((ipcMessage) => {
      ipc.postMessage(ipcMessage);
    });
    ipc.onMessage((message) => {
      ipc_to_worker.postMessage(message);
    });

    /**
     * 开始执行代码
     */
    await apis.runProcessMain(processInfo.process_id, {
      main_url: httpDwebServer.startResult.urlInfo.buildInternalUrl((url) => {
        url.pathname = entry;
      }).href,
    });

    /// 绑定销毁
    /**
     * “模块之间的IPC通道”关闭的时候，关闭“代码IPC流通道”
     *
     * > 自己shutdown的时候，这些ipc会被关闭
     */
    ipc.onClose(() => {
      streamIpc.close();
      ipc_to_worker.close();
    });
    /**
     * “代码IPC流通道”关闭的时候，关闭这个子域名
     */
    streamIpc.onClose(() => {
      httpDwebServer.close();
      httpStreamIpc.close();
    });
    return {
      streamIpc,
      processInfo,
    };
  }

  private async createIpc(
    _ipc: Ipc,
    apis: Remote<$APIS>,
    process_id: number,
    mmid: string
  ) {
    const env = JSON.stringify({
      "ipc-support-protocols": "raw message_pack",
    } satisfies Record<string, string>);
    /**
     * 创建一个通往 worker 的消息通道
     */
    const channel_for_worker = new MessageChannel();
    // 把一个 mesageChange 发送给 worker
    await apis.createIpc(
      process_id,
      mmid,
      transfer(channel_for_worker.port2, [channel_for_worker.port2]),
      env
    );
    // 把一个messageChange保存到全局对象
    return saveNative2JsIpcPort(channel_for_worker.port1);
  }
}
