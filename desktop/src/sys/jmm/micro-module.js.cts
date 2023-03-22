import chalk from "chalk";
import type { $BootstrapContext } from "../../core/bootstrapContext.cjs";
import { ReadableStreamIpc } from "../../core/ipc-web/ReadableStreamIpc.cjs";
import { Ipc, IpcResponse, IPC_ROLE } from "../../core/ipc/index.cjs";
import { MicroModule } from "../../core/micro-module.cjs";
import { httpMethodCanOwnBody } from "../../helper/httpMethodCanOwnBody.cjs";
import type { $IpcSupportProtocols } from "../../helper/types.cjs";
import { buildUrl } from "../../helper/urlHelper.cjs";
import { Native2JsIpc } from "../js-process/ipc.native2js.cjs";

import type { JmmMetadata } from "./JmmMetadata.cjs";

/**
 * 所有的js程序都只有这么一个动态的构造器
 */
export class JsMicroModule extends MicroModule {
  readonly ipc_support_protocols: $IpcSupportProtocols = {
    message_pack: true,
    protobuf: false,
    raw: true,
  };
  constructor(
    /**
     * js程序是动态外挂的
     * 所以需要传入一份配置信息
     */
    readonly metadata: JmmMetadata
  ) {
    super();
  }
  get mmid() {
    return this.metadata.config.id;
  }

  /**
   * 和 dweb 的 port 一样，pid 是我们自己定义的，它跟我们的 mmid 关联在一起
   * 所以不会和其它程序所使用的 pid 冲突
   */
  private _process_id?: string;
  /**
   * 一个 jsMM 可能连接多个模块
   */
  private _remoteIpcs = new Map<string, Ipc>();
  private _workerIpc: Native2JsIpc | undefined;

  /** 每个 JMM 启动都要依赖于某一个js */
  async _bootstrap(context: $BootstrapContext) {
    console.log(`[micro-module.js.ct _bootstrap ${this.mmid}]`);
    // 需要添加 onConenct 这样通过 jsProcess 发送过来的 ipc.posetMessage 能够能够接受的到这个请求
    // 也就是能够接受 匹配的 worker 发送你过来的请求能够接受的到
    this.onConnect((workerIpc) => {
      console.log(
        `[micro-module.js.cts ${this.mmid} onConnect by ${workerIpc.remote.mmid}]`
      );
      // ipc === js-process registerCommonIpcOnMessageHandler /create-process" handle 里面的第二个参数ipc
      workerIpc.onRequest(async (request) => {
        // console.log('[micro-module.js.cts ipc onRequest]',JSON.stringify(request))
        // console.log('[micro-module.js.cts ipc onRequest request.parsed_url.href]',request.parsed_url.href)
        // console.log('[micro-module.js.cts ]   ipc ', ipc.remote.mmid)
        // console.log(chalk.red(`[micro-module.js.cts 这里错误，传递 init 参数否则无法正确的创建ipc通信🔗]`))
        console.log(
          chalk.red(
            `[micro-module.js.cts 这里需要区分 请求的方法，如果请求的方法是 post | put 需要把 rquest init 带上]`
          )
        );
        const init = httpMethodCanOwnBody(request.method)
          ? { method: request.method, body: await request.body.stream() }
          : { method: request.method };

        const response = await this.nativeFetch(request.parsed_url.href, init);
        workerIpc.postMessage(
          await IpcResponse.fromResponse(request.req_id, response, workerIpc)
        );
      });

      workerIpc.onMessage(async (request) => {
        // console.log('ipc.onMessage', request)
      });

      workerIpc.onEvent((event) => {
        console.log("ipc.onEvent", event);
      });
    });

    const pid = Math.ceil(Math.random() * 1000).toString();
    this._process_id = pid;
    const streamIpc = new ReadableStreamIpc(this, IPC_ROLE.SERVER);
    streamIpc.onRequest(async (request) => {
      console.log("-----------------------2", request.parsed_url);
      if (request.parsed_url.pathname.endsWith("/")) {
        streamIpc.postMessage(
          IpcResponse.fromText(
            request.req_id,
            403,
            undefined,
            "Forbidden",
            streamIpc
          )
        );
      } else {
        const main_code = await this.nativeFetch(
          this.metadata.config.server.root + request.parsed_url.pathname
        ).text();

        streamIpc.postMessage(
          IpcResponse.fromText(
            request.req_id,
            200,
            undefined,
            main_code,
            streamIpc
          )
        );
      }
    });

    // console.log("[micro-module.js.cts 执行 bindIncomeStream:]", this.mmid)
    void streamIpc.bindIncomeStream(
      this.nativeFetch(
        buildUrl(new URL(`file://js.sys.dweb/create-process`), {
          search: {
            entry: this.metadata.config.server.entry,
            process_id: pid,
          },
        }),
        {
          method: "POST",
          body: streamIpc.stream,
        }
      ).stream()
    );
    this._connecting_ipcs.add(streamIpc);

    const [jsIpc] = await context.dns.connect("js.sys.dweb");
    jsIpc.onRequest(async (ipcRequest) => {
      const response = await this.nativeFetch(ipcRequest.toRequest());
      jsIpc.postMessage(
        await IpcResponse.fromResponse(ipcRequest.req_id, response, jsIpc)
      );
    });

    jsIpc.onEvent(async (ipcEvent) => {
      console.log("接收到连接的请求");
      if (ipcEvent.name === "dns/connect") {
        const { mmid } = JSON.parse(ipcEvent.text);
        const [targetIpc] = await context.dns.connect(mmid);
        const portId = await this.nativeFetch(
          buildUrl(new URL(`file://js.sys.dweb/create-ipc`), {
            search: { pid, mmid },
          })
        ).number();
        const originIpc = new Native2JsIpc(portId, this);
        /**
         * 将两个消息通道间接互联
         */
        originIpc.onMessage((ipcMessage) => targetIpc.postMessage(ipcMessage));
        targetIpc.onMessage((ipcMessage) => originIpc.postMessage(ipcMessage));
      }
    });
  }
  private _connecting_ipcs = new Set<Ipc>();

  async _beConnect(from: MicroModule): Promise<Native2JsIpc> {
    const process_id = this._process_id;
    if (process_id === undefined) {
      throw new Error("process_id no found.");
    }
    // console.log(chalk.red(`问题从这里开始 process_id === ${this._process_id}`))
    const port_id = await this.nativeFetch(
      `file://js.sys.dweb/create-ipc?process_id=${process_id}&mmid=${this.mmid}`
    ).number();

    const outer_ipc = new Native2JsIpc(port_id, this);
    this._connecting_ipcs.add(outer_ipc);
    this._workerIpc = outer_ipc; /** 测试代码 */
    return outer_ipc;
  }

  _shutdown() {
    for (const outer_ipc of this._connecting_ipcs) {
      outer_ipc.close();
    }
    this._connecting_ipcs.clear();

    /**
     * @TODO 发送指令，关停js进程
     */
    this._process_id = undefined;
  }
}
