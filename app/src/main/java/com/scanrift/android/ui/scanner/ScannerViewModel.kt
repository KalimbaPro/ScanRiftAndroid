package com.scanrift.android.ui.scanner

import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanrift.android.core.Constants
import com.scanrift.android.core.log.Log
import com.scanrift.android.data.prefs.UserPreferences
import com.scanrift.android.data.repository.CollectionRepository
import com.scanrift.android.domain.model.Card
import com.scanrift.android.service.feedback.FeedbackService
import com.scanrift.android.service.scanning.CameraService
import com.scanrift.android.service.scanning.CardMatchingService
import com.scanrift.android.service.scanning.MotionDetector
import com.scanrift.android.service.scanning.OcrService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScannerOverlayState { IDLE, DETECTING, PROCESSING, MATCHED, ERROR }

/** A card captured during this session, before it is committed to the collection. */
data class ScanResult(
    val card: Card,
    val quantity: Int = 1,
    val isFoil: Boolean = card.isAlwaysFoil,
)

data class ScannerDebugState(
    val ocrText: String = "",
    val motionStatus: String = "Idle",
    val lastMatchedCode: String = "",
    val lastConfidence: Double = 0.0,
)

data class ScannerUiState(
    val isScanning: Boolean = false,
    val overlayState: ScannerOverlayState = ScannerOverlayState.IDLE,
    val results: List<ScanResult> = emptyList(),
    val lastScanned: Card? = null,
    val debug: ScannerDebugState = ScannerDebugState(),
    val debugEnabled: Boolean = false,
    val errorMessage: String? = null,
) {
    val scannedCount: Int get() = results.sumOf { it.quantity }
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    val cameraService: CameraService,
    private val ocrService: OcrService,
    private val collectionRepository: CollectionRepository,
    private val feedbackService: FeedbackService,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    val cameraState = cameraService.state
    val torchEnabled = cameraService.torchEnabled
    val hasFlashUnit = cameraService.hasFlashUnit

    private val motionDetector = MotionDetector()
    private var matcher: CardMatchingService? = null

    private var frameCounter = 0
    private var lastScanAtMs = 0L

    @Volatile
    private var isProcessingFrame = false

    val cardCount: StateFlow<Int> = collectionRepository.observeCardCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            // A snapshot is enough: the catalogue does not change mid-session.
            val cards = collectionRepository.observeAllCards().first()
            matcher = CardMatchingService(cards)
        }
        viewModelScope.launch {
            userPreferences.debugMode.collect { enabled ->
                _uiState.update { it.copy(debugEnabled = enabled) }
            }
        }
    }

    fun startScanning() {
        motionDetector.reset(waitForChange = false)
        _uiState.update { it.copy(isScanning = true, overlayState = ScannerOverlayState.DETECTING) }
    }

    fun stopScanning() {
        _uiState.update { it.copy(isScanning = false, overlayState = ScannerOverlayState.IDLE) }
    }

    /**
     * The analyser callback.
     *
     * Runs on CameraX's single analysis thread. Every path must close the frame, or
     * the analyser stalls once `imageQueueDepth` frames are outstanding.
     */
    fun onFrame(imageProxy: ImageProxy) {
        if (!_uiState.value.isScanning || isProcessingFrame) {
            imageProxy.close()
            return
        }

        // Process one frame in N — OCR is far slower than the camera.
        frameCounter = (frameCounter + 1) % Constants.Scanning.FRAME_SKIP_RATE
        if (frameCounter != 0) {
            imageProxy.close()
            return
        }

        val moved = runCatching { detectMotion(imageProxy) }.getOrDefault(false)
        val ready = motionDetector.processMotionResult(moved)
        _uiState.update { it.copy(debug = it.debug.copy(motionStatus = motionDetector.scanStatus)) }

        val now = System.currentTimeMillis()
        if (!ready || now - lastScanAtMs < Constants.Scanning.SCAN_DEBOUNCE_MS) {
            imageProxy.close()
            return
        }

        isProcessingFrame = true
        _uiState.update { it.copy(overlayState = ScannerOverlayState.PROCESSING) }

        viewModelScope.launch {
            try {
                // extractText closes the proxy in its own finally.
                val ocr = ocrService.extractText(imageProxy)
                handleOcr(ocr, now)
            } catch (e: Exception) {
                Log.ocr.w(e, "OCR failed for this frame")
                _uiState.update { it.copy(overlayState = ScannerOverlayState.DETECTING) }
            } finally {
                isProcessingFrame = false
            }
        }
    }

    private suspend fun handleOcr(ocr: com.scanrift.android.service.scanning.OcrResult, now: Long) {
        _uiState.update {
            it.copy(
                debug = it.debug.copy(
                    ocrText = ocr.extractedText.take(200),
                    lastConfidence = ocr.confidence,
                ),
            )
        }

        if (ocr.confidence <= Constants.Scanning.MINIMUM_OCR_CONFIDENCE) {
            _uiState.update { it.copy(overlayState = ScannerOverlayState.DETECTING) }
            return
        }

        val match = matcher?.findMatch(ocr)
        if (match == null) {
            // Allow another attempt at the same card without needing it to move.
            motionDetector.markRetry()
            _uiState.update { it.copy(overlayState = ScannerOverlayState.DETECTING) }
            return
        }

        lastScanAtMs = now
        motionDetector.markScanned()
        addScanned(match.card)

        val haptics = userPreferences.hapticFeedback.first()
        val sound = userPreferences.soundFeedback.first()
        feedbackService.scanSuccess(haptics, sound)

        _uiState.update {
            it.copy(
                overlayState = ScannerOverlayState.MATCHED,
                lastScanned = match.card,
                debug = it.debug.copy(lastMatchedCode = match.matchedText),
            )
        }

        if (userPreferences.autoAddToCollection.first()) {
            collectionRepository.addCopies(match.card)
        }

        kotlinx.coroutines.delay(Constants.Scanning.MATCH_DISPLAY_DURATION_MS)
        _uiState.update {
            if (it.isScanning) it.copy(overlayState = ScannerOverlayState.DETECTING) else it
        }
    }

    /** Merges into an existing result for the same card and foil state. */
    private fun addScanned(card: Card) {
        _uiState.update { state ->
            val index = state.results.indexOfFirst { it.card.id == card.id && it.isFoil == card.isAlwaysFoil }
            val results = if (index >= 0) {
                state.results.toMutableList().apply {
                    this[index] = this[index].copy(quantity = this[index].quantity + 1)
                }
            } else {
                state.results + ScanResult(card)
            }
            state.copy(results = results)
        }
    }

    fun setQuantity(index: Int, quantity: Int) = _uiState.update { state ->
        if (index !in state.results.indices) return@update state
        val results = state.results.toMutableList()
        if (quantity <= 0) results.removeAt(index) else results[index] = results[index].copy(quantity = quantity)
        state.copy(results = results)
    }

    fun toggleFoil(index: Int) = _uiState.update { state ->
        if (index !in state.results.indices) return@update state
        val result = state.results[index]
        // Rare, Epic and Showcase only exist as foils, so the toggle is inert there.
        if (result.card.isAlwaysFoil) return@update state
        val results = state.results.toMutableList()
        results[index] = result.copy(isFoil = !result.isFoil)
        state.copy(results = results)
    }

    fun remove(index: Int) = setQuantity(index, 0)

    fun replaceResult(index: Int, card: Card) = _uiState.update { state ->
        if (index !in state.results.indices) return@update state
        val results = state.results.toMutableList()
        results[index] = ScanResult(card, quantity = results[index].quantity)
        state.copy(results = results)
    }

    fun searchCards(query: String): List<Card> = matcher?.searchCards(query).orEmpty()

    fun addSessionToCollection(onDone: () -> Unit = {}) {
        val results = _uiState.value.results
        viewModelScope.launch {
            results.forEach { result ->
                collectionRepository.addCopies(result.card, result.quantity, result.isFoil)
            }
            _uiState.update { it.copy(results = emptyList(), lastScanned = null) }
            onDone()
        }
    }

    fun clearSession() = _uiState.update { it.copy(results = emptyList(), lastScanned = null) }

    fun toggleTorch() = cameraService.toggleTorch()

    private fun detectMotion(imageProxy: ImageProxy): Boolean {
        val plane = imageProxy.planes.firstOrNull() ?: return false
        return motionDetector.detectMotion(
            yPlane = plane.buffer,
            width = imageProxy.width,
            height = imageProxy.height,
            rowStride = plane.rowStride,
        )
    }

    override fun onCleared() {
        cameraService.shutdown()
        ocrService.close()
        feedbackService.release()
        super.onCleared()
    }
}
