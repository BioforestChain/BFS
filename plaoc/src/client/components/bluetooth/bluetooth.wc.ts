import { bluetoothPlugin } from "./bluetooth.plugin.ts"
export class HTMLBluetoothElement extends HTMLElement{
  static readonly tagName = "dweb-bluetooth";
  plugin = bluetoothPlugin;

  requestDevice = () => {
    return this.plugin.requestDevice();
  }

  requestDeviceCancel = () => {
    return this.plugin.requestDeviceCancel();
  }

  // toggle(isOpen: boolean){
  //   return this.plugin.toggle(isOpen)
  // }

  // selected(device: $Device){
  //   return this.plugin.selected(device)
  // }
}

// 注册
customElements.define(
  HTMLBluetoothElement.tagName,
  HTMLBluetoothElement
)

