import http from "node:http";
import {
  $isMatchReq,
  $ReqMatcher,
  openNwWindow,
  wrapCommonJsCode,
} from "../core/helper.cjs";
import { Ipc, IpcResponse, IPC_DATA_TYPE, IPC_ROLE } from "../core/ipc.cjs";
import { NativeIpc } from "../core/ipc.native.cjs";
import type { MicroModule } from "../core/micro-module.cjs";
import { NativeMicroModule } from "../core/micro-module.native.cjs";

const packageJson = require("../../package.json");

type $APIS = typeof import("./js-process.web.cjs")["APIS"];

class ProcessImports {
  constructor(
    // readonly ipc: Ipc,
    // readonly process_id: number,
    readonly host: string
  ) {}
  /// 我们将托管用户的代码，响应虚拟环境中的 import 请求
  readonly importMaps: Array<{
    pathMatcher: $ReqMatcher;
    hanlder: (parsed_url: URL) => { mime: string; data: Uint8Array | string };
  }> = [];

  async linker(url: URL) {
    for (const item of this.importMaps) {
      if ($isMatchReq(item.pathMatcher, url.pathname)) {
        return item.hanlder(url);
      }
    }
  }
}

/**
 * 将指定的js运行在后台的一个管理器，
 * 注意它们共享一个域，所以要么就关闭
 */
export class JsProcessNMM extends NativeMicroModule {
  override mmid = `js.sys.dweb` as const;
  private window?: nw.Window;
  private server?: http.Server;

  private processImportsMap = new Map<string, ProcessImports>();
  async _bootstrap() {
    /// 创建 http 服务
    const port = await new Promise<number>((resolve, reject) => {
      const server = (this.server = http
        .createServer(async (req, res) => {
          const req_host = req.headers.host;
          if (req_host == null) {
            defaultErrorPage(req, res, 403, "invalid host");
            return;
          }
          /// TODO 这里需要检查 Referer 值是否正常才行，避免跨应用盗取
          const processImports = this.processImportsMap.get(req_host);
          if (processImports === undefined) {
            defaultErrorPage(req, res, 502, "no found process");
            return;
          }

          const code = await processImports.linker(
            new URL(req.url ?? "/", `http://${req_host}`)
          );
          if (code === undefined) {
            defaultErrorPage(req, res, 404, "no found");
            return;
          }
          res.statusCode = 200;
          res.setHeader("Content-Type", code.mime);
          res.end(code.data);
        })
        .listen(() => {
          const address_info = server.address();
          if (address_info == null || typeof address_info === "string") {
            reject(new Error(`fail to get port`));
          } else {
            resolve(address_info.port);
          }
        }));
    });
    const defaultErrorPage = (
      req: http.IncomingMessage,
      res: http.ServerResponse,
      statusCode: number,
      errorMessage: string
    ) => {
      res.statusCode = statusCode;
      res.statusMessage = errorMessage;
      res.end();
    };

    /// 内部的代码
    const internalProcessImports = new ProcessImports(
      `internal.js.sys.dweb.localhost:${port}`
    );
    const JS_PROCESS_WORKER_CODE = await fetch(
      new URL("bundle/js-process.worker.cjs", location.href)
    ).then((res) => res.text());
    internalProcessImports.importMaps.push({
      pathMatcher: {
        pathname: "/env.js",
        matchMode: "full",
      },
      hanlder(url) {
        const install_code = wrapCommonJsCode(JS_PROCESS_WORKER_CODE, {
          after: `.installEnv(${JSON.stringify(url.searchParams.get("mmid"))})`,
        });
        return {
          mime: "application/javascript",
          data: install_code,
        };
      },
    });
    this.processImportsMap.set(
      internalProcessImports.host,
      internalProcessImports
    );

    const window = (this.window = await openNwWindow("../../js-process.html", {
      /// 如果起始界面是html，说明是调试模式，那么这个窗口也一同展示
      show: packageJson.main.endsWith(".html"),
    }));
    if (window.window.APIS_READY !== true) {
      await new Promise((resolve) => {
        window.window.addEventListener("apis-ready", resolve);
      });
    }
    const apis = window.window as $APIS;
    /// 创建 web worker
    this.registerCommonIpcOnMessageHanlder({
      pathname: "/create-process",
      matchMode: "full",
      input: { main_code: "string" },
      output: "number",
      hanlder: (args, ipc) => {
        return this.createProcessAndRun(
          { apis, port, ipc },
          `http://${
            internalProcessImports.host
          }/env.js?mmid=${encodeURIComponent(this.mmid)}`,
          args.main_code
        );
      },
    });
    /// 创建 web 通讯管道
    this.registerCommonIpcOnMessageHanlder({
      pathname: "/create-ipc",
      matchMode: "full",
      input: { process_id: "number" },
      output: "number",
      hanlder: (args) => {
        const port2 = apis.createIpc(args.process_id);
        const port_id = all_ipc_id_acc++;
        ALL_IPC_CACHE.set(port_id, port2);
        return port_id;
      },
    });
  }
  async _shutdown() {
    this.window?.close();
    this.window = undefined;

    this.server?.close();
    this.server = undefined;

    this.processImportsMap.clear();
  }

