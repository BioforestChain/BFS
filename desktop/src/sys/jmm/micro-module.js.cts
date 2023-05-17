import type { $BootstrapContext } from "../../core/bootstrapContext.cjs";
import { ReadableStreamIpc } from "../../core/ipc-web/ReadableStreamIpc.cjs";
import { IPC_ROLE, Ipc, IpcResponse } from "../../core/ipc/index.cjs";
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
  private _connecting_ipcs = new Set<Ipc>();

  /** 每个 JMM 启动都要依赖于某一个js */
  async _bootstrap(context: $BootstrapContext) {
    console.log(`[${this.metadata.config.id} micro-module.js.ct _bootstrap ${this.mmid}]`);

    const pid = Math.ceil(Math.random() * 1000).toString();
    this._process_id = pid;
    // 这个 streamIpc 专门服务于 file://js.sys.dweb/create-process
    // 也就是 JsMicroModule 对应的 worker.js 发送过来的消息
    const streamIpc = new ReadableStreamIpc(this, IPC_ROLE.SERVER);
    streamIpc.onRequest(async (request) => {
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

    // 创建一个 streamIpc
    void streamIpc.bindIncomeStream(
      this.nativeFetch(
        buildUrl(new URL(`file://js.sys.dweb/create-process`), {
          search: {
            entry: this.metadata.config.server.entry,
            process_id: this._process_id,
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

      console.log("------------- jsIPc", ipcRequest.url)
      const request = ipcRequest.toRequest()
      const response = await this.nativeFetch(request);
      const newResponse = await IpcResponse.fromResponse(ipcRequest.req_id, response, jsIpc)
      jsIpc.postMessage(newResponse);
    });

    jsIpc.onEvent(async (ipcEvent) => {
      if (ipcEvent.name === "dns/connect") {
        const { mmid } = JSON.parse(ipcEvent.text);
        const [targetIpc] = await context.dns.connect(mmid);
        const portId = await this.nativeFetch(
          buildUrl(new URL(`file://js.sys.dweb/create-ipc`), {
            search: { process_id: this._process_id, mmid },
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
 
  _shutdown() {
    console.log('关闭了进程 micro-module.js.cts')
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
