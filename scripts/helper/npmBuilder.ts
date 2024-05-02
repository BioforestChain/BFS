import { BuildOptions, PackageJson, build } from "@deno/dnt";
import { $once } from "@dweb-browser/helper/decorator/$once.ts";
import node_fs from "node:fs";
import node_path from "node:path";
import { fileURLToPath } from "node:url";
import { createBaseResolveTo, viteTaskFactory } from "./ConTasks.helper.ts";
import { ConTasks } from "./ConTasks.ts";
import { WalkFiles } from "./WalkDir.ts";
import { calcDirHash } from "./dirHash.ts";
import { rootResolve } from "./resolver.ts";

export const npmNameToFolderName = (name: string) => name.replace("/", "__");
export const npmNameToFolder = (name: string) => rootResolve(`./npm/${npmNameToFolderName(name)}`);
export type NpmBuilderContext = { packageResolve: (path: string) => string; npmResolve: (path: string) => string };
export type NpmBuilderDntBuildOptions = Omit<BuildOptions, "package"> & { package: Omit<PackageJson, "name"> };
export const npmBuilder = async (config: {
  packageDir: string;
  version?: string;
  importMap?: string;
  options?: Partial<NpmBuilderDntBuildOptions> | ((ctx: NpmBuilderContext) => Partial<NpmBuilderDntBuildOptions>);
  entryPointsDirName?: string | boolean;
  force?: boolean;
  skipNpmInstall?: boolean;
}) => {
  const {
    packageDir,
    version,
    importMap,
    options: optionsBuilder,
    entryPointsDirName = "./src",
    force = false,
    // TODO 这里要默认跳过安装，我们在外面只做一次就够了。但目前的问题是，package.json 中的依赖是dnt自己分析出来后添加到文件中的，所以如果要做到这点，还需要一些自动化的工作
    skipNpmInstall = false,
  } = config;
  const packageResolve = (path: string) => fileURLToPath(new URL(path, packageDir));
  const options =
    typeof optionsBuilder === "function"
      ? optionsBuilder({
          packageResolve,
          get npmResolve() {
            return npmResolve;
          },
        })
      : optionsBuilder;

  const packageJson = options?.package ?? JSON.parse(node_fs.readFileSync(packageResolve("./package.json"), "utf-8"));
  Object.assign(packageJson, {
    version: version ?? packageJson.version,
    // delete fields
    main: undefined,
    module: undefined,
  });

  const customPostBuild = options?.postBuild;
  delete options?.postBuild;

  const npmDir = npmNameToFolder(packageJson.name);
  const npmResolve = (p: string) => node_path.resolve(npmDir, p);

  //#region 缓存检查
  const dirHasher = calcDirHash(packageResolve("./"), { ignore: "node_modules" });
  if (force === false && dirHasher.isChange(npmDir, "dnt") === false) {
    console.log(`\n🚀 DNT MATCH CACHE: ${packageJson.name}`);
    return;
  }
  //#endregion

  //#region emptyDir(npmDir)
  try {
    for (const item of Deno.readDirSync(npmDir)) {
      // 这里要保留 package.json，因为在并发编译的时候，需要读取 package.json 以确保 workspace 能找到对应的项目所在的路径从而创造 symbol-link
      if (item.name === "package.json") {
        continue;
      }
      // 如果跳过了依赖安装，说明外面已经自己处理好安装了，所以这里不能删除
      if (skipNpmInstall && item.name === "node_modules") {
        continue;
      }
      Deno.removeSync(npmResolve(item.name), { recursive: true });
    }
  } catch (err) {
    if (!(err instanceof Deno.errors.NotFound)) {
      throw err;
    }
    // if not exist. then create it
    Deno.mkdirSync(npmDir, { recursive: true });
  }
  //#endregion

  const srcEntryPoints =
    typeof entryPointsDirName === "string"
      ? [...WalkFiles(packageResolve(entryPointsDirName), { ignore: "node_modules" })]
          .filter((it) => it.entryname.endsWith(".ts") && false === it.entryname.endsWith(".test.ts"))
          .map((it) => it.relativepath)
      : [];

  console.log(`\n🐢 DNT START: ${packageJson.name}`);

  const dntPackageJson = {
    ...options?.package,
    ...packageJson,
  };
  await build({
    entryPoints: [
      { name: ".", path: packageResolve("./index.ts") },
      ...srcEntryPoints.map((name) => ({ name: `./${name}`, path: packageResolve(`${entryPointsDirName}/${name}`) })),
    ],
    outDir: npmDir,
    packageManager: "pnpm",
    skipNpmInstall,
    shims: {
      // see JS docs for overview and more options
      deno: false,
    },
    test: false,
    importMap: importMap,
    compilerOptions: {
      lib: ["DOM", "ES2020"],
      target: "ES2020",
      emitDecoratorMetadata: true,
    },
    postBuild() {
      Deno.copyFileSync(rootResolve("./LICENSE"), npmResolve("./LICENSE"));
      // 拷贝必要的文件
      for (const filename of ["README.md", ".npmrc"]) {
        if (node_fs.existsSync(packageResolve(filename))) {
          Deno.copyFileSync(packageResolve(filename), npmResolve(filename));
        }
      }
      customPostBuild?.();
    },
    ...options,
    package: dntPackageJson,
  });
  dirHasher.writeHash(npmDir, "dnt");
};

