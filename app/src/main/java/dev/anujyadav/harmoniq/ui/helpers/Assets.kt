package dev.anujyadav.harmoniq.ui.helpers

import android.content.ContentResolver
import android.content.res.Resources
import android.net.Uri
import coil.request.ImageRequest
import dev.anujyadav.harmoniq.R
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.ui.theme.isLight
import dev.anujyadav.harmoniq.ui.theme.toColorSchemeMode

object Assets {
    val placeholderDarkId = R.raw.placeholder_dark
    val placeholderLightId = R.raw.placeholder_light

    private fun getPlaceholderId(isLight: Boolean = false) = when {
        isLight -> placeholderLightId
        else -> placeholderDarkId
    }

    fun getPlaceholderId(symphony: Symphony) = getPlaceholderId(
        isLight = symphony.settings.themeMode.value.toColorSchemeMode(symphony)
            .isLight(),
    )

    fun getPlaceholderUri(symphony: Symphony) = buildUriOfResource(
        symphony.applicationContext.resources,
        getPlaceholderId(symphony),
    )

    fun createPlaceholderImageRequest(symphony: Symphony) =
        ImageRequest.Builder(symphony.applicationContext)
            .data(getPlaceholderUri(symphony))

    private fun buildUriOfResource(resources: Resources, resourceId: Int): Uri {
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(resources.getResourcePackageName(resourceId))
            .appendPath(resources.getResourceTypeName(resourceId))
            .appendPath(resources.getResourceEntryName(resourceId))
            .build()
    }
}
