package dev.anujyadav.harmoniq.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.service.quicksettings.TileService
import androidx.test.core.app.ApplicationProvider
import dev.anujyadav.harmoniq.HarmoniqActivity
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.services.groove.Playlist
import dev.anujyadav.harmoniq.services.playback.PlaybackCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowShortcutManager
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppShortcutsTest {
    private val application = ApplicationProvider.getApplicationContext<HarmoniqApplication>()
    private val manager = application.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager
    private val playlist = Playlist("mix-id", "Mix", emptyList(), null, null)

    // Robolectric keeps them apart instead of marking them, and has no way to ask
    private fun disabledIds() = ReflectionHelpers
        .getStaticField<Map<String, Any>>(ShadowShortcutManager::class.java, "disabledPinnedShortcuts")
        .keys

    private fun links() = AppShortcuts.launcherShortcuts(application)
        .associate { it.id to AppLink.fromIntent(it.intent) }

    @Test
    fun theFourShortcutsDoWhatTheySay() {
        assertEquals(
            mapOf(
                "continue_playing" to AppLink(command = PlaybackCommand.Resume),
                "shuffle_all" to AppLink(command = PlaybackCommand.ShuffleAll),
                "favorites" to AppLink(AppDestination.Favorites),
                "search" to AppLink(AppDestination.Search),
            ),
            links(),
        )
    }

    @Test
    fun theyGoStraightToTheActivityAndNotAnAlias() {
        AppShortcuts.launcherShortcuts(application).forEach {
            assertEquals(HarmoniqActivity::class.java.name, it.intent.component?.className)
        }
    }

    @Test
    fun publishingIsSafeToRepeat() {
        AppShortcuts.publish(application)
        AppShortcuts.publish(application)
        assertEquals(4, manager.dynamicShortcuts.size)
    }

    @Test
    fun aLauncherWithoutPinSupportIsReported() {
        shadowOf(manager).setIsRequestPinShortcutSupported(false)
        assertEquals(AppShortcuts.PinResult.NotSupported, AppShortcuts.pinPlaylist(application, playlist))
    }

    @Test
    fun aPinnedPlaylistOpensAndPlays() {
        shadowOf(manager).setIsRequestPinShortcutSupported(true)
        assertEquals(AppShortcuts.PinResult.Requested, AppShortcuts.pinPlaylist(application, playlist))
        val pinned = manager.pinnedShortcuts.single()
        assertEquals(AppShortcuts.playlistShortcutId("mix-id"), pinned.id)
        assertEquals("Mix", pinned.shortLabel)
        assertEquals(
            AppLink(AppDestination.Playlist("mix-id"), PlaybackCommand.PlayPlaylist("mix-id")),
            AppLink.fromIntent(pinned.intent),
        )
    }

    @Test
    fun aDeletedPlaylistsShortcutIsDisabled() {
        shadowOf(manager).setIsRequestPinShortcutSupported(true)
        AppShortcuts.pinPlaylist(application, playlist)
        AppShortcuts.onPlaylistDeleted(application, "mix-id")
        assertTrue(disabledIds().single().startsWith("playlist_"))
    }

    @Test
    fun aRenameReachesAPinnedShortcut() {
        shadowOf(manager).setIsRequestPinShortcutSupported(true)
        AppShortcuts.pinPlaylist(application, playlist)
        AppShortcuts.onPlaylistRenamed(application, playlist.copy(title = "New name"))
        assertEquals("New name", manager.pinnedShortcuts.single().shortLabel)
    }

    @Test
    fun renamingWhatIsNotPinnedPinsNothing() {
        AppShortcuts.onPlaylistRenamed(application, playlist)
        assertTrue(manager.pinnedShortcuts.isEmpty())
    }

    @Test
    fun deletingFromTheRepositoryDisablesTheShortcut() {
        shadowOf(manager).setIsRequestPinShortcutSupported(true)
        val repository = application.symphony.groove.playlist
        val created = repository.create("Road trip", emptyList())
        repository.add(created)
        AppShortcuts.pinPlaylist(application, created)
        repository.delete(created.id)
        assertTrue(disabledIds().single().startsWith("playlist_"))
    }

    @Test
    fun everyDestinationSurvivesAnIntent() {
        listOf(
            AppDestination.Search,
            AppDestination.Favorites,
            AppDestination.Settings,
            AppDestination.Playlist("id:with:colons"),
        ).forEach {
            assertEquals(it, AppLink.fromIntent(AppLink(it).createIntent(application))?.destination)
        }
    }

    @Test
    fun aCommandWithArgumentsSurvivesAnIntent() {
        val command = PlaybackCommand.PlaySong("primary:Music/Rock & Roll/é?#.mp3")
        assertEquals(command, AppLink.fromIntent(AppLink(command = command).createIntent(application))?.command)
    }

    @Test
    fun comingBackThroughRecentsDoesNothingAgain() {
        val intent = AppLink(AppDestination.Search, PlaybackCommand.ShuffleAll).createIntent(application)
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(AppLink.fromIntent(intent))
    }

    @Test
    fun theLauncherOwnIntentIsNoLink() {
        assertNull(AppLink.fromIntent(null))
        assertNull(AppLink.fromIntent(Intent(Intent.ACTION_MAIN)))
    }

    @Test
    fun longPressingTheTileOpensSettings() {
        assertEquals(
            AppLink(AppDestination.Settings),
            AppLink.fromIntent(Intent(TileService.ACTION_QS_TILE_PREFERENCES)),
        )
    }

    @Test
    fun anUnknownDestinationIsIgnored() {
        assertNull(AppDestination.fromKey("nope"))
        assertNull(AppDestination.fromKey(null))
    }
}
