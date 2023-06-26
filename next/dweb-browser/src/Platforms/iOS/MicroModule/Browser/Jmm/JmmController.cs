﻿using DwebBrowser.Base;

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

