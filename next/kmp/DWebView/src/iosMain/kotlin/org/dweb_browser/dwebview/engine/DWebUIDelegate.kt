package org.dweb_browser.dwebview.engine

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import org.dweb_browser.core.module.getUIApplication
import org.dweb_browser.dwebview.DWebViewOptions
import org.dweb_browser.dwebview.DwebViewI18nResource
import org.dweb_browser.dwebview.IDWebView
import org.dweb_browser.dwebview.create
import org.dweb_browser.dwebview.debugDWebView
import org.dweb_browser.helper.Signal
import org.dweb_browser.helper.launchWithMain
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaType
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSAttributedString
import platform.Foundation.create
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UILabel
import platform.UIKit.UIViewController
import platform.WebKit.WKFrameInfo
import platform.WebKit.WKMediaCaptureType
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKPermissionDecision
import platform.WebKit.WKSecurityOrigin
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWindowFeatures
import platform.darwin.NSObject

class DWebUIDelegate(private val engine: DWebViewEngine) : NSObject(), WKUIDelegateProtocol {

    //#region UIDelegate
    private val jsAlertSignal = Signal<Pair<JsParams, SignalResult<Unit>>>()
    private val jsConfirmSignal = Signal<Pair<JsParams, SignalResult<Boolean>>>()
    private val jsPromptSignal = Signal<Pair<JsPromptParams, SignalResult<String?>>>()
    private val closeSignal = engine.closeSignal
    private val createWindowSignal = engine.createWindowSignal

    internal data class JsParams(
        val webView: WKWebView,
        val message: String,
        val wkFrameInfo: WKFrameInfo
    )

    internal data class JsPromptParams(
        val webView: WKWebView,
        val prompt: String,
        val defaultText: String?,
        val wkFrameInfo: WKFrameInfo
    )

    data class CreateWebViewParams(
        val webView: WKWebView,
        val configuration: WKWebViewConfiguration,
        val navigationAction: WKNavigationAction,
        val windowFeatures: WKWindowFeatures,
        val completionHandler: (WKWebView?) -> Unit
    )

    @OptIn(ExperimentalForeignApi::class)
    override fun webView(
        webView: WKWebView,
        createWebViewWithConfiguration: WKWebViewConfiguration,
        forNavigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures
    ): WKWebView? {
        val url = forNavigationAction.request.URL?.absoluteString
        return if (url != null && engine.closeWatcher.consuming.remove(url)) {
            val isUserGesture =
                forNavigationAction.targetFrame == null || !forNavigationAction.targetFrame!!.mainFrame
            val watcher = engine.closeWatcher.apply(isUserGesture)

            engine.mainScope.launchWithMain {
                engine.closeWatcher.resolveToken(url, watcher)
            }
            null
        } else {
            val createDwebviewEngin = DWebViewEngine(
                engine.frame, // TODO use windowFeatures.x/y/width/height
                engine.remoteMM,
                DWebViewOptions(url ?: ""),
                createWebViewWithConfiguration
            )
            val dwebView = IDWebView.create(
                createDwebviewEngin
            )
            engine.mainScope.launch {
                createWindowSignal.emit(dwebView)
            }
            createDwebviewEngin
        }

        // if (url != null) {
        // engine.onReady {
        //   url = forNavigationAction.request.URL?.absoluteString
        //   createAction()
        // }
    }

