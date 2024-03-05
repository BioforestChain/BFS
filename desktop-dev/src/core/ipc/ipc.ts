import { PromiseOut } from "../../helper/PromiseOut.ts";
import { CacheGetter } from "../../helper/cacheGetter.ts";
import { $Callback, createSignal } from "../../helper/createSignal.ts";
import { MicroModule } from "../micro-module.ts";
import type { $MicroModuleManifest } from "../types.ts";
import { IpcRequest } from "./IpcRequest.ts";
import type { IpcResponse } from "./IpcResponse.ts";
import type { IpcHeaders } from "./helper/IpcHeaders.ts";
import {
  $IpcMessage,
  $OnIpcErrorMessage,
  $OnIpcEventMessage,
  $OnIpcLifeCycleMessage,
  $OnIpcRequestMessage,
  $OnIpcStreamMessage,
  IPC_MESSAGE_TYPE,
  IPC_STATE,
  type $OnIpcMessage,
} from "./helper/const.ts";

import { mapHelper } from "../../helper/mapHelper.ts";
import { $OnFetch, createFetchHandler } from "../helper/ipcFetchHelper.ts";
import { IpcLifeCycle } from "./IpcLifeCycle.ts";
import { PureChannel, pureChannelToIpcEvent } from "./PureChannel.ts";
import { IpcPool } from "./IpcPool.ts";
export {
  FetchError,
  FetchEvent,
  type $FetchResponse,
  type $OnFetch,
  type $OnFetchReturn
} from "../helper/ipcFetchHelper.ts";

let ipc_uid_acc = 0;
let _order_by_acc = 0;
export abstract class Ipc {

  readonly uid = ipc_uid_acc++;
  static order_by_acc = _order_by_acc++;
  /**
   * 是否支持使用 MessagePack 直接传输二进制
   * 在一些特殊的场景下支持字符串传输，比如与webview的通讯
   * 二进制传输在网络相关的服务里被支持，里效率会更高，但前提是对方有 MessagePack 的编解码能力
   * 否则 JSON 是通用的传输协议
   */
  get support_cbor() {
    return this._support_cbor;
  }
  protected _support_cbor = false;
  /**
   * 是否支持使用 Protobuf 直接传输二进制
   * 在网络环境里，protobuf 是更加高效的协议
   */
  get support_protobuf() {
    return this._support_protobuf;
  }
  protected _support_protobuf = false;

  /**
   * 是否支持结构化内存协议传输：
   * 就是说不需要对数据手动序列化反序列化，可以直接传输内存对象
   */
  get support_raw() {
    return this._support_raw;
  }
  protected _support_raw = false;
  /**
   * 是否支持二进制传输
   */
  get support_binary() {
    return this._support_binary ?? (this.support_cbor || this.support_protobuf || this.support_raw);
  }

  protected _support_binary = false;
  // 跟ipc绑定的模块
  abstract readonly remote: $MicroModuleManifest;
  // 当前ipc生命周期
  private ipcLifeCycleState: IPC_STATE = IPC_STATE.OPENING;
  protected _closeSignal = createSignal<() => unknown>(false);
  onClose = this._closeSignal.listen;
  asRemoteInstance() {
    if (this.remote instanceof MicroModule) {
      return this.remote;
    }
  }

  // deno-lint-ignore no-explicit-any
  private _createSignal<T extends $Callback<any[]>>(autoStart?: boolean) {
    const signal = createSignal<T>(autoStart);
    this.onClose(() => signal.clear());
    return signal;
  }

  protected _messageSignal = this._createSignal<$OnIpcMessage>(false);
  postMessage(message: $IpcMessage): void {
    if (this._closed) {
      return;
    }
    this._doPostMessage(message);
  }
  abstract _doPostMessage(data: $IpcMessage): void;
  onMessage = this._messageSignal.listen;

  /**
   * 强制触发消息传入，而不是依赖远端的 postMessage
   */
  emitMessage = (args: IpcRequest) => this._messageSignal.emit(args, this);

