import type {
  $Ipc,
  $IpcRequest,
  $MMID,
  IpcPool,
} from "../deps.ts";
import { PureBinaryFrame, ReadableStreamEndpoint, ReadableStreamOut, streamRead } from "../deps.ts";
import { merge } from "./merge.ts";

type CreateDuplexIpcType = (
  ipcPool: IpcPool,
  subdomain: string,
  mmid: $MMID,
  ipcRequest: $IpcRequest,
  onClose: () => unknown
) => $Ipc;

export const createDuplexIpc: CreateDuplexIpcType = (
  ipcPool: IpcPool,
  subdomain: string,
  mmid: $MMID,
  ipcRequest: $IpcRequest,
  onClose: () => unknown
) => {
  const remote = {
    mmid: mmid,
    name: `${subdomain}.${mmid}`,
    ipc_support_protocols: {
      cbor: false,
      protobuf: false,
      json: false,
    },
    dweb_deeplinks: [],
    categories: [],
  };
  const endpoint = new ReadableStreamEndpoint(`${mmid}-plaoc-external-duplex`);
  const streamIpc = ipcPool.createIpc(endpoint, 0, remote, remote, true);

  const incomeStream = new ReadableStreamOut<Uint8Array>();
  // 拿到自己前端的channel
  const pureServerChannel = ipcRequest.getChannel();
  pureServerChannel.start();

  // fetch(https://ext.dweb) => ipcRequest => streamIpc.request => streamIpc.postMessage => chunk => outgoing => ws.onMessage
  void (async () => {
    // 拿到网络层来的外部消息，发到前端处理
    for await (const chunk of streamRead(endpoint.stream)) {
      pureServerChannel.outgoing.controller.enqueue(
        new PureBinaryFrame(merge(chunk))
      );
    }
  })();
  // ws.send => income.pureFrame =>
  void (async () => {
    //  绑定自己前端发送的数据通道
    for await (const pureFrame of streamRead(pureServerChannel.income.stream)) {
      if (pureFrame instanceof PureBinaryFrame) {
        incomeStream.controller.enqueue(pureFrame.data);
      }
    }
  })();

  void endpoint.bindIncomeStream(incomeStream.stream).finally(() => {
    onClose();
  });
  return streamIpc;
};