import { cacheGetter } from "../../helper/cacheGetter.ts";
import { CameraDirection } from "../camera/camera.type.ts";
import { CloseWatcher } from "../close-watcher/index.ts";
import { barcodeScannerPlugin } from "./barcode-scanning.plugin.ts";
import {
  BarcodeScannerPermission,
  ScanResult,
  SupportedFormat,
} from "./barcode-scanning.type.ts";

export class HTMLDwebBarcodeScanningElement extends HTMLElement {
  static readonly tagName = "dweb-barcode-scanning";
  readonly plugin = barcodeScannerPlugin;

  private _video: HTMLVideoElement | null = null;
  private _canvas: HTMLCanvasElement | null = null;
  private _formats = SupportedFormat.QR_CODE;
  private _direction: string = CameraDirection.BACK;
  private _activity = false;
  private _isCloceLock = false;

  constructor() {
    super();
    this.createClose();
  }

  private createClose() {
    const closer = new CloseWatcher();
    this._isCloceLock = true;
    closer.addEventListener("close", (_event) => {
      this._isCloceLock = false;
      if (this._activity) {
        this.stopScanning();
      }
    });
  }

  /**
   * 返回扫码页面DOM
   * 根据这个DOM 用户可以自定义样式
   * @returns HTMLElement
   */
  @cacheGetter()
  get getView() {
    if (this._video) {
      return this._video.parentElement;
    }
    return null;
  }

  @cacheGetter()
  get process() {
    return this.plugin.process;
  }
  @cacheGetter()
  get stop() {
    return this.plugin.stop;
  }

  /**
   * 启动扫码
   * @returns
   */
  async startScanning(
    rotation = 0,
    formats = SupportedFormat.QR_CODE
  ): Promise<ScanResult> {
    if (!this._isCloceLock) {
      this.createClose();
    }
    await this.createElement();
    const permission = await this._startVideo();
    let data: string[] = [];
    if (permission === BarcodeScannerPermission.UserAgree) {
      data = await this.taskPhoto(rotation, formats);
    }
    return {
      hasContent: data.length !== 0,
      content: data,
      permission,
    };
  }
  /**
   * 停止扫码
   */
  stopScanning() {
    this._activity = false;
    this.stopCamera("user stop");
  }

  // deno-lint-ignore no-explicit-any
  private stopCamera(error: any) {
    console.error(error);
    this._stop();
  }

  /**
   * 不断识图的任务
   * @returns
   */
  taskPhoto(rotation: number, formats: SupportedFormat): Promise<string[]> {
    this._activity = true;
    return new Promise((resolve, reject) => {
      const task = () => {
        if (!this._canvas) {
          console.error("service close！");
          return resolve([]);
        }
        if (!this._activity) {
          console.error("user close！");
          return resolve([]);
        }
        this._canvas.toBlob(
          async (imageBlob) => {
            if (imageBlob) {
              const value = await this.plugin
                .process(imageBlob, rotation, formats)
                .then((res) => res)
                .catch(() => {
                  this._activity = false;
                  return reject("502 service error");
                });
              const result = Array.from(value ?? []);
              if (result.length > 0) {
                this.stopCamera(result);
                this._activity = false;
                return resolve(result);
              }
              return task();
            }
          },
          "image/jpeg",
          0.5 // lossy compression
        );
      };
      task();
    });
  }

