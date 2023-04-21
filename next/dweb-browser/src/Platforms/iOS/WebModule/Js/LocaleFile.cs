﻿using DwebBrowser.MicroService.Core;
using DwebBrowser.Helper;
using System.Net;
using System.Web;
using Foundation;
using MobileCoreServices;

#nullable enable

namespace DwebBrowser.WebModule.Js;

public static class LocaleFile
{
    /// <summary>
    /// 应用根目录
    /// </summary>
    /// <returns></returns>
    public static string RootPath() => AppContext.BaseDirectory;

    /// <summary>
    /// 资源文件目录
    /// </summary>
    /// <returns></returns>
    public static string AssetsPath() => Path.Combine(RootPath(), "Assets");

    /// <summary>
    /// 获取文件MimeType
    /// </summary>
    /// <param name="filepath"></param>
    /// <returns></returns>
    public static string? GetMimeType(string filepath)
    {
        try
        {
            var url = NSUrl.FromFilename(filepath);
            var suc = url.TryGetResource(NSUrl.ContentTypeKey, out NSObject obj);

            if (suc)
            {
                return UTType.GetPreferredTag(obj.ToString(), UTType.TagClassMIMEType);
            }

            return null;
        }
        catch
        {
            return null;
        }
    }

    public static HttpResponseMessage? LocaleFileFetch(MicroModule remote, HttpRequestMessage request)
    {
        try
        {
            if (request.RequestUri is not null && request.RequestUri.Scheme == "file" && request.RequestUri.Host == "")
            {
                var query = HttpUtility.ParseQueryString(request.RequestUri.Query);

                var mode = query["mode"] ?? "auto";
                var chunk = query["chunk"]?.ToIntOrNull() ?? 1024 * 1024;
                var preRead = query["pre-read"]?.ToBooleanStrictOrNull() ?? false;

                var src = request.RequestUri.AbsolutePath.Substring(1);

                Console.WriteLine($"OPEN {src}");
                string dirname = Path.GetDirectoryName(src) ?? "";
                string filename = Path.GetFileName(src) ?? "";


                /// 尝试打开文件，如果打开失败就走 404 no found 响应
                var absoluteDir = Path.Combine(AssetsPath(), dirname);
                var filenameList = Directory.GetFileSystemEntries(absoluteDir) ?? Array.Empty<string>();

                HttpResponseMessage response = null!;

                var targetPath = Path.Combine(absoluteDir, filename);
                if (!filenameList.Contains(targetPath))
                {
                    Console.WriteLine($"NO-FOUND {request.RequestUri.AbsolutePath}");
                    response = new HttpResponseMessage(HttpStatusCode.NotFound).Also(it =>
                    {
                        it.Content = new StringContent($"the file({request.RequestUri.AbsolutePath}) not found.");
                    });
                }
                else
                {
                    src = Path.Combine(AssetsPath(), src);
                    response = new HttpResponseMessage(HttpStatusCode.OK);

                    // buffer 模式，就是直接全部读取出来
                    // TODO auto 模式就是在通讯次数和单次通讯延迟之间的一个取舍，如果分片次数少于2次，那么就直接发送，没必要分片
                    if (mode is not "stream")
                    {
                        Console.WriteLine("auto mode");
                        /**
                         * 打开一个读取流
                         */
                        using (var fs = File.OpenRead(src))
                        {
                            /**
                             * 一次性发送
                             */
                            response.Content = new ByteArrayContent(fs.ToByteArray());

                            var mimeType = GetMimeType(src);
                            if (mimeType is not null)
                            {
                                response.Content.Headers.Add("Content-Type", mimeType);
                            }

                        }

                        return response;
                    }
                    else
                    {
                        Console.WriteLine("stream mode");
                        var fs = File.OpenRead(src);
                        response.Content = new StreamContent(fs);
                        var mimeType = GetMimeType(src);
                        if (mimeType is not null)
                        {
                            Console.WriteLine($"mimeType: {mimeType}");
                            response.Content.Headers.Add("Content-Type", mimeType);
                        }
                        return response;
                    }
                }
            }
        }
        catch (Exception e)
        {
            Console.WriteLine($"Exception: {e.Message}");
            return new HttpResponseMessage(HttpStatusCode.InternalServerError);
        }

        return null;
    }
}

