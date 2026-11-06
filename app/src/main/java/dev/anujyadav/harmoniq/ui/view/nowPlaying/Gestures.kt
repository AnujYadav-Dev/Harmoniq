package dev.anujyadav.harmoniq.ui.view.nowPlaying

import androidx.lifecycle.Lifecycle
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.ui.view.QueueViewRoute

/**
 * Not resumed means a transition is running. Navigating then could act twice,
 * and a second pop would remove the main screen underneath.
 */
private fun ViewContext.isSettled() =
    navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

internal fun ViewContext.closeNowPlaying() {
    if (isSettled()) {
        navController.popBackStack()
    }
}

internal fun ViewContext.openQueueFromNowPlaying() {
    if (isSettled()) {
        navController.navigate(QueueViewRoute) {
            launchSingleTop = true
        }
    }
}
