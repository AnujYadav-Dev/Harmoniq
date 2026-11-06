package dev.anujyadav.harmoniq.services.groove

import android.net.Uri
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.utils.ActivityUtils
import dev.anujyadav.harmoniq.utils.Logger

/**
 * Gives back the file access Android keeps for us (it is limited per app) once the library
 * no longer uses it.
 *
 * Whatever takes a grant has to save the media folder or playlist using it first: grants are
 * read before what uses them here, so a grant seen here always has its use saved already.
 */
class UriGrants(private val symphony: Symphony) {
    suspend fun releaseUnused() {
        try {
            val context = symphony.applicationContext
            val granted = context.contentResolver.persistedUriPermissions.map { it.uri }
            val inUse = symphony.settings.mediaFolders.value +
                    // not the loaded playlists: one that failed to load still needs its file
                    symphony.database.playlists.entries().values.mapNotNull { it.uri }
            findUnused(granted, inUse).forEach { uri ->
                runCatching { ActivityUtils.releasePersistableReadableUri(context, uri) }
                    .onFailure { Logger.warn("UriGrants", "releasing $uri failed", it) }
            }
        } catch (err: Exception) {
            // without knowing what is in use, nothing is safe to release
            Logger.error("UriGrants", "release unused failed", err)
        }
    }

    companion object {
        internal fun findUnused(granted: List<Uri>, inUse: Set<Uri>) = granted.filterNot { it in inUse }
    }
}
