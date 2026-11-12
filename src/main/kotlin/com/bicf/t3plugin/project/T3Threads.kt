package com.bicf.t3plugin.project

import com.bicf.t3plugin.T3Exception
import com.bicf.t3plugin.T3Exception.Reason
import com.bicf.t3plugin.server.T3Cli
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration

/**
 * Starts T3 threads and sends messages to them with the CLI.
 * The message goes through stdin (`-`), so a long selection never hits the limit of the command line.
 */
object T3Threads {

    /** Runs `t3 thread start --json <projectPath> -` and returns the id of the new thread. */
    fun start(cli: T3Cli, projectPath: String, message: String, timeout: Duration = Duration.ofSeconds(60)): String {
        val result = cli.run(timeout, listOf("thread", "start", "--json", projectPath, "-"), stdin = message)
        requireSuccess(result, "start")
        return threadIdFromStart(result.stdout)
            ?: throw T3Exception(Reason.THREAD_FAILED, "`t3 thread start --json` printed no thread id.")
    }

    /** Runs `t3 thread send <threadId> -`. A busy thread gets the message as its next turn. */
    fun send(cli: T3Cli, threadId: String, message: String, timeout: Duration = Duration.ofSeconds(60)) {
        requireSuccess(cli.run(timeout, listOf("thread", "send", threadId, "-"), stdin = message), "send")
    }

    internal fun threadIdFromStart(json: String): String? = runCatching {
        Json.parseToJsonElement(json).jsonObject["threadId"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun requireSuccess(result: T3Cli.Result, command: String) {
        val exitCode = result.exitCode
            ?: throw T3Exception(Reason.THREAD_FAILED, "`t3 thread $command` did not stop in time.")
        if (exitCode != 0) {
            throw T3Exception(Reason.THREAD_FAILED, "`t3 thread $command` stopped with exit code $exitCode. ${result.lastErrorLine}".trim())
        }
    }
}
