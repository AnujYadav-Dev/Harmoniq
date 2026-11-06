package dev.anujyadav.harmoniq

import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HarmoniqApplicationTest {
    private val application = ApplicationProvider.getApplicationContext<HarmoniqApplication>()

    @Test
    fun startingTheProcessDoesNotCreateSymphony() {
        assertNull(application.symphonyIfCreated)
    }

    @Test
    fun thereIsOneSymphony() {
        assertSame(application.symphony, application.symphony)
    }

    @Test
    fun symphonyIsRefusedOffTheMainThread() {
        var failure: Throwable? = null
        Thread { failure = runCatching { application.symphony }.exceptionOrNull() }
            .apply { start() }
            .join()
        assertTrue(failure is IllegalStateException)
        assertNull(application.symphonyIfCreated)
    }

    @Test
    fun aReleasedSymphonyIsReplaced() {
        val first = application.symphony
        first.emitReady()
        application.releaseSymphony()
        assertNull(application.symphonyIfCreated)
        assertNotSame(first, application.symphony)
    }

    @Test
    fun onlyAStartedActivityCountsAsVisible() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        assertNull(application.visibleActivity)
        controller.start()
        assertSame(controller.get(), application.visibleActivity)
        controller.stop()
        assertNull(application.visibleActivity)
    }

    @Test
    fun aCrashWithNothingVisibleGoesToThePreviousHandler() {
        var received: Throwable? = null
        val error = IllegalStateException("crash")
        AppCrashHandler(application) { _, err -> received = err }
            .uncaughtException(Thread.currentThread(), error)
        assertSame(error, received)
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun aCrashInUseShowsTheErrorScreenInstead() {
        val symphony = application.symphony
        symphony.emitReady()
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create().start()
        var previousCalled = false
        AppCrashHandler(application) { _, _ -> previousCalled = true }
            .uncaughtException(Thread.currentThread(), IllegalStateException("crash"))
        assertFalse(previousCalled)
        assertTrue(controller.get().isFinishing)
        // nothing outlives the error screen, opening the app again starts over
        assertNull(application.symphonyIfCreated)
        val started = shadowOf(controller.get()).nextStartedActivity
        assertNotNull(started)
        assertEquals(ErrorActivity::class.java.name, started.component?.className)
    }

    @Test
    fun aCrashOfTheErrorScreenDoesNotOpenItAgain() {
        Robolectric.buildActivity(ErrorActivity::class.java).create().start()
        var previousCalled = false
        AppCrashHandler(application) { _, _ -> previousCalled = true }
            .uncaughtException(Thread.currentThread(), IllegalStateException("crash"))
        assertTrue(previousCalled)
        assertNull(shadowOf(application).nextStartedActivity)
    }
}
