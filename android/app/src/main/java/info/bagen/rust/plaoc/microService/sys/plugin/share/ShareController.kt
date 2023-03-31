package info.bagen.rust.plaoc.microService.sys.plugin.share

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import info.bagen.rust.plaoc.App
import info.bagen.rust.plaoc.microService.helper.PromiseOut

class ShareController() {

    companion object {
         val controller = ShareController()
    }
    val RESULT_SHARE_CODE = 3

    var activity: ShareActivity? = null

    private var activityResultLauncherTask = PromiseOut<ActivityResultLauncher<Intent>>()
    suspend fun waitActivityResultLauncherCreated() = activityResultLauncherTask.waitPromise()

    var resultLauncher: ActivityResultLauncher<Intent>? = null
        set(value) {
            if (field == value) {
                return
            }
            field = value
            if (value == null) {
                activityResultLauncherTask = PromiseOut()
            } else {
                activityResultLauncherTask.resolve(value)
            }
        }
}