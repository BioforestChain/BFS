import process from "node:process";
import type {
  $BootstrapContext,
  $DnsMicroModule,
} from "../../core/bootstrapContext.ts";
import { IpcHeaders } from "../../core/ipc/IpcHeaders.ts";
import { IpcResponse } from "../../core/ipc/IpcResponse.ts";
import { NativeMicroModule } from "../../core/micro-module.native.ts";
import type { MicroModule } from "../../core/micro-module.ts";
import {
  $ConnectResult,
  connectMicroModules,
} from "../../core/nativeConnect.ts";
import { $readRequestAsIpcRequest } from "../../helper/$readRequestAsIpcRequest.ts";
import { PromiseOut } from "../../helper/PromiseOut.ts";
import "../../helper/electron.ts";
import { mapHelper } from "../../helper/mapHelper.ts";
import type { $DWEB_DEEPLINK, $MMID } from "../../helper/types.ts";
import { nativeFetchAdaptersManager } from "./nativeFetch.ts";
 
class MyDnsMicroModule implements $DnsMicroModule {
  constructor(private dnsNN: DnsNMM, private fromMM: MicroModule) {}

  install(mm: MicroModule): void {
    this.dnsNN.install(mm);
  }

  uninstall(mm: MicroModule): void {
    this.dnsNN.uninstall(mm);
  }

  connect(mmid: $MMID, reason?: Request) {
    return this.dnsNN[connectTo_symbol](
      this.fromMM,
      mmid,
      reason ?? new Request(`file://${mmid}`)
    );
  }

  query(mmid: $MMID) {
    return this.dnsNN.query(mmid);
  }
}

class MyBootstrapContext implements $BootstrapContext {
  constructor(readonly dns: MyDnsMicroModule) {}
}

const connectTo_symbol = Symbol("connectTo");

/** DNS 服务，内核！
 * 整个系统都围绕这个 DNS 服务来展开互联
 */
export class DnsNMM extends NativeMicroModule {
  mmid = "dns.sys.dweb" as const;
  readonly dweb_deeplinks = ["dweb:open"] as $DWEB_DEEPLINK[];
  private apps = new Map<$MMID, MicroModule>();

  bootstrap(
    ctx: $BootstrapContext = new MyBootstrapContext(
      new MyDnsMicroModule(this, this)
    )
  ) {
    return super.bootstrap(ctx);
  }

  bootstrapMicroModule(fromMM: MicroModule) {
    return fromMM.bootstrap(
      new MyBootstrapContext(new MyDnsMicroModule(this, fromMM))
    );
  }

  // 拦截 nativeFetch
  private mmConnectsMap = new WeakMap<
    MicroModule,
    Map<$MMID, PromiseOut<$ConnectResult>>
  >();

  /**
   * 创建通过 MessageChannel 实现同行的 ipc
   * @param fromMM
   * @param toMmid
   * @param reason
   * @returns
   */
  [connectTo_symbol](
    fromMM: MicroModule,
    toMmid: $MMID,
    reason: Request
  ) {
    // v2.0
    // 创建连接
    const fromMMconnectsMap = mapHelper.getOrPut(
      this.mmConnectsMap,
      fromMM,
      () => new Map<$MMID, PromiseOut<$ConnectResult>>()
    );

    const po = mapHelper.getOrPut(fromMMconnectsMap, toMmid, () => {
      const po = new PromiseOut<$ConnectResult>();
      (async () => {
        /// 与指定应用建立通讯
        const toMM = await this.open(toMmid);

        const result = await connectMicroModules(fromMM, toMM, reason);
        const [ipcForFromMM, ipcForToMM] = result;

        // 监听生命周期 释放引用
        ipcForFromMM.onClose(() => {
          fromMMconnectsMap?.delete(toMmid);
        });

        // 反向存储 toMM
        if (ipcForToMM) {
          const result2: $ConnectResult = [ipcForToMM, ipcForFromMM];
          const toMMconnectsMap = mapHelper.getOrPut(
            this.mmConnectsMap,
            toMM,
            () => new Map<$MMID, PromiseOut<$ConnectResult>>()
          );

          mapHelper.getOrPut(toMMconnectsMap, fromMM.mmid, () => {
            const toMMPromise = new PromiseOut<$ConnectResult>();
            ipcForToMM.onClose(() => {
              toMMconnectsMap?.delete(fromMM.mmid);
            });
            toMMPromise.resolve(result2);
            return toMMPromise;
          });
        }
        po.resolve(result);
      })();
      return po;
    });
    return po.promise;
  }

