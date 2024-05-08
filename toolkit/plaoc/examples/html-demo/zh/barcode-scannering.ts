import { barcodeScannerPlugin } from "@plaoc/plugins";
import { error, success } from "./debug";
import { b64toBlob } from "./util/util";
export const process = async () => {
  const result = await barcodeScannerPlugin.process(await b64toBlob(codeImg));
  if (result.length == 1 && result[0] === "https://dweb.waterbang.top") {
    return success("process: pass!");
  }
  error("process error");
};

const codeImg =
  "data:image/png;base64,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";
