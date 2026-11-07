package dev.anujyadav.harmoniq.widgets

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.hasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import dev.anujyadav.harmoniq.HarmoniqActivity
import dev.anujyadav.harmoniq.HarmoniqApplication
import dev.anujyadav.harmoniq.services.playback.LyricsWidgetContent
import dev.anujyadav.harmoniq.services.playback.LyricsWidgetContent.Kind
import dev.anujyadav.harmoniq.services.playback.LyricsWidgetStore
import dev.anujyadav.harmoniq.ui.theme.ThemeMode
import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.utils.Eventer
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LyricsWidgetContentTest {
    private val application = ApplicationProvider.getApplicationContext<HarmoniqApplication>()

    private fun widget(
        content: LyricsWidgetContent,
        isEnabled: Boolean = true,
        size: DpSize = LyricsWidgetSizes.Wide,
        test: GlanceAppWidgetUnitTest.() -> Unit,
    ) = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(size)
        setContext(application)
        val state = LyricsWidgetState(content, isEnabled, WidgetAppearance(ThemeMode.SYSTEM, true, null))
        provideComposable { LyricsContent(state) }
        test()
    }

    private fun GlanceAppWidgetUnitTest.shows(text: String) = onNode(hasTextEqualTo(text)).assertExists()

    @Test
    fun syncedShowsThreeLines() = widget(LyricsWidgetContent(Kind.Synced, "Song", "One", "Two", "Three")) {
        shows("One")
        shows("Two")
        shows("Three")
    }

    private val artwork = AppStrings.ArtworkOfX("Song")
    private val full = LyricsWidgetContent(Kind.Synced, "Song", "One", "Two", "Three", songArtist = "Artist")

    @Test
    fun theCoverFillsTheRoomOfAWideWidget() = widget(full) {
        onNode(hasContentDescriptionEqualTo(artwork)).assertExists()
        onNode(hasTextEqualTo("Song")).assertDoesNotExist()
    }

    @Test
    fun aNarrowWidgetIsOnlyTheWords() = widget(full, size = LyricsWidgetSizes.Narrow) {
        onNode(hasContentDescriptionEqualTo(artwork)).assertDoesNotExist()
        shows("Two")
    }

    @Test
    fun aLargeWidgetNamesTheSong() = widget(full, size = LyricsWidgetSizes.Large) {
        shows("Song")
        onNode(hasTextEqualTo("Artist")).assertDoesNotExist()
        shows("Two")
    }

    @Test
    fun aHugeWidgetNamesTheArtistToo() = widget(full, size = LyricsWidgetSizes.Huge) {
        shows("Song")
        shows("Artist")
    }

    @Test
    fun withoutASongThereIsNoCover() = widget(LyricsWidgetContent(), size = LyricsWidgetSizes.Huge) {
        onNode(hasContentDescriptionEqualTo(artwork)).assertDoesNotExist()
        shows(AppStrings.LyricsWidgetIdle)
    }

    @Test
    fun aPauseInTheSongIsShownAsSuch() = widget(LyricsWidgetContent(Kind.Synced, "Song", "One", null, "Three")) {
        shows(AppStrings.LyricsPause)
    }

    @Test
    fun unsyncedIsCalm() = widget(LyricsWidgetContent(Kind.Unsynced, "Song")) {
        shows("Song")
        shows(AppStrings.LyricsAreNotSynced)
    }

    @Test
    fun missingIsCalm() = widget(LyricsWidgetContent(Kind.Missing, "Song")) {
        shows("Song")
        shows(AppStrings.NoLyrics)
    }

    @Test
    fun idleSaysWhenItWillShow() = widget(LyricsWidgetContent()) {
        shows(AppStrings.LyricsWidgetIdle)
    }

    @Test
    fun turnedOffSaysWhereToTurnItOn() = widget(
        LyricsWidgetContent(Kind.Synced, "Song", "One", "Two", "Three"),
        isEnabled = false,
    ) {
        shows(AppStrings.LyricsWidgetIsOff)
        onNode(hasTextEqualTo("Two")).assertDoesNotExist()
    }

    @Test
    fun tappingOpensTheApp() = widget(LyricsWidgetContent()) {
        onNode(hasStartActivityClickAction(Intent(application, HarmoniqActivity::class.java))).assertExists()
    }

    @Test
    fun itIsOffByDefault() {
        assertFalse(application.symphony.settings.lyricsWidgetEnabled.value)
    }

    @Test
    fun theWidgetReadsTheSettingWithoutSymphony() = runBlocking {
        assertFalse(LyricsWidgetState.observe(application).first().isEnabled)
        assertNull(application.symphonyIfCreated)
    }

    @Test
    fun theWidgetFollowsTheSetting() = runBlocking {
        application.symphony.settings.lyricsWidgetEnabled.setValue(true)
        assertTrue(LyricsWidgetState.observe(application).first().isEnabled)
    }

    @Test
    fun aFirstWidgetDoesNotCreateSymphony() {
        LyricsReceiver().onEnabled(application)
        assertNull(application.symphonyIfCreated)
    }

    @Test
    fun itIsDeclaredWithAPickerEntry() {
        val info = application.packageManager.getReceiverInfo(
            ComponentName(application, LyricsReceiver::class.java),
            PackageManager.GET_META_DATA,
        )
        assertFalse(info.exported)
        assertNotNull(application.resources.getXml(info.metaData.getInt("android.appwidget.provider")))
    }

    @Test
    fun theStoreStartsIdle() {
        assertEquals(Kind.Idle, LyricsWidgetStore.content.value.kind)
    }

    // the player's timer thread sends these while subscribers come and go
    @Test
    fun eventsSurviveSubscribingFromAnotherThread() {
        val eventer = Eventer<Int>()
        repeat(SUBSCRIBERS) { eventer.subscribe {} }
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        val sender = thread {
            try {
                while (done.count > 0) {
                    eventer.dispatch(1)
                }
            } catch (err: Throwable) {
                failure = err
            }
        }
        repeat(SUBSCRIBE_ROUNDS) { eventer.subscribe {}() }
        done.countDown()
        sender.join()
        assertNull(failure)
    }

    private companion object {
        const val SUBSCRIBERS = 8
        const val SUBSCRIBE_ROUNDS = 5_000
    }
}
