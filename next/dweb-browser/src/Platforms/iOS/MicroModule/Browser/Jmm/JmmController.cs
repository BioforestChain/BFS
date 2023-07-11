﻿using DwebBrowser.Base;
using DwebBrowserFramework;
using Foundation;
using UIKit;

namespace DwebBrowser.MicroService.Browser.Jmm;

public class JmmController : BaseViewController
{
    static readonly Debugger Console = new("JmmController");
    private JmmNMM _jmmNMM { get; init; }
    private Dictionary<Mmid, Ipc> _openIpcMap = new();

    public JmmController(JmmNMM jmmNMM)
    {
        _jmmNMM = jmmNMM;
    }

    public Task OpenDownloadPageAsync(JmmMetadata jmmMetadata, DownloadStatus downloadStatus) =>
        MainThread.InvokeOnMainThreadAsync(async () =>
        {
            var data = NSData.FromString(jmmMetadata.ToJson(), NSStringEncoding.UTF8);
            var vc = await IOSNativeMicroModule.RootViewController.WaitPromiseAsync();
            var manager = new DownloadAppManager(data, (nint)downloadStatus);

            manager.DownloadView.Frame = UIScreen.MainScreen.Bounds;

            // 移除所有子视图
            foreach (var subview in View.Subviews)
            {
                subview.RemoveFromSuperview();
            }

            View.AddSubview(manager.DownloadView);

            // 无法push同一个UIViewController的实例两次
            var viewControllersList = vc.ViewControllers?.ToList();
            var index = viewControllersList.FindIndex(uvc => uvc == this);
            if (index >= 0)
            {
                // 不是当前controller时，推到最新
                //if (index != vc.ViewControllers!.Length - 1)
                //{
                //    vc.PopToViewController(this, true);
                //}
                viewControllersList.RemoveAt(index);
                vc.PushViewController(this, true);
            }
            else
            {
                vc.PushViewController(this, true);
            }

            // 点击下载
            manager.ClickButtonActionWithCallback(async d =>
            {
                switch (d.ToString())
                {
                    case "download":
                        if (downloadStatus == DownloadStatus.NewVersion)
                        {
                            this?.CloseApp(jmmMetadata.Id);
                        }
                        var jmmDownload = JmmDwebService.Add(jmmMetadata,
                             manager.OnDownloadChangeWithDownloadStatus,
                             manager.OnListenProgressWithProgress);

                        JmmDwebService.Start();
                        break;
                    case "open":
                        Console.Log("open", jmmMetadata.ToJson());
                        new JsMicroModule(jmmMetadata).Also((jsMicroModule) =>
                        {
                            _jmmNMM.BootstrapContext.Dns.Install(jsMicroModule);
                        });
                        this?.OpenApp(jmmMetadata.Id);

                        break;
                    case "back":
                        vc.PopViewController(true);
                        break;
                    default:
                        break;
                }
            });
        });

    public async Task OpenApp(Mmid mmid)
    {
        var ipc = await _openIpcMap.GetValueOrPutAsync(mmid, async () =>
        {
            var connectResult = await _jmmNMM.ConnectAsync(mmid);
            connectResult.IpcForFromMM.OnEvent += async (Event, _, _) =>
            {
                if (Event.Name == EIpcEvent.Close.Event)
                {
                    Console.Log("openApp", "event::{0}==>{1} from==> {2}", Event.Name, Event.Data, mmid);
                    _openIpcMap.Remove(mmid);
                }
            };

            return connectResult.IpcForFromMM;
        });

        Console.Log("openApp", "postMessage ==> activity {0}, {1}", mmid, ipc.Remote.Mmid);
        await ipc.PostMessageAsync(IpcEvent.FromUtf8(EIpcEvent.Activity.Event, ""));
    }

    public async Task CloseApp(Mmid mmid)
    {
        if (_openIpcMap.TryGetValue(mmid, out var ipc))
        {
            Console.Log("closeApp", "postMessage ==> activity {0}, {1}", mmid, ipc.Remote.Mmid);
            await ipc.PostMessageAsync(IpcEvent.FromUtf8(EIpcEvent.Close.Event, ""));
        }
        _openIpcMap.Remove(mmid);
    }
}