  private async createProcessAndRun(
    ctx: {
      apis: $APIS;
      port: number;
      ipc: Ipc;
    },
    bootstrap_url: string,
    main_code: string,
    main_pathname = "/index.js"
  ) {
    const channel = new MessageChannel();
    const process = await ctx.apis.createProcess(bootstrap_url, channel.port2);

    /// 将 js-worker 中的请求进行中转代理
    const worker_ipc = new NativeIpc(channel.port1, this, IPC_ROLE.CLIENT);
    worker_ipc.onMessage(async (ipcMessage, ipc) => {
      if (ipcMessage.type === IPC_DATA_TYPE.REQUEST) {
        /// 收到 Worker 的数据请求，转发出去
        const response = await this.fetch(ipcMessage.url, ipcMessage);
        ipc.postMessage(
          await IpcResponse.fromResponse(ipcMessage.req_id, response, ipc)
        );
      }
    });

    const host = `p_${process.process_id}.js.sys.dweb.localhost:${ctx.port}`;

    const processImports = new ProcessImports(host);
    processImports.importMaps.push({
      pathMatcher: {
        pathname: main_pathname,
        matchMode: "full",
      },
      hanlder() {
        return {
          mime: "application/javascript",
          data: wrapCommonJsCode(main_code),
        };
      },
    });
    this.processImportsMap.set(host, processImports);

    process.runMain({
      mmid: this.mmid,
      main_url: new URL(main_pathname, `http://${host}`).href,
    });

    return process.process_id;
  }

  // static singleton = once(() => new JsProcessManager());
}

const ALL_IPC_CACHE = new Map<number, MessagePort>();
let all_ipc_id_acc = 0;
const getIpcCache = (port_id: number) => {
  const port = ALL_IPC_CACHE.get(port_id);
  if (port === undefined) {
    throw new Error(`no found port2(js-process) by id: ${port_id}`);
  }
  return port;
};

/**
 * 在NW.js里，JsIpc几乎等价于 NativeIPC，都是使用原生的 MessagePort 即可
 * 差别只在于 JsIpc 的远端是在 js-worker 中的
 *
 * ### 原理
 * 连接发起方执行 `fetch('file://js.sys.dweb/create-ipc')` 后，
 * 由 js-worker 创建了 channel-port1/2，然后 js-process(native) 负责中转这个信道（在nwjs中，我们直接使用内存引用，在mobile中，我们需要拦截webRequest），并为其存下一个 id(number)。
 * 最终将这个 id 通过 fetch 返回值返回。
 *
 * 那么连接发起方就可以通过这个 id(number) 和 JsIpc 构造器来实现与 js-worker 的直连
 */
export class JsIpc extends NativeIpc {
  constructor(port_id: number, module: MicroModule) {
    super(getIpcCache(port_id), module, IPC_ROLE.CLIENT);
    /// TODO 这里应该放在和 ALL_IPC_CACHE.set 同一个函数下，只是原生的 MessageChannel 没有 close 事件，这里没有给它模拟，所以有问题
    this.onClose(() => {
      ALL_IPC_CACHE.delete(port_id);
    });
  }
}
