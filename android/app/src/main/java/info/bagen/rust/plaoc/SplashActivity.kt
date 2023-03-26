package info.bagen.rust.plaoc

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.google.accompanist.web.WebContent
import com.google.accompanist.web.WebView
import com.google.accompanist.web.WebViewState
import info.bagen.rust.plaoc.ui.splash.SplashPrivacyDialog
import info.bagen.rust.plaoc.ui.theme.RustApplicationTheme
import info.bagen.rust.plaoc.util.KEY_ENABLE_AGREEMENT
import info.bagen.rust.plaoc.util.getBoolean
import info.bagen.rust.plaoc.util.saveBoolean

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        App.startMicroModuleProcess()

        val enable = this.getBoolean(KEY_ENABLE_AGREEMENT, false)
        setContent {
            RustApplicationTheme {
                val webUrl = remember { mutableStateOf("") }
                SplashMainView()
                if (enable) {
                    App.grant.resolve(true)
                    return@RustApplicationTheme
                }

                SplashPrivacyDialog(
                    openHome = {
                        App.appContext.saveBoolean(KEY_ENABLE_AGREEMENT, true)
                        App.grant.resolve(true)
                    },
                    openWebView = { url -> webUrl.value = url },
                    closeApp = {
                        App.grant.resolve(false)
                        finish()
                    }
                )
                PrivacyView(url = webUrl)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        finish()
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun SplashMainView() {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val gradient = listOf(
                Color(0xFF71D78E), Color(0xFF548FE3)
            )
            Text(
                text = stringResource(id = R.string.app_name),
                modifier = Modifier.align(Alignment.BottomCenter),
                style = TextStyle(
                    brush = Brush.linearGradient(gradient), fontSize = 50.sp
                )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

@Composable
fun PrivacyView(url: MutableState<String>) {
    BackHandler { url.value = "" }
    if (url.value.isNotEmpty()) {
        WebView(state = WebViewState(WebContent.Url(url.value)), modifier = Modifier.fillMaxSize())
    }
}