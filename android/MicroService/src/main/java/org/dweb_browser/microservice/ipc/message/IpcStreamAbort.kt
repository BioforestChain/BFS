package org.dweb_browser.microservice.ipc.message

data class IpcStreamAbort(override val stream_id: String) :
    IpcMessage(IPC_MESSAGE_TYPE.STREAM_ABORT), IpcStream