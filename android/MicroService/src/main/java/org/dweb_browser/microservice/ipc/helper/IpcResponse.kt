package org.dweb_browser.microservice.ipc.helper

import kotlinx.serialization.Serializable
import org.dweb_browser.microservice.ipc.Ipc
import org.http4k.core.Response
import org.http4k.core.Status
import java.io.InputStream

class IpcResponse(
  val req_id: Int,
  val statusCode: Int,
  val headers: IpcHeaders,
  val body: IpcBody,
  val ipc: Ipc,
) : IpcMessage(IPC_MESSAGE_TYPE.RESPONSE) {

  init {
    if (body is IpcBodySender) {
      IpcBodySender.IPC.usableByIpc(ipc, body)
    }
  }

  companion object {
    fun fromText(
      req_id: Int,
      statusCode: Int = 200,
      headers: IpcHeaders = IpcHeaders(),
      text: String,
      ipc: Ipc
    ) = IpcResponse(
      req_id,
      statusCode,
      headers.also { headers.init("Content-Type", "text/plain") },
      IpcBodySender.fromText(text, ipc),
      ipc,
    )

    fun fromBinary(
      req_id: Int, statusCode: Int = 200, headers: IpcHeaders, binary: ByteArray, ipc: Ipc
    ) = IpcResponse(
      req_id,
      statusCode,
      headers.also {
        headers.init("Content-Type", "application/octet-stream");
        headers.init("Content-Length", binary.size.toString());
      },
      IpcBodySender.fromBinary(binary, ipc),
      ipc,
    );


    fun fromStream(
      req_id: Int,
      statusCode: Int = 200,
      headers: IpcHeaders = IpcHeaders(),
      stream: InputStream,
      ipc: Ipc
    ) = IpcResponse(
      req_id,
      statusCode,
      headers.also {
        headers.init("Content-Type", "application/octet-stream");
      },
      IpcBodySender.fromStream(stream, ipc),
      ipc,
    )

    enum class BodyStrategy {
      AUTO,
      STREAM,
      BINARY,
    }

    fun fromResponse(
      req_id: Int, response: Response, ipc: Ipc, bodyStrategy: BodyStrategy = BodyStrategy.AUTO
    ) = IpcResponse(
      req_id,
      response.status.code,
      IpcHeaders(response.headers),
      when (val len = response.body.length) {
        0L -> IpcBodySender.fromText("", ipc)
        else -> when (bodyStrategy) {
          BodyStrategy.AUTO -> if (len == null) false else len <= DEFAULT_BUFFER_SIZE
          BodyStrategy.STREAM -> false
          BodyStrategy.BINARY -> true
        }.let { asBinary ->
          if (asBinary) {
            IpcBodySender.fromBinary(response.body.payload.array(), ipc)
          } else {
            IpcBodySender.fromStream(response.body.stream, ipc)
          }
        }
      },
      ipc,
    )
  }

  fun toResponse() =
    Response(
      Status.fromCode(statusCode) ?: throw Exception("invalid statusCode $statusCode")
    ).headers(this.headers.toList()).let { res ->
      when (val body = body.raw) {
        is String -> res.body(body)
        is ByteArray -> res.body(body.inputStream(), body.size.toLong())
        is InputStream -> res.body(body)
        else -> throw Exception("invalid body to response: $body")
      }
    }

  val ipcResMessage by lazy {
    IpcResMessage(req_id, statusCode, headers.toMap(), body.metaBody)
  }
}

@Serializable
data class IpcResMessage(
  val req_id: Int,
  val statusCode: Int,
  val headers: MutableMap<String, String>,
  val metaBody: MetaBody,
) : IpcMessage(IPC_MESSAGE_TYPE.RESPONSE)
