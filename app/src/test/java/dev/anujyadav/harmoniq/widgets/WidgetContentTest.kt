package dev.anujyadav.harmoniq.widgets

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.hasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.isLinearProgressIndicator
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import dev.anujyadav.harmoniq.HarmoniqActivity
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.services.playback.PlaybackCommand
import dev.anujyadav.harmoniq.services.playback.PlaybackSnapshot
import dev.anujyadav.harmoniq.services.radio.RadioQueue
import dev.anujyadav.harmoniq.ui.theme.ThemeMode
import dev.anujyadav.harmoniq.utils.AppStrings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetContentTest {
    private val application = ApplicationProvider.getApplicationContext<HarmoniqApplication>()
    private val bar = DpSize(250.dp, 40.dp)
    private val narrowBar = DpSize(180.dp, 40.dp)
    private val full = DpSize(250.dp, 110.dp)
    private val square = DpSize(110.dp, 110.dp)
    private val twoByOne = DpSize(110.dp, 40.dp)
    private val oneByOne = DpSize(40.dp, 40.dp)

    private val song = PlaybackSnapshot.Item("primary:Music/a.mp3", "A song", "An artist")
    private val played = PlaybackSnapshot(song = song, positionMs = 30_000L, durationMs = 60_000L)

    private fun state(snapshot: PlaybackSnapshot = played, isPlaying: Boolean = false) = PlaybackWidgetState(
        snapshot = snapshot,
        isPlaying = isPlaying,
        artwork = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
        appearance = WidgetAppearance(ThemeMode.SYSTEM, true, null),
    )

    private fun player(size: DpSize, state: PlaybackWidgetState, test: GlanceAppWidgetUnitTest.() -> Unit) =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(size)
            setContext(application)
            provideComposable { PlayerContent(state) }
            test()
        }

    private fun GlanceAppWidgetUnitTest.button(description: String) =
        onNode(hasContentDescriptionEqualTo(description))

    private fun GlanceAppWidgetUnitTest.assertSends(description: String, command: PlaybackCommand) {
        button(description).assertExists()
            .assert(sends(command))
    }

    @Test
    fun theBarShowsTheSongAndItsControls() = player(bar, state()) {
        onNode(hasTextEqualTo("A song")).assertExists()
        onNode(hasTextEqualTo("An artist")).assertExists()
        button(AppStrings.ArtworkOfX("A song")).assertExists()
        assertSends(AppStrings.Previous, PlaybackCommand.Previous)
        assertSends(AppStrings.Play, PlaybackCommand.Resume)
        assertSends(AppStrings.Next, PlaybackCommand.Next)
    }

    @Test
    fun theBarLeavesOutTheFullPlayerControls() = player(bar, state()) {
        button(AppStrings.Favorite).assertDoesNotExist()
        button(AppStrings.ShuffleIsOff).assertDoesNotExist()
        button(AppStrings.RepeatIsOff).assertDoesNotExist()
    }

    @Test
    fun aNarrowBarGivesUpPrevious() = player(narrowBar, state()) {
        button(AppStrings.Previous).assertDoesNotExist()
        button(AppStrings.Next).assertExists()
    }

    @Test
    fun whilePlayingTheMainButtonPauses() = player(bar, state(isPlaying = true)) {
        assertSends(AppStrings.Pause, PlaybackCommand.PlayPause)
        button(AppStrings.Play).assertDoesNotExist()
    }

    @Test
    fun theFullPlayerAddsEverythingElse() = player(full, state()) {
        assertSends(AppStrings.Favorite, PlaybackCommand.ToggleFavorite)
        assertSends(AppStrings.ShuffleIsOff, PlaybackCommand.ToggleShuffle)
        assertSends(AppStrings.RepeatIsOff, PlaybackCommand.CycleRepeat)
        assertSends(AppStrings.Previous, PlaybackCommand.Previous)
        assertSends(AppStrings.Next, PlaybackCommand.Next)
        onNode(isLinearProgressIndicator(0.5f)).assertExists()
    }

    @Test
    fun theFullPlayerShowsWhatIsOn() = player(
        full,
        state(played.copy(shuffle = true, loopMode = RadioQueue.LoopMode.Song, isFavorite = true)),
    ) {
        button(AppStrings.ShuffleIsOn).assertExists()
        button(AppStrings.RepeatingSong).assertExists()
        button(AppStrings.Unfavorite).assertExists()
    }

    @Test
    fun repeatingTheQueueIsNamed() = player(full, state(played.copy(loopMode = RadioQueue.LoopMode.Queue))) {
        button(AppStrings.RepeatingQueue).assertExists()
    }

    @Test
    fun progressIsDescribedForScreenReaders() = player(full, state()) {
        button(AppStrings.PlayedXOfY("00:30", "01:00")).assertExists()
    }

    @Test
    fun nothingPlayedOffersToStart() {
        listOf(bar, full).forEach { size ->
            player(size, state(PlaybackSnapshot())) {
                onNode(hasTextEqualTo(AppStrings.NothingIsBeingPlayedRightNow)).assertExists()
                onNode(hasTextEqualTo(AppStrings.WidgetNothingPlayedHint)).assertExists()
                assertSends(AppStrings.Play, PlaybackCommand.Resume)
                assertSends(AppStrings.ShuffleAll, PlaybackCommand.ShuffleAll)
                button(AppStrings.Next).assertDoesNotExist()
                button(AppStrings.Favorite).assertDoesNotExist()
            }
        }
    }

    @Test
    fun anywhereElseOpensTheApp() {
        val open = hasStartActivityClickAction(Intent(application, HarmoniqActivity::class.java))
        player(bar, state()) { onNode(open).assertExists() }
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(square)
            setContext(application)
            provideComposable { AlbumArtContent(state()) }
            onNode(open).assertExists()
        }
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(twoByOne)
            setContext(application)
            provideComposable { QuickActionsContent(state()) }
            onNode(open).assertExists()
        }
    }

    @Test
    fun albumArtIsTheArtworkAndAPlayButton() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(square)
        setContext(application)
        provideComposable { AlbumArtContent(state()) }
        button(AppStrings.ArtworkOfX("A song")).assertExists()
        assertSends(AppStrings.Play, PlaybackCommand.Resume)
        onNode(hasTextEqualTo("A song")).assertDoesNotExist()
    }

    @Test
    fun albumArtWithNothingPlayedStillDescribesItself() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(square)
        setContext(application)
        provideComposable { AlbumArtContent(state(PlaybackSnapshot())) }
        button(AppStrings.ArtworkOfX(AppStrings.NothingIsBeingPlayedRightNow)).assertExists()
        assertSends(AppStrings.Play, PlaybackCommand.Resume)
    }

    private fun quick(size: DpSize, state: PlaybackWidgetState, test: GlanceAppWidgetUnitTest.() -> Unit) =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(size)
            setContext(application)
            provideComposable { QuickActionsContent(state) }
            test()
        }

    @Test
    fun twoCellsHoldBothQuickActions() = quick(twoByOne, state()) {
        assertSends(AppStrings.ShuffleAll, PlaybackCommand.ShuffleAll)
        assertSends(AppStrings.Play, PlaybackCommand.Resume)
    }

    @Test
    fun oneCellHoldsPlayPause() = quick(oneByOne, state(isPlaying = true)) {
        assertSends(AppStrings.Pause, PlaybackCommand.PlayPause)
        button(AppStrings.ShuffleAll).assertDoesNotExist()
    }

    @Test
    fun oneCellWithNothingPlayedOffersShuffleAll() = quick(oneByOne, state(PlaybackSnapshot())) {
        assertSends(AppStrings.ShuffleAll, PlaybackCommand.ShuffleAll)
        button(AppStrings.Play).assertDoesNotExist()
    }
}
