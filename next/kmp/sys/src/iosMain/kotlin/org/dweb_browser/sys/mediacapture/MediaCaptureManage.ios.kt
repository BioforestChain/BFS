package org.dweb_browser.sys.mediacapture

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import org.dweb_browser.core.ipc.helper.ReadableStream
import org.dweb_browser.core.module.MicroModule
import org.dweb_browser.core.std.permission.AuthorizationStatus
import org.dweb_browser.helper.WARNING
import org.dweb_browser.helper.withMainContext
import org.dweb_browser.sys.permission.SystemPermissionAdapterManager
import org.dweb_browser.sys.permission.SystemPermissionName
import platform.AVFAudio.AVAudioApplication
import platform.AVFAudio.AVAudioApplicationRecordPermissionDenied
import platform.AVFAudio.AVAudioApplicationRecordPermissionGranted
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIModalPresentationFullScreen

actual class MediaCaptureManage actual constructor() {
  init {
    SystemPermissionAdapterManager.append {
      when (task.name) {
        SystemPermissionName.CAMERA -> cameraAuthorizationStatus()
        SystemPermissionName.MICROPHONE -> microphoneAuthorizationStatus()
        else -> null
      }
    }
  }

  private suspend fun cameraAuthorizationStatus(): AuthorizationStatus {
    val status = when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
      AVAuthorizationStatusAuthorized -> AuthorizationStatus.GRANTED
      AVAuthorizationStatusNotDetermined -> {
        val result = CompletableDeferred<AuthorizationStatus>()
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
          if (granted) {
            result.complete(AuthorizationStatus.GRANTED)
          } else {
            result.complete(AuthorizationStatus.DENIED)
          }
        }
        return result.await()
      }

      else -> AuthorizationStatus.DENIED
    }
    return status
  }

  private suspend fun microphoneAuthorizationStatus(): AuthorizationStatus {
    val status = when (AVAudioApplication.sharedInstance.recordPermission) {
      AVAudioApplicationRecordPermissionDenied -> AuthorizationStatus.DENIED
      AVAudioApplicationRecordPermissionGranted -> AuthorizationStatus.GRANTED
      else -> {
        val result = CompletableDeferred<AuthorizationStatus>()
        AVAudioApplication.requestRecordPermissionWithCompletionHandler { success ->
          if (success) {
            result.complete(AuthorizationStatus.GRANTED)
          } else {
            result.complete(AuthorizationStatus.DENIED)
          }
        }
        return result.await()
      }
    }
    return status
  }

  actual suspend fun takePicture(microModule: MicroModule): String {
    val result = CompletableDeferred<String>()
    MediaCaptureHandler().launchCameraString {
      result.complete(it)
    }
    return result.await()
  }

  actual suspend fun captureVideo(microModule: MicroModule): String {
    val result = CompletableDeferred<String>()
    withMainContext {
      val rootController = UIApplication.sharedApplication.keyWindow?.rootViewController
      val videoController = MediaVideoViewController()
      videoController.videoPathBlock = {
        result.complete(it)
      }
      rootController?.presentViewController(videoController,true,null)
    }
    return result.await()
  }

  actual suspend fun recordSound(microModule: MicroModule): String {
    WARNING("Not yet implemented captureVideo")
    return ""
  }
}