package com.aetherexa.photoflow.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Gallery pickers give read grants only. Never interpret these as permission to move photos. */
object GallerySelection {
    const val MAX_PICKED = 100

    suspend fun read(resolver: ContentResolver, uris: List<Uri>): List<PhotoSource> =
        withContext(Dispatchers.IO) {
            uris.distinct().take(MAX_PICKED).mapNotNull { uri ->
                coroutineContext.ensureActive()
                try {
                    var filename: String? = null
                    var bytes = -1L
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                        null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIndex >= 0 && !cursor.isNull(nameIndex))
                                filename = cursor.getString(nameIndex)
                            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex))
                                bytes = cursor.getLong(sizeIndex)
                        }
                    }
                    // No source parent: user-selected gallery URIs must never go to FolderMover directly.
                    PhotoSource(uri, Uri.EMPTY, filename?.takeIf { it.isNotBlank() }
                        ?: uri.lastPathSegment.orEmpty().takeLast(55).ifBlank { "Image" }, bytes)
                } catch (_: Exception) { null }
            }
        }
}

/** Exact-byte verification before converting a selected read-only image into a writable SAF document. */
object GalleryOriginalResolver {
    data class Result(val linked: Map<Uri, PhotoSource>, val unmatched: Int)

    suspend fun link(context: Context, sourceTree: Uri, picked: List<PhotoItem>): Result =
        withContext(Dispatchers.IO) {
            val pending = picked.filterNot { it.moved }
            val candidates = FolderScanner.scan(context, sourceTree)
            val byNameAndSize = candidates.groupBy { key(it.name, it.bytes) }
            val resolver = context.contentResolver
            val links = mutableMapOf<Uri, PhotoSource>()
            val used = mutableSetOf<Uri>()
            for (item in pending) {
                coroutineContext.ensureActive()
                val matches = byNameAndSize[key(item.source.name, item.source.bytes)]
                    .orEmpty().filterNot { it.uri in used }
                // Ambiguous duplicate names/files are deliberately skipped rather than risking a wrong move.
                if (matches.size != 1 || item.source.bytes <= 0) continue
                val original = matches.single()
                val galleryHash = sha256(resolver, item.originUri) ?: continue
                val fileHash = sha256(resolver, original.uri) ?: continue
                if (!galleryHash.contentEquals(fileHash)) continue
                links[item.originUri] = original
                used += original.uri
            }
            Result(links, pending.size - links.size)
        }

    private fun key(name: String, size: Long) = name.lowercase(java.util.Locale.ROOT) to size

    private fun sha256(resolver: ContentResolver, uri: Uri): ByteArray? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = stream.read(buffer)
                if (n == -1) break
                digest.update(buffer, 0, n)
            }
            digest.digest()
        }
    } catch (_: Exception) { null }
}
