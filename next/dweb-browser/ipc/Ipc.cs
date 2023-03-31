﻿using System.Threading.Tasks.Dataflow;

namespace ipc;

using IpcMessageArgs = Tuple<IpcMessage, Ipc>;
using IpcRequestMessageArgs = Tuple<IpcRequest, Ipc>;
using IpcResponseMessageArgs = Tuple<IpcResponse, Ipc>;
using IpcEventMessageArgs = Tuple<IpcEvent, Ipc>;
using IpcStreamMessageArgs = Tuple<IpcStream, Ipc>;

public abstract class Ipc
{
    private static int s_uid_acc = 1;
    private static int s_req_id_acc = 0;

    public int Uid { get; set; } = Interlocked.Exchange(ref s_uid_acc, Interlocked.Increment(ref s_uid_acc));

    /**
     * <summary>
     * 是否支持 messagePack 协议传输：
     * 需要同时满足两个条件：通道支持直接传输二进制；通达支持 MessagePack 的编解码
     * </summary>
     */
    public bool SupportMessagePack { get; set; } = false;

    /**
     * <summary>
     * 是否支持 Protobuf 协议传输：
     * 需要同时满足两个条件：通道支持直接传输二进制；通达支持 Protobuf 的编解码
     * </summary>
     */
    public bool SupportProtobuf { get; set; } = false;

    /**
     * <summary>
     * 是否支持结构化内存协议传输：
     * 就是说不需要对数据手动序列化反序列化，可以直接传输内存对象
     * </summary>
     */
    public bool SupportRaw { get; set; } = false;

    /** <summary>是否支持 二进制 传输</summary> */
    public bool SupportBinary { get; set; } = false;

    public abstract MicroModuleInfo Remote { get; set; }

    public interface MicroModuleInfo
    {
        public Mmid Mmid { get; init; }
    }

    // TODO: MicroModule还未实现
    //public MicroModule AsRemoteInstance()

    public abstract string Role { get; }

    public override string ToString() => $"#i{Uid}";

    public async Task PostMessageAsync(IpcMessage message)
    {
        if (_closed)
        {
            return;
        }

        await _doPostMessageAsync(message);
    }

    public Task PostResponseAsync(int req_id, HttpResponseMessage response) =>
        PostMessageAsync(IpcResponse.FromResponse(req_id, response, this));

    private Event<IpcMessage, Ipc> _onMessageEvent = new();

    public void OnMessageEmit(IpcMessage ipcMessage, Ipc ipc) => _onMessageEvent.Emit(ipcMessage, ipc);

    public abstract Task _doPostMessageAsync(IpcMessage data);

    public Event<IpcRequest, Ipc> OnRequestEvent = new();

    public void OnRequest(OnMessageHandler<IpcRequest, Ipc> cb)
    {
        OnRequestEvent.Listen(cb);
        _onMessageEvent.Listen(async (ipcMessage, ipc) =>
        {
            if (ipcMessage is IpcRequest ipcRequest)
            {
                OnRequestEvent.Emit(ipcRequest, ipc);
            }
        });
    }

    public Event<IpcResponse, Ipc> OnResponseEvent = new();

    public void OnResponse(OnMessageHandler<IpcResponse, Ipc> cb)
    {
        OnResponseEvent.Listen(cb);
        _onMessageEvent.Listen(async (ipcMessage, ipc) =>
        {
            if (ipcMessage is IpcResponse ipcResponse)
            {
                OnResponseEvent.Emit(ipcResponse, ipc);
            }
        });
    }

    public Event<IpcStream, Ipc> OnStreamEvent = new();

    public void OnStream(OnMessageHandler<IpcStream, Ipc> cb)
    {
        OnStreamEvent.Listen(cb);

        /// 这里建立起一个独立的顺序队列，目的是避免处理阻塞
        /// TODO 这里不应该使用 UNLIMITED，而是压力到一定程度方向发送限流的指令
        var streamChannel = new BufferBlock<KeyValuePair<IpcStream, Ipc>>();
        Task.Run(async () =>
        {
            await foreach (var message in streamChannel.ReceiveAllAsync())
            {
                OnStreamEvent.Emit(message.Key, message.Value);
            }
        });

        _onMessageEvent.Listen(async (ipcStream, ipc) =>
        {
            if (ipcStream is IpcStream stream)
            {
                await streamChannel.SendAsync(KeyValuePair.Create(stream, ipc));
            }
        });
    }

    public Event<IpcEvent, Ipc> OnEventEvent = new();

    public void OnEvent(OnMessageHandler<IpcEvent, Ipc> cb)
    {
        OnEventEvent.Listen(cb);

        _onMessageEvent.Listen(async (ipcMessage, ipc) =>
        {
            if (ipcMessage is IpcEvent ipcEvent)
            {
                OnEventEvent.Emit(ipcEvent, ipc);
            }
        });
    }


    public abstract Task DoClose();

    private bool _closed { get; set; } = false;

    public async Task Close()
    {
        if (_closed)
        {
            return;
        }

        _closed = true;
        await DoClose();
    }

    public bool IsClosed
    {
        get { return _closed; }
    }

    public SimpleEvent OnCloseEvent = new();

    public void OnClose(OnSimpleMessageHandler cb) => OnCloseEvent.Listen(cb);

    public SimpleEvent OnDestoryEvent = new();

    public void OnDestory(OnSimpleMessageHandler cb) => OnDestoryEvent.Listen(cb);

    private bool _destroyed = false;
    public bool IsDestroy
    {
        get => _destroyed;
    }

    /**
     * 销毁实例
     */
    public async Task Destroy(bool close = true)
    {
        if (_destroyed)
        {
            return;
        }

        _destroyed = true;

        if (close)
        {
            await Close();
        }

        OnDestoryEvent.Clear();
    }

    /**
     * 发送请求
     */
    public Task<HttpResponseMessage> Request(string url) =>
        Request(new HttpRequestMessage(HttpMethod.Get, new Uri(url)));

    public Task<HttpResponseMessage> Request(Uri url) =>
        Request(new HttpRequestMessage(HttpMethod.Get, url));

    public Ipc()
    {
        _reqResMap = new Dictionary<int, PromiseOut<IpcResponse>>().Also(reqResMap =>
        {
            OnResponse(async (ipcResponse, ipc) =>
            {
                var res = reqResMap[ipcResponse.ReqId];

                if (res is null)
                {
                    throw new Exception($"no found response by req_id: {ipcResponse.ReqId}");
                }

                res.Resolve(ipcResponse);
            });
        });
    }

    private Dictionary<int, PromiseOut<IpcResponse>> _reqResMap;

    public async Task<IpcResponse> Request(IpcRequest ipcRequest)
    {
        var result = new PromiseOut<IpcResponse>();
        _reqResMap[ipcRequest.ReqId] = result;
        await PostMessageAsync(ipcRequest);
        return await result.WaitPromiseAsync();
    }

    public async Task<HttpResponseMessage> Request(HttpRequestMessage request) =>
        (await Request(IpcRequest.FromRequest(AllocReqId(), request, this))).ToResponse();

    public int AllocReqId() => Interlocked.Exchange(ref s_req_id_acc, Interlocked.Increment(ref s_req_id_acc));
}

