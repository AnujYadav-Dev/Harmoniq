package dev.anujyadav.harmoniq.ui.helpers

import androidx.navigation.NavHostController
import dev.anujyadav.harmoniq.HarmoniqActivity
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.ui.view.navigation.LibraryNavigator

data class ViewContext(
    val symphony: Symphony,
    val activity: HarmoniqActivity,
    val navController: NavHostController,
    val libraryNavigator: LibraryNavigator = LibraryNavigator(),
) {
    companion object {
        fun <T> parameterizedFn(fn: (ViewContext) -> T) = fn
    }
}
