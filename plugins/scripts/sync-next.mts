import { SyncTask } from "./helper/SyncTask.mts";

const syncTask = new SyncTask(
  {
    from: import.meta.resolve("../"),
    to: import.meta.resolve("../../next/dweb-browser/src/Resources/Assets/"),
  },
  [{ from: "../example/vue3/dist", to: "cot-demo" }]
);
syncTask.auto();
