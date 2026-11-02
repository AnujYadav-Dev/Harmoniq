package dev.anujyadav.harmoniq.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Offers to undo actions that make a row disappear from the list being viewed.
 * Screens that support it provide one through [LocalUndoHost].
 */
class UndoHost(
    val snackbarHostState: SnackbarHostState,
    private val coroutineScope: CoroutineScope,
) {
    fun show(message: String, onUndo: () -> Unit) {
        coroutineScope.launch {
            // a newer action replaces the pending one, which stays applied
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = AppStrings.Undo,
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                onUndo()
            }
        }
    }
}

@Composable
fun rememberUndoHost(): UndoHost {
    val coroutineScope = rememberCoroutineScope()
    return remember { UndoHost(SnackbarHostState(), coroutineScope) }
}

val LocalUndoHost = staticCompositionLocalOf<UndoHost?> { null }