  override async _bootstrap(context: $BootstrapContext) {
    this.install(this);
    this.running_apps.set(this.mmid, this);

    this.registerCommonIpcOnMessageHandler({
      pathname: "/open",
      matchMode: "full",
      input: { app_id: "mmid" },
      output: "boolean",
      handler: async (args, client_ipc, request) => {
        /// TODO 询问用户是否授权该行为
        const app = await this.open(args.app_id);
        return IpcResponse.fromJson(
          request.req_id,
          200,
          new IpcHeaders({
            "Content-Type": "application/json; charset=UTF-8",
          }),
          JSON.stringify(app),
          client_ipc
        );
      },
    });
    /// dweb deeplink
    this.registerCommonIpcOnMessageHandler({
      protocol: "dweb:",
      pathname: "open/",
      matchMode: "prefix",
      input: {},
      output: "boolean",
      handler: async (_args, _client_ipc, request) => {
        const app_id = request.parsed_url.pathname.replace(
          "open/",
          ""
        ) as $MMID;
        /// TODO 询问用户是否授权该行为
        await this.open(app_id);
        return true;
      },
    });

    this.registerCommonIpcOnMessageHandler({
      pathname: "/close",
      matchMode: "full",
      input: { app_id: "mmid" },
      output: "number",
      handler: async (args) => {
        /// TODO 关闭应用首先要确保该应用的 parentProcessId 在 processTree 中
        const n = await this.close(args.app_id);
        await this.nativeFetch(
          `file://mwebview.sys.dweb/close/focused_window`
        );
        return n;
      },
    });

    // 检查工具 提供查询 mmConnectsMap  的结果
    this.registerCommonIpcOnMessageHandler({
      pathname: "/query/mm_connects_map",
      matchMode: "full",
      input: { app_id: "mmid" },
      output: "object",
      handler: async (args) => {
        const mm = await this.query(args.app_id);
        if (mm === undefined) {
          throw new Error(`mm === undefined`);
        }
        const _map = this.mmConnectsMap.get(mm);
        return {};
      },
    });

    this.registerCommonIpcOnMessageHandler({
      pathname: "/restart",
      matchMode: "full",
      input: { app_id: "mmid" },
      output: "boolean",
      handler: async (args, _ipc, _request) => {
        // 需要停止匹配的 jsMicroModule
        const mm = await this.query(args.app_id);
        if (mm === undefined) return false;
        this.close(args.app_id);
        // 关闭当前window对象
        const result = await this.nativeFetch(
          `file://mwebview.sys.dweb/close/focused_window`
        ).boolean();

        this.install(mm);
        this.open(args.app_id);
        return result;
      },
    });

    this._after_shutdown_signal.listen(
      nativeFetchAdaptersManager.append(
        async (fromMM, parsedUrl, requestInit) => {
          // 测试代码
          // Reflect.set(requestInit, "duplex", "half")
          // fetch("file://xxx.dweb") 匹配
          if (
            parsedUrl.protocol === "file:" &&
            parsedUrl.hostname.endsWith(".dweb")
          ) {
            const mmid = parsedUrl.hostname as $MMID;
            const [ipc] = await this[connectTo_symbol](
              fromMM,
              mmid,
              new Request(parsedUrl, requestInit)
            );
            const ipc_req_init = await $readRequestAsIpcRequest(requestInit);
            const ipc_response = await ipc.request(
              parsedUrl.href,
              ipc_req_init
            );
            return ipc_response.toResponse(parsedUrl.href);
          }
        }
      )
    );

    //#region 启动引导程序
    await this.open(`boot.sys.dweb`);
    //#endregion

    /**
     * 获取应用程序命令行参数： 如果是开发模式，electron 后面需要跟 目录，所以从 2 开始
     * 如果是应用模式，只需要应用程序名，所以从1开始
     */
    const args = process.argv.slice(
      process.argv.findIndex((arg) => /^\w+$/.test(arg))
      // path.parse(process.argv0).name.toLowerCase() === "electron" ? 2 : 1
    );

    if (args.length > 0) {
      const [domain, ...deeplink_args] = args;
      const dweb_deeplink = `dweb:${domain}`;
      const buildReqUrl = () => {
        const normalizePath: string[] = [];
        const normalizeQuery = new URLSearchParams();
        let hasSearch = false;
        for (let i = 0; i < deeplink_args.length; i++) {
          const arg = deeplink_args[i];
          if (arg.startsWith("-")) {
            const k_v = arg.match(/^\-+(.+?)\=(.+)/);
            if (k_v) {
              normalizeQuery.append(k_v[1], k_v[2]);
            } else {
              normalizeQuery.append(arg.replace(/^-+/, ""), deeplink_args[++i]);
            }
            hasSearch = true;
          } else {
            normalizePath.push(arg);
          }
        }
        const pathname = "/" + normalizePath.join("/");
        const url = new URL(
          (dweb_deeplink + pathname)
            .replace(/\/{2,}/g, "/")
            .replace(/\/$/, "") +
            (hasSearch ? "?" + normalizeQuery.toString() : "")
        );
        return url;
      };
      let _req_url: undefined | URL;
      const getReqUrl = () => (_req_url ??= buildReqUrl());

      /// 查询匹配deeplink的程序
      for (const app of this.apps.values()) {
        if (
          undefined !==
          app.dweb_deeplinks.find((dl) => dl.startsWith(dweb_deeplink))
        ) {
          const req = new Request(getReqUrl());
          const [ipc] = await context.dns.connect(app.mmid, req);
          const ipc_req_init = await $readRequestAsIpcRequest(req);
          /// 发送请求
          await ipc.request(req.url, ipc_req_init);
        }
      }
    }
  }

