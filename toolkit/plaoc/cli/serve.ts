import crypto from "node:crypto";
import fs from "node:fs";
import http from "node:http";
import os from "node:os";
import { generate, QRErrorCorrectLevel } from "npm:ts-qrcode-terminal";
import { colors, NumberPrompt } from "./deps/cliffy.ts";
import { SERVE_MODE, type $ServeOptions } from "./helper/const.ts";
import { injectPrepare, MetadataJsonGenerator, BundleResourceNameHelper } from "./helper/generator.ts";
import { startStaticFileServer, staticServe } from "./helper/http-static-helper.ts";
import Mime from "mime";

// deno-lint-ignore require-await
const doServe = async (flags: $ServeOptions, staticFileServerPort?: number) => {
  const port = +flags.port;
  if (Number.isFinite(port) === false) {
    throw new Error(`need input '--port 8080'`);
  }

  const serveTarget = flags.webPublic;
  if (typeof serveTarget !== "string") {
    throw new Error(`need input 'YOUR/FOLDER/FOR/BUNDLE'`);
  }

  const metadataFlagHelper = new MetadataJsonGenerator(flags);
  // 获取manifest.json文件路径，用于监听变化时重启服务
  const manifestFilePath = metadataFlagHelper.metadataFilepaths.filter(
    (item) => item.endsWith(BundleResourceNameHelper.metadataName) && fs.existsSync(item)
  )?.[0];

  let { bundleFlagHelper, bundleResourceNameHelper } = injectPrepare(flags, metadataFlagHelper);

  /// 启动http服务器
  const server = http.createServer().listen(port, async () => {
    const map: { hostname: string; dwebLink: string }[] = [];
    let index = 0;
    for (const info of Object.values(os.networkInterfaces())
      .flat()
      .filter((info) => info?.family === "IPv4")) {
      console.log(
        `${colors.green(`${index++}:`)} \t ${
          colors.dim("dweb://install?url=") +
          colors.blue(colors.underline(`http://${info?.address}:${port}/${BundleResourceNameHelper.metadataName}`))
        }`
      );
      map.push({
        hostname: info?.address ?? "",
        dwebLink: `dweb://install?url=http://${info?.address}:${port}/${BundleResourceNameHelper.metadataName}`,
      });
      // console.log(`package: \thttp://${info?.address}:${port}/${nameFlagHelper.bundleName}`)
    }

    const selectNumber = await NumberPrompt.prompt({
      message: "Enter the corresponding number to generate a QR code.",
      default: 0,
    });

    const { hostname, dwebLink } = map[selectNumber];

    if (dwebLink) {
      // 启动静态文件服务器
      if (staticFileServerPort) {
        flags.mode = SERVE_MODE.LIVE;
        flags.webPublic = `http://${hostname}:${staticFileServerPort}`;
        const injectResult = injectPrepare(flags, metadataFlagHelper);
        bundleFlagHelper = injectResult.bundleFlagHelper;
        bundleResourceNameHelper = injectResult.bundleResourceNameHelper;
        startStaticFileServer(serveTarget, hostname, staticFileServerPort);
      }
      generate(dwebLink, {
        small: true,
        qrErrorCorrectLevel: QRErrorCorrectLevel.L,
      });
    }
  });

  server.on("request", async (req, res) => {
    if (req.method && req.url) {
      console.log(colors.blue(req.method), colors.green(req.url));
    }
    try {
      const url = new URL(req.url ?? "/", "http://localhost");
      if (url.pathname === "/" + bundleResourceNameHelper.bundleName()) {
        res.setHeader("Content-Type", Mime.getType(bundleResourceNameHelper.bundleName())!);
        /// 尝试读取上次 metadata.json 生成的 zip 文件
        const zip = await bundleFlagHelper.bundleZip();
        zip.generateNodeStream({ compression: "STORE" }).pipe(res as NodeJS.WritableStream);
        return;
      } else if (url.pathname === "/" + BundleResourceNameHelper.metadataName) {
        /// 动态生成 合成 metadata
        res.setHeader("Content-Type", Mime.getType(BundleResourceNameHelper.metadataName)!);
        /// 每次请求的 metadata.json 的时候，我们强制重新生成 metadata 与 zip 文件
        const metadata = metadataFlagHelper.readMetadata(true);
        const zip = await bundleFlagHelper.bundleZip(true);

        const zipData = await zip.generateAsync({ type: "uint8array" });
        const hasher = crypto.createHash("sha256").update(zipData);
        metadata.bundle_size = zipData.byteLength;
        metadata.bundle_hash = "sha256:" + hasher.digest("hex");
        metadata.bundle_url = `./${bundleResourceNameHelper.bundleName()}`;
        // metadata.bundle_signature =

        res.setHeader("Access-Control-Allow-Origin", "*");
        res.setHeader("Access-Control-Allow-Headers", "*");
        res.setHeader("Access-Control-Allow-Methods", "*");
        res.end(JSON.stringify(metadata, null, 2));
        return;
      }

      if (bundleFlagHelper.www_dir) {
        await staticServe(bundleFlagHelper.www_dir, req, res);
      } else {
        res.statusCode = 502;
        res.end();
      }
    } catch (err) {
      res.statusCode = 500;
      const html = String.raw;
      res.setHeader("Content-Type", "text/html");
      res.end(
        html`<h1 style="color:red">${err.message}</h1>
          <hr />
          <pre>${err.stack}</pre>`
      );
    }
  });

  return { server, manifestFilePath };
};

export const startServe = async (flags: $ServeOptions, staticFileServerPort?: number) => {
  const { server, manifestFilePath } = await doServe(flags, staticFileServerPort);
  server.once("restart", () => {
    server.once("close", async () => {
      await startServe(flags, staticFileServerPort);
    });
    server.close();
  });
  if (manifestFilePath)
    fs.watch(manifestFilePath, (eventname, filename) => {
      if (eventname === "change" && filename?.endsWith("manifest.json")) {
        // \x1b[3J 清除所有内容
        // \x1b[H 把光标移动到行首
        // \x1b[2J 清除所有内容
        console.log("\x1b[3J\x1b[H\x1b[2J");
        server.emit("restart", []);
      }
    });
};
