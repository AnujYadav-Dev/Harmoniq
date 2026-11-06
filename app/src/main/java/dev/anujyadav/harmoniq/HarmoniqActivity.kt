package dev.anujyadav.harmoniq

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.anujyadav.harmoniq.services.radio.RadioNotificationService
import dev.anujyadav.harmoniq.shortcuts.AppDestination
import dev.anujyadav.harmoniq.shortcuts.AppLink
import dev.anujyadav.harmoniq.shortcuts.AppShortcuts
import dev.anujyadav.harmoniq.ui.theme.SplashTheme
import dev.anujyadav.harmoniq.ui.view.BaseView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class HarmoniqActivity : ComponentActivity() {
    private val ignition: ActivityIgnition by viewModels()
    private var gSymphony: Symphony? = null
    private val closeApp = { finish() }
    private val _pendingDestination = MutableStateFlow<AppDestination?>(null)

    /** Somewhere a shortcut asked to go. Whoever goes there calls [onDestinationOpened]. */
    internal val pendingDestination = _pendingDestination.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        // must run before super.onCreate, it also swaps in the post-splash theme
        installSplashScreen().setKeepOnScreenCondition { !ignition.ready.value }

        super.onCreate(savedInstanceState)

        val symphony = (application as HarmoniqApplication).symphony
        symphony.permission.handle(this)
        gSymphony = symphony
        symphony.emitActivityReady(isFreshStart = savedInstanceState == null)

        // only on a fresh start, not when the activity is recreated from saved state
        if (savedInstanceState == null) {
            SplashTheme.startSession(this, symphony.settings)
            AppShortcuts.publish(this)
            handleLink(intent)
        }
        symphony.closeApp = closeApp

        enableEdgeToEdge()
        setContent {
            LaunchedEffect(LocalContext.current) {
                ignition.emitReady()
            }
            BaseView(symphony = symphony, activity = this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent?) {
        val link = AppLink.fromIntent(intent) ?: return
        link.command?.let { RadioNotificationService.start(this, it) }
        link.destination?.let { destination -> _pendingDestination.value = destination }
    }

    internal fun onDestinationOpened() {
        _pendingDestination.value = null
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
            gSymphony?.applyChosenAppIcon()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gSymphony?.let {
            // Symphony lives on: it must not keep this activity alive through these
            if (it.closeApp === closeApp) {
                it.closeApp = null
            }
            it.emitActivityDestroy()
            // finished for good, like going back on older Android: music keeps going, but
            // paused music has nothing left to bring it back apart from opening the app again
            if (isFinishing) {
                it.radio.stopIfIdle()
            }
        }
        gSymphony = null
    }

    /** See [AppCrashHandler], the error screen takes over from here. */
    internal fun onUncaughtCrash() {
        gSymphony = null
        ignition.emitReady()
        finish()
    }
}
