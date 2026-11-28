package com.bicf.t3plugin.workspace

import com.bicf.t3plugin.toolWindow.T3PageTarget
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * The "Thread details" cards of T3 pages (`aside[data-workspace-area=details]`: environment, branch, pull requests,
 * changes, servers, automations, lineage, usage, …) in the "T3 Details" tool window. Each editor tab has at most one
 * panel; the panel shows the card of the page of the tab (see [com.bicf.t3plugin.toolWindow.T3Layout.Mode.DETAILS]).
 *
 * The plugin creates a panel on "Toggle thread details panel" in the tab. The other rules are in [T3SidePanels].
 */
@Service(Service.Level.PROJECT)
class T3DetailsPanels(project: Project) :
    T3SidePanels(project, T3DetailsToolWindowFactory.ID, { file -> T3PageTarget.Details(file) })