  async _shutdown() {
    for (const mmid of this.running_apps.keys()) {
      await this.close(mmid);
    }
  }

  /** 安装应用 */
  install(mm: MicroModule) {
    this.apps.set(mm.mmid, mm);
  }

  /** 卸载应用 */
  uninstall(mm: MicroModule) {
    this.apps.delete(mm.mmid);
  }

  /** 查询应用 */
  // deno-lint-ignore require-await
  async query(mmid: $MMID) {
    return this.apps.get(mmid);
  }

  private running_apps = new Map<$MMID, MicroModule>();
  /** 打开应用 */
  async open(mmid: $MMID) {
    let app = this.running_apps.get(mmid);
    if (app === undefined) {
      const mm = await this.query(mmid);
      if (mm === undefined) {
        throw new Error(`no found app: ${mmid}`);
      }
      this.running_apps.set(mmid, mm);
      // @TODO bootstrap 函数应该是 $singleton 修饰
      await this.bootstrapMicroModule(mm);
      app = mm;
    }
    return app;
  }

  /** 关闭应用 */
  async close(mmid: $MMID) {
    const app = this.running_apps.get(mmid);
    if (app === undefined) {
      // 关闭失败没有匹配的 microModule 运行
      return -1;
    }
    try {
      this.running_apps.delete(mmid);
      await app.shutdown();
      this.uninstall(app);
      // 关闭成功
      return 0;
    } catch {
      // 关闭失败
      return 1;
    }
  }
}
