import picocolors from "npm:picocolors";
import { createBaseResolveTo } from "./ConTasks.helper.ts";
import { WalkAny } from "./WalkDir.ts";
import { whichSync } from "./WhichCommand.ts";

let defaultResolveTo = createBaseResolveTo(Deno.cwd());
let preCwd = Deno.cwd();
export const $ = Object.assign(
  async (cmd: string | string[], cwd?: string | URL, options: { useWhich?: boolean } = {}) => {
    const { useWhich = false } = options;
    if (typeof cmd === "string") {
      cmd = cmd.split(/\s+/);
    }
    const [exec, ...args] = cmd;
    const cmdWhich = useWhich ? (exec.startsWith("./") ? exec : whichSync(exec)) : exec;
    cwd = defaultResolveTo(cwd ?? "./");
    if (preCwd !== cwd) {
      preCwd = cwd;
      console.log(picocolors.green(">"), picocolors.magenta(picocolors.bold("cd")), picocolors.magenta(cwd));
    }
    console.log(picocolors.green(">"), picocolors.magenta(picocolors.bold(exec)), picocolors.magenta(args.join(" ")));
    const command = new Deno.Command(cmdWhich!, { args, cwd, stdout: "inherit", env: Deno.env.toObject() });
    await command.output();
  },
  {
    cd: (dir: string | URL) => {
      defaultResolveTo = createBaseResolveTo(defaultResolveTo(dir));
    },
    pwd: () => defaultResolveTo(),
    ls: (dir: string | URL) => WalkAny(defaultResolveTo(dir)),
  }
);
