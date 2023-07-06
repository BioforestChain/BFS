import { bindThis } from "../../helper/bindThis.ts";
import { domInsetsToJson, insetsToDom } from "../../util/insets.ts";
import { $Coder } from "../../util/StateObserver.ts";
import { InsetsPlugin } from "../base/InsetsPlugin.ts";
import {
  $SafeAreaRawState,
  $SafeAreaState,
  $SafeAreaWritableState,
} from "./safe-area.type.ts";

export class SafeAreaPlugin extends InsetsPlugin<
  $SafeAreaRawState,
  $SafeAreaState,
  $SafeAreaWritableState
> {
  constructor() {
    super("safe-area.nativeui.browser.dweb");
  }

  readonly coder: $Coder<$SafeAreaRawState, $SafeAreaState> = {
    decode: (raw) => ({
      ...this.baseCoder.decode(raw),
      cutoutInsets: insetsToDom(raw.cutoutInsets),
      outerInsets: insetsToDom(raw.outerInsets),
    }),
    encode: (state) => ({
      ...this.baseCoder.encode(state),
      cutoutInsets: domInsetsToJson(state.cutoutInsets),
      outerInsets: domInsetsToJson(state.outerInsets),
    }),
  };

  @bindThis
  async setState(state: Partial<$SafeAreaWritableState>) {
    await this.commonSetState(state);
  }
  @bindThis
  setStateByKey<K extends keyof $SafeAreaWritableState>(
    key: K,
    value: $SafeAreaWritableState[K]
  ) {
    return this.setState({ [key]: value });
  }
  override get getState() {
    return this.state.getState;
  }
  @bindThis
  override setOverlay(overlay: boolean) {
    return this.setStateByKey("overlay", overlay);
  }
  @bindThis
  override async getOverlay() {
    return (await this.state.getState()).overlay;
  }
}

export const safeAreaPlugin = new SafeAreaPlugin();
