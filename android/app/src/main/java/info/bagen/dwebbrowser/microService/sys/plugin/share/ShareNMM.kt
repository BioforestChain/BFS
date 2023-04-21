package info.bagen.dwebbrowser.microService.sys.plugin.share

import android.content.Intent
import info.bagen.dwebbrowser.App
import info.bagen.dwebbrowser.microService.core.AndroidNativeMicroModule
import info.bagen.dwebbrowser.microService.core.BootstrapContext
import info.bagen.dwebbrowser.microService.helper.Mmid
import info.bagen.dwebbrowser.microService.helper.PromiseOut
import info.bagen.dwebbrowser.microService.helper.printdebugln
import info.bagen.dwebbrowser.microService.sys.plugin.fileSystem.EFileDirectory
import info.bagen.dwebbrowser.microService.sys.plugin.share.ShareController.Companion.controller
import org.http4k.core.Method
import org.http4k.core.MultipartFormBody
import org.http4k.lens.Query
import org.http4k.lens.composite
import org.http4k.lens.string
import org.http4k.routing.bind
import org.http4k.routing.routes


data class ShareOptions(
    val title: String?,
    val text: String?,
    val url: String?,
)

fun debugShare(tag: String, msg: Any? = "", err: Throwable? = null) =
    printdebugln("Share", tag, msg, err)

class ShareNMM : AndroidNativeMicroModule("share.sys.dweb") {

    private val plugin = CacheFilePlugin()
    override suspend fun _bootstrap(bootstrapContext: BootstrapContext) {
        val shareOption = Query.composite { spec ->
            ShareOptions(
                title = string().optional("title")(spec),
                text = string().optional("text")(spec),
                url = string().optional("url")(spec)
            )
        }
        apiRouting = routes(
            /** 分享*/
            "/share" bind Method.POST to defineHandler { request, ipc ->
                val ext = shareOption(request)
                val receivedForm = MultipartFormBody.from(request)
                val fileByteArray = receivedForm.files("files")
                val files = mutableListOf<String>()
                val result = PromiseOut<String>()
                // 写入缓存
                fileByteArray.map { file ->
                    val url = plugin.writeFile(
                        file.filename,
                        EFileDirectory.Cache.location,
                        file.content,
                        false
                    )
                    files.add(url)
                }
                debugShare("open_share", "share===>${ipc.remote.mmid}  ${files}")
                openActivity(ipc.remote.mmid)
                controller.waitActivityResultLauncherCreated()
              SharePlugin.share(controller, ext.title, ext.text, ext.url, files, result)
                // 等待结果回调
                controller.activity?.getShareData { it ->
                    debugShare("share", "result => $it")
                    result.resolve(it)
                }

                return@defineHandler result.waitPromise()
            },
        )
    }

    override fun openActivity(remoteMmid: Mmid) {
        val activity = getActivity(remoteMmid)
        val intent = Intent(getActivity(remoteMmid), ShareActivity::class.java)
        intent.action = "info.bagen.dwebbrowser.share"
        intent.`package` = App.appContext.packageName
//         intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
//         intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        activity?.startActivity(intent)
    }


    override suspend fun _shutdown() {
        TODO("Not yet implemented")
    }
}
