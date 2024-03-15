package org.dweb_browser.core.std.http

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import multipart.MultipartConsumer
import multipart.getBoundary
import multipart.processMultipartOpen
import multipart.processMultipartWrite
import org.dweb_browser.core.http.router.bind
import org.dweb_browser.core.module.BootstrapContext
import org.dweb_browser.core.module.NativeMicroModule
import org.dweb_browser.helper.consumeEachArrayRange
import org.dweb_browser.helper.ioAsyncExceptionHandler
import org.dweb_browser.helper.platform.MultipartFieldData
import org.dweb_browser.helper.platform.MultipartFieldDescription
import org.dweb_browser.helper.platform.MultipartFieldEnd
import org.dweb_browser.helper.platform.MultipartFilePackage
import org.dweb_browser.helper.platform.MultipartFileType
import org.dweb_browser.pure.http.PureMethod

class MultipartNMM : NativeMicroModule("multipart.http.std.dweb", "multipart/form-data parser") {


  @OptIn(ExperimentalSerializationApi::class)
  override suspend fun _bootstrap(bootstrapContext: BootstrapContext) {
    routes(
      "/parser" bind PureMethod.POST by defineCborPackageResponse {
        val boundary =
          getBoundary(request.headers.toMap()) ?: throw Exception("boundary parser failed")
        val deferred = CompletableDeferred<Int>()
        val context = SupervisorJob() + ioAsyncExceptionHandler
        val multipartEachArrayRangeCallback = object : MultipartConsumer {
          override fun onOpen(id: Int) {
            deferred.complete(id)
          }

          override fun onFieldStart(
            name: String?,
            fileName: String?,
            contentType: String?,
            fieldIndex: Int
          ) {
            runBlocking(context) {
              emit(
                MultipartFilePackage(
                  MultipartFileType.Desc,
                  Cbor.encodeToByteArray<MultipartFieldDescription>(
                    MultipartFieldDescription(
                      name,
                      fileName,
                      contentType,
                      fieldIndex
                    )
                  )
                )
              )
            }
          }

          override fun onFieldChunk(fieldIndex: Int, chunk: ByteArray) {
            runBlocking(context) {
              emit(
                MultipartFilePackage(
                  MultipartFileType.Data,
                  Cbor.encodeToByteArray<MultipartFieldData>(MultipartFieldData(fieldIndex, chunk))
                )
              )
            }
          }

          override fun onFieldEnd(fieldIndex: Int) {
            runBlocking(context) {
              emit(
                MultipartFilePackage(
                  MultipartFileType.End,
                  Cbor.encodeToByteArray<MultipartFieldEnd>(MultipartFieldEnd(fieldIndex))
                )
              )
            }
          }

          override fun onClose(id: Int) {
            runBlocking(context) {
              end()
            }
          }
        }

        ioAsyncScope.launch {
          processMultipartOpen(boundary, multipartEachArrayRangeCallback)
        }
        val id = deferred.await()

        ioAsyncScope.launch {
          request.body.toPureStream().getReader("multipart/form-data")
            .consumeEachArrayRange { byteArray, last ->
              if (!(last && byteArray.isEmpty())) {
                processMultipartWrite(id, byteArray)
              }
            }
        }
      }
    )
  }

  override suspend fun _shutdown() {}
}