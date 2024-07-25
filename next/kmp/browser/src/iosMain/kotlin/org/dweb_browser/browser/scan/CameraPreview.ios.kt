package org.dweb_browser.browser.scan

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.interop.LocalUIViewController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import org.dweb_browser.dwebview.UnScaleBox
import org.dweb_browser.helper.PurePoint
import org.dweb_browser.helper.PureRect
import org.dweb_browser.helper.WARNING
import org.dweb_browser.helper.platform.NSDataHelper.toByteArray
import org.dweb_browser.helper.platform.PureViewController
import org.dweb_browser.helper.times
import org.dweb_browser.helper.toPoint
import org.dweb_browser.helper.toRect
import org.dweb_browser.sys.window.render.LocalWindowContentStyle
import org.dweb_browser.sys.window.render.UIKitViewInWindow
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession.Companion.discoverySessionWithDeviceTypes
import platform.AVFoundation.AVCaptureDeviceInput.Companion.deviceInputWithDevice
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInDualCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInDualWideCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInDuoCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInUltraWideCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.hasTorch
import platform.AVFoundation.torchMode
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRect
import platform.Foundation.NSDictionary
import platform.Foundation.NSNumber
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue


@Composable
actual fun CameraPreviewRender(
  modifier: Modifier,
  controller: SmartScanController
) {

  // 判断是否在模拟器执行
  if (PureViewController.isSimulator) {
    Text(
      """
      Camera is not available on simulator.
      Please try to run on a real iOS device.
      """.trimIndent(),
      color = Color.White,
      modifier = modifier,
      textAlign = TextAlign.Center
    )
  } else {
    RealDeviceCamera(modifier, controller)
  }
}


@OptIn(ExperimentalForeignApi::class)
@Composable
private fun RealDeviceCamera(
  modifier: Modifier,
  controller: SmartScanController
) {
  val uiViewController = LocalUIViewController.current
  val density = LocalDensity.current.density
  // 创建一个 UIView 作为相机预览的容器
  val uiView = remember { UIView() }
  // 创建相机控制器
  val cameraController = remember(uiViewController) {
    val c = CameraControllerImpl(controller, uiViewController, uiView, density)
    controller.cameraController?.stop()
    controller.cameraController = c // 赋值
    c
  }
  DisposableEffect(Unit) {
    onDispose {
      cameraController.stop()
    }
  }
  cameraController.triggerCapture()
  val scale by controller.scaleFlow.collectAsState()
  UnScaleBox(scale, modifier) {
    uiView.UIKitViewInWindow(
      modifier = Modifier.fillMaxSize(),
      style = LocalWindowContentStyle.current.frameStyle,
      onResize = { _, frame ->
        cameraController.onResize(frame)
      }
    )
  }
}


