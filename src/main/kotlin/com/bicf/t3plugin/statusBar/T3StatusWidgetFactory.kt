package com.bicf.t3plugin.statusBar

import com.bicf.t3plugin.T3Bundle
import com.bicf.t3plugin.server.T3ServerService
import com.bicf.t3plugin.server.T3ServerState
import com.bicf.t3plugin.toolWindow.T3ProjectScope
import com.bicf.t3plugin.toolWindow.T3ToolWindowFactory
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.Consumer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.awt.Component
import java.awt.event.MouseEvent

/** Status bar widget with the state of the T3 server. A click opens the "T3 Code" tool window. */
class T3StatusWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = ID

    override fun getDisplayName(): String = T3Bundle["statusBar.displayName"]

    override fun createWidget(project: Project): StatusBarWidget = T3StatusWidget(project)

    companion object {
        const val ID = "com.bicf.t3plugin.ServerStatus"
    }
}

/** Text and tooltip of the widget for each [T3ServerState]. */
internal object T3StatusText {

    fun text(state: T3ServerState): String = when (state) {
        T3ServerState.Unknown -> T3Bundle["statusBar.unknown"]
        is T3ServerState.Running -> T3Bundle["statusBar.running"]
        T3ServerState.Stopped -> T3Bundle["statusBar.stopped"]
        is T3ServerState.Failed -> T3Bundle["statusBar.failed"]
        is T3ServerState.Remote -> if (state.reachable) T3Bundle["statusBar.remote"] else T3Bundle["statusBar.remoteUnreachable"]
    }

    fun tooltip(state: T3ServerState): String = when (state) {
        T3ServerState.Unknown -> T3Bundle["statusBar.tooltip.unknown"]
        is T3ServerState.Running -> T3Bundle.message("statusBar.tooltip.running", state.origin)
        T3ServerState.Stopped -> T3Bundle["statusBar.tooltip.stopped"]
        is T3ServerState.Failed -> T3Bundle.message("statusBar.tooltip.failed", state.message)
        is T3ServerState.Remote -> T3Bundle.message("statusBar.tooltip.remote", state.origin)
    }
}

private class T3StatusWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {

    @Volatile
    private var state: T3ServerState = T3ServerState.Unknown
    private var job: Job? = null

    override fun ID(): String = T3StatusWidgetFactory.ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        val server = service<T3ServerService>()
        server.startMonitoring()
        job = project.service<T3ProjectScope>().coroutineScope.launch(Dispatchers.EDT) {
            server.state.collect {
                state = it
                statusBar.updateWidget(ID())
            }
        }
    }

    override fun dispose() {
        job?.cancel()
    }

    override fun getText(): String = T3StatusText.text(state)

    override fun getTooltipText(): String = T3StatusText.tooltip(state)

    override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        ToolWindowManager.getInstance(project).getToolWindow(T3ToolWindowFactory.ID)?.activate(null)
    }
}