const regMap = new Map<string, ReturnType<typeof $once>>();
/**
 * 编译依赖，等待依赖编译完成
 */
const waitDependencies = async (packageJson: PackageJson) => {
  for (const [key, version] of Object.entries(packageJson.dependencies || {})) {
    if (version.startsWith("workspace:")) {
      const depBuilder = regMap.get(key);
      if (!depBuilder) {
        console.warn(`❌ NO-FOUND DEPENDENCY ${key}\t---\t${packageJson.name}`);
        continue;
      }
      console.log(`⏳ WAITING DEPENDENCY ${key}\t---\t${packageJson.name}`);
      await depBuilder();
    }
  }
};

/**
 * 注册一个 dnt 编译项目
 *
 * 会自动等待依赖项目完成编译后，再开始自身的编译
 */
export const registryNpmBuilder = (config: Parameters<typeof npmBuilder>[0]) => {
  const { packageDir } = config;
  const packageResolve = (path: string) => fileURLToPath(new URL(path, packageDir));
  const packageJson: PackageJson = JSON.parse(node_fs.readFileSync(packageResolve("./package.json"), "utf-8"));
  const build_npm = $once(async () => {
    console.log(`🛫 START ${packageJson.name}`);
    await waitDependencies(packageJson);
    // 编译自身
    console.log(`⏳ BUILDING ${packageJson.name}`);
    try {
      await npmBuilder(config);
      console.log(`✅ END ${packageJson.name}`);
    } catch (e) {
      console.error(`❌ ERROR ${packageJson.name}`);
      console.error(e);
    }
  });
  regMap.set(packageJson.name, build_npm);

  return build_npm;
};

/**
 * 注册一个 vite 编译项目
 *
 * 会自动等待依赖项目完成编译后，再开始自身的编译
 */
export const registryViteBuilder = (config: {
  name: string;
  inDir: string;
  outDir: string;
  viteConfig?: string;
  force?: boolean;
}) => {
  const { name, inDir, outDir, force = false } = config;
  const packageDir = node_path.resolve(inDir, "./package.json");
  const packageJson: PackageJson = JSON.parse(node_fs.readFileSync(packageDir, "utf-8"));
  const build_vite = $once(async (args: string[] = Deno.args) => {
    console.log(`🛫 START ${packageJson.name}`);
    await waitDependencies(packageJson);

    const packageResolve = createBaseResolveTo(inDir);
    //#region 缓存检查
    const dirHasher = calcDirHash(packageResolve("./"), { ignore: "node_modules" });
    if (force === false && dirHasher.isChange(outDir, "vite") === false) {
      console.log(`\n🚀 VITE MATCH CACHE: ${packageJson.name}`);
      return;
    }
    //#endregion
    // 编译自身
    console.log(`⏳ BUILDING ${packageJson.name}`);
    try {
      const viteTasks = new ConTasks(
        {
          [name]: viteTaskFactory(config),
        },
        import.meta.resolve("./")
      );

      const children = viteTasks.spawn(args).children;
      // 判断是否编译完成，编译完成后将 manifest.json 文件移动到编译目录中
      await children[name].stdoutLogger.waitContent("built in");
      for (const filename of ["manifest.json", "LICENSE"]) {
        const fromPath = node_path.resolve(inDir, filename);
        if (node_fs.existsSync(fromPath)) {
          const toPath = node_path.resolve(outDir, filename);
          node_fs.mkdirSync(node_path.dirname(toPath), { recursive: true });
          node_fs.copyFileSync(fromPath, toPath);
        }
      }

      dirHasher.writeHash(outDir, "vite")
      console.log(`✅ END ${packageJson.name}`);
    } catch (e) {
      console.error(`❌ ERROR ${packageJson.name}`);
      console.error(e);
    }
  });
  regMap.set(packageJson.name, build_vite);

  return build_vite;
};
