package dev.anujyadav.harmoniq.ui.view.globalsearch

import dev.anujyadav.harmoniq.services.globalsearch.api.PlaylistResultDto
import dev.anujyadav.harmoniq.services.globalsearch.api.SearchResultDto
import dev.anujyadav.harmoniq.services.globalsearch.api.ServerJson
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GlobalSheetPageTest {
    private val playlist = GlobalSheetPage.Playlist(PlaylistResultDto(playlistId = "PL0123456789", title = "Mix"))
    private val preview = GlobalSheetPage.Preview(
        SearchResultDto(videoId = "FGBhQbmPwH8", resultType = "song", title = "One More Time"),
        sourcePlaylistId = "PL0123456789",
    )

    @Test
    fun stackSurvivesSerialization() {
        // what the rememberSaveable Saver stores across rotation and process death
        val serializer = ListSerializer(GlobalSheetPage.serializer())
        val json = ServerJson.encodeToString(serializer, listOf(playlist, preview))
        assertEquals(listOf(playlist, preview), ServerJson.decodeFromString(serializer, json))
    }

    @Test
    fun navigation() {
        val navigator = GlobalSheetNavigator()
        navigator.open(playlist)
        assertFalse(navigator.canGoBack)
        navigator.push(preview)
        assertTrue(navigator.canGoBack)
        assertEquals(preview, navigator.current)
        navigator.back()
        assertEquals(playlist, navigator.current)
        // opening from the list replaces whatever was open
        navigator.open(preview)
        assertEquals(listOf<GlobalSheetPage>(preview), navigator.stack)
        navigator.close()
        assertEquals(null, navigator.current)
    }
}
