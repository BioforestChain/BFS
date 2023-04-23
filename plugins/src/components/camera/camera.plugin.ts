import { bindThis } from "../../helper/bindThis.ts";
import { BasePlugin } from "../base/BasePlugin.ts";
import type { ImageOptions } from "./camera.type.ts";

export class CameraPlugin extends BasePlugin {
  readonly tagName = "dweb-camera";

  constructor() {
    super("camera.sys.dweb");
  }
  /**
   * 打开相册
   */
  @bindThis
  async getPhoto(options: ImageOptions) {
    console.log("--");
    return await this.fetchApi("/getPhoto", {
      search: {
        resultType: options.resultType,
        source: options.source,
        quality: options.quality,
      },
    })
      // .then(
      //   res => console.log('res: ', res),
      //   err => console.log('err: ', err)
      // )
      .binary();
  }
}

export const cameraPlugin = new CameraPlugin();
