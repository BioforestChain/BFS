package org.dweb_browser.core.ipc.helper

import kotlinx.serialization.json.Json
import org.dweb_browser.helper.JsonLoose
import org.dweb_browser.core.ipc.Ipc

fun jsonToIpcMessage(data: String, ipc: Ipc): Any {
  if (data == "close" || data == "ping" || data == "pong") {
    return data
  }

  try {
    val typeInfo = Regex(""""type"\s*:\s*(\d+)""").find(data) ?: return data
    return when (JsonLoose.decodeFromString<IPC_MESSAGE_TYPE>(typeInfo.groupValues[1])) {
      IPC_MESSAGE_TYPE.REQUEST -> Json.decodeFromString<IpcReqMessage>(data).let {
        IpcRequest(
          it.req_id,
          it.url,
          it.method,
          IpcHeaders(it.headers),
          IpcBodyReceiver.from(it.metaBody, ipc),
          ipc
        )
      }

      IPC_MESSAGE_TYPE.RESPONSE -> Json.decodeFromString<IpcResMessage>(data).let {
        IpcResponse(
          it.req_id,
          it.statusCode,
          IpcHeaders(it.headers),
          IpcBodyReceiver.from(it.metaBody, ipc),
          ipc
        )
      }

      IPC_MESSAGE_TYPE.EVENT -> Json.decodeFromString<IpcEvent>(data)
      IPC_MESSAGE_TYPE.STREAM_DATA -> Json.decodeFromString<IpcStreamData>(data)
      IPC_MESSAGE_TYPE.STREAM_PULL -> Json.decodeFromString<IpcStreamPulling>(data)
      IPC_MESSAGE_TYPE.STREAM_PAUSED -> Json.decodeFromString<IpcStreamPaused>(data)
      IPC_MESSAGE_TYPE.STREAM_END -> Json.decodeFromString<IpcStreamEnd>(data)
      IPC_MESSAGE_TYPE.STREAM_ABORT -> Json.decodeFromString<IpcStreamAbort>(data)
    }
  } catch (e: Exception) {
    return data
  }
}