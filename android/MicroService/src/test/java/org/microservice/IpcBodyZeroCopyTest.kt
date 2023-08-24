package org.microservice

import kotlinx.coroutines.runBlocking
import org.dweb_browser.microservice.core.BootstrapContext
import org.dweb_browser.microservice.core.NativeMicroModule
import org.dweb_browser.microservice.ipc.NativeIpc
import org.dweb_browser.microservice.ipc.NativeMessageChannel
import org.dweb_browser.microservice.ipc.helper.IPC_ROLE
import org.dweb_browser.microservice.ipc.helper.IpcBodySender
import org.dweb_browser.microservice.ipc.helper.IpcHeaders
import org.dweb_browser.microservice.ipc.helper.IpcMessage
import org.dweb_browser.microservice.ipc.helper.IpcMethod
import org.dweb_browser.microservice.ipc.helper.IpcRequest
import org.dweb_browser.microservice.ipc.helper.ReadableStream
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class IpcBodyZeroCopyTest {
    @Test
    fun ipcRequestZeroCopyTest() = runBlocking {
        val m1 = object : NativeMicroModule("m1", "m1") {
            override suspend fun _bootstrap(bootstrapContext: BootstrapContext) {
            }

            override suspend fun _shutdown() {
            }
        }
        val m2 = object : NativeMicroModule("m2", "m2") {
            override suspend fun _bootstrap(bootstrapContext: BootstrapContext) {
            }

            override suspend fun _shutdown() {
            }
        }

        val channel = NativeMessageChannel<IpcMessage, IpcMessage>();
        val ipc1 = NativeIpc(channel.port1, m1, IPC_ROLE.SERVER);
        val ipc2 = NativeIpc(channel.port2, m2, IPC_ROLE.CLIENT);

        var readableStream = ReadableStream();
        var ipcBody = IpcBodySender.fromStream(readableStream, ipc1);
        var ipcRequest =
            IpcRequest(1, "http://test.com", IpcMethod.POST, IpcHeaders(), ipcBody, ipc1);


        var httpRequestMessage = ipcRequest.toRequest();
        var httpContentStream = httpRequestMessage.body.stream

        assertEquals(httpContentStream, readableStream);
    }
}