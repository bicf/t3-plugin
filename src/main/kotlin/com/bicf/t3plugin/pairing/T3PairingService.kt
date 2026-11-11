package com.bicf.t3plugin.pairing

import com.bicf.t3plugin.T3Exception
import com.bicf.t3plugin.T3Exception.Reason
import com.bicf.t3plugin.server.T3Cli
import com.bicf.t3plugin.server.T3Home
import com.bicf.t3plugin.server.T3Runtime
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.Service
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.time.Duration

/**
 * Creates one-time pairing links with `t3 auth pairing create`.
 *
 * Rules:
 * - A link is valid for [PAIRING_TTL] and for one exchange only.
 * - Each exchange creates a session on the server. The caller asks for a link only when `GET /api/auth/session` fails.
 * - The service accepts only a link on the origin of the server.
 */
@Service(Service.Level.APP)
class T3PairingService {

    /**
     * Creates a pairing link for [runtime]. [label] appears in `t3 auth session list`.
     * Blocks for up to 60 s.
     *
     * @throws T3Exception when the `t3` command is missing or does not print a link.
     */
    @RequiresBackgroundThread
    fun createPairingLink(runtime: T3Runtime, label: String): PairingLink {
        val home = T3Home.detect()
        val cli = T3Cli.detect(home) ?: throw T3Exception(
            Reason.CLI_NOT_FOUND,
            "The `t3` command is not in ${home.dir.resolve("bin")}, ~/.t3/bin or PATH.",
        )
        return runPairingCreate(cli, runtime.origin, label)
    }

    companion object {
        /** Label for a session of this IDE, for example "JetBrains IntelliJ IDEA – t3-plugin". */
        fun labelFor(projectName: String): String =
            "JetBrains ${ApplicationNamesInfo.getInstance().fullProductName} – $projectName"
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
        val detail = result.stderr.lineSequence().lastOrNull { it.isNotBlank() }?.trim()?.take(300).orEmpty()
        throw T3Exception(Reason.PAIRING_FAILED, "`t3 auth pairing create` stopped with exit code $exitCode. $detail".trim())
    }
    // Never put stdout in a message or a log: it contains the token.
    return PairingLink.parse(result.stdout, origin) ?: throw T3Exception(
        Reason.PAIRING_BAD_OUTPUT,
        "`t3 auth pairing create --json` did not print a pairing link for $origin.",
    )
}
