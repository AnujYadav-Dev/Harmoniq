package dev.anujyadav.harmoniq.services.globalsearch

import dev.anujyadav.harmoniq.services.globalsearch.api.ErrorCodes
import dev.anujyadav.harmoniq.services.globalsearch.api.ServerError
import dev.anujyadav.harmoniq.services.globalsearch.connection.ConnectionState

/** A call that needs the server, made while none is configured. */
class ServerNotConfigured : Exception("Global Search is not configured")

/** Whether the server can be used right now, and if not, why. */
sealed interface GlobalSearchAvailability {
    data object Available : GlobalSearchAvailability

    // no server address or token, or a custom one that isn't valid
    data object NotConfigured : GlobalSearchAvailability

    // the config file turned the default server off, e.g. for maintenance
    data class Paused(val message: String?) : GlobalSearchAvailability

    // connection errors: server down, wrong address, or the phone is offline
    data object Unreachable : GlobalSearchAvailability

    // the server answered, but doesn't accept the token
    data object Rejected : GlobalSearchAvailability

    val isAvailable get() = this == Available

    // a later attempt could succeed without the user changing anything
    val canRecover get() = this == Unreachable || this == Rejected

    companion object {
        fun from(connection: ConnectionState, meta: GlobalSearch.MetaState) = when (connection) {
            ConnectionState.NotConfigured -> NotConfigured
            is ConnectionState.Disabled -> Paused(connection.message)
            is ConnectionState.Ready -> when (val error = (meta as? GlobalSearch.MetaState.Failed)?.error) {
                is ServerError.Network -> Unreachable
                is ServerError.Problem -> if (error.code == ErrorCodes.UNAUTHORIZED) Rejected else Available
                else -> Available
            }
        }
    }
}
