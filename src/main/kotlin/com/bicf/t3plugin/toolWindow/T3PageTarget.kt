package com.bicf.t3plugin.toolWindow

/** What a [T3BrowserPanel] shows. */
sealed interface T3PageTarget {

    /**
     * The "T3 Code" tool window: the T3 home page with the thread list. It pairs the IDE and registers the project.
     * With "Open conversations in editor tabs", a thread selected here opens in an editor tab.
     */
    data object Navigator : T3PageTarget

    /** One thread in an editor tab. The panel never pairs the IDE: the tool window does it. */
    data class Thread(val route: T3Routes.ThreadRoute) : T3PageTarget
}
