// 模拟状态栏模块-用来提供状态UI的模块
import type { Ipc } from "../../../../core/ipc/ipc.ts";
import { NativeMicroModule } from "../../../../core/micro-module.native.ts";
import { log } from "../../../../helper/devtools.ts";
// import type { $BootstrapContext } from "../../../../core/bootstrapContext.ts";
// import type { IncomingMessage, OutgoingMessage } from "node:http";
import type { HttpServerNMM } from "../../../http-server/http-server.ts";
// import type {
//   $ReqRes,
//   $Observe,
// } from "../../native-ui/status-bar/status-bar.main.ts";
import Jimp from "jimp";
import jsQR from "jsqr";

export class BarcodeScanningNativeUiNMM extends NativeMicroModule {
  mmid = "barcode-scanning.sys.dweb" as const;
  httpIpc: Ipc | undefined;
  httpNMM: HttpServerNMM | undefined;
  // observe: Map<string, OutgoingMessage> = new Map();
  // waitForOperationRes: Map<string, OutgoingMessage> = new Map();
  // reqResMap: Map<number, $ReqRes> = new Map();
  // observeMap: Map<string, $Observe> = new Map();
  encoder = new TextEncoder();
  allocId = 0;

  _bootstrap = () => {
    log.green(`[${this.mmid} _bootstrap]`);
    let isStop = false;
    this.registerCommonIpcOnMessageHandler({
      method: "POST",
      pathname: "/process",
      matchMode: "full",
      input: {},
      output: "string",
      handler: async (args, client_ipc, ipcRequest) => {

        // cosnole.log()
        // const host: string = ipcRequest.parsed_url.host;
        // const pathname = ipcRequest.parsed_url.pathname;
        // const search = ipcRequest.parsed_url.search;
        // const url = `file://mwebview.sys.dweb/plugin/${host}${pathname}${search}`
        // const result = await this.nativeFetch(url)
        // console.log(ipcRequest.body);

        // 直接解析二维码
        return await Jimp.read(await ipcRequest.body.u8a()).then(
          ({ bitmap }: any) => {
            const result = jsQR(bitmap.data, bitmap.width, bitmap.height);
            console.log("result: ", result);
            return JSON.stringify(result === null ? [] : [result.data]);
          }
        );
      },
    });

    this.registerCommonIpcOnMessageHandler({
      method: "GET",
      pathname: "/stop",
      matchMode: "full",
      input: {},
      output: "boolean",
      handler: (args, client_ipc, ipcRequest) => {
        // 停止及解析
        isStop = true;
        return true;
      },
    });
  };

  // private _process = (req: IncomingMessage, res: OutgoingMessage) => {
  //   switch (req.method) {
  //     case "OPTIONS":
  //       this._processOPTIONS(req, res);
  //       break;
  //     case "POST":
  //       this._processPOST(req, res);
  //       break;
  //     default:
  //       new Error(`没有处理的方法 ${req.method}`);
  //   }
  // };

  // private _processOPTIONS = (
  //   req: IncomingMessage,
  //   res: OutgoingMessage
  // ) => {
  //   res.end();
  // };
  // private _processPOST = async (req: IncomingMessage, res: OutgoingMessage) => {
  //   // const buffer = Buffer.from(data.body)
  //   let chunks = Buffer.alloc(0);
  //   req.on("data", (chunk) => (chunks = Buffer.concat([chunks, chunk])));
  //   req.on("end", () => {
  //     Jimp.read(chunks).then(({ bitmap }: any) => {
  //       const result = jsQR(bitmap.data, bitmap.width, bitmap.height);
  //       res.end(JSON.stringify(result === null ? [] : [result.data]));
  //     });
  //   });
  // };

  // private _stop = async (req: IncomingMessage, res: OutgoingMessage) => {
  //   throw new Error(`_stop 还没有处理`);
  // };

  // private _getPhoto = async (req: IncomingMessage, res: OutgoingMessage) => {
  //   const id = this.allocId++;
  //   const origin = req.headers.origin;
  //   if (origin === undefined) throw new Error(`origin === null`);
  //   const waitForRes = this.waitForOperationRes.get(origin);
  //   if (waitForRes === undefined) throw new Error(`waitForRes ===  undefined`);
  //   waitForRes.write(
  //     this.encoder.encode(
  //       `${JSON.stringify({
  //         operationName: "getPhoto",
  //         value: "",
  //         from: origin,
  //         id: id,
  //       })}\n`
  //     )
  //   );
  //   this.reqResMap.set(id, { req, res });
  // };

  // private _waitForOperation = async (
  //   req: IncomingMessage,
  //   res: OutgoingMessage
  // ) => {
  //   const appUrl = new URL(
  //     req.url as string,
  //     req.headers.referer
  //   ).searchParams.get("app_url");
  //   if (appUrl === null)
  //     throw new Error(`${this.mmid} _waiForOperation appUrl === null`);
  //   this.waitForOperationRes.set(appUrl, res);
  // };

  // private _operationReturn = async (
  //   req: IncomingMessage,
  //   res: OutgoingMessage
  // ) => {
  //   const id = req.headers.id;
  //   if (typeof id !== "string")
  //     throw new Error(`${this.mmid} typeof id !== string`);
  //   if (Object.prototype.toString.call(id).slice(8, -1) === "Array")
  //     throw new Error(`id === Array`);
  //   let chunks = Buffer.alloc(0);
  //   req.on("data", (chunk) => {
  //     chunks = Buffer.concat([chunks, chunk]);
  //   });
  //   req.on("end", () => {
  //     res.end();
  //     const key = parseInt(id);
  //     const reqRes = this.reqResMap.get(key);
  //     if (reqRes === undefined) throw new Error(`reqRes === undefined`);
  //     reqRes.res.end(Buffer.from(chunks));
  //     this.reqResMap.delete(key);
  //   });
  // };

  _shutdown() {}
}