  /**
   * 启动摄像
   * @returns
   */
  private async _startVideo() {
    // 判断是否支持
    if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
      const constraints: MediaStreamConstraints = {
        video: { facingMode: this._direction },
      };
      return await navigator.mediaDevices
        .getUserMedia(constraints)
        .then(async (stream) => {
          await this.gotMedia(stream);
          return BarcodeScannerPermission.UserAgree;
        })
        .catch((e) => {
          console.error(
            "You need to authorize the camera permission to use the scan code!",
            e
          );
          this.stopScanning();
          return BarcodeScannerPermission.UserReject;
        });
    } else {
      this.stopScanning();
      console.error("Your browser does not support scanning code!");
      return BarcodeScannerPermission.UserError;
    }
  }

  /**
   * 拿到帧对象
   * @param mediastream
   */
  private async gotMedia(mediastream: MediaStream) {
    if (!this._video) {
      throw new Error("not create video");
    }
    this._video.srcObject = mediastream;
    const videoTracks = mediastream.getVideoTracks();
    if (videoTracks.length > 0 && this._canvas) {
      this._canvas.captureStream(100);
      const ctx = this._canvas.getContext("2d");
      // 压缩为 100 * 100
      const update = () =>
        requestAnimationFrame(() => {
          if (ctx && this._video) {
            ctx.drawImage(
              this._video,
              0,
              0,
              this._canvas?.width ?? 480,
              this._canvas?.height ?? 360
            );
            update();
          }
        });
      update();
    }
    await this._video.play();
    this._video?.parentElement?.setAttribute(
      "style",
      `
      position:fixed; top: 0; left: 0; width:100%; height: 100%; background-color: black;
      -webkit-transition:all 0.2s linear;
      -moz-transition:all 0.2s linear;
      -ms-transition:all 0.2s linear;
      -o-transition:all 0.2s linear;
      transition:all 0.2s linear;
      visibility: visible;`
    );
  }

  private _stop() {
    if (this._video) {
      this._video.pause();

      const st = this._video.srcObject;
      if (st) {
        // deno-lint-ignore no-explicit-any
        const tracks = (st as any).getTracks();

        for (let i = 0; i < tracks.length; i++) {
          const track = tracks[i];
          track.stop();
        }
      }

      this._video.parentElement?.remove();
      this._video = null;
    }
    if (this._canvas) {
      this._canvas
        .getContext("2d")
        ?.clearRect(0, 0, this._canvas.width, this._canvas.height);
      this._canvas = null;
    }
  }

  /**
   * 创建video
   * @param direction
   * @returns
   */
  private createElement(direction: CameraDirection = CameraDirection.BACK) {
    return new Promise((resolve, reject) => {
      const body = document.body;

      const video = document.getElementById("video");
      const canvas = document.getElementById("canvas");

      if (video) {
        reject("camera already started");
        return { message: "camera already started" };
      }
      const parent = document.createElement("div");
      parent.setAttribute(
        "style",
        "position:fixed; top: 0; left: 0; width:100%; height: 100%; background-color: black;visibility: hidden;"
      );
      this._video = document.createElement("video");
      this._video.id = "video";

      // 如果摄像头朝后，请勿翻转视频源
      if (direction !== CameraDirection.BACK) {
        this._video.setAttribute(
          "style",
          "-webkit-transform: scaleX(-1); transform: scaleX(-1); width:100%; height: 100%;"
        );
      } else {
        this._video.setAttribute("style", "width:100%; height: 100%;");
      }
      this._video.setAttribute("autoplay", "true");

      const userAgent = navigator.userAgent.toLowerCase();
      const isSafari =
        userAgent.includes("safari") && !userAgent.includes("chrome");

      // iOS 上的 Safari 需要设置 autoplay、muted 和 playsinline 属性，video.play() 才能成功
      // 如果没有这些属性，this.video.play() 将抛出 NotAllowedError
      // https://developer.apple.com/documentation/webkit/delivering_video_content_for_safari
      if (isSafari) {
        this._video.setAttribute("muted", "true");
        this._video.setAttribute("playsinline", "true");
      }

      parent.appendChild(this._video);
      if (!canvas) {
        this._canvas = document.createElement("canvas");
        this._canvas.setAttribute("style", "visibility: hidden;");
        this._canvas.width = 480;
        this._canvas.height = 360;
        this._canvas.id = "canvas";
        parent.appendChild(this._canvas);
      }
      body.appendChild(parent);
      resolve(true);
    });
  }

  getSupportedFormats() {
    return this.plugin.getSupportedFormats();
  }
}

customElements.define(
  HTMLDwebBarcodeScanningElement.tagName,
  HTMLDwebBarcodeScanningElement
);
declare global {
  interface HTMLElementTagNameMap {
    [HTMLDwebBarcodeScanningElement.tagName]: HTMLDwebBarcodeScanningElement;
  }
}
