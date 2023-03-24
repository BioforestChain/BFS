import { PromiseOut } from "../../helper/PromiseOut.mjs";
import type { ViewTree } from "../../sys/multi-webview/assets/multi-webview.html.mjs";
import { EVENT } from "./cotDemo.event.mjs";
import { cros, onApiRequest } from "./cotDemo.request.mjs";



const main = async () => {
  const { IpcEvent } = ipc;
  const mainUrl = new PromiseOut<string>();
  const webviewSet = new Map<string, ViewTree>()

  const tryOpenView = async (webview_id?: string) => {
    console.log("tryOpenView", webview_id)
    if (webview_id && webviewSet.has(webview_id)) {
      const result = await jsProcess
        .nativeFetch(
          `file://mwebview.sys.dweb/reOpen?webview_id=${encodeURIComponent(webview_id)}`
        )
        .text();
      return result
    }
    // open 
    const url = await mainUrl.promise;
    const view_id = await jsProcess
      .nativeFetch(
        `file://mwebview.sys.dweb/open?url=${encodeURIComponent(url)}`
      )
      .text();
    if (webviewSet.size == 0) {
      // const mwebviewIpc = await jsProcess.connect("mwebview.sys.dweb");
      // Object.assign(globalThis, { mwebviewIpc });
      // mwebviewIpc.onEvent((event) => {
      //   console.log("cotDemo#got event:", event.name, event.text);
      //   if (event.name === EVENT.State) {

      //   }
      // });

    }

    return view_id
  };
  let hasActivity = false;
  /// 根据桌面协议，收到activity后就会被唤醒
  jsProcess.onConnect((ipc) => {

    ipc.onEvent(async (event) => {
      console.log("cotDemo.worker => ", event.name, event.text);
      if (event.name === "activity" && typeof event.data === "string") {
        hasActivity = true;
        const view_id = await tryOpenView(event.data);
        console.log("cotDemo.worker => activity", view_id);
        ipc.postMessage(IpcEvent.fromText("ready", view_id));
        return
      }
    });
  });

  console.log("[cotDemo.worker.mts] main");
  const { IpcResponse, IpcHeaders } = ipc;

  // createHttpDwebServer 的本质是通过 jsProcess 这个模块向
  // 发起一个 file://http.sys.dweb/start 请求
  // 现在的问题是 这个请求 http.sys.dweb/start 并没有收到
  // 需要解决这个问题
  // 但是 启动这个模块的时候却触发这个请求

  //http.createHttpDwebServer(jsProcess, {}) === jsProcess.nativeFetch()
  // jsProcess.nativeFetch() === jsProcess._nativeFetch()
  // jsProcess._nativeFetch() === this.fetchIpc.request()
  // this.fetchIpc.request() === this.nativeFetchPort.postMessage()
  // this.nativeFetchPort === js-process.web.mts 发送过来的port
  // js-process.web.mts 发送过来的post === js-process.cts createProcess() 发送过来的port
  // js-process.cts createProcess() 发送过来的是port2, port1 还是保存在 ipc_to_worker 对象里面
  // ipc_to_worker.onMessage 会把消息 通过 ipc 转发出去
  // ipc js-process.cts 调用createProcessAndRun（） 传递进来的
  // createProcessAndRun() 传递来的ipc 是通过 registerCommonIpcOnMessageHandler. /create-process 传递进来的ipc
  // registerCommonIpcOnMessageHandler. /create-process  是通过 micro-module.js.cts. streamIpc.bindIncomeStream 中调用 调用传递过来的ipc
  // 到这里为止 cotdemo.worker.mts 发送的消息会转发到 micro-module.js.cts 中 卡在这里了没有接受到消息
  // micro-module.js.cts 需要注册 this.onConnect() 把消息转发给指定的 nmm 模块
  // 消息的扭转
  // http.createHttpDwebServer(jsProcess, {}) === jsProcess.nativeFetch() 本质上就是通过 jsProcess.worker 向 http.sys.dweb 发送一个创建服务的消息
  // js 对 nmm 模块的访问方法1
  // appworker -> jsProcess.worker ->jsProcess.cts -> micro-module.js.cts 也就是 JsMicroModule 模块 -> 在转发给相应的 nmm 模块
  // 最终实现对 nmm 模块的访问

  const wwwServer = await http.createHttpDwebServer(jsProcess, {
    subdomain: "www",
    port: 443,
  });

  const apiServer = await http.createHttpDwebServer(jsProcess, {
    subdomain: "api",
    port: 443,
  });

  (await apiServer.listen()).onRequest(async (request, ipc) => {
    onApiRequest(apiServer.startResult.urlInfo, request, ipc);
  });

  // await sleep(5000)
  // await wwwServer.listen();
  (await wwwServer.listen()).onRequest(async (request, ipc) => {
    let pathname = request.parsed_url.pathname;
    if (pathname === "/") {
      pathname = "/index.html";
    }

    console.time(`open file ${pathname}`);

    const remoteIpcResponse = await jsProcess.nativeRequest(
      `file:///cot-demo${pathname}?mode=stream`
    );
    console.timeEnd(`open file ${pathname}`);
    // console.log(`${remoteIpcResponse.statusCode} ${JSON.stringify(remoteIpcResponse.headers.toJSON())}`)
    /**
     * 流转发，是一种高性能的转发方式，等于没有真正意义上去读取response.body，
     * 而是将response.body的句柄直接转发回去，那么根据协议，一旦流开始被读取，自己就失去了读取权。
     *
     * 如此数据就不会发给我，节省大量传输成本
     */
    ipc.postMessage(
      new IpcResponse(
        request.req_id,
        remoteIpcResponse.statusCode,
        cros(remoteIpcResponse.headers),
        remoteIpcResponse.body,
        ipc
      )
    );
  });

  {
    const interUrl = wwwServer.startResult.urlInfo.buildInternalUrl((url) => {
      url.pathname = "/index.html";
    }).href;
    mainUrl.resolve(interUrl);
    // 如果没有被 browser 激活，那么也尝试自启动
    if (hasActivity === false) {
      await tryOpenView();
    }

    // const windowHanlder = mwebview.openWindow(interUrl);
    // windowHanlder.onClose(() => {});

    // jsProcess.fetchIpc.onEvent((event) => {});
  }
  {
    // const mwebviewIpc = await jsProcess.connect("mwebview.sys.dweb");
    // Object.assign(globalThis, { mwebviewIpc });
    // mwebviewIpc.onEvent((event) => {
    //   console.log("got event:", event.name, event.text);
    //   if (event.name === "close") {
    //     wwwServer.close()
    //     apiServer.close()
    //   }
    // });
  }
};

main();
