package dev.anujyadav.harmoniq.ui.view.navigation

import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Lets other screens open a specific library tab. The library tab consumes the
 * request once it has switched to the requested pill.
 */
class LibraryNavigator {
    private val _pendingPill = MutableStateFlow<LibraryPill?>(null)
    val pendingPill = _pendingPill.asStateFlow()

    fun request(pill: LibraryPill) = _pendingPill.update { pill }

    fun consume(pill: LibraryPill) = _pendingPill.update { if (it == pill) null else it }
}

fun ViewContext.navigateToLibraryPill(pill: LibraryPill) {
    libraryNavigator.request(pill)
    symphony.settings.lastMainTab.setValue(MainTab.LIBRARY)
}
