package com.vicechanger.app.recording

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Where voice files live and how they leave the app.
 *
 * Processed messages are written twice on purpose: once inside the app (so the share sheet
 * can hand out a FileProvider URI the receiving app is guaranteed to be allowed to read) and
 * once into the device's music library under `Music/VICECHANGER` via MediaStore, so the file
 * is still there after the app is uninstalled or its cache cleared. No storage permission is
 * needed for either on API 29+.
 */
class AudioFileStore(private val context: Context) {

    val rawDir: File get() = File(context.filesDir, "raw").apply { mkdirs() }
    val processedDir: File get() = File(context.filesDir, "processed").apply { mkdirs() }
    val shareDir: File
        get() = File(context.cacheDir, "share").apply {
            mkdirs()
            cleanOld()
        }

    fun newRawFile(): File = File(rawDir, "raw_${timestamp()}.wav")

    /** Name for the next saved file, e.g. ViceChanger_2026-10-08_001.wav */
    fun newProcessedFile(displayName: String): File = File(processedDir, displayName)

    fun listProcessed(): List<File> =
        processedDir.listFiles()?.filter { it.extension.lowercase() == "wav" }?.sortedBy { it.name }
            ?: emptyList()

    /** Copy stored processed files into the share cache under a friendly name. */
    fun shareableCopy(source: File, displayName: String): File {
        val target = File(shareDir, displayName)
        source.copyTo(target, overwrite = true)
        return target
    }

    fun shareUriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * Publish a processed file into the shared music library.
     * @return the MediaStore content URI, or null when the platform refused the insert.
     */
    fun saveToMediaStore(source: File, displayName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.TITLE, displayName.substringBeforeLast('.'))
            put(MediaStore.Audio.Media.MIME_TYPE, MIME_WAV)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
            // minSdk is 29, so the scoped-storage columns are always available.
            put(
                MediaStore.Audio.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MUSIC}/$MEDIA_FOLDER",
            )
        }
        return runCatching {
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: return null
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.getOrNull()
    }

    fun delete(file: File): Boolean = file.delete()

    fun timestamp(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

    /** First free index for today, based on the names already on disk. */
    fun nextIndexFor(date: LocalDate, existing: List<String> = listProcessed().map { it.name }): Int =
        nextIndex(existing, date)

    private fun cleanOld() {
        runCatching {
            val cutoff = System.currentTimeMillis() - SHARE_TTL_MS
            shareDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        }
    }

    companion object {
        const val FILE_PREFIX = "ViceChanger"
        const val MEDIA_FOLDER = "VICECHANGER"
        const val MIME_WAV = "audio/x-wav"
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private const val SHARE_TTL_MS = 24L * 60L * 60L * 1000L

        /** Name for the next saved file, e.g. ViceChanger_2026-10-08_001.wav */
        fun buildFileName(date: LocalDate, index: Int, prefix: String = FILE_PREFIX): String =
            "%s_%s_%03d.wav".format(prefix, date.format(DATE_FORMAT), index)

        /**
         * Index of the next file for [date]. Pure function: given the file names already in
         * the folder it returns one past the highest index used for that day.
         */
        fun nextIndex(existing: List<String>, date: LocalDate, prefix: String = FILE_PREFIX): Int {
            val day = date.format(DATE_FORMAT)
            val used = existing.mapNotNull { name ->
                val match = Regex("^%s_%s_(\\d{3})\\.wav$".format(prefix, day)).find(name)
                match?.groupValues?.get(1)?.toIntOrNull()
            }
            return (used.maxOrNull() ?: 0) + 1
        }
    }
}
