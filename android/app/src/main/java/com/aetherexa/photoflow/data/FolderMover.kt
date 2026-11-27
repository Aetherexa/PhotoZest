package com.aetherexa.photoflow.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.aetherexa.photoflow.core.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Native SAF moves only. No copy/ZIP fallback, no app-managed delete, no overwrite.
 * The document provider must support moving an existing document within its storage volume.
 * This avoids PhotoFlow temporarily or permanently duplicating the user's photo bytes.
 */
enum class FolderLayout { CATEGORY_FOLDERS, ONE_COLLECTION }

object FolderMover {
    data class Moved(val original: Uri, val destination: Uri, val parent: Uri)
    data class Report(val output: Uri, val successes: Int, val failures: List<String>)

    /** Accessible for regression tests without an Android provider. */
    fun storageVolume(treeDocumentId: String): String = treeDocumentId.substringBefore(':')

    fun safeFolderName(name: String): String {
        val cleaned = name.trim().replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "-")
        return cleaned.take(58).ifBlank { "Trip" }
    }

    suspend fun move(
        context: Context,
        sourceTree: Uri,
        destinationTree: Uri,
        sourceName: String,
        items: List<PhotoItem>,
        layout: FolderLayout,
        onItem: suspend (PhotoItem, Moved?, String?) -> Unit
    ): Report = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        require(items.isNotEmpty()) { "Select at least one photo to organize" }
        require(items.all { it.source.parentUri != Uri.EMPTY }) {
            "Gallery selections need verified original-folder access before moving"
        }
        require(sourceTree.authority == destinationTree.authority) {
            "Choose a destination in the same storage provider as the source folder."
        }
        val sourceVolume = storageVolume(DocumentsContract.getTreeDocumentId(sourceTree))
        val targetVolume = storageVolume(DocumentsContract.getTreeDocumentId(destinationTree))
        require(sourceVolume == targetVolume) {
            "Choose a destination on the same storage volume. Moving across drives would require copying."
        }
        val destination = DocumentFile.fromTreeUri(context, destinationTree)
            ?: throw IllegalArgumentException("Unable to open destination folder")
        require(destination.isDirectory && destination.canWrite()) {
            "Destination is read-only. Choose a writable folder."
        }
        val sourcePermission = resolver.persistedUriPermissions.any {
            it.uri == sourceTree && it.isReadPermission && it.isWritePermission
        }
        require(sourcePermission) {
            "Source folder needs write access. Choose the source folder again and allow modifications."
        }
        val outputBaseName = "PhotoFlow - ${safeFolderName(sourceName)}"
        val outputName = generateSequence(0) { it + 1 }
            .map { if (it == 0) outputBaseName else "$outputBaseName ($it)" }
            .first { destination.findFile(it) == null }
        val output = destination.createDirectory(outputName)
            ?: throw IllegalStateException("Could not create destination project folder")
        val categoryDirs = mutableMapOf<Category, DocumentFile>()
        // Subfolders are optional; a single named collection is supported too.
        if (layout == FolderLayout.CATEGORY_FOLDERS) {
            for (category in items.map { it.category }.distinct()) {
                categoryDirs[category] = output.createDirectory(category.folder)
                    ?: throw IllegalStateException("Could not create ${category.folder} folder")
            }
        }

        var moved = 0
        val failures = mutableListOf<String>()
        for (item in items) {
            coroutineContext.ensureActive()
            val categoryDir = if (layout == FolderLayout.CATEGORY_FOLDERS)
                categoryDirs.getValue(item.category) else output
            try {
                // Never overwrite existing files, including two photos with the same name.
                check(categoryDir.findFile(item.source.name) == null) {
                    "File name already exists in target folder"
                }
                val newUri = DocumentsContract.moveDocument(
                    resolver, item.source.uri, item.source.parentUri, categoryDir.uri
                ) ?: error("Storage provider declined this move")
                // The provider owns the move operation. Do not delete the source separately.
                // If the provider returns a URI, keep it for gallery access and move accounting.
                moved++
                onItem(item, Moved(item.source.uri, newUri, categoryDir.uri), null)
            } catch (error: Exception) {
                val reason = "${item.source.name}: ${error.message ?: "Provider does not support native move"}"
                failures.add(reason)
                onItem(item, null, reason)
            }
        }
        Report(output.uri, moved, failures)
    }
}
