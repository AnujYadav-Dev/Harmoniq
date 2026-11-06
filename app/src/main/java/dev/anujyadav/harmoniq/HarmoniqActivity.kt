package dev.anujyadav.harmoniq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import dev.anujyadav.harmoniq.services.radio.Radio
import dev.anujyadav.harmoniq.ui.theme.SplashTheme
import dev.anujyadav.harmoniq.ui.view.BaseView
import dev.anujyadav.harmoniq.utils.EventUnsubscribeFn
import dev.anujyadav.harmoniq.utils.Logger

class HarmoniqActivity : ComponentActivity() {
    private var gSymphony: Symphony? = null
    private var unsubscribeFromRadio: EventUnsubscribeFn? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // must run before super.onCreate, it also swaps in the post-splash theme
        val ignition: ActivityIgnition by viewModels()
        installSplashScreen().setKeepOnScreenCondition { !ignition.ready.value }

        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler { _, err ->
            ignition.emitReady()
            Logger.error("HarmoniqActivity", "uncaught exception", err)
            ErrorActivity.start(this, err)
            finish()
        }

        val symphony: Symphony by viewModels()
        symphony.permission.handle(this)
        gSymphony = symphony
        symphony.emitActivityReady()

        // only on a fresh start, not when the activity is recreated from saved state
        if (savedInstanceState == null) {
            SplashTheme.startSession(this, symphony.settings)
        }
        attachHandlers()

        enableEdgeToEdge()
        setContent {
            LaunchedEffect(LocalContext.current) {
                ignition.emitReady()
            }
            BaseView(symphony = symphony, activity = this)
        }
    }

    override fun onPause() {
        super.onPause()
        gSymphony?.let {
            // picks up appearance changes made during this session
            SplashTheme.registerForNextLaunch(this, it.settings)
            it.emitActivityPause()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            applyChosenAppIcon()
        }
    }

    override fun onDestroy() {
        unsubscribeFromRadio?.invoke()
        super.onDestroy()
        gSymphony?.emitActivityDestroy()
    }

    private fun attachHandlers() {
        gSymphony?.closeApp = {
            finish()
        }
        // stopped from the notification while away: there may be no "leaving the app" after this
        unsubscribeFromRadio = gSymphony?.radio?.onUpdate?.subscribe { event ->
            if (event == Radio.Events.Player.Ended && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                applyChosenAppIcon()
            }
        }
    }

    // waits for a moment with no music, as switching closes the app, see AppIcon.applyIfNeeded
    private fun applyChosenAppIcon() {
        gSymphony?.let {
            if (!it.radio.isPlaying) {
                it.settings.appIcon.value.applyIfNeeded(applicationContext)
            }
        }
    }
}
