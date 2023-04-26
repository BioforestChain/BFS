﻿namespace DwebBrowser.MicroService.Core;

public abstract partial class MicroModule : Ipc.MicroModuleInfo
{
    public Mmid Mmid { get; init; }
    public MicroModule(Mmid mmid)
    {
        this.Mmid = mmid;
    }
    public Router? Router = null;

    private PromiseOut<bool> _runningStateLock = PromiseOut<bool>.StaticResolve(false);
    public bool Running { get => _runningStateLock.Value; }

    private async Task _beforeBootsStrap(IBootstrapContext bootstrapContext)
    {
        if (await _runningStateLock.WaitPromiseAsync())
        {
            throw new Exception(String.Format("module {0} already running", Mmid));
        }

        _runningStateLock = new PromiseOut<bool>();
        _bootstrapContext = bootstrapContext;
    }

    private IBootstrapContext? _bootstrapContext = null;
    public IBootstrapContext BootstrapContext { get => _bootstrapContext ?? throw new Exception("module no run."); }

    protected abstract Task _bootstrapAsync(IBootstrapContext bootstrapContext);

    private void _afterBootstrap(IBootstrapContext dnsMM) => _runningStateLock.Resolve(true);

    public async Task BootstrapAsync(IBootstrapContext bootstrapContext)
    {
        await _beforeBootsStrap(bootstrapContext);
        try
        {
            await _bootstrapAsync(bootstrapContext);
        }
        finally
        {
            _afterBootstrap(bootstrapContext);
        }
    }

    protected event Signal? _onAfterShutdown;

    protected async Task _beforeShutdownAsync()
    {
        if (!await _runningStateLock.WaitPromiseAsync())
        {
            throw new Exception(String.Format("module {0} already shutdown", Mmid));
        }

        _runningStateLock = new PromiseOut<bool>();

        /// 关闭所有的通讯
        _ipcSet.ToList().ForEach(async it => await it.Close());
        _ipcSet.Clear();
    }

    protected virtual async Task _shutdownAsync() { }

    protected async Task _afterShutdownAsync()
    {
        await (_onAfterShutdown?.Emit()).ForAwait();
        _onAfterShutdown = null;
        _runningStateLock.Resolve(false);
        _bootstrapContext = null;
    }

    public async Task ShutdownAsync()
    {
        await _beforeShutdownAsync();

        try
        {
            await _shutdownAsync();
        }
        finally
        {
            await _afterShutdownAsync();
        }
    }

    /**
     * <summary>
     * 连接池
     * </summary>
     */
    protected HashSet<Ipc> _ipcSet = new();

    /**
     * <summary>
     * 给内部程序自己使用的 onConnect，外部与内部建立连接时使用
     * 因为 NativeMicroModule 的内部程序在这里编写代码，所以这里会提供 onConnect 方法
     * 如果时 JsMicroModule 这个 onConnect 就是写在 WebWorker 那边了
     * </summary>
     */
    public event Signal<Ipc, HttpRequestMessage>? OnConnect;

    /**
     * <summary>
     * 尝试连接到指定对象
     * </summary>
     */
    //public Task<ConnectResult> ConnectAsync(Mmid mmid, HttpRequestMessage? reason = null) =>
    //    _bootstrapContext!.Dns.Let(it =>
    //    {
    //        it.BootstrapAsync(mmid);
    //        return it.ConnectAsync(mmid);
    //    });
    public async Task<ConnectResult> ConnectAsync(Mmid mmid, HttpRequestMessage? reason = null)
    {
        await _bootstrapContext!.Dns.BootstrapAsync(mmid);
        return await _bootstrapContext!.Dns.ConnectAsync(mmid);
    }
        

    /**
     * <summary>
     * 收到一个连接，触发相关事件
     * </summary>
     */
    public Task BeConnectAsync(Ipc ipc, HttpRequestMessage reason)
    {
        _ipcSet.Add(ipc);
        ipc.OnClose += async (_) => _ipcSet.Remove(ipc);
        ipc.OnEvent += async (ipcMessage, ipc, _) =>
        {
            if (ipcMessage.Name == "activity")
            {
                await _onActivityAsync(ipcMessage, ipc);
            }
        };

        return (OnConnect?.Emit(ipc, reason)).ForAwait();
    }

    protected virtual async Task _onActivityAsync(IpcEvent Event, Ipc ipc) { }


}
