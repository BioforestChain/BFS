﻿using Foundation;
using System;
using WebKit;
using UIKit;
using static CoreFoundation.DispatchSource;

namespace DwebBrowser.DWebView;

public partial class DWebView : WKWebView
{
    public DWebView(CGRect frame, WKWebViewConfiguration configuration) : base(frame, configuration)
    {
    }

    //WKPreferences preferences;
    //WKWebpagePreferences webpagePreferences;
    //public WKUserContentController controller;
    //public WKWebViewConfiguration configuration { get => base.Configuration; }
    static public DWebView Create(CGRect? frame = default)
    {
        return Create(frame ?? CGRect.Empty, new WKWebViewConfiguration());
    }
    static public DWebView Create(CGRect frame, WKWebViewConfiguration configuration)
    {

        var preferences = configuration.Preferences;
        preferences.JavaScriptCanOpenWindowsAutomatically = true;
        preferences.JavaScriptEnabled = true;

        var webpagePreferences = configuration.DefaultWebpagePreferences ?? new WKWebpagePreferences();
        webpagePreferences.AllowsContentJavaScript = true;
        configuration.DefaultWebpagePreferences = webpagePreferences;

        var webview = new DWebView(frame, configuration);
        return webview;

    }
    static readonly string webMessagePortPrepareCode = @"
const ALL_PORT = new Map();
let portIdAcc = 1;
const PORTS_ID = new WeakMap();
const getPortId = (port) => {
    let port_id = PORTS_ID.get(port);
    if (port_id === undefined) {
        const current_port_id = portIdAcc++;
        port_id = current_port_id;
        ALL_PORT.set(port_id, port);
        port.onmessage = (event) => {
            webkit.messageHandlers.webMessagePort.postMessage({
                type: 'message',
                id: current_port_id,
                data: event.data,
                ports: event.ports.map(getPortId),
            });
        };
    }
    return port_id;
};
function nativeCreateMessageChannel() {
    const channel = new MessageChannel();
    const port1_id = getPortId(channel.port1);
    const port2_id = getPortId(channel.port2);
    return [port1_id, port2_id];
}
function forceGetPort(port_id) {
    const port = ALL_PORT.get(port_id);
    if (port === undefined) {
        throw new Error(`no found messagePort by ref: ${port_id}`);
    }
    return port;
}
function nativePortPostMessage(port_id, data, ports_id) {
    const origin_port = forceGetPort(port_id);
    const transfer_ports = ports_id.map(forceGetPort);
    origin_port.postMessage(data, transfer_ports);
}
function nativeStart(port_id) {
    const origin_port = forceGetPort(port_id);
    origin_port.start();
}
function nativeWindowPostMessage(data, ports_id) {
    const ports = ports_id.map(forceGetPort);
    dispatchEvent(new MessageEvent('message', { data, ports }));
}
    ";
    internal static readonly WKContentWorld webMessagePortContentWorld = WKContentWorld.Create("web-message-port");
    internal static Dictionary<int, WebMessagePort> allPorts = new Dictionary<int, WebMessagePort>();


    readonly WKScriptMessageHandler webMessagePortMessageHanlder = new WebMessagePortMessageHanlder();

    internal class WebMessagePortMessageHanlder : WKScriptMessageHandler
    {
        [Export("userContentController:didReceiveScriptMessage:")]
        public override async void DidReceiveScriptMessage(WKUserContentController userContentController, WKScriptMessage messageEvent)
        {
            var message = messageEvent.Body;
            try
            {
                var type = (string)(NSString)message.ValueForKey(new NSString("type"));
                if (type == "message")
                {
                    var id = (int)(NSNumber)message.ValueForKey(new NSString("id"));
                    var data = message.ValueForKey(new NSString("data"));
                    WebMessagePort[] ports = new WebMessagePort[0];//message.ValueForKey(new NSString("ports"));

                    var originPort = DWebView.allPorts[id] ?? throw new KeyNotFoundException();
                    Console.WriteLine("onmessage: {0}", data);
                    await originPort._emitOnMessage(new WebMessage(data, ports));
                }
            }
            catch { }
        }
    }

    public async Task<WebMessageChannel> createWebMessageChannel()
    {
        /// 页面可能会被刷新，所以需要重新判断：函数可不可用
        var webMessagePortInited = (bool)(NSNumber)await base.EvaluateJavaScriptAsync("typeof nativeCreateMessageChannel==='function'", null, webMessagePortContentWorld);
        if (!webMessagePortInited)
        {
            await base.EvaluateJavaScriptAsync(new NSString(webMessagePortPrepareCode), null, webMessagePortContentWorld);
            base.Configuration.UserContentController.AddScriptMessageHandler(webMessagePortMessageHanlder, webMessagePortContentWorld, "webMessagePort");
        }
        var ports_id = (NSArray)await base.EvaluateJavaScriptAsync(@"nativeCreateMessageChannel()", null, webMessagePortContentWorld);

        var port1_id = (int)ports_id.GetItem<NSNumber>(0);
        var port2_id = (int)ports_id.GetItem<NSNumber>(1);
        //var port1_id = NSArray.from
        //var messagePort = new WebMessagePort();
        var port1 = new WebMessagePort(port1_id, this);
        var port2 = new WebMessagePort(port2_id, this);
        var channel = new WebMessageChannel(port1, port2);

        return channel;
    }

    public Task PostMessage(string message, WebMessagePort[]? ports) => PostMessage(WebMessage.From(message, ports));
    public Task PostMessage(int message, WebMessagePort[]? ports) => PostMessage(WebMessage.From(message, ports));
    public Task PostMessage(float message, WebMessagePort[]? ports) => PostMessage(WebMessage.From(message, ports));
    public Task PostMessage(double message, WebMessagePort[]? ports) => PostMessage(WebMessage.From(message, ports));
    public Task PostMessage(bool message, WebMessagePort[]? ports) => PostMessage(WebMessage.From(message, ports));
    public async Task PostMessage(WebMessage message)
    {
        var arguments = new NSDictionary<NSString, NSObject>(new NSString[] {
                new NSString("data"),
                new NSString("ports")
            }, new NSObject[] {
                message.Data,
                NSArray.FromNSObjects(message.Ports.Select(port => new NSNumber(port.portId)).ToArray())
            });
        await base.CallAsyncJavaScriptAsync("nativeWindowPostMessage(data,ports)", arguments, null, webMessagePortContentWorld);
    }
}

