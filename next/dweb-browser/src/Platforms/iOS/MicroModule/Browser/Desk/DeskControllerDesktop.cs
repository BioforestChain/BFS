﻿using System.Text.Json;

namespace DwebBrowser.MicroService.Browser.Desk;

public partial class DeskController
{
    public DWebView.DWebView DesktopView { get; set; }

    record AppOrder(int Order);

    private LazyBox<Dictionary<Mmid, AppOrder>> SAppOrders = new();
    private Dictionary<Mmid, AppOrder> AppOrders
    {
        get
        {
            var dic = JsonSerializer.Deserialize<Dictionary<Mmid, AppOrder>>(DeskStore.Instance.Get("desktop/orders",
                () => JsonSerializer.Serialize(new Dictionary<Mmid, AppOrder>())));

            if (dic.Count == 0)
            {
                return SAppOrders.GetOrPut(() => new Dictionary<string, AppOrder>());
            }

            return dic;
        }
    }

    /// <summary>
    /// 列出桌面应用
    /// </summary>
    /// <returns></returns>
    public async Task<List<DeskNMM.DesktopAppMetadata>> GetDesktopAppList()
    {
        var apps = await DeskNMM.BootstrapContext.Dns.Search(MicroModuleCategory.Application);

        List<DeskNMM.DesktopAppMetadata> desktopApps = new();
        foreach (var app in apps)
        {
            var deskApp = DeskNMM.DesktopAppMetadata.FromMicroModuleManifest(app);
            deskApp.Running = DeskNMM.RunningApps.ContainsKey(app.Mmid);
            desktopApps.Add(deskApp);
        }

        return desktopApps.OrderBy(it => AppOrders.GetValueOrDefault(it.Mmid)?.Order ?? 0).ToList();
    }
}

