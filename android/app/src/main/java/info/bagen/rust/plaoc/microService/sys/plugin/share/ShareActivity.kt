package info.bagen.rust.plaoc.microService.sys.plugin.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import info.bagen.rust.plaoc.microService.helper.*
import info.bagen.rust.plaoc.microService.sys.plugin.share.ShareController.Companion.controller
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class ShareActivity : AppCompatActivity() {

    private var isFirstOpen = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller.activity = this

        controller.resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val data: Intent? = result.data
                val code = data?.getStringExtra("result")
            debugShare(
                "RESULT_SHARE_CODE",
                "data:${result.data} resultCode:${result.resultCode} code:$code"
            )
                GlobalScope.launch(ioAsyncExceptionHandler) {
                    getShareSignal.emit(data?.dataString ?: "OK")
                }
            }
    }

    override fun onResume() {
        super.onResume()
        if (isFirstOpen) {
            isFirstOpen = false
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.resultLauncher = null
    }

    private val getShareSignal = Signal<String>()

    fun getShareData(cb: Callback<String>) = getShareSignal.listen(cb)

}