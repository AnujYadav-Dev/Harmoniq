package dev.anujyadav.harmoniq.ui.helpers

import androidx.navigation.NavHostController
import dev.anujyadav.harmoniq.MainActivity
import dev.anujyadav.harmoniq.Symphony

data class ViewContext(
    val symphony: Symphony,
    val activity: MainActivity,
    val navController: NavHostController,
) {
    companion object {
        fun <T> parameterizedFn(fn: (ViewContext) -> T) = fn
    }
}
