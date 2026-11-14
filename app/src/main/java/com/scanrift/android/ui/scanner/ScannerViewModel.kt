package com.scanrift.android.ui.scanner

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
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
import com.scanrift.android.domain.model.CardLanguage
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.domain.model.DeckSection
import com.scanrift.android.domain.model.ScanLanguage
import com.scanrift.android.service.feedback.FeedbackService
import com.scanrift.android.service.scanning.CameraService
import com.scanrift.android.service.scanning.CameraState
import com.scanrift.android.service.scanning.CardMatchingService
import com.scanrift.android.service.scanning.LanguageDetector
import com.scanrift.android.service.scanning.NormalizedBox
import com.scanrift.android.service.scanning.PageScanService
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

/** Single = live frames, one card at a time. Page = one still of a binder page. */
enum class ScanMode(val label: String) { SINGLE("Single"), PAGE("Page") }

/**
 * One card found on a binder page. [card] is null when the collector code was read but
 * matches nothing in the catalogue: drawn orange, and fixed through the correction sheet.
 */
data class PageItem(
    val id: String,
    val bounds: NormalizedBox,
    val code: String,
    val card: Card?,
    val language: CardLanguage,
    val isFoil: Boolean = card?.isAlwaysFoil == true,
)

data class PageReview(
    val image: Bitmap,
    val items: List<PageItem> = emptyList(),
    val isProcessing: Boolean = true,
    val error: String? = null,
) {
    val matched: List<PageItem> get() = items.filter { it.card != null }
}

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
    /** The session row the last scan landed in; null in auto-add mode, which keeps no rows. */
    val lastResultId: String? = null,
    /** Language recorded for the last scan (detected, or the fixed preference). */
    val lastLanguage: CardLanguage = CardLanguage.EN,
    val scanLanguage: ScanLanguage = ScanLanguage.AUTO,
    val debug: ScannerDebugState = ScannerDebugState(),
    val debugEnabled: Boolean = false,
    val correctingId: String? = null,
    /** A binder-page item being corrected, instead of a session row. */
    val correctingPageItemId: String? = null,
    val scanMode: ScanMode = ScanMode.SINGLE,
    /** Non-null while a binder page is being read or reviewed. */
    val page: PageReview? = null,
    val isCapturing: Boolean = false,
    val correctionQuery: String = "",
    val correctionResults: List<Card> = emptyList(),
) {
    val scannedCount: Int get() = results.sumOf { it.quantity }
    val correcting: ScanResult? get() = results.firstOrNull { it.id == correctingId }
    val lastResult: ScanResult? get() = results.firstOrNull { it.id == lastResultId }
    val correctingPageItem: PageItem? get() = page?.items?.firstOrNull { it.id == correctingPageItemId }
    val isCorrecting: Boolean get() = correctingId != null || correctingPageItemId != null
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    val cameraService: CameraService,
    private val ocrService: OcrService,
    private val collectionRepository: CollectionRepository,
    private val deckRepository: DeckRepository,
    private val feedbackService: FeedbackService,
    private val userPreferences: UserPreferences,
    private val pageScanService: PageScanService,
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

    /** Set ids a binder-page code may start with, raw and normalised. */
    private var knownSetIds: Set<String> = emptySet()
    private var pageJob: Job? = null

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
            knownSetIds = cards.flatMapTo(HashSet()) { listOf(it.setId, it.sourceSetId) }
                .mapTo(HashSet()) { it.uppercase(java.util.Locale.ROOT) }
        }
        viewModelScope.launch {
            userPreferences.debugMode.collect { enabled ->
                _uiState.update { it.copy(debugEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferences.scanLanguage.collect { language ->
                _uiState.update { it.copy(scanLanguage = language) }
            }
        }
    }

    suspend fun startCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        pageMode: Boolean = false,
    ) {
        repeat(Constants.Scanning.MAX_CAMERA_RETRIES + 1) { attempt ->
            if (attempt > 0) delay(Constants.Scanning.CAMERA_RETRY_DELAY_MS)
            cameraService.start(context, lifecycleOwner, previewView, ::onFrame, pageMode)
            if (cameraService.state.value == CameraState.Running) {
                if (pageMode) stopScanning() else startScanning()
                return
            }
        }
    }

    // ── Binder page ──────────────────────────────────────────────────────────

    fun setScanMode(mode: ScanMode) = _uiState.update { it.copy(scanMode = mode) }

    /** Shutter in page mode: one full-resolution still, then the page pipeline. */
    fun capturePage() {
        if (_uiState.value.isCapturing || _uiState.value.page != null) return
        _uiState.update { it.copy(isCapturing = true) }
        pageJob = viewModelScope.launch {
            val photo = runCatching { cameraService.capturePhoto() }
            _uiState.update { it.copy(isCapturing = false) }
            photo.onSuccess(::readPage).onFailure { error ->
                Log.camera.e(error, "Page capture failed")
                _uiState.update { it.copy(page = null) }
            }
        }
    }

    /** A page photographed earlier, picked from the library. */
    fun importPage(uri: Uri) {
        if (_uiState.value.page != null) return
        pageJob = viewModelScope.launch {
            runCatching { pageScanService.decode(uri) }
                .onSuccess(::readPage)
                .onFailure { error -> Log.scanning.e(error, "Could not open the picked photo") }
        }
    }

    private fun readPage(image: Bitmap) {
        _uiState.update { it.copy(page = PageReview(image)) }
        pageJob = viewModelScope.launch {
            val preference = _uiState.value.scanLanguage
            val result = runCatching {
                pageScanService.scan(image, knownSetIds, detectScripts = preference == ScanLanguage.AUTO)
            }
            val matcher = matcher
            _uiState.update { state ->
                val review = state.page?.takeIf { it.image === image } ?: return@update state
                result.fold(
                    onSuccess = { page ->
                        val items = page.cards.mapIndexed { index, found ->
                            PageItem(
                                id = "page-$index-${found.code}",
                                bounds = found.bounds,
                                code = found.code,
                                card = matcher?.findByCardCode(found.code)?.card,
                                language = LanguageDetector.resolve(preference, found.text).language,
                            )
                        }
                        state.copy(page = review.copy(items = items, isProcessing = false))
                    },
                    onFailure = { error ->
                        Log.scanning.e(error, "Page OCR failed")
                        state.copy(page = review.copy(isProcessing = false, error = "Could not read this page."))
                    },
                )
            }
            val found = _uiState.value.page?.matched.orEmpty()
            if (found.isNotEmpty()) {
                feedbackService.scanSuccess(
                    haptics = userPreferences.hapticFeedback.first(),
                    sound = userPreferences.soundFeedback.first(),
                )
            }
        }
    }

    private fun updatePageItems(transform: (List<PageItem>) -> List<PageItem>) = _uiState.update { state ->
        val page = state.page ?: return@update state
        state.copy(page = page.copy(items = transform(page.items)))
    }

    fun removePageItem(id: String) = updatePageItems { items -> items.filterNot { it.id == id } }

    fun togglePageItemFoil(id: String) = updatePageItems { items ->
        items.map { if (it.id == id && it.card?.isAlwaysFoil != true) it.copy(isFoil = !it.isFoil) else it }
    }

    fun setPageItemLanguage(id: String, language: CardLanguage) =
        updatePageItems { items -> items.map { if (it.id == id) it.copy(language = language) else it } }

    fun startPageCorrection(id: String) = _uiState.update {
        it.copy(correctingPageItemId = id, correctingId = null, correctionQuery = "", correctionResults = emptyList())
    }

    fun dismissPage() {
        pageJob?.cancel()
        _uiState.update { it.copy(page = null, correctingPageItemId = null, isCapturing = false) }
    }

    /**
     * "Add N": the matched cards join the scan session, so they go on to the collection
     * or a deck through exactly the same summary sheet as live scans.
     *
     * @return how many copies were added.
     */
    fun addPageToSession(): Int {
        val matched = _uiState.value.page?.matched.orEmpty()
        if (matched.isEmpty()) return 0
        _uiState.update { state ->
            val results = matched.fold(state.results) { acc, item -> acc.adding(item.card!!, item.isFoil, item.language) }
            state.copy(results = results, page = null, correctingPageItemId = null)
        }
        return matched.size
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
                // extractText closes the proxy in its own finally. The Chinese/Korean
                // pass is paid only for a matched card with no Latin name, in Auto.
                val auto = _uiState.value.scanLanguage == ScanLanguage.AUTO
                val ocr = ocrService.extractText(imageProxy) { latin ->
                    auto && !LanguageDetector.hasLatinName(latin.cardNameRegion) &&
                        latin.confidence > Constants.Scanning.MINIMUM_OCR_CONFIDENCE &&
                        matcher.findMatch(latin) != null
                }
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

        val detection = LanguageDetector.resolve(_uiState.value.scanLanguage, ocr.languageText)
        updateDebug { it.copy(ocrText = "${ocr.extractedText}\n[language ${detection.language.code} via ${detection.source}]") }

        val autoAdd = userPreferences.autoAddToCollection.first()
        if (autoAdd) collectionRepository.addCopies(match.card, language = detection.language)

        _uiState.update {
            val (results, rowId) = if (autoAdd) {
                it.results to null
            } else {
                it.results.addingScanReturningId(match.card, detection.language)
            }
            it.copy(
                overlayState = ScannerOverlayState.MATCHED,
                lastScanned = match.card,
                lastResultId = rowId,
                lastLanguage = detection.language,
                results = results,
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

    /**
     * Applies a session edit and keeps the "last scanned" banner pointing at a real row:
     * a merge can remove the row it pointed at, in which case it follows the card.
     */
    private fun updateResults(transform: (List<ScanResult>) -> List<ScanResult>) = _uiState.update { state ->
        val results = transform(state.results)
        if (state.lastResultId == null) return@update state.copy(results = results)
        val lastRow = results.firstOrNull { it.id == state.lastResultId }
            ?: results.firstOrNull { it.card.id == state.lastScanned?.id }
        state.copy(
            results = results,
            lastResultId = lastRow?.id,
            lastScanned = lastRow?.card,
            lastLanguage = lastRow?.language ?: state.lastLanguage,
        )
    }

    fun setLanguage(id: String, language: CardLanguage) = updateResults { it.withLanguage(id, language) }

    /**
     * The scan-language chip. Persists the choice for future scans; a fixed language
     * also re-labels the card just scanned, which is almost always why it was tapped.
     */
    fun setScanLanguage(value: ScanLanguage) {
        viewModelScope.launch { userPreferences.setScanLanguage(value) }
        val language = value.language ?: return
        val lastId = _uiState.value.lastResultId ?: return
        setLanguage(lastId, language)
    }

    fun toggleLastFoil() {
        val lastId = _uiState.value.lastResultId ?: return
        // The foil toggle can split a row; follow the copy that changed.
        val before = _uiState.value.lastResult ?: return
        updateResults { it.togglingFoil(lastId) }
        val moved = _uiState.value.results.firstOrNull {
            it.card.id == before.card.id && it.language == before.language && it.isFoil != before.isFoil
        }
        moved?.let { row -> _uiState.update { it.copy(lastResultId = row.id) } }
    }

    /** "+1": one more copy of the last scan, as if it had been scanned again. */
    fun incrementLast() {
        val state = _uiState.value
        val last = state.lastResult
        if (last != null) {
            setQuantity(last.id, last.quantity + 1)
        } else if (state.lastScanned != null) {
            // Auto-add mode keeps no session rows; the extra copy goes straight in.
            val card = state.lastScanned
            viewModelScope.launch { collectionRepository.addCopies(card, language = state.lastLanguage) }
        }
    }

    fun setQuantity(id: String, quantity: Int) = updateResults { it.withQuantity(id, quantity) }

    fun toggleFoil(id: String) = updateResults { it.togglingFoil(id) }

    fun remove(id: String) = setQuantity(id, 0)

    fun startCorrection(result: ScanResult) = _uiState.update {
        it.copy(correctingId = result.id, correctingPageItemId = null, correctionQuery = "", correctionResults = emptyList())
    }

    fun startCorrectionForLastCard() {
        val state = _uiState.value
        (state.lastResult ?: state.results.firstOrNull { it.card.id == state.lastScanned?.id })?.let(::startCorrection)
    }

    fun searchForCorrection(query: String) = _uiState.update {
        it.copy(correctionQuery = query, correctionResults = matcher?.searchCards(query).orEmpty())
    }

    fun applyCorrection(card: Card) {
        _uiState.value.correctingPageItemId?.let { pageItemId ->
            updatePageItems { items ->
                items.map {
                    if (it.id == pageItemId) it.copy(card = card, isFoil = card.isAlwaysFoil || it.isFoil) else it
                }
            }
            dismissCorrection()
            return
        }
        val id = _uiState.value.correctingId ?: return
        val wasLast = id == _uiState.value.lastResultId
        _uiState.update { it.copy(results = it.results.correcting(id, card), lastScanned = card) }
        if (wasLast) {
            // The corrected row may have merged into a twin; point the banner at it.
            val row = _uiState.value.results.firstOrNull { it.id == id }
                ?: _uiState.value.results.firstOrNull { it.card.id == card.id && it.language == _uiState.value.lastLanguage }
            _uiState.update { it.copy(lastResultId = row?.id) }
        }
        dismissCorrection()
    }

    fun dismissCorrection() = _uiState.update {
        it.copy(correctingId = null, correctingPageItemId = null, correctionQuery = "", correctionResults = emptyList())
    }

    fun addSessionToCollection() {
        val results = _uiState.value.results
        viewModelScope.launch {
            results.forEach { collectionRepository.addCopies(it.card, it.quantity, it.isFoil, language = it.language) }
            clearSession()
        }
    }

    fun addToDeck(deck: Deck, section: DeckSection, results: List<ScanResult>, alsoToCollection: Boolean) {
        viewModelScope.launch {
            val copies = results.groupBy { it.card.id }.mapValues { (_, rows) -> rows.sumOf { it.quantity } }
            deckRepository.addCopies(deck.id, section, copies)
            if (alsoToCollection) {
                results.forEach { collectionRepository.addCopies(it.card, it.quantity, it.isFoil, language = it.language) }
            }
        }
    }

    fun clearSession() = _uiState.update { it.copy(results = emptyList(), lastScanned = null, lastResultId = null) }

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
