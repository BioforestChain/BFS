import { webcrypto } from "node:crypto";
import process from "node:process";
import { BrowserNMM } from "./browser/browser/browser.ts";
import { JsProcessNMM } from "./browser/js-process/js-process.ts";
import { MultiWebviewNMM } from "./browser/multi-webview/multi-webview.nmm.ts";
import { setFilter } from "./helper/devtools.ts";
import { BluetoothNMM } from "./std/bluetooth/bluetooth.main.ts";
import { HttpServerNMM } from "./std/http/http.nmm.ts";
import { BarcodeScanningNMM } from "./sys/barcode-scanning/barcode-scanning.main.ts";
import { BootNMM } from "./sys/boot/boot.ts";
import { DeviceNMM } from "./sys/device/device.main.ts";
import { DnsNMM } from "./sys/dns/dns.ts";
import "./sys/dns/localeFileFetch.ts";
// import { NavigationBarNMM } from "./browser/native-ui/navigation-bar/navigation-bar.main.ts";
// import { SafeAreaNMM } from "./browser/native-ui/safe-area/safe-area.main.ts";
// import { StatusbarNativeUiNMM } from "./browser/native-ui/status-bar/status-bar.main.ts";
// import { TorchNMM } from "./browser/native-ui/torch/torch.main.ts";
// import { VirtualKeyboardNMM } from "./browser/native-ui/virtual-keyboard/virtual-keyboard.main.ts";
// import { ToastNMM } from "./sys/toast/toast.main.ts";
// import { ShareNMM } from "./sys/share/share.main.ts";
// import { HapticsNMM } from "./sys/haptics/haptics.main.ts";

/**
 * 设置 debugger 过滤条件
 * 预设的值
 * "micro-module/native"
 * "http"
 * "http/dweb-server",
 * 'http/port-listener'
 * "mm"
 * "jmm"
 * "jsmm"
 * "mwebview"
 * "dns"
 * "browser"
 * "error"
 *
 * "jsProcess"
 *
 * "biometrices"
 *
 *
 * "sender/init"
 * "sender/pulling"
 * "sender/read"
 * "sender/end"
 * "sender/pull-end"
 * "sender/use-by"
 *
 * "receiver/data"
 * "receiver/end"
 * "receiver/pull"
 * "receiver/start"
 * "maphelper"
 */
// 设置 console 过滤条件
setFilter(["error", "browser", "mwebveiw", ""]);

export const dns = new DnsNMM();
dns.install(new MultiWebviewNMM());
dns.install(new JsProcessNMM());
dns.install(new HttpServerNMM());
dns.install(new DeviceNMM());
const dwebBrowser = new BrowserNMM();
dns.install(dwebBrowser);
// dns.install(new StatusbarNativeUiNMM());
// dns.install(new NavigationBarNMM());
// dns.install(new SafeAreaNMM());
// dns.install(new VirtualKeyboardNMM());
// dns.install(new ToastNMM());
// dns.install(new TorchNMM());
// dns.install(new ShareNMM());
// dns.install(new HapticsNMM());
dns.install(new BarcodeScanningNMM());
// dns.install(new BiometricsNMM());
dns.install(new BluetoothNMM());

import { DesktopNMM } from "./browser/desktop/desktop.nmm.ts";
import { JmmNMM } from "./browser/jmm/jmm.ts";
const jmm = new JmmNMM();
dns.install(jmm);
const dwebDesktop = new DesktopNMM();
dns.install(dwebDesktop);

const custom_boot = process.argv.find((arg) => arg.startsWith("--boot="))?.slice("--boot=".length) as "*.dweb";

dns.install(
  new BootNMM([
    /// 一定要直接启动jmm，这样js应用才会被注册安装
    jmm.mmid,

    /// 启动自定义模块，或者桌面模块
    custom_boot ?? dwebDesktop.mmid,
  ])
);

Object.assign(globalThis, { dns: dns });
process.on("unhandledRejection", (error: Error) => {
  console.error("on unhandledRejection=>", error);
});

if (typeof crypto === "undefined") {
  Object.assign(globalThis, { crypto: webcrypto });
}
