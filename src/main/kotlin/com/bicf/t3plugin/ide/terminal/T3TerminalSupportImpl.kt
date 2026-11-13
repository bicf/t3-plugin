package com.bicf.t3plugin.ide.terminal

import com.bicf.t3plugin.ide.T3TerminalSupport
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.nio.file.Path

/** [T3TerminalSupport] with the Terminal plugin of the IDE. Registered in `t3-terminal.xml`. */
internal class T3TerminalSupportImpl(private val project: Project) : T3TerminalSupport {

    override fun open(directory: Path, tabName: String): Boolean {
        ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.activate(null)
        TerminalToolWindowManager.getInstance(project).createShellWidget(directory.toString(), tabName, true, true)
        return true
    }

    private companion object {
        /** Id of the Terminal tool window. */
        const val TOOL_WINDOW_ID = "Terminal"
    }
}
