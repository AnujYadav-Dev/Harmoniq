package dev.anujyadav.harmoniq.services.globalsearch.download

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import dev.anujyadav.harmoniq.services.globalsearch.ServerConfig

/**
 * Connects the downloads folder to the library. Harmoniq only scans the SAF trees in
 * `settings.mediaFolders`, so a saved song shows up only when one of them covers
 * `Download/Harmoniq`.
 */
object LibraryLink {
    const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val PRIMARY_VOLUME = "primary"
    private const val VOLUME_SEPARATOR = ':'
    private const val PATH_SEPARATOR = '/'

    val downloadsRelativePath = "${Environment.DIRECTORY_DOWNLOADS}/${ServerConfig.DOWNLOAD_FOLDER_NAME}"
    val downloadsDocumentId = "$PRIMARY_VOLUME$VOLUME_SEPARATOR$downloadsRelativePath"

    // where the folder picker opens, so granting is a single tap
    val downloadsInitialUri: Uri
        get() = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, downloadsDocumentId)

    fun isCovered(mediaFolders: Collection<Uri>): Boolean = isCoveredByDocumentIds(
        mediaFolders.mapNotNull { uri ->
            uri.takeIf { it.authority == EXTERNAL_STORAGE_AUTHORITY }
                ?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
        },
        downloadsDocumentId,
    )

    /**
     * A tree covers [targetDocumentId] when it is that folder or one of its parents, e.g.
     * `primary:`, `primary:Download` and `primary:Download/Harmoniq` all cover
     * `primary:Download/Harmoniq`. Shared storage paths are case-insensitive.
     */
    fun isCoveredByDocumentIds(treeDocumentIds: Collection<String>, targetDocumentId: String) =
        treeDocumentIds.any { covers(it, targetDocumentId) }

    fun covers(treeDocumentId: String, targetDocumentId: String): Boolean {
        val (treeVolume, treePath) = split(treeDocumentId) ?: return false
        val (targetVolume, targetPath) = split(targetDocumentId) ?: return false
        if (!treeVolume.equals(targetVolume, ignoreCase = true)) return false
        if (treePath.isEmpty()) return true
        return targetPath.equals(treePath, ignoreCase = true) ||
                targetPath.startsWith("$treePath$PATH_SEPARATOR", ignoreCase = true)
    }

    private fun split(documentId: String): Pair<String, String>? {
        val index = documentId.indexOf(VOLUME_SEPARATOR)
        if (index <= 0) return null
        val path = documentId.substring(index + 1).trim(PATH_SEPARATOR)
        return documentId.substring(0, index) to path
    }
}
