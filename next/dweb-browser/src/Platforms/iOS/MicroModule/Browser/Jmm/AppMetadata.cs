﻿using System.Text.Json;
using System.Text.Json.Serialization;

#nullable enable

namespace DwebBrowser.MicroService.Browser.Jmm;

public class AppMetaData : IEquatable<AppMetaData>
{
    [JsonPropertyName("id")]
    public Mmid Id { get; set; }    // jmmApp的id
    [JsonPropertyName("server")]
    public MainServer Server { get; set; }      // 打开应用地址
    [JsonPropertyName("dweb_deeplinks")]
    public List<Dweb_DeepLink>? Dweb_DeepLinks { get; set; }       // dweb-deeplinks
    [JsonPropertyName("name")]
    public string Name { get; set; }       // 应用名称
    [JsonPropertyName("short_name")]
    public string ShortName { get; set; }        // 应用副标题
    [JsonPropertyName("icon")]
    public string Icon { get; set; }        // 应用图标
    [JsonPropertyName("images")]
    public List<string>? Images { get; set; }       // 应用截图
    [JsonPropertyName("description")]
    public string Description { get; set; }        // 应用描述
    [JsonPropertyName("author")]
    public List<string>? Author { get; set; }       // 开发者，作者
    [JsonPropertyName("version")]
    public string Version { get; set; }     // 应用版本
    [JsonPropertyName("new_feature")]
    public string NewFeature { get; set; }      // 新特性，新功能
    [JsonPropertyName("categories")]
    public List<MicroModuleCategory>? Categories { get; set; }     // 关键词
    [JsonPropertyName("home")]
    public string Home { get; set; }        // 首页地址
    [JsonPropertyName("bundle_url")]
    public string BundleUrl { get; set; }     // 下载应用地址
    [JsonPropertyName("bundle_size")]
    public long BundleSize { get; set; }        // 应用大小
    [JsonPropertyName("bundle_hash")]
    public string BundleHash { get; set; }        // 文件hash
    [JsonPropertyName("permissions")]
    public List<string>? Permissions { get; set; }      // app使用权限情况
    [JsonPropertyName("plugins")]
    public List<string>? Plugins { get; set; }      // app使用插件情况
    [JsonPropertyName("release_date")]
    public string ReleaseDate { get; set; }     // 发布时间

    [Obsolete("使用带参数的构造函数", true)]
#pragma warning disable CS8618 // 在退出构造函数时，不可为 null 的字段必须包含非 null 值。请考虑声明为可以为 null。
    public AppMetaData()
#pragma warning restore CS8618 // 在退出构造函数时，不可为 null 的字段必须包含非 null 值。请考虑声明为可以为 null。
    {
        /// 给JSON反序列化用的空参数构造函数
    }

    public AppMetaData(
        Mmid id,
        MainServer server,
        List<Dweb_DeepLink>? dweb_deeplinks = null,
        string name = "",
        string short_name = "",
        string icon = "",
        List<string>? images = null,
        string description = "",
        List<string>? author = null,
        string version = "",
        string new_feature = "",
        List<MicroModuleCategory>? categories = null,
        string home = "",
        string bundle_url = "",
        long bundle_size = 0L,
        string bundle_hash = "",
        List<string>? permissions = null,
        List<string>? plugins = null,
        string release_date = "")
    {
        Id = id;
        Server = server;
        Name = name;
        ShortName = short_name;
        Icon = icon;
        Images = images;
        Description = description;
        Author = author;
        Version = version;
        NewFeature = new_feature;
        Categories = categories;
        Home = home;
        BundleUrl = bundle_url;
        BundleSize = bundle_size;
        BundleHash = bundle_hash;
        Permissions = permissions;
        Plugins = plugins;
        ReleaseDate = release_date;
        if (dweb_deeplinks is null)
        {
            Dweb_DeepLinks = new();
        }
        else
        {
            Dweb_DeepLinks = dweb_deeplinks;
        }
    }

    public string ToJson() => JsonSerializer.Serialize(this);
    public static AppMetaData? FromJson(string json) =>
        JsonSerializer.Deserialize<AppMetaData>(json);

    public bool Equals(AppMetaData? other)
    {
        return GetHashCode() == other?.GetHashCode();
    }

    public override int GetHashCode()
    {
        return Id.GetHashCode() ^ 
            Server.GetHashCode() ^
            Name.GetHashCode() ^
            ShortName.GetHashCode() ^
            Icon.GetHashCode() ^
            Images?.GetHashCode() ?? 0 ^
            Description.GetHashCode() ^
            Author?.GetHashCode() ?? 0 ^
            Version.GetHashCode() ^
            NewFeature.GetHashCode() ^
            Categories?.GetHashCode() ?? 0 ^
            Home.GetHashCode() ^
            BundleUrl.GetHashCode() ^
            BundleSize.GetHashCode() ^
            BundleHash.GetHashCode() ^
            Permissions?.GetHashCode() ?? 0 ^
            Plugins?.GetHashCode() ?? 0 ^
            ReleaseDate.GetHashCode() ^
            Dweb_DeepLinks?.GetHashCode() ?? 0;
    }
}
