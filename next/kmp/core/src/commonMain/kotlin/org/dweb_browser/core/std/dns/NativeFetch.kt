package org.dweb_browser.core.std.dns

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.fullPath
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.toLowerCasePreservingASCIIRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.dweb_browser.core.help.AdapterManager
import org.dweb_browser.core.help.toHttpRequestBuilder
import org.dweb_browser.core.help.toPureResponse
import org.dweb_browser.core.http.PureBinaryBody
import org.dweb_browser.core.http.PureRequest
import org.dweb_browser.core.http.PureResponse
import org.dweb_browser.core.http.PureStreamBody
import org.dweb_browser.core.http.PureStringBody
import org.dweb_browser.core.ipc.helper.IpcHeaders
import org.dweb_browser.core.ipc.helper.IpcMethod
import org.dweb_browser.core.module.MicroModule
import org.dweb_browser.helper.Debugger
import org.dweb_browser.helper.PromiseOut
import org.dweb_browser.helper.ioAsyncExceptionHandler
import org.dweb_browser.helper.platform.httpFetcher

typealias FetchAdapter = suspend (remote: MicroModule, request: PureRequest) -> PureResponse?

val debugFetch = Debugger("fetch")

val debugFetchFile = Debugger("fetch-file")

/**
 * file:/// => /usr & /sys as const
 * file://file.sys.dweb/ => /home & /tmp & /share as userData
 */
val nativeFetchAdaptersManager = NativeFetchAdaptersManager()

class NativeFetchAdaptersManager : AdapterManager<FetchAdapter>() {

  private var client = httpFetcher

  fun setClientProvider(client: HttpClient) {
    this.client = client
  }

  class HttpFetch(val manager: NativeFetchAdaptersManager) {
    val client get() = manager.client
    suspend operator fun invoke(request: PureRequest) = fetch(request)
    suspend operator fun invoke(url: String) =
      fetch(PureRequest(method = IpcMethod.GET, href = url))

    suspend fun fetch(request: PureRequest): PureResponse {
      try {
        debugFetch("httpFetch request", request.href)

        if (request.url.protocol.name == "data") {
          val dataUriContent = request.url.fullPath
          val dataUriContentInfo = dataUriContent.split(',', limit = 2)
          when (dataUriContentInfo.size) {
            2 -> {
              val meta = dataUriContentInfo[0]
              val bodyContent = dataUriContentInfo[1]
              val metaInfo = meta.split(';', limit = 2)
//              val response = PureResponse(HttpStatusCode.OK)
              when (metaInfo.size) {
                1 -> {
                  return PureResponse(
                    HttpStatusCode.OK,
                    headers = IpcHeaders().apply { set("Content-Type", meta) },
                    body = PureStringBody(bodyContent)
                  )
                }

                2 -> {
                  val encoding = metaInfo[1]
                  return if (encoding.trim().toLowerCasePreservingASCIIRules() == "base64") {
                    PureResponse(
                      HttpStatusCode.OK,
                      headers = IpcHeaders().apply { set("Content-Type", metaInfo[0]) },
                      body = PureBinaryBody(bodyContent.decodeBase64Bytes())
                    )
                  } else {
                    PureResponse(
                      HttpStatusCode.OK,
                      headers = IpcHeaders().apply { set("Content-Type", meta) },
                      body = PureStringBody(bodyContent)
                    )
                  }
                }
              }
            }
          }
          /// 保底操作
          return PureResponse(HttpStatusCode.OK, body = PureStringBody(dataUriContent))
        }
        val responsePo = PromiseOut<PureResponse>()
        CoroutineScope(ioAsyncExceptionHandler).launch {
          try {
            client.prepareRequest(request.toHttpRequestBuilder()).execute {
              debugFetch("httpFetch execute", request.href)
              val byteChannel = it.bodyAsChannel()
              val streamBody = PureStreamBody(byteChannel)
              val response = it.toPureResponse(body = streamBody)
              debugFetch("httpFetch response", request.href)
              responsePo.resolve(response)
            }
          } catch (e: Throwable) {
            val response = PureResponse(
              HttpStatusCode.ServiceUnavailable,
              body = PureStringBody(request.url.toString() + "\n" + e.stackTraceToString())
            )
            responsePo.resolve(response)
          }
        }
        return responsePo.waitPromise()
      } catch (e: Throwable) {
        debugFetch("httpFetch", request.url, e)
        return PureResponse(
          HttpStatusCode.ServiceUnavailable,
          body = PureStringBody(request.url.toString() + "\n" + e.stackTraceToString())
        )
      }
    }
  }

  val httpFetch = HttpFetch(this)
}

val httpFetch = nativeFetchAdaptersManager.httpFetch

suspend fun MicroModule.nativeFetch(request: PureRequest): PureResponse {
  for (fetchAdapter in nativeFetchAdaptersManager.adapters) {
    val response = fetchAdapter(this, request)
    if (response != null) {
      return response
    }
  }
  return nativeFetchAdaptersManager.httpFetch(request)
}

suspend inline fun MicroModule.nativeFetch(url: Url) = nativeFetch(IpcMethod.GET, url)

suspend inline fun MicroModule.nativeFetch(url: String) = nativeFetch(IpcMethod.GET, url)

suspend inline fun MicroModule.nativeFetch(method: IpcMethod, url: Url) =
  nativeFetch(PureRequest(url.toString(), method))

suspend inline fun MicroModule.nativeFetch(method: IpcMethod, url: String) =
  nativeFetch(PureRequest(url, method))
