package com.scanrift.android.ui.scanner

import android.content.Context
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanrift.android.core.Constants
import com.scanrift.android.core.log.Log
import com.scanrift.android.data.prefs.UserPreferences
import com.scanrift.android.data.repository.CollectionRepository
import com.scanrift.android.data.repository.DeckRepository
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.domain.model.DeckSection
import com.scanrift.android.service.feedback.FeedbackService
import com.scanrift.android.service.scanning.CameraService
import com.scanrift.android.service.scanning.CameraState
import com.scanrift.android.service.scanning.CardMatchingService
import com.scanrift.android.service.scanning.MotionDetector
import com.scanrift.android.service.scanning.OcrResult
import com.scanrift.android.service.scanning.OcrService
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScannerOverlayState { IDLE, DETECTING, PROCESSING, MATCHED, ERROR }

data class ScannerDebugState(
    val ocrText: String = "",
    val motionStatus: String = "Idle",
    val lastProcessTime: String = "",
    val matchCount: Int = 0,
)

data class ScannerUiState(
    val isScanning: Boolean = false,
    val overlayState: ScannerOverlayState = ScannerOverlayState.IDLE,
    val results: List<ScanResult> = emptyList(),
    val lastScanned: Card? = null,
    val debug: ScannerDebugState = ScannerDebugState(),
    val debugEnabled: Boolean = false,
    val correctingId: String? = null,
    val correctionQuery: String = "",
    val correctionResults: List<Card> = emptyList(),
) {
    val scannedCount: Int get() = results.sumOf { it.quantity }
    val correcting: ScanResult? get() = results.firstOrNull { it.id == correctingId }
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    val cameraService: CameraService,
    private val ocrService: OcrService,
    private val collectionRepository: CollectionRepository,
    private val deckRepository: DeckRepository,
    private val feedbackService: FeedbackService,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    val cameraState = cameraService.state
    val torchEnabled = cameraService.torchEnabled
    val hasFlashUnit = cameraService.hasFlashUnit

    val decks: StateFlow<List<Deck>> = deckRepository.observeDecks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val motionDetector = MotionDetector()
    private var matcher: CardMatchingService? = null

    private var frameCounter = 0
    private var lastScanAtMs = 0L
    private var isPaused = false
    private var resumeJob: Job? = null

    @Volatile
    private var isProcessingFrame = false

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

    suspend fun startCamera(context: Context, lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        repeat(Constants.Scanning.MAX_CAMERA_RETRIES + 1) { attempt ->
            if (attempt > 0) delay(Constants.Scanning.CAMERA_RETRY_DELAY_MS)
            cameraService.start(context, lifecycleOwner, previewView, ::onFrame)
            if (cameraService.state.value == CameraState.Running) {
                startScanning()
                return
            }
        }
    }

    fun startScanning() {
        resumeJob?.cancel()
        isPaused = false
        motionDetector.reset(waitForChange = false)
        _uiState.update { it.copy(isScanning = true, overlayState = ScannerOverlayState.DETECTING) }
    }

    fun stopScanning() {
        resumeJob?.cancel()
        _uiState.update { it.copy(isScanning = false, overlayState = ScannerOverlayState.IDLE) }
    }

    fun pauseScanning() {
        isPaused = true
        stopScanning()
    }

    fun resumeScanning() {
        if (!isPaused) return
        isPaused = false
        resumeJob = viewModelScope.launch {
            delay(Constants.Scanning.RESUME_DELAY_MS)
            lastScanAtMs = System.currentTimeMillis()
            motionDetector.reset(waitForChange = true)
            _uiState.update { it.copy(isScanning = true, overlayState = ScannerOverlayState.DETECTING) }
        }
    }

    /**
     * The analyser callback.
     *
     * Runs on CameraX's single analysis thread. Every path must close the frame, or
     * the analyser stalls once `imageQueueDepth` frames are outstanding.
     */
    fun onFrame(imageProxy: ImageProxy) {
        val state = _uiState.value
        if (!state.isScanning || state.overlayState != ScannerOverlayState.DETECTING || isProcessingFrame) {
            imageProxy.close()
            return
        }

        // Process one frame in N — OCR is far slower than the camera.
        frameCounter = (frameCounter + 1) % Constants.Scanning.FRAME_SKIP_RATE
        if (frameCounter != 0) {
            imageProxy.close()
            return
        }

        val matcher = matcher
        if (matcher == null) {
            updateDebug { it.copy(ocrText = "ERROR: Card matching service not initialized") }
            imageProxy.close()
            return
        }

        val moved = runCatching { detectMotion(imageProxy) }.getOrDefault(false)
        val ready = motionDetector.processMotionResult(moved)
        updateDebug { it.copy(motionStatus = motionDetector.scanStatus) }

        val now = System.currentTimeMillis()
        if (!ready || now - lastScanAtMs < Constants.Scanning.SCAN_DEBOUNCE_MS) {
            imageProxy.close()
            return
        }

        isProcessingFrame = true
        motionDetector.readyToScan = false
        _uiState.update { it.copy(overlayState = ScannerOverlayState.PROCESSING) }

        viewModelScope.launch {
            try {
                // extractText closes the proxy in its own finally.
                val ocr = ocrService.extractText(imageProxy)
                handleOcr(ocr, matcher)
            } catch (e: Exception) {
                Log.ocr.w(e, "OCR failed for this frame")
                updateDebug { it.copy(ocrText = "ERROR: ${e.message}") }
                motionDetector.markRetry()
            } finally {
                isProcessingFrame = false
                _uiState.update {
                    if (it.overlayState == ScannerOverlayState.PROCESSING) {
                        it.copy(overlayState = ScannerOverlayState.DETECTING)
                    } else {
                        it
                    }
                }
            }
        }
    }

    private suspend fun handleOcr(ocr: OcrResult, matcher: CardMatchingService) {
        updateDebug {
            it.copy(
                ocrText = ocr.extractedText,
                lastProcessTime = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date()),
            )
        }

        val match = if (ocr.confidence > Constants.Scanning.MINIMUM_OCR_CONFIDENCE) matcher.findMatch(ocr) else null
        updateDebug { it.copy(matchCount = if (match == null) 0 else 1) }
        if (match == null) {
            motionDetector.markRetry()
            return
        }

        lastScanAtMs = System.currentTimeMillis()
        motionDetector.markScanned()

        val autoAdd = userPreferences.autoAddToCollection.first()
        if (autoAdd) collectionRepository.addCopies(match.card)

        _uiState.update {
            it.copy(
                overlayState = ScannerOverlayState.MATCHED,
                lastScanned = match.card,
                results = if (autoAdd) it.results else it.results.addingScan(match.card),
            )
        }

        feedbackService.scanSuccess(
            haptics = userPreferences.hapticFeedback.first(),
            sound = userPreferences.soundFeedback.first(),
        )

        delay(Constants.Scanning.MATCH_DISPLAY_DURATION_MS)
        _uiState.update {
            if (it.overlayState == ScannerOverlayState.MATCHED) it.copy(overlayState = ScannerOverlayState.DETECTING) else it
        }
    }

    private fun updateResults(transform: (List<ScanResult>) -> List<ScanResult>) = _uiState.update { state ->
        val results = transform(state.results)
        val lastScanned = state.lastScanned?.takeIf { card -> results.any { it.card.id == card.id } }
        state.copy(results = results, lastScanned = lastScanned)
    }

    fun setQuantity(id: String, quantity: Int) = updateResults { it.withQuantity(id, quantity) }

    fun toggleFoil(id: String) = updateResults { it.togglingFoil(id) }

    fun remove(id: String) = setQuantity(id, 0)

    fun startCorrection(result: ScanResult) = _uiState.update {
        it.copy(correctingId = result.id, correctionQuery = "", correctionResults = emptyList())
    }

    fun startCorrectionForLastCard() {
        val last = _uiState.value.lastScanned ?: return
        _uiState.value.results.firstOrNull { it.card.id == last.id }?.let(::startCorrection)
    }

    fun searchForCorrection(query: String) = _uiState.update {
        it.copy(correctionQuery = query, correctionResults = matcher?.searchCards(query).orEmpty())
    }

    fun applyCorrection(card: Card) {
        val id = _uiState.value.correctingId ?: return
        _uiState.update { it.copy(results = it.results.correcting(id, card), lastScanned = card) }
        dismissCorrection()
    }

    fun dismissCorrection() = _uiState.update {
        it.copy(correctingId = null, correctionQuery = "", correctionResults = emptyList())
    }

    fun addSessionToCollection() {
        val results = _uiState.value.results
        viewModelScope.launch {
            results.forEach { collectionRepository.addCopies(it.card, it.quantity, it.isFoil) }
            clearSession()
        }
    }

    fun addToDeck(deck: Deck, section: DeckSection, results: List<ScanResult>, alsoToCollection: Boolean) {
        viewModelScope.launch {
            val copies = results.groupBy { it.card.id }.mapValues { (_, rows) -> rows.sumOf { it.quantity } }
            deckRepository.addCopies(deck.id, section, copies)
            if (alsoToCollection) {
                results.forEach { collectionRepository.addCopies(it.card, it.quantity, it.isFoil) }
            }
        }
    }

    fun clearSession() = _uiState.update { it.copy(results = emptyList(), lastScanned = null) }

    fun toggleTorch() = cameraService.toggleTorch()

    private fun updateDebug(transform: (ScannerDebugState) -> ScannerDebugState) {
        if (_uiState.value.debugEnabled) _uiState.update { it.copy(debug = transform(it.debug)) }
    }

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
