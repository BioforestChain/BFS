﻿using System.Text.Json;
using System.Text.Json.Serialization;
using DwebBrowser.DWebView;
using DwebBrowser.IpcWeb;
using DwebBrowser.MicroService;
using DwebBrowser.MicroService.Message;
using Foundation;

namespace DwebBrowser.MicroService.Sys.Js;

public class JsProcessWebApi
{
    static Debugger Console = new Debugger("JsProcessWebApi");
    public DWebView.DWebView DWebView { get; init; }

    public JsProcessWebApi(DWebView.DWebView dWebView)
    {
        DWebView = dWebView;
    }


    public class ProcessInfo
    {
        [JsonPropertyName("process_id")]
        public int ProcessId { get; set; }
        public ProcessInfo(int process_id)
        {
            ProcessId = process_id;
        }
    }

    public record ProcessHandler(ProcessInfo Info, MessagePortIpc Ipc);

    /**
     * 执行js"多步骤"代码时的并发编号
     */
    private int _hidAcc = 0;

    public async Task<ProcessHandler> CreateProcess(
        string env_script_url,
        string metadata_json,
        string env_json,
        Ipc.MicroModuleInfo remoteModule,
        string host)
    {
        var channel = await this.DWebView.CreateWebMessageChannel();
        var port1 = channel.Port1;
        var port2 = channel.Port2;

        var hid = Interlocked.Increment(ref _hidAcc);
        var nsProcessInfo = await this.DWebView.EvaluateAsyncJavascriptCode($$"""
           new Promise((resolve,reject)=>{
                addEventListener("message", async function doCreateProcess(event) {
                    if (event.data === "js-process/create-process/{{hid}}") {
                        removeEventListener("message", doCreateProcess);
                        const fetch_port = event.ports[0];
                        try{
                            resolve(await createProcess(`{{env_script_url}}`,JSON.stringify({{metadata_json}}),JSON.stringify({{env_json}}),fetch_port,`{{host}}`))
                        }catch(err){
                            reject(err)
                        }
                    }
                })
            })
        """.Trim(), () => this.DWebView.PostMessage("js-process/create-process/" + hid, new[] { port1 }));

        Console.Log("CreateProcess", "processInfo {0}", nsProcessInfo);

        var processId = (int)(NSNumber)nsProcessInfo.ValueForKey(new NSString("process_id"));
        var processInfo = new ProcessInfo(processId);
        return new ProcessHandler(processInfo, new MessagePortIpc(port2, remoteModule, IPC_ROLE.CLIENT));
    }

    public record RunProcessMainOptions(string MainUrl);

    public Task RunProcessMain(int process_id, RunProcessMainOptions options) =>
        this.DWebView.EvaluateJavaScriptAsync("void runProcessMain(" + process_id + ", {main_url:`" + options.MainUrl + "`})");

    public Task DestroyProcess(int process_id) =>
        this.DWebView.EvaluateJavaScriptAsync(String.Format("void destroy({0})", process_id).Trim());

    public async Task<int> CreateIpc(int process_id, Mmid mmid)
    {
        var channel = await this.DWebView.CreateWebMessageChannel();
        var port1 = channel.Port1;
        var port2 = channel.Port2;

        var hid = Interlocked.Increment(ref _hidAcc);
        await this.DWebView.EvaluateAsyncJavascriptCode($$"""
        new Promise((resolve,reject)=>{
            addEventListener("message", async function doCreateIpc(event) {
                if (event.data === "js-process/create-ipc/{{hid}}") {
                    removeEventListener("message", doCreateIpc);
                    const ipc_port = event.ports[0];
                    try{
                        resolve(await createIpc({{process_id}}, `{{mmid}}`, ipc_port))
                    }catch(err){
                        reject(err)
                    }
                }
            })
        })
        """.Trim(), () => this.DWebView.PostMessage("js-process/create-ipc/" + hid, new[] { port1 }));

        return IpcWebMessageCache.SaveNative2JsIpcPort(port2);
    }

    public void Destroy()
    { }
}

