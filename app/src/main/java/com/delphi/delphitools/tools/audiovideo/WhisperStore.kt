package com.delphi.delphitools.tools.audiovideo

import android.content.Context
import android.os.StatFs
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class WhisperDownloadException(val reason: WhisperFailure) : IOException(reason.name)

/**
 * dt-ios WhisperModelStore over plain HTTPS: each model's files in `filesDir/whisper/<id>/`, fetched from
 * Hugging Face with a bare GET, resumed from a `.part` file, checked against SHA-256, then renamed into place.
 * Main thread only, apart from the progress writes.
 */
object WhisperModelStore {
    private val states = mutableStateMapOf<String, WhisperState>()
    private val jobs = HashMap<String, Job>()
    private const val PROGRESS_MS = 100L

    fun state(model: WhisperModel): WhisperState = states[model.id] ?: WhisperState.Absent

    val installed: List<WhisperModel> get() = WhisperModel.all.filter { state(it) == WhisperState.Installed }

    fun folder(context: Context, model: WhisperModel) = File(File(context.filesDir, "whisper"), model.id)

    suspend fun refresh(context: Context) {
        val found = withContext(Dispatchers.IO) {
            WhisperModel.all.associateWith { model ->
                val dir = folder(context, model)
                WhisperDownloadPlan.settled(model, { File(dir, it.name).takeIf(File::isFile)?.length() }, states[model.id])
            }
        }
        for ((model, state) in found) if (model.id !in jobs) states[model.id] = state
    }

    // ponytail: downloads run in the page's scope and stop when it closes; a foreground service or WorkManager job to outlive it
    fun download(context: Context, model: WhisperModel, scope: CoroutineScope) {
        if (model.id in jobs) return
        states[model.id] = WhisperState.Downloading(0.0)
        jobs[model.id] = scope.launch {
            var outcome: WhisperState = WhisperState.Absent
            try {
                fetch(context.applicationContext, model)
                outcome = WhisperState.Installed
            } catch (e: CancellationException) {
                throw e
            } catch (e: WhisperDownloadException) {
                outcome = WhisperState.Failed(e.reason)
            } catch (_: Exception) {
                outcome = WhisperState.Failed(WhisperFailure.Network)
            } finally {
                jobs.remove(model.id)
                states[model.id] = outcome
            }
        }
    }

    fun cancel(model: WhisperModel) {
        jobs[model.id]?.cancel()
    }

    fun cancelAll() = jobs.values.toList().forEach(Job::cancel)

    suspend fun remove(context: Context, model: WhisperModel) {
        jobs.remove(model.id)?.cancel()
        withContext(Dispatchers.IO) { folder(context, model).deleteRecursively() }
        states[model.id] = WhisperState.Absent
    }

    private fun part(dir: File, file: WhisperFile) = File(dir, "${file.name}.part")

    private suspend fun fetch(context: Context, model: WhisperModel) = withContext(Dispatchers.IO) {
        val dir = folder(context, model).apply { mkdirs() }
        fun present(file: WhisperFile) = File(dir, file.name).takeIf(File::isFile)?.length() ?: part(dir, file).length()
        val remaining = WhisperDownloadPlan.remaining(model, ::present)
        if (StatFs(dir.path).availableBytes < remaining) throw WhisperDownloadException(WhisperFailure.Space)
        var before = 0L
        var shown = 0L
        for (file in model.files) {
            fetch(model.url(file), dir, file) { bytes ->
                val now = System.currentTimeMillis()
                if (now - shown >= PROGRESS_MS) {
                    shown = now
                    states[model.id] = WhisperState.Downloading(WhisperDownloadPlan.fraction(before + bytes, model.bytes))
                }
            }
            before += file.bytes
        }
    }

    /** One file into [dir]: resumes its `.part`, hashes it whole, renames it over [WhisperFile.name]. */
    private suspend fun fetch(url: String, dir: File, file: WhisperFile, progress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        val target = File(dir, file.name)
        if (target.isFile && target.length() == file.bytes) return@withContext
        val part = part(dir, file)
        val digest = MessageDigest.getInstance("SHA-256")
        var from = WhisperDownloadPlan.resumeFrom(part.length(), file.bytes)
        if (from > 0) part.inputStream().use { input -> feed(input.buffered(), digest) }
        if (from < file.bytes) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.useCaches = false
                // the platform default names the device model and build
                connection.setRequestProperty("User-Agent", "delphitools")
                connection.setRequestProperty("Accept-Encoding", "identity")
                if (from > 0) connection.setRequestProperty("Range", "bytes=$from-")
                val start = WhisperDownloadPlan.bodyStart(connection.responseCode, from, connection.getHeaderField("Content-Range"))
                    ?: throw IOException("HTTP ${connection.responseCode}")
                if (start != from) {
                    digest.reset()
                    from = start
                }
                connection.inputStream.use { input ->
                    FileOutputStream(part, from > 0).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var written = from
                        progress(written)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            written += read
                            progress(written)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
        if (part.length() != file.bytes || WhisperDownloadPlan.hex(digest.digest()) != file.sha256) {
            part.delete()
            throw WhisperDownloadException(WhisperFailure.Checksum)
        }
        Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun feed(input: java.io.InputStream, digest: MessageDigest) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return
            digest.update(buffer, 0, read)
        }
    }
}
