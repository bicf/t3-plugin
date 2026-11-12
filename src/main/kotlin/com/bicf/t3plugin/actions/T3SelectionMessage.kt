package com.bicf.t3plugin.actions

/** Builds the message that "Ask T3 About Selection" sends to a thread. */
internal object T3SelectionMessage {

    /** Longer selections are cut, so that one action never sends a whole file by mistake. */
    const val MAX_CODE_CHARS = 20_000

    /**
     * Format: the question, an empty line, `<path>:<line>` or `<path>:<start>-<end>`, an empty line, and the code in a fence.
     * The fence is longer than the longest run of backticks in the code, so the code never closes it.
     */
    fun build(question: String, relativePath: String, startLine: Int, endLine: Int, language: String, code: String): String {
        val lines = if (startLine == endLine) "$startLine" else "$startLine-$endLine"
        val text = if (code.length > MAX_CODE_CHARS) code.take(MAX_CODE_CHARS) + "\n… (selection cut at $MAX_CODE_CHARS characters)" else code
        val longestRun = Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0
        val fence = "`".repeat(maxOf(3, longestRun + 1))
        return buildString {
            if (question.isNotBlank()) append(question.trim()).append("\n\n")
            append("$relativePath:$lines\n\n")
            append("$fence$language\n$text\n$fence")
        }
    }
}
