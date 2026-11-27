package com.aetherexa.photoflow.data

import android.content.ContentResolver
import android.net.Uri
import com.aetherexa.photoflow.core.SmartTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

/** Stream original bytes to a user-chosen document. Never modify/delete source files. */
object ZipExporter {
    private fun safeName(raw: String): String = raw.replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('.').take(100).ifBlank { "photo.jpg" }
    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    suspend fun export(
        resolver: ContentResolver,
        output: Uri,
        items: List<PhotoItem>,
        includeSmartCollections: Boolean,
        onProgress: suspend (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val stream = resolver.openOutputStream(output, "w") ?: throw IOException("Cannot open export destination")
        stream.use { os -> ZipOutputStream(os.buffered(64 * 1024)).use { zip ->
            val csvRows = mutableListOf("name,folder,confidence,reason,tags,labels,width,height,bytes")
            val buffer = ByteArray(64 * 1024)
            suspend fun copyIntoZip(path: String, item: PhotoItem) {
                coroutineContext.ensureActive()
                zip.putNextEntry(ZipEntry(path))
                resolver.openInputStream(item.source.uri)?.use { input ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        zip.write(buffer, 0, count)
                    }
                } ?: throw IOException("Source image not available: ${item.source.name}")
                zip.closeEntry()
            }
            for ((index, item) in items.withIndex()) {
                coroutineContext.ensureActive()
                val filename = "${(index + 1).toString().padStart(4,'0')}_${safeName(item.source.name)}"
                copyIntoZip("${item.category.folder}/$filename", item)
                if (includeSmartCollections) {
                    for (tag in item.tags) copyIntoZip("Smart Collections/${tag.folder}/$filename", item)
                }
                csvRows += listOf(
                    item.source.name, item.category.folder, "%.2f".format(java.util.Locale.US, item.confidence),
                    item.reason, item.tags.joinToString("; ") { it.display }, item.labels,
                    item.width.toString(), item.height.toString(), item.source.bytes.toString()
                ).joinToString(",") { csv(it) }
                onProgress(index + 1)
            }
            zip.putNextEntry(ZipEntry("classification-manifest.csv"))
            zip.write(csvRows.joinToString("\n", postfix="\n").toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write(("PhotoFlow Android • Aetherexa\n\n" +
                "Photos sorted into one primary category each. Smart Collections are optional COPIES.\n" +
                "AI, blur, framing and duplicate results are only suggestions. Manually verify before sharing.\n" +
                "Original photos on device were not modified.\n").toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }}
    }
}
