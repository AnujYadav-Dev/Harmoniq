package dev.anujyadav.harmoniq.ui.view.globalsearch

// one value loaded from the server for a sheet or dialog
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
    data class Failed(val error: Throwable) : Loadable<Nothing>

    companion object {
        fun <T> of(result: Result<T>): Loadable<T> = result.fold({ Ready(it) }, { Failed(it) })
    }
}
