package com.bicf.t3plugin.server

/** State of the T3 server, for the status bar. */
sealed interface T3ServerState {

    /** No check has run yet. */
    data object Unknown : T3ServerState

    /** A local server answers at [origin]. */
    data class Running(val origin: String) : T3ServerState

    /** No local server answers. */
    data object Stopped : T3ServerState

    /** The last connection attempt failed with [message]. The state stays until a server answers. */
    data class Failed(val message: String) : T3ServerState

    /** Settings point to a remote server at [origin]. [reachable] is the result of a TCP check. */
    data class Remote(val origin: String, val reachable: Boolean) : T3ServerState
}

/**
 * The next state of the periodic check.
 * A [T3ServerState.Failed] state stays while no local server answers, so the user still sees the error.
 */
internal fun nextServerState(
    previous: T3ServerState,
    remoteOrigin: String?,
    remoteReachable: (String) -> Boolean,
    reachableLocalOrigin: () -> String?,
): T3ServerState {
    if (remoteOrigin != null) return T3ServerState.Remote(remoteOrigin, remoteReachable(remoteOrigin))
    reachableLocalOrigin()?.let { return T3ServerState.Running(it) }
    return previous as? T3ServerState.Failed ?: T3ServerState.Stopped
}
