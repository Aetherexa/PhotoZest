package com.aetherexa.photoflow

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aetherexa.photoflow.core.Category
import com.aetherexa.photoflow.core.CategoryEngine
import com.aetherexa.photoflow.core.SmartTag
import com.aetherexa.photoflow.core.ScreenshotSignals
import com.aetherexa.photoflow.data.FolderScanner
import com.aetherexa.photoflow.data.PhotoItem
import com.aetherexa.photoflow.data.VisionAnalyzer
import com.aetherexa.photoflow.data.FolderMover
import com.aetherexa.photoflow.data.LocalTagEdits
import com.aetherexa.photoflow.data.GallerySelection
import com.aetherexa.photoflow.data.GalleryOriginalResolver
import com.aetherexa.photoflow.data.PhotoSource
import com.aetherexa.photoflow.data.FolderLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable

enum class WorkflowPhase { IDLE, SCANNING, ANALYZING, LINKING, READY, MOVING, ERROR }
enum class InputKind { FOLDER, GALLERY }

class PhotoFlowViewModel(private val app: Application) : AndroidViewModel(app) {
    val photos = mutableStateListOf<PhotoItem>()
        // Exposes categories and tags with observable replacement updates, not mutable item internals.

    var phase by mutableStateOf(WorkflowPhase.IDLE)
        private set
    var selectedFolder by mutableStateOf("No folder selected")
        private set
    var selectedCount by mutableStateOf(0)
        private set
    var processedCount by mutableStateOf(0)
        private set
    var failedCount by mutableStateOf(0)
        private set
    var message by mutableStateOf("Choose a trip folder to begin")
        private set
    var useOnDeviceAI by mutableStateOf(true)
    var useScreenshotOCR by mutableStateOf(true)
    var inputKind by mutableStateOf(InputKind.FOLDER)
        private set
    var linkedOriginals by mutableStateOf(0)
        private set
    var unmatchedOriginals by mutableStateOf(0)
        private set
    var sourceUri by mutableStateOf<Uri?>(null)
        private set
    var movedCount by mutableStateOf(0)
        private set
    var moveFailures by mutableStateOf<List<String>>(emptyList())
        private set
    var outputFolder by mutableStateOf<Uri?>(null)
        private set
    private var task: Job? = null
    private val manualEdits = LocalTagEdits(app)

    val isBusy get() = phase in setOf(WorkflowPhase.SCANNING, WorkflowPhase.ANALYZING,
        WorkflowPhase.LINKING, WorkflowPhase.MOVING)

    fun scan(uri: Uri, folderName: String) = startScan(folderName, uri, InputKind.FOLDER) {
        FolderScanner.scan(app, uri)
    }

    fun scanFromGallery(uris: List<Uri>) = startScan("Gallery selection", null, InputKind.GALLERY) {
        GallerySelection.read(app.contentResolver, uris)
    }

