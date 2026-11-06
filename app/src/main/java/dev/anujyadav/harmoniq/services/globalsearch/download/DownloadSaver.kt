package dev.anujyadav.harmoniq.services.globalsearch.download

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import dev.anujyadav.harmoniq.services.globalsearch.ServerConfig
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SavedFile(val uri: String, val displayName: String, val reused: Boolean)

/** Writes finished downloads to `Download/Harmoniq/`. The bytes are saved exactly as received. */
interface DownloadSaver {
    /**
     * Saves [source] as [fileName]. An existing file with that name and the same content is
     * reused; a different one makes the new file "<name> (2).<ext>", "(3)"…
     */
    suspend fun save(source: File, fileName: String, mimeType: String, sha256: String): SavedFile

    /** A small file next to the song, e.g. its `.lrc`. Never overwrites. */
    suspend fun saveCompanion(bytes: ByteArray, fileName: String): SavedFile

    fun exists(uri: String): Boolean

    /** Creates the folder ahead of the first download, false when that's not possible. */
    fun ensureFolder(): Boolean = runCatching {
        @Suppress("DEPRECATION")
        val folder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            ServerConfig.DOWNLOAD_FOLDER_NAME,
        )
        folder.isDirectory || folder.mkdirs()
    }.getOrDefault(false)

    companion object {
        const val MAX_NAME_ATTEMPTS = 50

        fun create(context: Context): DownloadSaver = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> MediaStoreDownloadSaver(context.applicationContext)
            else -> LegacyDownloadSaver(context.applicationContext)
        }

        // MediaStore renames a file whose extension doesn't match its MIME type ("x.opus.ogg"),
        // so prefer the type the platform derives from the extension
        fun platformMimeType(fileName: String, fallback: String) =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileNames.extensionOf(fileName)) ?: fallback
    }
}

/** Android 10+: MediaStore.Downloads, no storage permission needed for the app's own files. */
@RequiresApi(Build.VERSION_CODES.Q)
class MediaStoreDownloadSaver(private val context: Context) : DownloadSaver {
    private val resolver: ContentResolver get() = context.contentResolver
    private val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    private val relativePath = "${LibraryLink.downloadsRelativePath}/"

    override suspend fun save(source: File, fileName: String, mimeType: String, sha256: String) =
        withContext(Dispatchers.IO) {
            for (attempt in 1..DownloadSaver.MAX_NAME_ATTEMPTS) {
                val name = FileNames.withCollisionSuffix(fileName, attempt)
                val existing = findOwn(name)
                if (existing == null) {
                    return@withContext insert(name, DownloadSaver.platformMimeType(name, mimeType)) { out ->
                        source.inputStream().use { it.copyTo(out) }
                    }
                }
                if (existing.second == source.length() && sameContent(existing.first, sha256)) {
                    return@withContext SavedFile(existing.first.toString(), name, reused = true)
                }
            }
            throw IOException("no free file name for $fileName")
        }

    override suspend fun saveCompanion(bytes: ByteArray, fileName: String) = withContext(Dispatchers.IO) {
        for (attempt in 1..DownloadSaver.MAX_NAME_ATTEMPTS) {
            val name = FileNames.withCollisionSuffix(fileName, attempt)
            if (findOwn(name) == null) {
                return@withContext insert(name, DownloadSaver.platformMimeType(name, GENERIC_MIME_TYPE)) {
                    it.write(bytes)
                }
            }
        }
        throw IOException("no free file name for $fileName")
    }

    override fun exists(uri: String) = runCatching {
        resolver.query(Uri.parse(uri), arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            ?.use { it.count > 0 } == true
    }.getOrDefault(false)

    private fun insert(name: String, mimeType: String, write: (OutputStream) -> Unit): SavedFile {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed for $name")
        try {
            resolver.openOutputStream(uri)?.use(write) ?: throw IOException("cannot open $uri")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (err: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw err
        }
        // MediaStore may still rename on a clash with a file we can't see (e.g. from a previous install)
        return SavedFile(uri.toString(), displayName(uri) ?: name, reused = false)
    }

    // only files this app created are visible without READ permission, which is what we want
    private fun findOwn(name: String): Pair<Uri, Long>? = resolver.query(
        collection,
        arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.SIZE),
        "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
        arrayOf(name, relativePath),
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val id = cursor.getLong(0)
        ContentUris.withAppendedId(collection, id) to cursor.getLong(1)
    }

    private fun sameContent(uri: Uri, sha256: String) = runCatching {
        resolver.openInputStream(uri)?.use { Sha256.of(it) } == sha256
    }.getOrDefault(false)

    private fun displayName(uri: Uri) = runCatching {
        resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    companion object {
        private const val GENERIC_MIME_TYPE = "application/octet-stream"
    }
}

/** Android 9: a plain file in the public Download folder, needs WRITE_EXTERNAL_STORAGE. */
class LegacyDownloadSaver(private val context: Context) : DownloadSaver {
    @Suppress("DEPRECATION")
    private val folder
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            ServerConfig.DOWNLOAD_FOLDER_NAME,
        )

    override suspend fun save(source: File, fileName: String, mimeType: String, sha256: String) =
        withContext(Dispatchers.IO) {
            requireFolder()
            for (attempt in 1..DownloadSaver.MAX_NAME_ATTEMPTS) {
                val target = File(folder, FileNames.withCollisionSuffix(fileName, attempt))
                if (!target.exists()) {
                    copy(source, target)
                    return@withContext SavedFile(Uri.fromFile(target).toString(), target.name, reused = false)
                }
                if (target.length() == source.length() && Sha256.of(target) == sha256) {
                    return@withContext SavedFile(Uri.fromFile(target).toString(), target.name, reused = true)
                }
            }
            throw IOException("no free file name for $fileName")
        }

    override suspend fun saveCompanion(bytes: ByteArray, fileName: String) = withContext(Dispatchers.IO) {
        requireFolder()
        for (attempt in 1..DownloadSaver.MAX_NAME_ATTEMPTS) {
            val target = File(folder, FileNames.withCollisionSuffix(fileName, attempt))
            if (!target.exists()) {
                target.writeBytes(bytes)
                scan(target)
                return@withContext SavedFile(Uri.fromFile(target).toString(), target.name, reused = false)
            }
        }
        throw IOException("no free file name for $fileName")
    }

    override fun exists(uri: String) = Uri.parse(uri).path?.let { File(it).exists() } == true

    private fun requireFolder() {
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("cannot create $folder")
    }

    private fun copy(source: File, target: File) {
        val partial = File(target.parentFile, "${target.name}$PARTIAL_SUFFIX")
        try {
            source.copyTo(partial, overwrite = true)
            if (!partial.renameTo(target)) throw IOException("cannot rename to $target")
        } finally {
            partial.delete()
        }
        scan(target)
    }

    private fun scan(file: File) {
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
    }

    companion object {
        private const val PARTIAL_SUFFIX = ".partial"
    }
}
