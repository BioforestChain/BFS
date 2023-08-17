import { assetsTasks } from "../desktop-dev/scripts/assets-tasks.ts";
import { ConTasks, ExitAbortController } from "./helper/ConTasks.ts";
export const devTasks = new ConTasks(
  {
    "plaoc:server": {
      cmd: "deno",
      args: "task build:watch:server",
      cwd: "./plaoc",
    },
    "plaoc:demo": {
      cmd: "deno",
      args: "task build:watch:demo",
      cwd: "./plaoc",
    },
    sync: {
      cmd: "deno",
      args: "task sync --watch",
    },
    "toolkit:fort-test-image": {
      cmd: "vite",
      args: "--host 0.0.0.0",
      cwd: "./toolkit/for-test-images",
    },
  },
  import.meta.resolve("../")
).merge(assetsTasks, "assets:");

if (import.meta.main) {
  Deno.addSignalListener("SIGINT", () => {
    ExitAbortController.abort();
    Deno.exit();
  });

  // /// 首先取保 init 任务执行完成
  // await initTasks.spawn([]).afterComplete();
  /// 开始执行，强制使用开发模式进行监听
  devTasks.spawn([...Deno.args, "--dev"]);
}
