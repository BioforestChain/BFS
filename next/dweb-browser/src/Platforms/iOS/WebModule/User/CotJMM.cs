﻿using DwebBrowser.WebModule.Jmm;

namespace DwebBrowser.WebModule.User;

public class CotJMM : JsMicroModule
{
    public CotJMM() : base(new JmmMetadata(
        "cot.bfs.dweb",
        new JmmMetadata.MainServer() { Root = "file:///bundle", Entry = "/cot.worker.js" },
        splashScreen: new JmmMetadata.SSplashScreen("https://www.bfmeta.org/")))
    {
        // TODO 测试打开的需要把metadata添加到 jmm app
        JmmNMM.GetAndUpdateJmmNmmApps().Add(Mmid, this);
    }
}