    private fun startScan(folderName: String, treeUri: Uri?, kind: InputKind,
                          gather: suspend () -> List<PhotoSource>) {
        if (isBusy) return
        task?.cancel()
        task = viewModelScope.launch {
            selectedFolder = folderName
            sourceUri = treeUri
            inputKind = kind
            linkedOriginals = 0
            unmatchedOriginals = 0
            movedCount = 0
            moveFailures = emptyList()
            outputFolder = null
            photos.clear()
            processedCount = 0
            selectedCount = 0
            failedCount = 0
            phase = WorkflowPhase.SCANNING
            message = if (kind == InputKind.GALLERY)
                "Reading the photos you selected…" else "Reading the selected folder…"
            try {
                val sources = gather()
                selectedCount = sources.size
                if (sources.isEmpty()) {
                    phase = WorkflowPhase.READY
                    message = "No supported images were selected."
                    return@launch
                }
                phase = WorkflowPhase.ANALYZING
                message = "Analyzing locally on your device"
                VisionAnalyzer(app.contentResolver).use { analyzer ->
                    for (source in sources) {
                        try {
                            val analyzed = analyzer.analyze(source, useOnDeviceAI, useScreenshotOCR)
                            val edits = manualEdits.load(source, analyzed.metadata)
                            val result = analyzed.copy(manualTags = edits.tags, manualCity = edits.city)
                            // Mark duplicate *suggestions* only, without deleting or moving photos.
                            val ratio = result.width.toDouble() / result.height.coerceAtLeast(1)
                            val match = photos.indexOfFirst { existing ->
                                when {
                                    existing.payment != null || result.payment != null ->
                                        existing.payment?.referenceLast4 != null &&
                                            existing.payment.referenceLast4 == result.payment?.referenceLast4 &&
                                            existing.payment.amountPaise == result.payment?.amountPaise &&
                                            existing.payment.provider == result.payment?.provider
                                    existing.category == Category.SCREENSHOTS ||
                                        result.category == Category.SCREENSHOTS -> false
                                    else -> existing.category == result.category &&
                                        existing.category != Category.REVIEW &&
                                        CategoryEngine.looksLikeDuplicate(
                                        existing.hash, result.hash,
                                        existing.width.toDouble() / existing.height.coerceAtLeast(1), ratio
                                    )
                                }
                            }
                            val marked = if (match < 0) result else result.copy(
                                tags = result.tags + SmartTag.MAYBE_DUPLICATE,
                                reason = result.reason + "; similar-looking photo"
                            )
                            if (match >= 0) {
                                val existing = photos[match]
                                photos[match] = existing.copy(tags = existing.tags + SmartTag.MAYBE_DUPLICATE)
                            }
                            photos.add(marked)
                        } catch (c: CancellationException) {
                            throw c
                        } catch (_: Exception) {
                            failedCount++ // Corrupt/unsupported image; never silently claim sorted.
                        }
                        processedCount++
                    }
                }
                phase = WorkflowPhase.READY
                message = "${photos.size} sorted • $failedCount skipped • Review your results"
            } catch (_: CancellationException) {
                phase = WorkflowPhase.IDLE
                message = "Sorting canceled"
            } catch (error: Exception) {
                phase = WorkflowPhase.ERROR
                message = error.message ?: "Could not access the selected folder"
            }
        }
    }

    fun cancel() {
        if (phase == WorkflowPhase.ANALYZING || phase == WorkflowPhase.SCANNING) {
            task?.cancel()
        }
    }

    fun changeCategory(uri: Uri, category: Category) {
        if (isBusy) return
        val index = photos.indexOfFirst { it.source.uri == uri }
        if (index >= 0) {
            photos[index] = photos[index].copy(
                category = category, confidence = 1f, reason = "Manually selected",
                // A false-positive receipt removed by the user must stop contributing to expense totals.
                payment = if (category == Category.PAYMENTS) photos[index].payment else null
            )
        }
    }

    fun toggleTag(uri: Uri, tag: SmartTag) {
        if (isBusy) return
        val index = photos.indexOfFirst { it.source.uri == uri }
        if (index >= 0) {
            val item = photos[index]
            photos[index] = item.copy(tags = if (tag in item.tags) item.tags - tag else item.tags + tag)
        }
    }

    /** Searchable labels are virtual: multiple labels never create multiple file copies. */
    fun addManualTag(uri: Uri, raw: String) {
        if (isBusy) return
        val value = raw.trim().replace(Regex("\\s+"), " ").take(48)
        if (value.isBlank()) return
        updatePhoto(uri) { item ->
            if (item.manualTags.size >= 30) item else item.copy(manualTags = item.manualTags + value)
        }
    }

    fun removeManualTag(uri: Uri, value: String) {
        if (isBusy) return
        updatePhoto(uri) { item -> item.copy(manualTags = item.manualTags - value) }
    }

