package com.aetherexa.photoflow.data

import android.content.Context
import com.aetherexa.photoflow.core.PhotoMetadata
import org.json.JSONArray
import java.security.MessageDigest

/** Manual tags and corrected city names are saved privately; original EXIF and images are untouched. */
class LocalTagEdits(context: Context) {
    private val prefs = context.getSharedPreferences("photoflow_manual_tags_v1", Context.MODE_PRIVATE)

    data class Edits(val tags: Set<String> = emptySet(), val city: String? = null)

    private fun key(source: PhotoSource, metadata: PhotoMetadata): String {
        // Filename + byte size + capture timestamp survives a same-volume move where SAF URI changes.
        val identity = "${source.name}|${source.bytes}|${metadata.capturedAt.orEmpty()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun load(source: PhotoSource, metadata: PhotoMetadata): Edits {
        val id = key(source, metadata)
        return runCatching {
            val list = JSONArray(prefs.getString("tags-$id", "[]"))
            val tags = buildSet {
                for (i in 0 until list.length()) list.optString(i)?.takeIf(String::isNotBlank)?.let { add(it) }
            }
            Edits(tags, prefs.getString("city-$id", null))
        }.getOrDefault(Edits())
    }

    fun save(source: PhotoSource, metadata: PhotoMetadata, tags: Set<String>, city: String?) {
        val id = key(source, metadata)
        prefs.edit()
            .putString("tags-$id", JSONArray(tags.sorted().take(30)).toString())
            .apply {
                if (city.isNullOrBlank()) remove("city-$id") else putString("city-$id", city)
            }
            .apply()
    }
}
