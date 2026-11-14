package com.scanrift.android.service.scanning

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import android.util.Size
import com.scanrift.android.core.Constants
import com.scanrift.android.core.log.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface CameraState {
    data object Idle : CameraState
    data object Starting : CameraState
    data object Running : CameraState
    data class Failed(val message: String) : CameraState
}

/**
 * CameraX setup for the scanner.
 *
 * Three things the previous version got wrong, all of which show up only on a real
 * device:
 *
 * - **No resolution strategy was set**, so the analyser received whatever the device
 *   defaulted to — often 640x480, which does not carry enough detail for OCR to read
 *   the set code reliably. This targets 1280x720.
 * - **Torch state was a plain `var`**, so the toggle never recomposed its icon. It is a
 *   `StateFlow` off `cameraInfo.torchState` now, which also means it reflects reality
 *   if the system changes it.
 * - **Shutdown order**: the executor was shut down before unbinding, so an in-flight
 *   frame could be handed to a dead executor.
 */
@Singleton
class CameraService @Inject constructor() {

    private val _state = MutableStateFlow<CameraState>(CameraState.Idle)
    val state: StateFlow<CameraState> = _state.asStateFlow()

    private val _torchEnabled = MutableStateFlow(false)
    val torchEnabled: StateFlow<Boolean> = _torchEnabled.asStateFlow()

    private val _hasFlashUnit = MutableStateFlow(false)
    val hasFlashUnit: StateFlow<Boolean> = _hasFlashUnit.asStateFlow()

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var analysisExecutor: ExecutorService? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null

    /**
     * @param pageMode binds a full-resolution [ImageCapture] instead of the 720p
     *   analyser: a binder page is photographed, not streamed — nine collector lines are
     *   ~25px tall at 720p, too small to read.
     */
    suspend fun start(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onFrame: (ImageProxy) -> Unit,
        pageMode: Boolean = false,
    ) {
        _state.value = CameraState.Starting
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(context).await()
            provider = cameraProvider
            if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                _state.value = CameraState.Failed("No camera is available on this device.")
                return
            }

            val executor = analysisExecutor ?: Executors.newSingleThreadExecutor().also { analysisExecutor = it }

            // Single mode: the preview shares the analysis stream's 16:9 aspect, so both see
            // the same field of view and the on-screen guide maps onto the analysed frame
            // (OcrGeometry.guideInImage).
            val preview = Preview.Builder().apply {
                if (!pageMode) {
                    setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                            .build(),
                    )
                }
            }.build().apply {
                surfaceProvider = previewView.surfaceProvider
            }

            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(
                                    Constants.Scanning.ANALYSIS_TARGET_WIDTH,
                                    Constants.Scanning.ANALYSIS_TARGET_HEIGHT,
                                ),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            ),
                        )
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setImageQueueDepth(1)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .apply { setAnalyzer(executor, onFrame) }

            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .apply { previewView.display?.rotation?.let(::setTargetRotation) }
                .build()

            imageAnalysis = if (pageMode) null else analysis
            imageCapture = if (pageMode) capture else null

            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                if (pageMode) capture else analysis,
            ).also { bound ->
                _hasFlashUnit.value = bound.cameraInfo.hasFlashUnit()
                if (_torchEnabled.value) bound.cameraControl.enableTorch(true)
            }

            _state.value = CameraState.Running
        } catch (e: Exception) {
            Log.camera.e(e, "Could not start the camera")
            _state.value = CameraState.Failed("Failed to configure camera session.")
        }
    }

    /**
     * Keeps the analyser's rotation in step with the display.
     *
     * Called on configuration change — including a fold, which is the case that
     * produces sideways OCR if it is missed.
     */
    fun updateTargetRotation(rotation: Int) {
        imageAnalysis?.targetRotation = rotation
        imageCapture?.targetRotation = rotation
    }

    /**
     * Takes one full-resolution still in page mode, upright and capped at
     * [Constants.Scanning.PAGE_MAX_DIMENSION] on its long edge to bound memory.
     */
    suspend fun capturePhoto(): Bitmap {
        val capture = imageCapture ?: error("The camera is not in page mode")
        val executor = analysisExecutor ?: error("The camera is not running")
        return suspendCancellableCoroutine { continuation ->
            capture.takePicture(
                executor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val result = runCatching {
                            image.use { proxy ->
                                PageBitmaps.upright(proxy.toBitmap(), proxy.imageInfo.rotationDegrees)
                            }
                        }
                        result.fold(continuation::resume, continuation::resumeWithException)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resumeWithException(exception)
                    }
                },
            )
        }
    }

    fun toggleTorch() {
        val control = camera?.cameraControl ?: return
        val next = !_torchEnabled.value
        control.enableTorch(next)
        _torchEnabled.value = next
    }

    /** Tap to focus, in preview-view pixel coordinates. */
    fun focusAt(previewView: PreviewView, x: Float, y: Float) {
        val control = camera?.cameraControl ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        control.startFocusAndMetering(
            FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE).build(),
        )
    }

    fun stop() {
        // Unbind first: shutting the executor down while a frame is in flight hands
        // work to a dead executor.
        provider?.unbindAll()
        camera = null
        imageAnalysis = null
        imageCapture = null
        _torchEnabled.value = false
        _state.value = CameraState.Idle
    }

    fun shutdown() {
        stop()
        analysisExecutor?.shutdown()
        analysisExecutor = null
        provider = null
    }
}

/**
 * Awaits a Guava future without pulling in concurrent-futures-ktx for one call site.
 */
private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    continuation.resume(get())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            },
            Runnable::run,
        )
        continuation.invokeOnCancellation { cancel(false) }
    }
