package com.bicf.t3plugin.toolWindow

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

/** Coroutine scope of the T3 tool window. The platform cancels it when the project closes. */
@Service(Service.Level.PROJECT)
class T3ProjectScope(val coroutineScope: CoroutineScope)
