package com.aetherexa.photoflow.data

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import android.content.Context
import com.aetherexa.photoflow.core.Category
import com.aetherexa.photoflow.core.SmartTag
import com.aetherexa.photoflow.core.PhotoMetadata
import com.aetherexa.photoflow.core.PhotoTagEngine
import com.aetherexa.photoflow.core.PaymentInsight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class PhotoSource(val uri: Uri, val parentUri: Uri, val name: String, val bytes: Long)

data class PhotoItem(
    val source: PhotoSource,
    val width: Int,
    val height: Int,
    val hash: Long,
    val category: Category,
    val confidence: Float,
    val reason: String,
    val tags: Set<SmartTag>,
    val labels: String,
    val moved: Boolean = false,
    val originUri: Uri = source.uri,
    val metadata: PhotoMetadata = PhotoMetadata(),
    val visualTags: Set<String> = emptySet(),
    val manualTags: Set<String> = emptySet(),
    val manualCity: String? = null,
    val payment: PaymentInsight? = null
) {
    val resolvedCity: String get() = manualCity ?: metadata.location
    val searchableTags: Set<String> get() = PhotoTagEngine.derivedTags(
        category, tags, metadata.copy(approximateCity = manualCity ?: metadata.approximateCity),
        visualTags, manualTags
    ) + resolvedCity + (payment?.searchableTags ?: emptySet())
}


object FolderScanner {
    private val accepted = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif", "bmp")
    const val MAX_PHOTOS = 900 // guard memory, battery and SAF performance

    suspend fun scan(context: Context, treeUri: Uri): List<PhotoSource> = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalArgumentException("Unable to open selected folder")
        if (!tree.isDirectory) throw IllegalArgumentException("Selected item is not a folder")
        val files = mutableListOf<PhotoSource>()
        val pending = ArrayDeque<Pair<DocumentFile, Int>>()
        pending.add(tree to 0)
        while (pending.isNotEmpty() && files.size < MAX_PHOTOS) {
            coroutineContext.ensureActive()
            val (folder, depth) = pending.removeFirst()
            // SAF tree enumeration can be slow for large folders; bound traversal depth.
            for (child in folder.listFiles()) {
                coroutineContext.ensureActive()
                if (child.isDirectory) {
                    if (depth < 6) pending.add(child to (depth + 1))
                } else if (child.isFile) {
                    val name = child.name ?: continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in accepted && child.canRead()) {
                        files.add(PhotoSource(child.uri, folder.uri, name, child.length()))
                        if (files.size >= MAX_PHOTOS) break
                    }
                }
            }
        }
        files.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}