  private __onRequestSignal = new CacheGetter(() => {
    const signal = this._createSignal<$OnIpcRequestMessage>(false);
    this.onMessage((request, ipc) => {
      if (request.type === IPC_MESSAGE_TYPE.REQUEST) {
        signal.emit(request, ipc);
      }
    });
    return signal;
  });
  private get _onRequestSignal() {
    return this.__onRequestSignal.value;
  }

  onRequest(cb: $OnIpcRequestMessage) {
    return this._onRequestSignal.listen(cb);
  }

  onFetch(...handlers: $OnFetch[]) {
    const onRequest = createFetchHandler(handlers);
    return onRequest.extendsTo(this.onRequest(onRequest));
  }
  private __onStreamSignal = new CacheGetter(() => {
    const signal = this._createSignal<$OnIpcStreamMessage>(false);
    this.onMessage((request, ipc) => {
      if ("stream_id" in request) {
        signal.emit(request, ipc);
      }
    });
    return signal;
  });
  private get _onStreamSignal() {
    return this.__onStreamSignal.value;
  }
  onStream(cb: $OnIpcStreamMessage) {
    return this._onStreamSignal.listen(cb);
  }

  private _onEventSignal = new CacheGetter(() => {
    const signal = this._createSignal<$OnIpcEventMessage>(false);
    this.onMessage((event, ipc) => {
      if (event.type === IPC_MESSAGE_TYPE.EVENT) {
        signal.emit(event, ipc);
      }
    });
    return signal;
  });

  onEvent(cb: $OnIpcEventMessage) {
    return this._onEventSignal.value.listen(cb);
  }
  // lifecycle start
  private _lifeCycleSignal = new CacheGetter(() => {
    const signal = this._createSignal<$OnIpcLifeCycleMessage>(false);
    this.onMessage((event, ipc) => {
      if (event.type === IPC_MESSAGE_TYPE.LIFE_CYCLE) {
        signal.emit(event, ipc);
      }
    });
    return signal;
  });

  onLifeCycle(cb: $OnIpcLifeCycleMessage) {
    return this._lifeCycleSignal.value.listen(cb);
  }

  lifeCycleHook() {
    // TODO 跟对方通信 协商数据格式
    this.onLifeCycle((lifeCycle, ipc) => {
      console.log("lifeCycleHook=>", lifeCycle.state);
      switch (lifeCycle.state) {
        // 收到打开中的消息，也告知自己已经准备好了
        case IPC_STATE.OPENING: {
          ipc.postMessage(new IpcLifeCycle(IPC_STATE.OPEN));
          break;
        }
        // 收到对方完成开始建立连接
        case IPC_STATE.OPEN: {
          if (!ipc.startDeferred.is_resolved) {
            ipc.startDeferred.resolve(lifeCycle);
          }
          break;
        }
        // 消息通道开始关闭
        case IPC_STATE.CLOSING: {
          ipc.closing();
          break;
        }
        // 对方关了，代表没有消息发过来了，我也关闭
        case IPC_STATE.CLOSED: {
          ipc.close();
        }
      }
    });
  }

  // lifecycle end

  private _errorSignal = new CacheGetter(() => {
    const signal = this._createSignal<$OnIpcErrorMessage>(false);
    this.onMessage((event, ipc) => {
      if (event.type === IPC_MESSAGE_TYPE.ERROR) {
        signal.emit(event, ipc);
      }
    });
    return signal;
  });

  onError(cb: $OnIpcErrorMessage) {
    return this._errorSignal.value.listen(cb);
  }

  private _req_id_acc = 0;
  allocReqId(_url?: string) {
    return this._req_id_acc++;
  }
  private __reqresMap = new CacheGetter(() => {
    const reqresMap = new Map<number, PromiseOut<IpcResponse>>();
    this.onMessage((message) => {
      if (message.type === IPC_MESSAGE_TYPE.RESPONSE) {
        const response_po = reqresMap.get(message.req_id);
        if (response_po) {
          reqresMap.delete(message.req_id);
          response_po.resolve(message);
        } else {
          throw new Error(`no found response by req_id: ${message.req_id}`);
        }
      }
    });
    return reqresMap;
  });
  private get _reqresMap() {
    return this.__reqresMap.value;
  }