@OptIn(ExperimentalForeignApi::class)
class CameraControllerImpl(
  private val controller: SmartScanController,
  private val uiViewController: UIViewController,
  private val uiView: UIView,
  private val density: Float
) : CameraController {
  //  设备类型
  private val deviceTypes = listOf(
    AVCaptureDeviceTypeBuiltInWideAngleCamera,
    AVCaptureDeviceTypeBuiltInDualWideCamera,
    AVCaptureDeviceTypeBuiltInDualCamera,
    AVCaptureDeviceTypeBuiltInUltraWideCamera,
    AVCaptureDeviceTypeBuiltInDuoCamera
  )

  // 创建输出
  private val metadataOutput = AVCaptureMetadataOutput()

  // 创建相机
  private val camera: AVCaptureDevice =
    discoverySessionWithDeviceTypes(
      deviceTypes = deviceTypes,
      mediaType = AVMediaTypeVideo,
      position = AVCaptureDevicePositionBack,
    ).devices.firstOrNull() as AVCaptureDevice

  // 创建一个相机会话
  private val captureSession = AVCaptureSession().also { captureSession ->
    // 配置相机设备
    captureSession.sessionPreset = AVCaptureSessionPresetPhoto
    val deviceInput = deviceInputWithDevice(device = camera, error = null)
    deviceInput?.let {
      if (captureSession.canAddInput(it)) {
        captureSession.addInput(it)
      }
    }
    if (captureSession.canAddOutput(metadataOutput)) {
      captureSession.addOutput(metadataOutput)
    } else {
      WARNING("Could not add photo output to capture session")
    }
  }

  // 创建相机预览层
  private val cameraPreviewLayer: AVCaptureVideoPreviewLayer =
    AVCaptureVideoPreviewLayer(session = captureSession).apply {
      // 设置预览图层的视频重力属性
      videoGravity = AVLayerVideoGravityResizeAspectFill
      uiView.layer.addSublayer(this)
    }

  // 从相机流中拿内容
  private val metaDelegate: AVCaptureMetadataOutputObjectsDelegateProtocol =
    object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
      override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection
      ) {
        val barcodes =
          didOutputMetadataObjects.filterIsInstance<AVMetadataMachineReadableCodeObject>()
        val results = mutableListOf<BarcodeResult>()
        for (barcode in barcodes) {
          val barcodeValue = barcode.stringValue
          val barcodeBounds =
            cameraPreviewLayer.transformedMetadataObjectForMetadataObject(barcode)?.bounds?.toRect()
              ?.times(density)
          val corners = (cameraPreviewLayer.transformedMetadataObjectForMetadataObject(barcode)
              as? AVMetadataMachineReadableCodeObject)?.corners?.mapNotNull {
            if (it is NSDictionary) {
              val x = (it.objectForKey("X") as? NSNumber)?.doubleValue ?: return@mapNotNull null
              val y = (it.objectForKey("Y") as? NSNumber)?.doubleValue ?: return@mapNotNull null
              CGPointMake(x * density, y * density)
            } else {
              null
            }
          }
          barcodeValue?.let {
            val result = BarcodeResult(
              data = barcodeValue,
              boundingBox = barcodeBounds ?: PureRect(0f, 0f),
              topLeft = corners?.get(2)?.toPoint() ?: PurePoint(0f, 0f),
              topRight = corners?.get(3)?.toPoint() ?: PurePoint(0f, 0f),
              bottomLeft = corners?.get(1)?.toPoint() ?: PurePoint(0f, 0f),
              bottomRight = corners?.get(0)?.toPoint() ?: PurePoint(0f, 0f),
            )
            results.add(result)
          }
        }
        controller.barcodeResultFlow.tryEmit(results)
      }
    }

  // 从相册拿内容
  private val galleryDelegate: PHPickerViewControllerDelegateProtocol =
    object : NSObject(), PHPickerViewControllerDelegateProtocol {
      override fun picker(
        picker: PHPickerViewController,
        didFinishPicking: List<*>
      ) {
        // 用户选中图片
        if (didFinishPicking.isNotEmpty()) {
          val result = didFinishPicking[0] as PHPickerResult
          // 获取结果中的 itemProvider
          val itemProvider = result.itemProvider
          if (itemProvider.hasItemConformingToTypeIdentifier(UTTypeImage.toString())) {
            itemProvider.loadDataRepresentationForTypeIdentifier(UTTypeImage.toString()) { data, error ->
              if (data != null) {
                val imageFromNsData = Image.makeFromEncoded(data.toByteArray())
                val bitmapFromNsData = Bitmap.makeFromImage(imageFromNsData).asComposeImageBitmap()
                controller.albumImageFlow.tryEmit(bitmapFromNsData)
                // 回调到二维码识别
                controller.imageCaptureFlow.tryEmit(data)
              } else if (error != null) {
                // 处理错误
                WARNING("Error loading data: ${error.localizedDescription}")
              }
            }
          }
        }
        // 关闭选择器视图控制器
        picker.dismissViewControllerAnimated(true, null)
      }
    }

  fun triggerCapture() {
    start()
    metadataOutput.setMetadataObjectsDelegate(metaDelegate, queue = dispatch_get_main_queue())
    metadataOutput.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
  }

  fun start() {
    captureSession.startRunning()
  }

  internal fun onResize(rect: CValue<CGRect>) {
    CATransaction.begin()
    CATransaction.setValue(true, kCATransactionDisableActions)
    uiView.layer.setFrame(rect)
    cameraPreviewLayer.setFrame(rect)
    CATransaction.commit()
  }


  @OptIn(ExperimentalForeignApi::class)
  override fun toggleTorch() {
    try {
      if (camera.hasTorch) {
        try {
          camera.lockForConfiguration(null)
          if (camera.torchMode == AVCaptureTorchModeOn) {
            camera.torchMode = AVCaptureTorchModeOff
          } else {
            camera.torchMode = AVCaptureTorchModeOn
          }
          camera.unlockForConfiguration()
        } catch (e: Throwable) {
          WARNING("Error toggling torch: $e")
        }
      }
    } catch (e: Exception) {
      WARNING("Torch not available: $e")
    }
  }

  /**实现打开相册*/
  override fun openAlbum() {
    val configuration = PHPickerConfiguration()
    val pickerController = PHPickerViewController(configuration)
    pickerController.setDelegate(galleryDelegate)
    uiViewController.presentViewController(pickerController, animated = true, completion = null)
  }

  override fun stop() {
    captureSession.stopRunning()
  }
}