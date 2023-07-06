import chalk from "npm:chalk";
import { esbuild, esbuild_deno_loader } from "../deps.ts";

export type $BuildOptions = esbuild.BuildOptions & {
  denoLoader?: boolean;
  importMapURL?: string;
  signal?: AbortSignal;
};

export class ESBuild {
  /**
   * 所有运行中的实例，目的是知道什么时候调用 stop 函数：
   *
   * Unlike node, Deno lacks the necessary APIs to clean up child processes
   * automatically. You must manually call stop() in Deno when you're done
   * using esbuild or Deno will continue running forever.
   */
  static _runnings = new Set<ESBuild>();
  static start(origin: ESBuild) {
    this._runnings.add(origin);
  }
  static dispose(origin: ESBuild) {
    this._runnings.delete(origin);
    if (this._runnings.size === 0) {
      esbuild.stop();
    }
  }

  constructor(readonly options: $BuildOptions) {}
  mergeOptions(...optionsList: Partial<$BuildOptions>[]) {
    const esbuildOptions = { ...this.options };
    for (const options of optionsList) {
      Object.assign(esbuildOptions, options);
    }
    const plugins = (esbuildOptions.plugins ??= []);
    if (esbuildOptions.denoLoader) {
      plugins.push(
        // ESBuild plugin to rewrite import starting "npm:" to "esm.sh" for https plugin
        {
          name: "the-npm-plugin",
          setup(build: any) {
            build.onResolve({ filter: /^npm:/ }, (args: any) => {
              return {
                path: args.path.replace(/^npm:/, "//esm.sh/"),
                namespace: "https",
              };
            });
          },
        },
        ...esbuild_deno_loader.denoPlugins({
          importMapURL: this.options.importMapURL,
        })
      );
    }

    for (const key of ["importMapURL", "signal", "denoLoader"] as const) {
      delete esbuildOptions[key];
    }
    return esbuildOptions as esbuild.BuildOptions &
      Required<Pick<esbuild.BuildOptions, "plugins">>;
  }

  async build(options: Partial<$BuildOptions> = {}) {
    ESBuild.start(this);
    const result = await esbuild.build(this.mergeOptions(options));
    options.signal?.addEventListener("abort", () => {
      ESBuild.dispose(this);
    });

    this._logResult(result);
    ESBuild.dispose(this);
  }
  private _logResult(result: esbuild.BuildResult) {
    if (result.warnings) {
      for (const warning of result.warnings) {
        console.warn(chalk.red(warning.text));
      }
    }
    if (result.errors && result.errors.length > 0) {
      for (const error of result.errors) {
        console.error(chalk.yellow(error.text));
      }
    } else {
      console.log(chalk.green("[build] success ✓"));
    }
  }
  async watch(options: Partial<$BuildOptions> = {}) {
    const esbuildOptions = this.mergeOptions({ minify: false }, options);
    esbuildOptions.plugins.push({
      name: "esbuild-watch-hook",
      setup: (build) => {
        build.onEnd((result) => {
          this._logResult(result);
          console.log(
            chalk.grey(`[watch] build finished, watching for changes...`)
          );
        });
      },
    });
    const context = await esbuild.context(esbuildOptions);
    ESBuild.start(this);
    options.signal?.addEventListener("abort", async () => {
      await context.dispose();
      ESBuild.dispose(this);
    });

    await context.watch();
  }

  async auto() {
    if (this.isDev) {
      await this.watch();
    } else {
      await this.build();
    }
  }
  get isDev() {
    return Deno.args.includes("--watch");
  }
}
