package com.bicf.t3plugin.project

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.time.Instant

/**
 * The projects and threads of the T3 server, as the T3 sidebar receives them (see
 * [com.bicf.t3plugin.toolWindow.T3ShellWatch]). One model serves all the IDE windows.
 *
 * Rules:
 * - A snapshot adds its projects and threads and reports no change. The first snapshot starts the watch: before it,
 *   no update reports a change.
 * - [Change.ProjectAdded]: a project that the model does not know yet ("Add existing project", "New project",
 *   "Turn into project", or `t3 project add`).
 * - [Change.WorktreeCreated]: a thread of the user gets a worktree path that it did not have (a new thread in
 *   "New worktree" mode). Threads of an agent (subagent, fork, delegated or MCP thread) are not reported.
 *   The snapshot has no older settled thread: a thread that the model does not know is reported only when it was created
 *   after the first snapshot ([Thread.createdAt]). Otherwise its first update would look like a new worktree.
 * - Each change is reported once, also when several IDE windows send the same update.
 * Not thread-safe: the caller synchronizes.
 */
class T3ShellModel(private val clock: () -> Instant = Instant::now) {

    data class Project(val id: String, val root: String)

    /**
     * [userCreated]: a top-level thread that the user started in a T3 client (web or mobile).
     * [createdAt]: the creation time of the thread, null when T3 does not send a valid one.
     */
    data class Thread(
        val id: String,
        val projectId: String,
        val worktreePath: String?,
        val userCreated: Boolean,
        val createdAt: Instant? = null,
    )

    /** One message of the shell watch: a snapshot, or the projects and threads that changed. */
    data class Update(val snapshot: Boolean, val projects: List<Project>, val threads: List<Thread>)

    sealed interface Change {
        data class ProjectAdded(val project: Project) : Change
        data class WorktreeCreated(val thread: Thread, val projectRoot: String?) : Change
    }

    private val projects = HashMap<String, Project>()
    private val threads = HashMap<String, Thread>()
    private var started = false

    /** The time of the first snapshot. */
    private var startedAt: Instant? = null

    fun apply(update: Update): List<Change> {
        val report = started && !update.snapshot
        if (update.snapshot && !started) {
            started = true
            startedAt = clock()
        }
        val changes = ArrayList<Change>()
        update.projects.forEach { project ->
            if (projects.put(project.id, project) == null && report) changes += Change.ProjectAdded(project)
        }
        update.threads.forEach { thread ->
            val old = threads.put(thread.id, thread)
            val path = thread.worktreePath
            val createdLater = thread.createdAt?.let { created -> startedAt?.let { !created.isBefore(it) } } == true
            val known = old != null || createdLater
            if (report && known && thread.userCreated && path != null && old?.worktreePath != path) {
                changes += Change.WorktreeCreated(thread, projects[thread.projectId]?.root)
            }
        }
        return changes
    }

    fun thread(id: String): Thread? = threads[id]

    fun projectRoot(projectId: String): String? = projects[projectId]?.root

    /** The newest known thread with the worktree [path] (the model keeps no time: any thread of the path). */
    fun threadInWorktree(path: String, same: (String, String) -> Boolean): Thread? =
        threads.values.firstOrNull { thread -> thread.worktreePath?.let { same(it, path) } == true }

    companion object {
        private const val MAX_ID = 200
        private const val MAX_PATH = 4096

        /**
         * Reads a message of the shell watch:
         * `{"snap":true?,"p":[[id,root],…],"t":[[id,projectId,worktree|null,user,createdAt|null],…]}`.
         * Returns null for invalid JSON. Skips each record with a missing or too long field.
         */
        fun parse(json: String): Update? {
            val obj = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return null
            val snapshot = (obj["snap"] as? JsonPrimitive)?.booleanOrNull == true
            val projects = (obj["p"] as? JsonArray).orEmpty().mapNotNull { record ->
                val fields = record as? JsonArray ?: return@mapNotNull null
                val id = fields.text(0, MAX_ID) ?: return@mapNotNull null
                val root = fields.text(1, MAX_PATH)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                Project(id, root)
            }
            val threads = (obj["t"] as? JsonArray).orEmpty().mapNotNull { record ->
                val fields = record as? JsonArray ?: return@mapNotNull null
                val id = fields.text(0, MAX_ID) ?: return@mapNotNull null
                val projectId = fields.text(1, MAX_ID) ?: return@mapNotNull null
                val worktree = fields.text(2, MAX_PATH)?.takeIf { it.isNotEmpty() }
                val user = (fields.getOrNull(3) as? JsonPrimitive)?.booleanOrNull == true
                val createdAt = fields.text(4, 64)?.let { runCatching { Instant.parse(it) }.getOrNull() }
                Thread(id, projectId, worktree, user, createdAt)
            }
            return Update(snapshot, projects, threads)
        }

        private fun JsonArray.text(index: Int, max: Int): String? {
            val value: JsonElement = getOrNull(index) ?: return null
            val primitive = value as? JsonPrimitive ?: return null
            if (!primitive.isString) return null
            return primitive.contentOrNull?.takeIf { it.isNotEmpty() && it.length <= max }
        }
    }
}
