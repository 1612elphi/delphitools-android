package com.delphi.delphitools.tools.audiovideo

import android.content.Context
import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import com.delphi.delphitools.canon.CanonTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** dt-ios AVInfoProbe.Tags. */
class AtlasTags(val title: String? = null, val artist: String? = null, val album: String? = null, val artwork: ByteArray? = null) {
    val isEmpty get() = title == null && artist == null && album == null && artwork == null
}

class AtlasProbeResult(val sections: List<AVEditProbeSection>, val tags: AtlasTags, val duration: Double)

/**
 * dt-ios AVInfoProbe for audio-atlas and video-atlas: container and per-track facts from MediaExtractor and MediaMetadataRetriever.
 * What those do not report (encoder software, bit depth of compressed video, data rate of most tracks) is absent, not guessed.
 */
object AtlasProbe {
    suspend fun probe(context: Context, uri: Uri): AtlasProbeResult? = withContext(Dispatchers.IO) {
        runCatching {
            val formats = MediaExtractor().run {
                try {
                    setDataSource(context, uri, null)
                    (0 until trackCount).map(::getTrackFormat)
                } finally {
                    release()
                }
            }
            val retriever = MediaMetadataRetriever()
            val meta = try {
                retriever.setDataSource(context, uri)
                Meta(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toDoubleOrNull()?.div(1000),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).clean(),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).clean(),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).clean(),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE).clean(),
                    retriever.embeddedPicture,
                )
            } catch (_: RuntimeException) {
                Meta()
            } finally {
                retriever.release()
            }
            val mimes = formats.map { it.getString(MediaFormat.KEY_MIME) ?: "" }
            val duration = meta.duration ?: ((formats.maxOfOrNull { it.long(MediaFormat.KEY_DURATION) ?: 0 } ?: 0) / 1e6)
            val video = formats.filterIndexed { index, _ -> mimes[index].startsWith("video/") }
            val audio = formats.filterIndexed { index, _ -> mimes[index].startsWith("audio/") }
            val text = formats.filterIndexed { index, _ -> VideoAtlasEngine.isText(mimes[index]) }
            if (video.isEmpty() && audio.isEmpty() && text.isEmpty()) return@runCatching null
            val counts = listOf(video.size to "video", audio.size to "audio", text.size to "text")
                .filter { it.first > 0 }.map { "${it.first} ${it.second}" }

            val sections = mutableListOf(AVEditProbeSection("General", general(context, uri, counts, duration, meta)))
            video.forEachIndexed { index, format -> sections += AVEditProbeSection("Video #${index + 1}", videoTiles(format)) }
            audio.forEachIndexed { index, format -> sections += AVEditProbeSection("Audio #${index + 1}", audioTiles(format)) }
            text.forEachIndexed { index, format -> sections += AVEditProbeSection("Text #${index + 1}", textTiles(format)) }
            AtlasProbeResult(
                sections.filter { it.tiles.isNotEmpty() },
                AtlasTags(meta.title, meta.artist, meta.album, meta.artwork),
                duration,
            )
        }.getOrNull()
    }

    private class Meta(
        val duration: Double? = null,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val date: String? = null,
        val artwork: ByteArray? = null,
    )

    private fun String?.clean() = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun general(context: Context, uri: Uri, counts: List<String>, duration: Double, meta: Meta): List<CanonTile> = buildList {
        val resolver = context.contentResolver
        val type = resolver.getType(uri)?.takeIf { it != "application/octet-stream" }
            ?: uri.lastPathSegment?.substringAfterLast('.', "")?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase()) }
        type?.let { add(CanonTile("Container", it)) }
        val bytes = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }
        }.getOrNull() ?: 0L
        if (bytes > 0) add(CanonTile("Size", Formatter.formatShortFileSize(context, bytes)))
        if (duration > 0) {
            add(CanonTile("Duration", AVEditEngine.timestamp(duration)))
            if (bytes > 0) add(CanonTile("Overall bitrate", AVEditEngine.bitrate(bytes * 8 / duration)))
        }
        if (counts.isNotEmpty()) add(CanonTile("Streams", counts.joinToString(" · ")))
        meta.title?.let { add(CanonTile("Title", it)) }
        meta.date?.let { add(CanonTile("Encoded", it)) }
    }

    private fun MutableList<CanonTile>.common(format: MediaFormat, label: String) {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return
        add(CanonTile(label, VideoAtlasEngine.codecName(mime)))
        add(CanonTile("Codec ID", mime))
    }

    private fun MutableList<CanonTile>.tail(format: MediaFormat) {
        val seconds = (format.long(MediaFormat.KEY_DURATION) ?: 0) / 1e6
        if (seconds > 0) add(CanonTile("Duration", AVEditEngine.timestamp(seconds)))
        format.getString(MediaFormat.KEY_LANGUAGE)?.takeIf { it != "und" }?.let { add(CanonTile("Language", it)) }
    }

    private fun MutableList<CanonTile>.bitrate(format: MediaFormat) {
        format.int(MediaFormat.KEY_BIT_RATE)?.takeIf { it > 0 }?.let { add(CanonTile("Bitrate", AVEditEngine.bitrate(it.toDouble()))) }
    }

    private fun videoTiles(format: MediaFormat): List<CanonTile> = buildList {
        common(format, "Codec")
        val rotation = format.int(MediaFormat.KEY_ROTATION) ?: 0
        val turned = rotation % 180 != 0
        val width = format.int(MediaFormat.KEY_WIDTH) ?: 0
        val height = format.int(MediaFormat.KEY_HEIGHT) ?: 0
        val (w, h) = if (turned) height to width else width to height
        if (w > 0 && h > 0) add(CanonTile("Frame size", "$w × $h (${AVEditEngine.ratio(w.toDouble() / h)})"))
        if (rotation != 0) add(CanonTile("Rotation", "${(rotation % 360 + 360) % 360}°"))
        val seconds = (format.long(MediaFormat.KEY_DURATION) ?: 0) / 1e6
        format.fps()?.takeIf { it > 0 }?.let { fps ->
            add(CanonTile("Frame rate", AVEditEngine.fpsLabel(fps)))
            if (seconds > 0) add(CanonTile("Frames", "${(seconds * fps).roundToInt()}"))
        }
        bitrate(format)
        val transfer = format.int(MediaFormat.KEY_COLOR_TRANSFER)
        VideoAtlasEngine.colour(format.int(MediaFormat.KEY_COLOR_STANDARD), transfer, format.int(MediaFormat.KEY_COLOR_RANGE))?.let { add(CanonTile("Colour", it)) }
        if (transfer == MediaFormat.COLOR_TRANSFER_ST2084 || transfer == MediaFormat.COLOR_TRANSFER_HLG) add(CanonTile("HDR", "Yes"))
        tail(format)
    }

    private fun audioTiles(format: MediaFormat): List<CanonTile> = buildList {
        common(format, "Codec")
        format.int(MediaFormat.KEY_CHANNEL_COUNT)?.let { add(CanonTile("Channels", "$it")) }
        format.int(MediaFormat.KEY_SAMPLE_RATE)?.takeIf { it > 0 }?.let { add(CanonTile("Sample rate", "$it Hz")) }
        if (format.getString(MediaFormat.KEY_MIME) == "audio/raw") {
            val bits = when (format.int(MediaFormat.KEY_PCM_ENCODING) ?: AudioFormat.ENCODING_PCM_16BIT) {
                AudioFormat.ENCODING_PCM_8BIT -> 8
                AudioFormat.ENCODING_PCM_16BIT -> 16
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
                else -> null
            }
            bits?.let { add(CanonTile("Bit depth", "$it bit")) }
        }
        bitrate(format)
        tail(format)
    }

    private fun textTiles(format: MediaFormat): List<CanonTile> = buildList {
        common(format, "Format")
        tail(format)
    }
}
