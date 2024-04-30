import { BuildOptions, PackageJson, build } from "@deno/dnt";
import { $once } from "@dweb-browser/helper/decorator/$once.ts";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { viteTaskFactory } from "./ConTasks.helper.ts";
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
}) => {
  const {
    packageDir,
    version,
    importMap,
    options: optionsBuilder,
    entryPointsDirName = "./src",
    force = false,
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

  const packageJson = options?.package ?? JSON.parse(fs.readFileSync(packageResolve("./package.json"), "utf-8"));
  Object.assign(packageJson, {
    version: version ?? packageJson.version,
    // delete fields
    main: undefined,
    module: undefined,
  });

  const customPostBuild = options?.postBuild;
  delete options?.postBuild;

  const npmDir = npmNameToFolder(packageJson.name);
  const npmResolve = (p: string) => path.resolve(npmDir, p);

  //#region 缓存检查
  const dirHasher = calcDirHash(packageResolve("./"), { ignore: "node_modules" });
  if (force === false && dirHasher.isChange(npmDir, "dnt") === false) {
    console.log(`\n🚀 DNT MATCH CACHE: ${packageJson.name}`);
    return;
  }
  //#endregion

  //#region emptyDir(npmDir)
  // 这里要保留 package.json，因为在并发编译的时候，需要读取 package.json 以确保 workspace 能找到对应的项目所在的路径从而创造 symbol-link
  try {
    for (const item of Deno.readDirSync(npmDir)) {
      if (item.name && item.name !== "package.json") {
        Deno.removeSync(npmResolve(item.name), { recursive: true });
      }
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
        if (fs.existsSync(packageResolve(filename))) {
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
  const packageJson: PackageJson = JSON.parse(fs.readFileSync(packageResolve("./package.json"), "utf-8"));
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
  baseDir?: string;
}) => {
  const { name, inDir, outDir, baseDir } = config;
  const packageDir = path.resolve(baseDir ?? ".", inDir, "./package.json");
  const packageJson: PackageJson = JSON.parse(fs.readFileSync(packageDir, "utf-8"));
  const build_vite = $once(async () => {
    console.log(`🛫 START ${packageJson.name}`);
    await waitDependencies(packageJson);
    // 编译自身
    console.log(`⏳ BUILDING ${packageJson.name}`);
    try {
      const viteTasks = new ConTasks(
        {
          [name]: viteTaskFactory(config),
        },
        import.meta.resolve("./")
      );

      const children = viteTasks.spawn([...Deno.args, "--dev"]).children;
      // 判断是否编译完成，编译完成后将 manifest.json 文件移动到编译目录中
      await children[name].stdoutLogger.waitContent("built");
      await Deno.copyFile(
        path.resolve(baseDir ?? ".", inDir, "./manifest.json"),
        path.resolve(baseDir ?? ".", outDir, "./manifest.json")
      );

      console.log(`✅ END ${packageJson.name}`);
    } catch (e) {
      console.error(`❌ ERROR ${packageJson.name}`);
      console.error(e);
    }
  });
  regMap.set(packageJson.name, build_vite);

  return build_vite;
};