    override fun webViewDidClose(webView: WKWebView) {
        engine.mainScope.launch {
            closeSignal.emit()
        }
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun webView(
        webView: WKWebView,
        runJavaScriptAlertPanelWithMessage: String,
        initiatedByFrame: WKFrameInfo,
        completionHandler: () -> Unit
    ) {
        val finalNext = Signal<Pair<JsParams, SignalResult<Unit>>>()

        finalNext.listen {
            val (params, ctx) = it
            val vc = webView.getUIViewController()
            if (vc != null) {
                val alertController = UIAlertController.alertControllerWithTitle(
                    params.webView.title,
                    params.message,
                    UIAlertControllerStyleAlert
                )
                val domainLabel = UILabel();
                domainLabel.attributedText = NSAttributedString.create(
                    string = engine.remoteMM.mmid, attributes = mapOf(
                        NSFontAttributeName to UIFont.systemFontOfSize(fontSize = 8.0),
                        NSForegroundColorAttributeName to UIColor.blackColor.colorWithAlphaComponent(alpha = 0.2)
                    )
                )
                domainLabel.sizeToFit() // 让标签自适应内容大小
                alertController.view.addSubview(domainLabel)

                domainLabel.translatesAutoresizingMaskIntoConstraints = false
                NSLayoutConstraint.activateConstraints(
                    constraints = listOf(
                        domainLabel.topAnchor.constraintEqualToAnchor(
                            anchor = alertController.view.topAnchor,
                            constant = 3.0
                        ),
                        domainLabel.centerXAnchor.constraintEqualToAnchor(anchor = alertController.view.centerXAnchor),
                        domainLabel.leadingAnchor.constraintGreaterThanOrEqualToAnchor(
                            anchor = alertController.view.leadingAnchor,
                            constant = 20.0
                        ),
                    )
                )

                alertController.addAction(
                    UIAlertAction.actionWithTitle(
                        DwebViewI18nResource.alert_action_ok.text,
                        UIAlertActionStyleDefault
                    ) {
                        ctx.complete(Unit)
                    })
                vc.presentViewController(alertController, true, null)
            } else {
                ctx.complete(Unit)
            }
        }

        engine.mainScope.launch {
            jsAlertSignal.emitForResult(
                JsParams(
                    webView,
                    runJavaScriptAlertPanelWithMessage,
                    initiatedByFrame
                ), finalNext
            )
            completionHandler()
        }
    }

    override fun webView(
        webView: WKWebView,
        runJavaScriptConfirmPanelWithMessage: String,
        initiatedByFrame: WKFrameInfo,
        completionHandler: (Boolean) -> Unit
    ) {
        val finalNext = Signal<Pair<JsParams, SignalResult<Boolean>>>()

        finalNext.listen {
            val (params, ctx) = it
            val vc = webView.getUIViewController()
            if (vc != null) {
                val confirmController = UIAlertController.alertControllerWithTitle(
                    params.webView.title,
                    params.message,
                    UIAlertControllerStyleAlert
                )
                confirmController.addAction(
                    UIAlertAction.actionWithTitle(
                        DwebViewI18nResource.confirm_action_cancel.text,
                        UIAlertActionStyleCancel
                    ) {
                        ctx.complete(false)
                    })
                confirmController.addAction(
                    UIAlertAction.actionWithTitle(
                        DwebViewI18nResource.confirm_action_confirm.text,
                        UIAlertActionStyleDefault
                    ) {
                        ctx.complete(true)
                    })
                vc.presentViewController(confirmController, true, null)
            } else {
                ctx.complete(false)
            }
        }

        engine.mainScope.launch {
            val (confirm, _) = jsConfirmSignal.emitForResult(
                JsParams(
                    webView,
                    runJavaScriptConfirmPanelWithMessage,
                    initiatedByFrame
                ), finalNext
            )
            completionHandler(confirm!!)
        }
    }

    override fun webView(
        webView: WKWebView,
        runJavaScriptTextInputPanelWithPrompt: String,
        defaultText: String?,
        initiatedByFrame: WKFrameInfo,
        completionHandler: (String?) -> Unit
    ) {
        val finalNext = Signal<Pair<JsPromptParams, SignalResult<String?>>>()

        finalNext.listen {
            val (params, ctx) = it
            val vc = webView.getUIViewController()
            if (vc != null) {
                val promptController = UIAlertController.alertControllerWithTitle(
                    params.webView.title,
                    params.prompt,
                    UIAlertControllerStyleAlert
                )
                promptController.addTextFieldWithConfigurationHandler { textField ->
                    textField?.text = params.defaultText
                    textField?.selectAll(null)
                    promptController.addAction(
                        UIAlertAction.actionWithTitle(
                            DwebViewI18nResource.prompt_action_cancel.text,
                            UIAlertActionStyleCancel
                        ) {
                            ctx.complete(null)
                        })
                    promptController.addAction(
                        UIAlertAction.actionWithTitle(
                            DwebViewI18nResource.prompt_action_confirm.text,
                            UIAlertActionStyleDefault
                        ) {
                            ctx.complete(textField?.text)
                        })
                }
                vc.presentViewController(promptController, true, null)
            } else {
                ctx.complete(null)
            }
        }

        engine.mainScope.launch {
            val (promptText, _) = jsPromptSignal.emitForResult(
                JsPromptParams(
                    webView,
                    runJavaScriptTextInputPanelWithPrompt,
                    defaultText,
                    initiatedByFrame
                ), finalNext
            )
            completionHandler(promptText ?: "")
        }
    }

    override fun webView(
        webView: WKWebView,
        requestMediaCapturePermissionForOrigin: WKSecurityOrigin,
        initiatedByFrame: WKFrameInfo,
        type: WKMediaCaptureType,
        decisionHandler: (WKPermissionDecision) -> Unit
    ) {
        val mediaTypes = when (type) {
            WKMediaCaptureType.WKMediaCaptureTypeCamera -> listOf(AVMediaTypeVideo)
            WKMediaCaptureType.WKMediaCaptureTypeMicrophone -> listOf(AVMediaTypeAudio)
            WKMediaCaptureType.WKMediaCaptureTypeCameraAndMicrophone -> listOf(
                AVMediaTypeVideo,
                AVMediaTypeAudio
            )

            else -> emptyList()
        }

        if (mediaTypes.isEmpty()) {
            decisionHandler(WKPermissionDecision.WKPermissionDecisionPrompt)
            return
        }

        engine.mainScope.launch {
            mediaTypes.forEach {
                val isAuthorized = when (AVCaptureDevice.authorizationStatusForMediaType(it)) {
                    AVAuthorizationStatusNotDetermined -> AVCaptureDevice.requestAccessForMediaType(
                        it
                    )
                        .await()

                    AVAuthorizationStatusAuthorized -> true
                    AVAuthorizationStatusDenied -> false
                    /*
                     * 1. 家长控制功能启用,限制了应用访问摄像头或麦克风
                     * 2. 机构部署的设备,限制了应用访问硬件功能
                     * 3. 用户在 iCloud 中的"隐私"设置中针对应用禁用了访问权限
                     */
                    AVAuthorizationStatusRestricted -> null
                    else -> null
                }

                /// 认证失败
                if (isAuthorized == false) {
                    decisionHandler(WKPermissionDecision.WKPermissionDecisionDeny)
                    return@launch
                }

                /// 受限 或者 未知？
                /// TODO 用额外的提示框提示用户
                if (isAuthorized == null) {
                    decisionHandler(WKPermissionDecision.WKPermissionDecisionPrompt)
                    return@launch
                }
            }

            /// 所有的权限都验证通过
            decisionHandler(WKPermissionDecision.WKPermissionDecisionGrant)
        }
    }

    fun AVCaptureDevice.Companion.requestAccessForMediaType(mediaType: AVMediaType?): Deferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()

        AVCaptureDevice.requestAccessForMediaType(mediaType) {
            deferred.complete(it)
        }

        return deferred
    }

    internal class SignalResult<R> {
        var result: R? = null
        var hasResult: Boolean = false

        /**
         * 写入结果
         */
        fun complete(result: R) {
            if (hasResult) return

            this.result = result
            hasResult = true
            next()
        }

        /**
         * 跳过处置，由下一个处理者接管
         */
        fun next() {
            waiter.complete(Unit)
        }

        val waiter = CompletableDeferred<Unit>()
    }

    private suspend fun <T, R> Signal<Pair<T, SignalResult<R>>>.emitForResult(
        args: T,
        finallyNext: Signal<Pair<T, SignalResult<R>>>
    ): Pair<R?, Boolean> {
        try {
            val ctx = SignalResult<R>()
            this@emitForResult.emit(Pair(args, ctx))
            finallyNext.emit(Pair(args, ctx))

            ctx.waiter.await()
            if (ctx.hasResult) {
                return Pair(ctx.result, true)
            }
        } catch (e: Throwable) {
            debugDWebView("DUIDelegateProtocol", e.message ?: e.stackTraceToString())
        }

        return Pair(null, false)
    }

    private fun WKWebView.getUIViewController(): UIViewController? {
        return engine.remoteMM.getUIApplication().keyWindow?.rootViewController
    }
    //#endregion
}