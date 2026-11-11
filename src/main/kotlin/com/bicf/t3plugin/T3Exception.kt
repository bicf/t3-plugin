package com.bicf.t3plugin

/**
 * Error of the T3 integration. [reason] lets callers react without parsing the message.
 * The message never contains a pairing token.
 */
class T3Exception(val reason: Reason, message: String, cause: Throwable? = null) : Exception(message, cause) {

    enum class Reason {
        /** The `t3` command is not in `<home>/bin`, `~/.t3/bin` or `PATH`. */
        CLI_NOT_FOUND,

        /** The operating system did not start the `t3` command. */
        CLI_START_FAILED,

        /** `t3 serve` stopped before a server answered. */
        SERVER_EXITED,

        /** Another process owns the T3 home (exit 78), but its server did not answer in time. */
        SERVER_OWNED_ELSEWHERE,

        /** No server answered before the start timeout. */
        SERVER_TIMEOUT,

        /** `t3 auth pairing create` stopped with an error, or did not stop in time. */
        PAIRING_FAILED,

        /** `t3 auth pairing create --json` printed no usable pairing link. */
        PAIRING_BAD_OUTPUT,
    }
}
