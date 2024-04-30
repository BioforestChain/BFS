import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { rootResolve } from "../../scripts/helper/resolver.ts";

const jmmAppDirname = path.resolve(
  os.userInfo().homedir,
  "Library/Application Support/dweb-browser/data/jmm.browser.dweb/apps/"
);

for (const appId of fs.readdirSync(jmmAppDirname)) {
  const target = path.resolve(jmmAppDirname, appId, "usr/server");
  if (fs.existsSync(target)) {
    fs.rmdirSync(target, { recursive: true });
    fs.symlinkSync(rootResolve("./npm/@plaoc__server/dist/dev"), target);
    console.log("hacked", target);
  }
}
//  `/Users/kzf/Library/Application Support/dweb-browser/data/jmm.browser.dweb/apps/`
