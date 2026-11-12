package com.bicf.t3plugin.toolWindow

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

/**
 * Coroutine scope of the T3 tool window. The platform cancels it when the project closes.
 * [browserPanel] is the open "T3 Code" panel of the project, or null; the editor action uses it.
 */
@Service(Service.Level.PROJECT)
class T3ProjectScope(val coroutineScope: CoroutineScope) {

    @Volatile
    internal var browserPanel: T3BrowserPanel? = null
}
