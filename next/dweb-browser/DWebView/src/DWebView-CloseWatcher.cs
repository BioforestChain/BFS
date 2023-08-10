﻿using DwebBrowser.Helper;

namespace DwebBrowser.DWebView;

public class CloseWatcher
{
    private static int s_acc_id = 0;
    private static readonly string JS_POLYFILL_KIT = "__native_close_watcher_kit__";

    private WKWebView Webview { get; init; }
    public readonly HashSet<string> Consuming = new();

    public CloseWatcher(WKWebView webview)
    {
        Webview = webview;
    }

    public Task RegistryToken(string consumeToken)
    {
        Consuming.Add(consumeToken);
        return Webview.InvokeOnMainThreadAsync(async () => await Webview.EvaluateJavaScriptAsync($"open('{consumeToken}')"));
    }

    public void TryClose(string id)
    {
        var watcher = _watchers.FirstOrDefault(w => w.Id == id);
        if (watcher != null)
        {
            _ = Task.Run(() => CloseAsync(watcher)).NoThrow();
        }
    }

    private readonly List<Watcher> _watchers = new();

    public class Watcher
    {
        public string Id = Interlocked.Increment(ref s_acc_id).ToString();
        private long _destroy = 0;
        private readonly Mutex _closeMutex = new(false);
        private WKWebView Webview { get; init; }

        public Watcher(WKWebView webview)
        {
            Webview = webview;
        }

        public async Task<bool> TryCloseAsync()
        {
            _closeMutex.WaitOne();

            if (Interlocked.Read(ref _destroy) == 1)
            {
                return false;
            }

            await Webview.InvokeOnMainThreadAsync(async () =>
            {
                await Webview.EvaluateJavaScriptAsync(
                    JS_POLYFILL_KIT + "._watchers?.get('" + Id + "')?.dispatchEvent(new CloseEvent('close'));");
            });

            _closeMutex.ReleaseMutex();

            return Destroy();
        }

        public bool Destroy() => Interlocked.CompareExchange(ref _destroy, 0, 1) == 0;
    }

    /// <summary>
    /// 申请一个 CloseWatcher
    /// </summary>
    public Watcher Apply(bool isUserGestrue)
    {
        if (isUserGestrue || _watchers.Count == 0)
        {
            _watchers.Add(new Watcher(Webview));
        }

        return _watchers.Last();
    }

    public async void ResolveToken(string consumeToken, Watcher watcher)
    {
        await Webview.InvokeOnMainThreadAsync(async () =>
        {
            await Webview.EvaluateJavaScriptAsync(
                JS_POLYFILL_KIT + "._tasks?.get('" + consumeToken + "')?.('" + watcher.Id + "');");
        });
    }

    /// <summary>
    /// 现在是否有 CloseWatcher 在等待被关闭
    /// </summary>
    public bool CanClose { get => _watchers.Count > 0; }

    /// <summary>
    /// 关闭指定的 CloseWatcher
    /// </summary>
    public async Task<bool> CloseAsync(Watcher? watcher = null)
    {
        watcher ??= _watchers.Last();

        if (await watcher.TryCloseAsync())
        {
            return _watchers.Remove(watcher);
        }

        return false;
    }
}

public partial class DWebView : WKWebView
{
    private readonly LazyBox<WKScriptMessageHandler> _webCloseWatcherMessageHandler = new();
    public WKScriptMessageHandler CloseWatcherMessageHanlder
    {
        get => _webCloseWatcherMessageHandler.GetOrPut(() => new WebCloseWatcherMessageHanlder(this));
    }

    private readonly LazyBox<CloseWatcher> _closeWatcherController = new();
    public CloseWatcher CloseWatcherController
    {
        get => _closeWatcherController.GetOrPut(() => new(this));
    }

    internal class WebCloseWatcherMessageHanlder : WKScriptMessageHandler
    {
        private DWebView DWebView { get; init; }
        public WebCloseWatcherMessageHanlder(DWebView dWebView)
        {
            DWebView = dWebView;
        }

        [Export("userContentController:didReceiveScriptMessage:")]
        public override async void DidReceiveScriptMessage(WKUserContentController userContentController, WKScriptMessage messageEvent)
        {
            var message = messageEvent.Body;
            var consumeToken = (string)(NSString)message.ValueForKey(new NSString("token"));
            var id = (string)(NSString)message.ValueForKey(new NSString("id"));

            if (!string.IsNullOrEmpty(consumeToken))
            {
                await DWebView.CloseWatcherController.RegistryToken(consumeToken);
            }
            else if (!string.IsNullOrEmpty(id))
            {
                DWebView.CloseWatcherController.TryClose(id);
            }
        }
    }

    public override bool CanGoBack
    {
        get
        {
            return CloseWatcherController.CanClose || base.CanGoBack;
        }
    }
    public override WKNavigation? GoBack()
    {
        if (CloseWatcherController.CanClose)
        {
            _ = CloseWatcherController.CloseAsync().NoThrow();
            return null;
        }
        return base.GoBack();
    }

}