  private _buildIpcRequest(url: string, init?: $IpcRequestInit) {
    const req_id = this.allocReqId();
    const ipcRequest = IpcRequest.fromRequest(req_id, this, url, init);
    return ipcRequest;
  }

  /** 发起请求并等待响应 */
  request(url: IpcRequest): Promise<IpcResponse>;
  request(url: string, init?: $IpcRequestInit): Promise<IpcResponse>;
  request(input: string | IpcRequest, init?: $IpcRequestInit) {
    const ipcRequest = input instanceof IpcRequest ? input : this._buildIpcRequest(input, init);
    const result = this.registerReqId(ipcRequest.req_id);
    this.postMessage(ipcRequest);
    return result.promise;
  }
  /** 自定义注册 请求与响应 的id */
  registerReqId(req_id = this.allocReqId()) {
    return mapHelper.getOrPut(this._reqresMap, req_id, () => new PromiseOut());
  }

  /**
   * 代理管道 发送数据 与 接收数据
   * @param channel
   */
  async pipeToChannel(channelId: string, channel: PureChannel) {
    await pureChannelToIpcEvent(channelId, this, channel, channel.income.controller, channel.outgoing.stream, () =>
      channel.afterStart()
    );
  }
  /**
   * 代理管道 发送数据 与 接收数据
   * @param channel
   */
  async pipeFromChannel(channelId: string, channel: PureChannel) {
    await pureChannelToIpcEvent(channelId, this, channel, channel.outgoing.controller, channel.income.stream, () =>
      channel.start()
    );
  }

  // private readyListener = once(async () => {
  //   const ready = new PromiseOut<IpcEvent>();
  //   this.onEvent((event, ipc) => {
  //     if (event.name === "ping") {
  //       ipc.postMessage(new IpcEvent("pong", event.data, event.encoding));
  //     } else if (event.name === "pong") {
  //       ready.resolve(event);
  //     }
  //   });
  //   (async () => {
  //     let timeDelay = 50;
  //     while (!ready.is_resolved && !this.isClosed && timeDelay < 5000) {
  //       this.postMessage(IpcEvent.fromText("ping", ""));
  //       await PromiseOut.sleep(timeDelay).promise;
  //       timeDelay *= 3;
  //     }
  //   })();
  //   return await ready.promise;
  // });
  // ready() {
  //   return this.readyListener();
  // }

  // 标记是否启动完成
  startDeferred = new PromiseOut<IpcLifeCycle>();
  isActivity = this.startDeferred.is_resolved;
  awaitStart = this.startDeferred.promise;
  // 告知对方我启动了
  start() {
    this.ipcLifeCycleState = IPC_STATE.OPEN;
    this.postMessage(new IpcLifeCycle(IPC_STATE.OPENING));
  }

  closing() {
    if (this.ipcLifeCycleState !== IPC_STATE.CLOSING) {
      this.ipcLifeCycleState = IPC_STATE.CLOSING;
      // TODO 这里是缓存消息处理的最后时间区间
      this.close();
    }
  }

  /**----- close start*/
  abstract _doClose(): void;

  private _closed = this.ipcLifeCycleState == IPC_STATE.CLOSED;
  close() {
    if (this._closed) {
      return;
    }
    this.ipcLifeCycleState = IPC_STATE.CLOSED;
    this._closed = true;
    this._doClose();
    this._closeSignal.emitAndClear();
  }
  get isClosed() {
    return this._closed;
  }
  /**----- close end*/
}
export type $IpcRequestInit = {
  method?: string;
  body?: /* json+text */
  | null
    | string
    /* base64 */
    | Uint8Array
    /* stream+base64 */
    | Blob
    | ReadableStream<Uint8Array>;
  headers?: IpcHeaders | HeadersInit;
};
