package dev.anujyadav.harmoniq.widgets

import android.app.PendingIntent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.services.playback.LyricsWidgetContent
import dev.anujyadav.harmoniq.services.playback.PlaybackCommand
import dev.anujyadav.harmoniq.services.playback.PlaybackSnapshot
import dev.anujyadav.harmoniq.ui.theme.ThemeMode
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Goes through what Android gets: Glance's translation to RemoteViews, on the oldest supported
 * Android as well, which has fewer ways to draw things.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class WidgetRenderTest(useSystemColors: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters
        fun useSystemColors() = listOf(true, false).map { arrayOf<Any>(it) }
    }

    private val appearance = when {
        useSystemColors -> WidgetAppearance(ThemeMode.SYSTEM, true, null)
        else -> WidgetAppearance(ThemeMode.BLACK, false, null)
    }
    private val application = ApplicationProvider.getApplicationContext<HarmoniqApplication>()
    private val song = PlaybackSnapshot.Item("primary:Music/a.mp3", "A song", "An artist")
    private val played = PlaybackSnapshot(song = song, positionMs = 1_000L, durationMs = 2_000L)

    private fun state(snapshot: PlaybackSnapshot = played) = PlaybackWidgetState(
        snapshot,
        isPlaying = false,
        artwork = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888),
        appearance = appearance,
    )

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private fun render(size: DpSize, content: @Composable () -> Unit): RemoteViews = runBlocking {
        GlanceRemoteViews().compose(application, size) {
            WidgetTheme(appearance, application, content)
        }.remoteViews
    }

    private fun inflate(views: RemoteViews) = views.apply(application, FrameLayout(application))

    private fun View.find(description: String): View? {
        if (contentDescription?.toString() == description) {
            return this
        }
        if (this is ViewGroup) {
            for (i in 0 until childCount) {
                getChildAt(i).find(description)?.let { return it }
            }
        }
        return null
    }

    private fun renderAll() {
        listOf(PlaybackSnapshot(), played).forEach { snapshot ->
            val state = state(snapshot)
            listOf(DpSize(180.dp, 40.dp), DpSize(250.dp, 40.dp), DpSize(250.dp, 110.dp), DpSize(400.dp, 300.dp))
                .forEach { size -> assertNotNull(inflate(render(size) { PlayerContent(state) })) }
            assertNotNull(inflate(render(DpSize(110.dp, 110.dp)) { AlbumArtContent(state) }))
            listOf(
                LyricsWidgetContent(),
                LyricsWidgetContent(LyricsWidgetContent.Kind.Missing, "A song"),
                LyricsWidgetContent(LyricsWidgetContent.Kind.Unsynced, "A song"),
                LyricsWidgetContent(LyricsWidgetContent.Kind.Synced, "A song", "One", "Two", "Three"),
                LyricsWidgetContent(LyricsWidgetContent.Kind.Synced, "A song"),
            ).forEach { content ->
                val lyrics = LyricsWidgetState(content, isEnabled = true, appearance)
                assertNotNull(inflate(render(DpSize(250.dp, 110.dp)) { LyricsContent(lyrics) }))
            }
            val listSize = DpSize(250.dp, 180.dp)
            val full = state(
                snapshot.copy(
                    playlists = List(4) { PlaybackSnapshot.PlaylistItem("p$it", "Playlist $it", it) },
                    queue = List(3) { PlaybackSnapshot.Item("q$it", "Song $it", "Artist", queueIndex = it + 1) },
                    suggestions = List(3) { PlaybackSnapshot.Item("s$it", "Song $it", "Artist") },
                )
            )
            listOf(state, full).forEach { listState ->
                assertNotNull(inflate(render(listSize) { PlaylistShortcutsContent(listState) }))
                assertNotNull(inflate(render(listSize) { UpNextContent(listState) }))
                assertNotNull(inflate(render(listSize) { SuggestedContent(listState) }))
            }
            listOf(DpSize(40.dp, 40.dp), DpSize(110.dp, 40.dp))
                .forEach { size -> assertNotNull(inflate(render(size) { QuickActionsContent(state) })) }
        }
    }

    @Test
    @Config(sdk = [28])
    fun everyWidgetRendersOnTheOldestAndroid() = renderAll()

    @Test
    @Config(sdk = [30])
    fun everyWidgetRendersBeforeAndroid12() = renderAll()

    @Test
    @Config(sdk = [34])
    fun everyWidgetRendersOnCurrentAndroid() = renderAll()

    @Test
    @Config(sdk = [34])
    fun buttonsBecomeImmutableForegroundIntentsThatKeepTheirCommand() {
        val view = inflate(render(DpSize(250.dp, 40.dp)) { PlayerContent(state()) })
        val next = view.find(AppStrings.Next)
        assertNotNull(next)
        // Glance puts the click on a wrapper around the icon, which is the whole 40dp target
        var clickable: View? = next
        while (clickable != null && !clickable.hasOnClickListeners()) {
            clickable = clickable.parent as? View
        }
        clickable!!.performClick()
        val started = shadowOf(application).nextStartedService
        assertNotNull(started)
        assertEquals(PlaybackCommand.Next, PlaybackCommand.fromIntent(started))
    }

    @Test
    @Config(sdk = [34])
    fun theCommandsOwnPendingIntentMatchesWhatGlanceBuilds() {
        val pending: PendingIntent = PlaybackCommand.Next.createPendingIntent(application)
        assertTrue(pending.isImmutable)
        assertTrue(shadowOf(pending).isForegroundService)
    }
}
