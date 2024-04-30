import fs from "node:fs";
import path from "node:path";
import ignore from "npm:ignore";
import { normalizeFilePath } from "./WalkDir.ts";

export class IgnoreGlob {
  #rules;
  get rules() {
    return Object.freeze(this.#rules.slice());
  }
  #ignore;
  constructor(rules: string[], readonly cwd: string) {
    this.cwd = normalizeFilePath(cwd);
    this.#rules = rules;
    this.#ignore = ignore().add(rules);
  }
  static fromIgnoreFile(filepath: string) {
    filepath = normalizeFilePath(filepath);
    const rules = fs
      .readFileSync(filepath, "utf-8")
      .split("\n")
      .map((it) => it.trim())
      .filter((it) => !it.startsWith("#") && it.length > 0);
    const cwd = path.dirname(filepath);
    return new IgnoreGlob(rules, cwd);
  }
  isIgnore(filepath: string): boolean {
    filepath = normalizeFilePath(filepath);

    const relativepath = path.isAbsolute(filepath) ? path.relative(this.cwd, filepath) : filepath;
    return this.#ignore.ignores(relativepath);
  }
}

// const reg = new IgnoreGlob(
//   [
//     //
//     "assets/*",
//     "!assets/zzz/",
//   ],
//   import.meta.resolve("./")
// );
// console.log(reg.isIgnore(path.resolve(reg.cwd, "assets/xzzz/a.js")));
