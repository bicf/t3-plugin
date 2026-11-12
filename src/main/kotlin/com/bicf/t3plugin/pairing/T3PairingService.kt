package com.bicf.t3plugin.pairing

import com.bicf.t3plugin.T3Exception
import com.bicf.t3plugin.T3Exception.Reason
import com.bicf.t3plugin.server.T3Cli
import com.bicf.t3plugin.server.T3Home
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.security.MessageDigest
import java.time.Duration

/**
 * Creates one-time pairing links with `t3 auth pairing create`.
 *
 * Rules:
 * - A link is valid for [PAIRING_TTL] and for one exchange only.
 * - Each exchange creates a session on the server. The caller asks for a link only when `GET /api/auth/session` fails.
 * - Before a new pairing, the service revokes the old sessions with the same label (see [sessionLabel]).
 * - The service accepts only a link on the origin of the server.
 */
@Service(Service.Level.APP)
class T3PairingService {

    /**
     * Creates a pairing link for the local server at [origin]. [label] appears in `t3 auth session list`.
     * Blocks for up to 3 CLI calls of 60 s each.
     *
     * @throws T3Exception when the `t3` command is missing or does not print a link.
     */
    @RequiresBackgroundThread
    fun createPairingLink(origin: String, label: String): PairingLink {
        val cli = T3Cli.require(T3Home.detect())
        T3Sessions.revokeWithLabel(cli, label)
        return runPairingCreate(cli, origin, label)
    }

    companion object {
        /**
         * Label for the sessions of this IDE installation, for example "JetBrains IntelliJ IDEA (3fa9c1)".
         * All projects of one IDE share one JCEF cookie store, which lives in the system directory of the IDE.
         * The hash of that directory therefore names exactly one cookie store: a session with the same label
         * and no valid cookie is a lost session, and the service can revoke it.
         */
        fun sessionLabel(): String = labelFor(ApplicationNamesInfo.getInstance().fullProductName, PathManager.getSystemPath())

        internal fun labelFor(productName: String, systemPath: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(systemPath.toByteArray(Charsets.UTF_8))
            val hash = digest.take(3).joinToString("") { "%02x".format(it) }
            return "JetBrains $productName ($hash)"
        }
    }
}

/** Validity of a pairing link. The web UI exchanges the token when the page loads. */
const val PAIRING_TTL = "2m"

internal fun runPairingCreate(
    cli: T3Cli,
    origin: String,
    label: String,
    timeout: Duration = Duration.ofSeconds(60),
): PairingLink {
    val result = cli.run(
        timeout,
        "auth", "pairing", "create", "--json", "--ttl", PAIRING_TTL, "--label", label, "--base-url", origin,
    )
    val exitCode = result.exitCode
        ?: throw T3Exception(Reason.PAIRING_FAILED, "`t3 auth pairing create` did not stop within ${timeout.seconds} s.")
    if (exitCode != 0) {
        throw T3Exception(Reason.PAIRING_FAILED, "`t3 auth pairing create` stopped with exit code $exitCode. ${result.lastErrorLine}".trim())
    }
    // Never put stdout in a message or a log: it contains the token.
    return PairingLink.parse(result.stdout, origin) ?: throw T3Exception(
        Reason.PAIRING_BAD_OUTPUT,
        "`t3 auth pairing create --json` did not print a pairing link for $origin.",
    )
}
