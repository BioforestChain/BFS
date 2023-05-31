﻿using DwebBrowser.MicroService.Sys.Jmm;
namespace DwebBrowser.MicroService.Sys.User;

public class ToyJMM : JsMicroModule
{
    public ToyJMM() : base(new JmmMetadata(
        "toy.bfs.dweb",
        new JmmMetadata.MainServer() { Root = "file:///jmm", Entry = "/toy.worker.js" }))
    {
    }
}