    fun setManualCity(uri: Uri, raw: String) {
        if (isBusy) return
        val value = raw.trim().replace(Regex("\\s+"), " ").take(60).ifBlank { null }
        updatePhoto(uri) { item -> item.copy(manualCity = value) }
    }

    private fun updatePhoto(uri: Uri, change: (PhotoItem) -> PhotoItem) {
        val index = photos.indexOfFirst { it.source.uri == uri }
        if (index < 0) return
        val updated = change(photos[index])
        photos[index] = updated
        manualEdits.save(updated.source, updated.metadata, updated.manualTags, updated.manualCity)
    }

    /** Called only after the user explicitly grants write access to a source directory. */
    fun linkGalleryOriginals(tree: Uri, folderName: String) {
        if (inputKind != InputKind.GALLERY || isBusy || photos.isEmpty()) return
        task = viewModelScope.launch {
            phase = WorkflowPhase.LINKING
            message = "Verifying gallery selections against original files…"
            try {
                val report = GalleryOriginalResolver.link(app, tree, photos.toList())
                for (index in photos.indices) {
                    val original = photos[index]
                    if (!original.moved) {
                        val linked = report.linked[original.originUri]
                        // Restore read-only gallery reference if a previous source folder was different.
                        val source = linked ?: original.source.copy(
                            uri = original.originUri, parentUri = Uri.EMPTY)
                        photos[index] = original.copy(source = source)
                    }
                }
                sourceUri = tree
                linkedOriginals = report.linked.size
                unmatchedOriginals = report.unmatched
                selectedFolder = folderName
                phase = WorkflowPhase.READY
                message = "$linkedOriginals verified originals ready to move; $unmatchedOriginals unmatched."
            } catch (c: CancellationException) {
                phase = WorkflowPhase.READY
                message = "Folder verification canceled. Nothing moved."
            } catch (error: Exception) {
                phase = WorkflowPhase.READY
                message = "Could not verify originals: ${error.message ?: "folder is not accessible"}. Nothing moved."
            }
        }
    }

    fun moveToFolder(destinationUri: Uri, originals: Set<Uri>, layout: FolderLayout, collectionName: String) {
        if (isBusy) return
        val rootUri = sourceUri ?: return
        val pending = photos.filter { !it.moved && it.originUri in originals &&
            it.source.parentUri != Uri.EMPTY }
        if (pending.isEmpty()) return
        task = viewModelScope.launch {
            processedCount = 0
            failedCount = 0
            moveFailures = emptyList()
            phase = WorkflowPhase.MOVING
            message = "Moving originals into categorized folders…"
            try {
                val report = FolderMover.move(app, rootUri, destinationUri, collectionName.ifBlank { selectedFolder }, pending, layout) { item, outcome, failure ->
                    withContext(NonCancellable + Dispatchers.Main) {
                        val index = photos.indexOfFirst { it.originUri == item.originUri }
                        if (outcome != null && index >= 0) {
                            photos[index] = photos[index].copy(
                                source = photos[index].source.copy(uri = outcome.destination, parentUri = outcome.parent),
                                moved = true
                            )
                            movedCount++
                        }
                        if (failure != null) {
                            moveFailures = moveFailures + failure
                            failedCount++
                        }
                        processedCount++
                    }
                }
                outputFolder = report.output
                phase = WorkflowPhase.READY
                message = if (report.failures.isEmpty()) {
                    "${report.successes} originals moved successfully. No duplicate photo copies created."
                } else {
                    "${report.successes} moved · ${report.failures.size} could not be moved. Originals remain at their source locations."
                }
            } catch (_: CancellationException) {
                phase = WorkflowPhase.READY
                message = "Move interrupted. $movedCount files moved; others remain in their original folders."
            } catch (error: Exception) {
                phase = WorkflowPhase.ERROR
                message = error.message ?: "Move failed; source photos were not deleted by PhotoFlow."
            }
        }
    }

    fun clearStatus() {
        if (!isBusy && phase == WorkflowPhase.ERROR) phase = WorkflowPhase.READY
    }
}
