package com.bicf.t3plugin.project

import com.bicf.t3plugin.T3Exception
import com.bicf.t3plugin.server.T3Cli
import com.bicf.t3plugin.server.T3Home
import com.bicf.t3plugin.toolWindow.T3Routes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.nio.file.Files
import java.time.Duration

/**
 * Step 7 of the flow in PLAN.md: registers the IDE project in T3 and finds the page to open.
 *
 * Rules:
 * - `t3 project add` with exit 0 adds the project. Exit 1 with `ProjectAlreadyExistsError` means that T3 has the project already: also a success.
 * - The page is the newest thread of the project (`t3 thread list` lists the newest first), or `/` when the project has no thread.
 */
object T3ProjectBinder {

    /**
     * Result of a binding: the path to open on the server, the environment id for thread routes,
     * and the newest thread of the project (the thread of [path], or null when [path] is `/`).
     */
    data class Binding(val path: String, val environmentId: String?, val threadId: String? = null)

    /** Blocks for up to 2 CLI calls. Throws [T3Exception] only when `t3 project add` fails. */
    fun bind(cli: T3Cli, projectPath: String, timeout: Duration = Duration.ofSeconds(60)): Binding {
        val add = cli.run(timeout, "project", "add", projectPath)
        if (!isAddedOrExisting(add)) {
            throw T3Exception(
                T3Exception.Reason.PROJECT_ADD_FAILED,
                "`t3 project add` stopped with exit code ${add.exitCode}. ${add.lastErrorLine}".trim(),
            )
        }
        val environmentId = environmentId(cli.home)
        val list = cli.run(timeout, "thread", "list", "--project", projectPath, "--json")
        val threadId = if (list.exitCode == 0) newestThreadId(list.stdout) else null
        val path = if (environmentId != null && threadId != null) T3Routes.threadPath(environmentId, threadId) else null
        return Binding(path ?: "/", environmentId, threadId.takeIf { path != null })
    }

    internal fun isAddedOrExisting(result: T3Cli.Result): Boolean =
        result.exitCode == 0 || (result.exitCode == 1 && "ProjectAlreadyExistsError" in result.stderr + result.stdout)

    /** The `threadId` of the first element of the `t3 thread list --json` array. */
    internal fun newestThreadId(json: String): String? = runCatching {
        Json.parseToJsonElement(json).jsonArray.firstOrNull()?.jsonObject?.get("threadId")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    /** The id in `userdata/environment-id`, or null when the file is missing or the id is not a safe route segment. */
    fun environmentId(home: T3Home): String? = try {
        Files.readString(home.environmentIdFile).trim().takeIf(T3Routes::isSafeSegment)
    } catch (e: IOException) {
        null
    }
}
