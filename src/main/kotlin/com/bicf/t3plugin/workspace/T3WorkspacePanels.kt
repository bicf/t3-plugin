package com.bicf.t3plugin.workspace

import com.bicf.t3plugin.toolWindow.T3PageTarget
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * The right panels of T3 pages ("Workspace panel" of T3: browser, device, pull requests, side chat, …) in the
 * "T3 Workspace" tool window. Each editor tab has at most one panel; the panel shows the right panel of the page of
 * the tab (see [com.bicf.t3plugin.toolWindow.T3Layout.Mode.PANEL]).
 *
 * The plugin creates a panel on "Toggle right panel" in the tab, or when T3 opens its right panel in the tab or in the
 * "T3 Details" tool window. The other rules are in [T3SidePanels].
 */
@Service(Service.Level.PROJECT)
class T3WorkspacePanels(project: Project) :
    T3SidePanels(project, T3WorkspaceToolWindowFactory.ID, { file -> T3PageTarget.Panel(file) })
