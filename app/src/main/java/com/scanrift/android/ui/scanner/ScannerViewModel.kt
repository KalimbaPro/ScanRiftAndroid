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
import com.scanrift.android.domain.model.CardPrintings
import com.scanrift.android.domain.model.PrintingIndex
import com.scanrift.android.di.DefaultDispatcher
import com.scanrift.android.domain.model.ScanLanguage
import com.scanrift.android.service.feedback.FeedbackService
import com.scanrift.android.service.scanning.CameraService
import com.scanrift.android.service.scanning.CameraState
import com.scanrift.android.service.scanning.CardMatchingService
import com.scanrift.android.service.scanning.NormalizedBox
import com.scanrift.android.service.scanning.ScanIdentifier
import com.scanrift.android.service.scanning.BitmapFrameArt
import com.scanrift.android.service.scanning.CatalogueArt
import com.scanrift.android.service.scanning.PageScanService
import com.scanrift.android.service.scanning.MotionDetector
import com.scanrift.android.service.scanning.OcrResult
import com.scanrift.android.service.scanning.OcrService
import com.scanrift.android.service.scanning.FocusDecision
import com.scanrift.android.service.scanning.OcrGeometry
import com.scanrift.android.service.scanning.ScanFocusPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    /** Read without its variant suffix and not settled by art (see [ScanResult.ambiguous]). */
    val ambiguous: Boolean = false,
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
    /** Every card's printings, built once when the catalogue snapshot loads. */
    val printingIndex: PrintingIndex = PrintingIndex.EMPTY,
    /** Non-null while the version picker is open. */
    val versionTarget: VersionTarget? = null,
    /** Auto-add mode: the copies the last scan wrote, which a version change re-keys. */
    val lastAutoAdded: AutoAddedScan? = null,
    /** Auto-add mode: the last scan's printing is a guess (see [ScanResult.ambiguous]). */
    val lastAmbiguous: Boolean = false,
) {
    val scannedCount: Int get() = results.sumOf { it.quantity }
    val correcting: ScanResult? get() = results.firstOrNull { it.id == correctingId }
    val lastResult: ScanResult? get() = results.firstOrNull { it.id == lastResultId }
    val correctingPageItem: PageItem? get() = page?.items?.firstOrNull { it.id == correctingPageItemId }
    val isCorrecting: Boolean get() = correctingId != null || correctingPageItemId != null

    /** The last scan's printing is a guess: the set chip draws attention. */
    val lastScanAmbiguous: Boolean
        get() = lastResult?.ambiguous ?: (lastAutoAdded != null && lastAmbiguous)

    /** True when the last scan has another printing to switch to: the set chip is active. */
    val lastScanHasVersions: Boolean
        get() = lastScanVersionTarget() != null && printingIndex.hasChoice(lastScanned)
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
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val catalogueArt: CatalogueArt,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    val cameraState = cameraService.state
    val torchEnabled = cameraService.torchEnabled
    val hasFlashUnit = cameraService.hasFlashUnit

    val decks: StateFlow<List<Deck>> = deckRepository.observeDecks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val motionDetector = MotionDetector()

    /** Wide vs focus matching when several cards are in view; see [ScanFocusPolicy]. */
    private val focusPolicy = ScanFocusPolicy()

    /** The scan guide as laid out on screen, in view pixels; null until measured. */
    @Volatile
    private var scanGuide: Pair<Size2, NormalizedBox>? = null
    private var matcher: CardMatchingService? = null

    /** Set ids a binder-page code may start with, raw and normalised. */
    private var knownSetIds: Set<String> = emptySet()
    private var pageJob: Job? = null

    /** Serialises auto-add writes so a version re-key never overtakes the scan or "+1" it moves. */
    private val autoAddWrites = Mutex()

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
            val index = withContext(defaultDispatcher) { CardPrintings.index(cards) }
            _uiState.update { it.copy(printingIndex = index) }
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
                val matcher = checkNotNull(matcher) { "Catalogue not loaded yet" }
                pageScanService.scan(image, knownSetIds, ScanIdentifier(matcher), preference, catalogueArt)
            }
            _uiState.update { state ->
                val review = state.page?.takeIf { it.image === image } ?: return@update state
                result.fold(
                    onSuccess = { cards ->
                        val items = cards.mapIndexed { index, (found, outcome) ->
                            PageItem(
                                id = "page-$index-${found.code}",
                                bounds = found.bounds,
                                code = found.code,
                                card = outcome.match?.card,
                                language = outcome.language.language,
                                ambiguous = outcome.ambiguous,
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
            // One row per card, newest first; folding in reverse keeps the page's reading order at the top.
            val results = matched.asReversed().fold(state.results) { acc, item ->
                acc.adding(item.card!!, item.isFoil, item.language, ambiguous = item.ambiguous)
            }
            state.copy(results = results, page = null, correctingPageItemId = null)
        }
        return matched.size
    }

    /**
     * Where the camera layer drew the guide: the view's size and the guide rect, both in
     * view pixels. Frames map it into their own coordinates ([OcrGeometry.guideInImage]).
     */
    fun setScanGuide(viewWidth: Float, viewHeight: Float, guide: NormalizedBox) {
        scanGuide = Size2(viewWidth, viewHeight) to guide
    }

    fun startScanning() {
        focusPolicy.reset()
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

        // Map the guide now: the proxy's size and rotation are gone once OCR closes it.
        val guide = scanGuide?.let { (view, rect) ->
            OcrGeometry.guideInImage(
                view.width, view.height, rect,
                imageProxy.width, imageProxy.height, imageProxy.imageInfo.rotationDegrees,
            )
        }

        isProcessingFrame = true
        motionDetector.readyToScan = false
        _uiState.update { it.copy(overlayState = ScannerOverlayState.PROCESSING) }

        viewModelScope.launch {
            try {
                // extractText closes the proxy in its own finally. The Chinese/Korean
                // pass is paid only for a matched card with no Latin name, in Auto.
                // In focus mode both the gate and the CJK lines read only the focused card.
                val preference = _uiState.value.scanLanguage
                var decision: FocusDecision? = null
                val decide = { latin: OcrResult ->
                    focusPolicy.evaluate(latin.lines, guide, knownSetIds, latin.imageAspect).also { decision = it }
                }
                val identifier = ScanIdentifier(matcher)
                val ocr = ocrService.extractText(
                    imageProxy,
                    needsScriptPass = { latin -> identifier.needsScriptPass(latin, decide(latin), preference) },
                    // Kept only when the code was read without its variant suffix.
                    needsFrame = { latin -> identifier.needsFrame(latin, decision ?: decide(latin)) },
                )
                handleOcr(ocr, identifier, decision ?: decide(ocr), guide)
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

    private suspend fun handleOcr(
        ocr: OcrResult,
        identifier: ScanIdentifier,
        decision: FocusDecision,
        guide: NormalizedBox?,
    ) {
        updateDebug {
            it.copy(
                ocrText = ocr.extractedText,
                lastProcessTime = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date()),
            )
        }

        // Where the card sits in the frame, for the art comparison of an ambiguous printing.
        val cardRegion = (decision as? FocusDecision.Focused)?.card?.bounds ?: guide ?: NormalizedBox(0f, 0f, 1f, 1f)
        val outcome = try {
            identifier.identifyFrame(
                ocr,
                _uiState.value.scanLanguage,
                frame = ocr.frame?.let { BitmapFrameArt(it, cardRegion) },
                art = catalogueArt,
                decision = decision,
            )
        } finally {
            ocr.frame?.recycle()
        }
        val view = outcome.view
        val match = outcome.match
        updateDebug {
            it.copy(
                matchCount = if (match == null) 0 else 1,
                motionStatus = "${it.motionStatus} · ${if (decision == FocusDecision.Wide) "wide" else "focus"}",
            )
        }
        if (match == null || view == null) {
            motionDetector.markRetry()
            return
        }

        lastScanAtMs = System.currentTimeMillis()
        motionDetector.markScanned()

        val detection = outcome.language
        updateDebug { it.copy(ocrText = "${ocr.extractedText}\n[language ${detection.language.code} via ${detection.source}]") }

        val autoAdd = userPreferences.autoAddToCollection.first()
        if (autoAdd) {
            autoAddWrites.withLock { collectionRepository.addCopies(match.card, language = detection.language) }
        }

        _uiState.update {
            val (results, rowId) = if (autoAdd) {
                it.results to null
            } else {
                it.results.addingScanReturningId(match.card, detection.language, ambiguous = outcome.ambiguous)
            }
            it.copy(
                overlayState = ScannerOverlayState.MATCHED,
                lastScanned = match.card,
                lastResultId = rowId,
                lastLanguage = detection.language,
                results = results,
                lastAutoAdded = if (autoAdd) AutoAddedScan(match.card, match.card.isAlwaysFoil, detection.language) else null,
                lastAmbiguous = outcome.ambiguous,
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
     * when its row is deleted it moves to the newest remaining row.
     */
    private fun updateResults(transform: (List<ScanResult>) -> List<ScanResult>) = _uiState.update { state ->
        val results = transform(state.results)
        if (state.lastResultId == null) return@update state.copy(results = results)
        val lastRow = results.firstOrNull { it.id == state.lastResultId }
            ?: results.firstOrNull()
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
        val before = _uiState.value.lastResult ?: return
        updateResults { it.togglingFoil(lastId) }
        if (before.quantity > 1) {
            // A "+1" stack split one copy off just below; the banner follows that copy.
            val results = _uiState.value.results
            val split = results.getOrNull(results.indexOfFirst { it.id == lastId } + 1)
            split?.let { row -> _uiState.update { it.copy(lastResultId = row.id) } }
        }
    }

    /** "+1": one more copy of the last scan, as if it had been scanned again. */
    fun incrementLast() {
        val state = _uiState.value
        val last = state.lastResult
        if (last != null) {
            setQuantity(last.id, last.quantity + 1)
        } else if (state.lastScanned != null) {
            // Auto-add mode keeps no session rows; the extra copy goes straight in.
            val added = state.lastAutoAdded
            val card = added?.card ?: state.lastScanned
            _uiState.update { it.copy(lastAutoAdded = it.lastAutoAdded?.let { a -> a.copy(quantity = a.quantity + 1) }) }
            viewModelScope.launch {
                autoAddWrites.withLock {
                    collectionRepository.addCopies(
                        card,
                        isFoil = added?.isFoil ?: card.isAlwaysFoil,
                        language = added?.language ?: state.lastLanguage,
                    )
                }
            }
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
        state.lastResult?.let(::startCorrection)
    }

    fun searchForCorrection(query: String) = _uiState.update {
        it.copy(correctionQuery = query, correctionResults = matcher?.searchCards(query).orEmpty())
    }

    fun applyCorrection(card: Card) {
        val state = _uiState.value
        val target = state.correctingPageItemId?.let { VersionTarget.PageItem(it) }
            ?: state.correctingId?.let { VersionTarget.Row(it) }
            ?: return
        applyVersion(target, card)
        dismissCorrection()
    }

    // ── Version picker ───────────────────────────────────────────────────────

    /** The set chip under the last scan. */
    fun openVersionPickerForLast() {
        val state = _uiState.value
        if (!state.lastScanHasVersions) return
        _uiState.update { it.copy(versionTarget = state.lastScanVersionTarget()) }
    }

    fun openVersionPicker(target: VersionTarget) = _uiState.update { it.copy(versionTarget = target) }

    fun dismissVersionPicker() = _uiState.update { it.copy(versionTarget = null) }

    /** A printing tapped in the picker: replaces the target's card and closes the picker. */
    fun selectVersion(card: Card) {
        val target = _uiState.value.versionTarget ?: return
        applyVersion(target, card)
        dismissVersionPicker()
    }

    private fun applyVersion(target: VersionTarget, card: Card) {
        var move: CopyMove? = null
        _uiState.update { state -> state.withVersion(target, card).also { move = it.second }.first }
        move?.let { m ->
            // Auto-add already owns the old printing; re-key exactly the copies it wrote.
            viewModelScope.launch {
                autoAddWrites.withLock {
                    val moved = collectionRepository.moveCopies(
                        from = m.from.card, fromFoil = m.from.isFoil,
                        to = m.to.card, toFoil = m.to.isFoil,
                        quantity = m.from.quantity, language = m.from.language,
                    )
                    if (moved < m.from.quantity) {
                        Log.database.w("Version change moved $moved of ${m.from.quantity} auto-added copies")
                    }
                }
            }
        }
    }

    fun dismissCorrection() = _uiState.update {
        it.copy(correctingId = null, correctingPageItemId = null, correctionQuery = "", correctionResults = emptyList())
    }

    fun addSessionToCollection() {
        // Identical rows merge only here, on the collection's entry key.
        val results = _uiState.value.results.collectionStacks()
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
                results.collectionStacks().forEach { collectionRepository.addCopies(it.card, it.quantity, it.isFoil, language = it.language) }
            }
        }
    }

    fun clearSession() = _uiState.update {
        it.copy(results = emptyList(), lastScanned = null, lastResultId = null, lastAutoAdded = null, versionTarget = null)
    }

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

/** A width and height in view pixels. */
data class Size2(val width: Float, val height: Float)
