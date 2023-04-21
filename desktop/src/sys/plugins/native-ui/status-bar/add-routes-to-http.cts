import type { $BootstrapContext } from "../../../../core/bootstrapContext.cjs"
import type { Ipc } from "../../../../core/ipc/ipc.cjs";
import type { StatusbarNativeUiNMM } from "./status-bar.main.cjs"
import type { $RequestDistributeIpcEventData } from "../../base/base-add-routes-to-http.cjs"
import { routes } from "./route.cjs"
import type { IpcEvent } from "../../../../core/ipc/IpcEvent.cjs";
import { converRGBAToHexa } from "../../helper.cjs"
import { log } from "../../../../helper/devtools.cjs"
import querystring from "node:querystring"
import url from "node:url"
import { BaseAddRoutesToHttp } from "../../base/base-add-routes-to-http.cjs"

/**
 * 向 http.sys.dweb 注册路由的类
 */
export class AddRoutesToHttp extends BaseAddRoutesToHttp<StatusbarNativeUiNMM>{

  constructor(
    nmm: StatusbarNativeUiNMM,
    context:  $BootstrapContext,
  ){
    super(nmm, context, routes)
  }

  _httpIpcOnEventRequestDistribute = async (ipcEvent: IpcEvent, httpIpc: Ipc) => {
    const data = this.creageRequestDistributeIpcEventData(ipcEvent.data)
    const pathname = url.parse(data.url).pathname;
    switch(pathname){
      case "/status-bar.nativeui.sys.dweb/getState":
        this._httpIpcOnEventRequestDistributeGetState(data, httpIpc);
        break;
      case "/status-bar.nativeui.sys.dweb/setState":
        this._httpIpcOnEventRequestDistributeSetState(data, httpIpc);
        break;
      case "/status-bar.nativeui.sys.dweb/startObserve":
        this._httpIpcOnEventRequestDistributeStartObserve(data, httpIpc);
        break;
      case "/status-bar.nativeui.sys.dweb/stopObserve":
        this._httpIpcOnEventRequestDistributeStopObserve(data, httpIpc);
        break;
      case "/status-bar-ui/wait_for_operation":
        this._httpIpcOnEventRequestDistributeWaitForOperationBase(data, httpIpc);
        break;
      case "/status-bar-ui/operation_return":
        this._httpIpcOnEventRequestDistributeOperationReturnBase(data, httpIpc)
        // this._httpIpcOnEventRequestDistributeOperationReturn(data, httpIpc);
        break;
      case "/internal/observe":
        this._httpIpcOnEventRequestDistributeInternalObserve(data, httpIpc);
        break;
      default: throw new Error(`${this._nmm.mmid} http-connect _httpIpcOnEventRequestDistribute 还有没有处理的路由 ${pathname}`)
    }
  }
}