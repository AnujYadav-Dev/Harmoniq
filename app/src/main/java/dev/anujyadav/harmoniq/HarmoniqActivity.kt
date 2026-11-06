package dev.anujyadav.harmoniq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.anujyadav.harmoniq.ui.theme.SplashTheme
import dev.anujyadav.harmoniq.ui.view.BaseView

class HarmoniqActivity : ComponentActivity() {
    private val ignition: ActivityIgnition by viewModels()
    private var gSymphony: Symphony? = null
    private val closeApp = { finish() }

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
