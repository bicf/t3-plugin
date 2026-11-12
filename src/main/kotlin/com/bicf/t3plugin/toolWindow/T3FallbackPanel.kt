package com.bicf.t3plugin.toolWindow

import com.bicf.t3plugin.T3Bundle
import com.bicf.t3plugin.T3Exception
import com.bicf.t3plugin.pairing.T3PairingService
import com.bicf.t3plugin.server.T3ServerService
import com.bicf.t3plugin.settings.T3Settings
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import javax.swing.JComponent

/**
 * Content of the tool window when the IDE runtime has no JCEF (step 1 of the flow in PLAN.md).
 * The button opens a new pairing link in the system browser. Each click creates one session on the server.
 */
internal class T3FallbackPanel(private val project: Project) {

    private val status = T3StatusView()
    val component: JComponent get() = status.component

    init {
        showIntro()
    }

    private fun showIntro(error: String? = null) {
        val text = listOfNotNull(T3Bundle["toolWindow.jcefUnsupported"], error).joinToString("\n\n")
        status.show(text, T3Bundle["toolWindow.openInBrowser"]) { openInBrowser() }
    }

    private fun openInBrowser() {
        // A remote server pairs in the browser itself: the CLI cannot reach it.
        T3Settings.getInstance().remoteOrigin?.let {
            BrowserUtil.browse(it)
            return
        }
        status.show(T3Bundle["toolWindow.connecting"])
        project.service<T3ProjectScope>().coroutineScope.launch(Dispatchers.EDT) {
            try {
                val link = runInterruptible(Dispatchers.IO) {
                    val runtime = service<T3ServerService>().ensureServer()
                    // Own label: a new click revokes only the session of the previous click, not a JCEF session.
                    service<T3PairingService>().createPairingLink(runtime.origin, T3PairingService.sessionLabel() + " – system browser")
                }
                BrowserUtil.browse(link.url)
                showIntro()
            } catch (e: T3Exception) {
                showIntro(T3Bundle.message("toolWindow.failed", e.message.orEmpty()))
            }
        }
    }
}
