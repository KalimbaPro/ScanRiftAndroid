package com.scanrift.android.ui.screens.scanner

import android.app.Application
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.data.local.entity.CollectionEntryEntity
import com.scanrift.android.data.repository.UserPreferences
import com.scanrift.android.service.feedback.FeedbackService
import com.scanrift.android.service.scanning.CardMatchingService
import com.scanrift.android.service.scanning.MatchCandidate
import com.scanrift.android.service.scanning.MotionDetector
import com.scanrift.android.service.scanning.OCRService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.scanrift.android.util.Constants

data class ScanResult(
    val card: CardEntity,
    var quantity: Int = 1,
    var isFoil: Boolean = card.isAlwaysFoil
)

data class ScanSession(
    val results: MutableList<ScanResult> = mutableListOf(),
    val totalScanned: Int = 0
) {
    // Backwards compatibility
    val scannedCards: List<CardEntity> get() = results.map { it.card }
}

data class DebugState(
    val ocrText: String = "",
    val motionStatus: String = "Idle",
    val lastMatchedCode: String = ""
)

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = ScanRiftDatabase.getInstance(application)
    private val cardDao = database.cardDao()
    private val collectionEntryDao = database.collectionEntryDao()
    private val preferences = UserPreferences(application)

    private val ocrService = OCRService()
    private val motionDetector = MotionDetector()
    private val feedbackService = FeedbackService(application)

    private var cardMatchingService: CardMatchingService? = null
    private var frameCount = 0
    private var lastScanTimeMs = 0L
    private var isProcessing = false

    // State
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _overlayState = MutableStateFlow(ScannerOverlayState.IDLE)
    val overlayState: StateFlow<ScannerOverlayState> = _overlayState.asStateFlow()

    private val _lastScannedCard = MutableStateFlow<CardEntity?>(null)
    val lastScannedCard: StateFlow<CardEntity?> = _lastScannedCard.asStateFlow()

    private val _lastMatchCandidate = MutableStateFlow<MatchCandidate?>(null)
    val lastMatchCandidate: StateFlow<MatchCandidate?> = _lastMatchCandidate.asStateFlow()

    private val _scanSession = MutableStateFlow(ScanSession())
    val scanSession: StateFlow<ScanSession> = _scanSession.asStateFlow()

    private val _debugState = MutableStateFlow(DebugState())
    val debugState: StateFlow<DebugState> = _debugState.asStateFlow()

    private val _showCorrectionSheet = MutableStateFlow(false)
    val showCorrectionSheet: StateFlow<Boolean> = _showCorrectionSheet.asStateFlow()

    private val _showSessionSummary = MutableStateFlow(false)
    val showSessionSummary: StateFlow<Boolean> = _showSessionSummary.asStateFlow()

    val debugMode: StateFlow<Boolean> = preferences.debugMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        loadCards()
    }

    private fun loadCards() {
        viewModelScope.launch {
            val cards = cardDao.getAllCardsList()
            cardMatchingService = CardMatchingService(cards)
            Timber.d("Loaded %d cards for matching", cards.size)
        }
    }

    /**
     * Process a camera frame from CameraX ImageAnalysis.
     */
    @androidx.camera.core.ExperimentalGetImage
    fun processFrame(imageProxy: ImageProxy) {
        frameCount++

        // Skip frames for performance
        if (frameCount % Constants.Scanning.FRAME_SKIP_RATE != 0) {
            imageProxy.close()
            return
        }

        if (isProcessing || !_isScanning.value) {
            imageProxy.close()
            return
        }

        // Debounce
        val now = System.currentTimeMillis()
        if (now - lastScanTimeMs < Constants.Scanning.SCAN_DEBOUNCE_MS) {
            imageProxy.close()
            return
        }

        viewModelScope.launch {
            isProcessing = true
            try {
                processFrameInternal(imageProxy)
            } catch (e: Exception) {
                Timber.e(e, "Frame processing error")
            } finally {
                isProcessing = false
                imageProxy.close()
            }
        }
    }

    @androidx.camera.core.ExperimentalGetImage
    private suspend fun processFrameInternal(imageProxy: ImageProxy) {
        val image = imageProxy.image ?: return
        val planes = imageProxy.planes
        if (planes.isEmpty()) return

        // Motion detection using Y plane
        val yBuffer = planes[0].buffer
        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = planes[0].rowStride

        val imageChanged = withContext(Dispatchers.Default) {
            motionDetector.detectMotion(yBuffer, width, height, rowStride)
        }

        val isReady = motionDetector.processMotionResult(imageChanged)
        _debugState.value = _debugState.value.copy(motionStatus = motionDetector.scanStatus)

        if (!isReady) {
            if (_overlayState.value != ScannerOverlayState.MATCHED) {
                _overlayState.value = ScannerOverlayState.IDLE
            }
            return
        }

        _overlayState.value = ScannerOverlayState.DETECTING

        // OCR
        try {
            val ocrResult = withContext(Dispatchers.Default) {
                ocrService.extractText(image, imageProxy.imageInfo.rotationDegrees)
            }

            _debugState.value = _debugState.value.copy(ocrText = ocrResult.extractedText)

            if (ocrResult.confidence < Constants.Scanning.MINIMUM_MATCH_CONFIDENCE) return

            // Card matching
            val matchService = cardMatchingService ?: return
            val match = matchService.findMatch(ocrResult) ?: return

            _debugState.value = _debugState.value.copy(lastMatchedCode = match.matchedText)

            // Check if already scanned in this session - increment quantity instead of skipping
            val session = _scanSession.value
            val existingResult = session.results.find { it.card.id == match.card.id }
            if (existingResult != null) {
                existingResult.quantity++
                _scanSession.value = session.copy(totalScanned = session.totalScanned + 1)
            } else {
                // New card in session
                session.results.add(ScanResult(card = match.card))
                _scanSession.value = session.copy(totalScanned = session.totalScanned + 1)
            }

            // Success!
            _overlayState.value = ScannerOverlayState.MATCHED
            lastScanTimeMs = System.currentTimeMillis()
            _lastScannedCard.value = match.card
            _lastMatchCandidate.value = match

            val hapticEnabled = preferences.hapticFeedback.first()
            val soundEnabled = preferences.soundFeedback.first()
            feedbackService.scanSuccess(hapticEnabled, soundEnabled)

            // Auto-add to collection if enabled
            val autoAdd = preferences.autoAddToCollection.first()
            if (autoAdd) {
                addToCollection(match.card)
            }

            motionDetector.markScanned()

            // Brief delay before next scan
            delay(Constants.Scanning.MATCH_DISPLAY_DURATION_MS)
            _overlayState.value = ScannerOverlayState.IDLE

        } catch (e: Exception) {
            _overlayState.value = ScannerOverlayState.ERROR
            Timber.e(e, "OCR/matching error")
            delay(500)
            _overlayState.value = ScannerOverlayState.IDLE
        }
    }

    private suspend fun addToCollection(card: CardEntity) {
        val existing = collectionEntryDao.findEntry(card.id, card.isAlwaysFoil)
        if (existing != null) {
            collectionEntryDao.update(existing.copy(quantity = existing.quantity + 1))
        } else {
            collectionEntryDao.insert(
                CollectionEntryEntity(
                    cardId = card.id,
                    isFoil = card.isAlwaysFoil
                )
            )
        }
        Timber.d("Added '%s' to collection", card.name)
    }

    fun startScanning() {
        _isScanning.value = true
        motionDetector.reset(waitForChange = true)
        _scanSession.value = ScanSession()
        _lastScannedCard.value = null
    }

    fun stopScanning() {
        _isScanning.value = false
    }

    fun showCorrection() {
        _showCorrectionSheet.value = true
    }

    fun dismissCorrection() {
        _showCorrectionSheet.value = false
    }

    fun selectCorrectedCard(card: CardEntity) {
        viewModelScope.launch {
            _lastScannedCard.value = card
            addToCollection(card)
            val session = _scanSession.value
            session.results.add(ScanResult(card = card))
            _scanSession.value = session.copy(totalScanned = session.totalScanned + 1)
            _showCorrectionSheet.value = false
        }
    }

    fun searchCards(query: String): List<CardEntity> {
        return cardMatchingService?.searchCards(query) ?: emptyList()
    }

    fun updateQuantity(index: Int, newQty: Int) {
        val session = _scanSession.value
        if (index in session.results.indices) {
            session.results[index].quantity = newQty.coerceAtLeast(1)
            _scanSession.value = session.copy() // trigger recomposition
        }
    }

    fun toggleFoil(index: Int) {
        val session = _scanSession.value
        if (index in session.results.indices) {
            val result = session.results[index]
            if (!result.card.isAlwaysFoil) {
                result.isFoil = !result.isFoil
                _scanSession.value = session.copy()
            }
        }
    }

    fun removeScannedCard(index: Int) {
        val session = _scanSession.value
        if (index in session.results.indices) {
            session.results.removeAt(index)
            _scanSession.value = session.copy(totalScanned = session.results.sumOf { it.quantity })
        }
    }

    fun addSessionToCollection() {
        viewModelScope.launch {
            val session = _scanSession.value
            for (result in session.results) {
                val existing = collectionEntryDao.findEntry(result.card.id, result.isFoil)
                if (existing != null) {
                    collectionEntryDao.update(existing.copy(quantity = existing.quantity + result.quantity))
                } else {
                    collectionEntryDao.insert(
                        CollectionEntryEntity(
                            cardId = result.card.id,
                            quantity = result.quantity,
                            isFoil = result.isFoil
                        )
                    )
                }
            }
            Timber.d("Added %d unique cards to collection from session", session.results.size)
        }
    }

    fun showSessionSummary() {
        _showSessionSummary.value = true
    }

    fun dismissSessionSummary() {
        _showSessionSummary.value = false
    }

    override fun onCleared() {
        super.onCleared()
        ocrService.close()
        feedbackService.release()
    }
}
