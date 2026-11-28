package com.bicf.t3plugin.toolWindow

import com.bicf.t3plugin.editor.T3PageFile

/** What a [T3BrowserPanel] shows. */
sealed interface T3PageTarget {

    /**
     * The "T3 Code" tool window. It pairs the IDE and registers the project. With "Open conversations in editor tabs",
     * it shows only the T3 sidebar ([T3Layout.Mode.NAVIGATOR]), and each page that the user selects opens in a tab.
     */
    data object Navigator : T3PageTarget

    /** One T3 page in an editor tab ([T3Layout.Mode.CONVERSATION]). The panel never pairs the IDE: the tool window does it. */
    data class Tab(val file: T3PageFile) : T3PageTarget

    /** The right panel of the page of [file], in the "T3 Workspace" tool window ([T3Layout.Mode.PANEL]). */
    data class Panel(val file: T3PageFile) : T3PageTarget

    /** The "Thread details" card of the page of [file], in the "T3 Details" tool window ([T3Layout.Mode.DETAILS]). */
    data class Details(val file: T3PageFile) : T3PageTarget
}
