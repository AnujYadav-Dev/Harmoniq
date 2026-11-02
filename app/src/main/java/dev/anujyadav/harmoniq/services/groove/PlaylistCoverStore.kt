package dev.anujyadav.harmoniq.services.groove

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.utils.ImagePreserver
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Custom playlist covers picked by the user. Each is copied (downscaled) into app
 * storage, so it keeps working even if the original image is moved or deleted, and
 * no playlist data (or m3u file) needs to change.
 */
class PlaylistCoverStore(private val symphony: Symphony) {
    private val directory
        get() = File(symphony.applicationContext.filesDir, DIRECTORY).apply { mkdirs() }

    fun get(playlistId: String) = fileOf(playlistId).takeIf { it.exists() }

    fun has(playlistId: String) = fileOf(playlistId).exists()

    /**
     * Replaces the cover of [playlistId] with the image at [uri].
     */
    suspend fun save(playlistId: String, uri: Uri) = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(symphony.applicationContext.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // software bitmaps can be compressed, and large photos are decoded small
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val maxSide = max(info.size.width, info.size.height)
            if (maxSide > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / maxSide
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
        val target = fileOf(playlistId)
        // written aside first, so a failure never leaves a half written cover
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)) {
                "compressing playlist cover failed"
            }
        }
        check(temporary.renameTo(target) || temporary.copyTo(target, overwrite = true).exists()) {
            "storing playlist cover failed"
        }
        temporary.delete()
        bitmap.recycle()
    }

    fun delete(playlistId: String) {
        fileOf(playlistId).delete()
    }

    private fun fileOf(playlistId: String) =
        File(directory, "${playlistId.replace(UNSAFE_FILENAME_CHARS, "_")}.jpg")

    companion object {
        private const val DIRECTORY = "playlist_covers"
        private const val JPEG_QUALITY = 90
        private val MAX_SIDE = ImagePreserver.Quality.High.maxSide!!
        private val UNSAFE_FILENAME_CHARS = Regex("[^A-Za-z0-9._-]")
    }
}
