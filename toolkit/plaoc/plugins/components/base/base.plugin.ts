import { IpcPool } from "../../../../dweb-core/index.ts";
import { X_PLAOC_QUERY } from "../../common/const.ts";
import { createSignal } from "../../helper/createSignal.ts";
import { $BuildRequestInit, buildRequest } from "../../helper/request.ts";
import type { $BuildChannelWithBaseInit, $BuildRequestWithBaseInit, $MMID } from "./base.type.ts";

export const webIpcPool = new IpcPool(`web-${location.host}`);

export abstract class BasePlugin {
  private static urlData = new URLSearchParams(location.search);
  static api_url = location.origin.replace("//www", "//api");
  static external_url = BasePlugin.getUrl(X_PLAOC_QUERY.EXTERNAL_URL);

  constructor(readonly mmid: $MMID) {}

  fetchApi(url: string, init?: $BuildRequestInit) {
    return this.buildApiRequest(url, init).fetch();
  }
  buildApiRequest(pathname: string, init?: $BuildRequestWithBaseInit) {
    const url = new URL(init?.base ?? BasePlugin.api_url);
    url.pathname = `${init?.pathPrefix ?? this.mmid}${pathname}`;
    return buildRequest(url, init);
  }

  async buildChannel(pathname: string, init?: $BuildChannelWithBaseInit) {
    const url = this.buildApiRequest(pathname, init).url.replace("http", "ws");
    const ws = new WebSocket(url);
    ws.binaryType = init?.binaryType ?? "blob";
    await new Promise((resolve, reject) => {
      ws.onopen = resolve;
      ws.onerror = reject;
      ws.onclose = reject;
    });

    return ws;
  }

  protected createSignal = createSignal;
  /**
   * 获取指定的url
   * @param urlType
   * @returns
   */
  static getUrl(urlType: X_PLAOC_QUERY) {
    const url = this.urlData.get(urlType) || localStorage.getItem("url:" + urlType);
    if (url === null) {
      console.error(`unconfig url: ${urlType}`);
      return "";
    }
    localStorage.setItem("url:" + urlType, url);
    return url;
  }
}

if (typeof HTMLElement !== "function") {
  Object.assign(globalThis, { HTMLElement: class HTMLElement {}, customElements: { define: () => {} } });
}
