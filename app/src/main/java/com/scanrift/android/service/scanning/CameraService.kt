package com.scanrift.android.service.scanning

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraX-based camera service for the card scanner.
 */
class CameraService {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageAnalysis: ImageAnalysis? = null

    var isTorchOn: Boolean = false
        private set

    /**
     * Start the camera with preview and frame analysis.
     */
    fun startCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onFrameAnalyzed: (ImageProxy) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder()
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysisUseCase ->
                    analysisUseCase.setAnalyzer(analysisExecutor) { imageProxy ->
                        onFrameAnalyzed(imageProxy)
                    }
                }

            imageAnalysis = analysis

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    analysis
                )

                // Restore torch state
                camera?.cameraControl?.enableTorch(isTorchOn)

                Timber.d("Camera started successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to bind camera use cases")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun toggleTorch() {
        isTorchOn = !isTorchOn
        try {
            camera?.cameraControl?.enableTorch(isTorchOn)
        } catch (e: Exception) {
            Timber.e(e, "Failed to toggle torch")
        }
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
        Timber.d("Camera stopped")
    }

    fun shutdown() {
        analysisExecutor.shutdown()
        stopCamera()
    }
}
