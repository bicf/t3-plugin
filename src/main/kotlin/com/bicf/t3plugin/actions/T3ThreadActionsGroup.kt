package com.bicf.t3plugin.actions

import com.bicf.t3plugin.editor.T3PageFile
import com.bicf.t3plugin.toolWindow.T3Routes
import com.bicf.t3plugin.toolWindow.T3ThreadMenu
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction

/**
 * The items of the "Thread actions" menu of T3 (rename, pin, settle, snooze, archive, delete, copy, …) in the menu of a
 * T3 editor tab (see [T3ThreadMenu]).
 *
 * Rules:
 * - The group shows only on the tab of a thread page whose page answers. It reads the fresh items of the page, and
 *   waits for them up to [WAIT_MS] on a background thread; after that it shows the last items of the page.
 * - Each item keeps the label, the state (disabled, checked) and the submenu of T3, and a separator before an item
 *   with `separatorBefore`. A separator follows the last item.
 * - An item selects its tab, then T3 runs the action in the page, with its own confirmations.
 */
class T3ThreadActionsGroup : ActionGroup(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val file = e?.getData(CommonDataKeys.VIRTUAL_FILE) as? T3PageFile ?: return EMPTY_ARRAY
        if (file.page.kind != T3Routes.PageKind.THREAD) return EMPTY_ARRAY
        val host = file.threadMenu ?: return EMPTY_ARRAY
        val items = host.items(WAIT_MS)?.takeIf { it.isNotEmpty() } ?: return EMPTY_ARRAY
        return (actions(items, file, host) + Separator.getInstance()).toTypedArray()
    }

    private fun actions(items: List<T3ThreadMenu.Item>, file: T3PageFile, host: T3ThreadMenu.Host): List<AnAction> =
        items.flatMap { item ->
            val action: AnAction = if (item.children.isEmpty()) {
                ItemAction(item, file, host)
            } else {
                SubmenuGroup(item, actions(item.children, file, host))
            }
            if (item.separatorBefore) listOf(Separator.getInstance(), action) else listOf(action)
        }

    /** A T3 item with children. The platform forbids a change of the enabled state on the template presentation. */
    private class SubmenuGroup(private val item: T3ThreadMenu.Item, children: List<AnAction>) :
        DefaultActionGroup(children), DumbAware {

        init {
            isPopup = true
            // A label of T3 has no mnemonic: an underscore stays an underscore.
            templatePresentation.setText(item.label, false)
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = item.enabled
        }
    }

    private class ItemAction(
        private val item: T3ThreadMenu.Item,
        private val file: T3PageFile,
        private val host: T3ThreadMenu.Host,
    ) : DumbAwareAction() {

        init {
            templatePresentation.setText(item.label, false)
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = item.enabled
            item.checked?.let { Toggleable.setSelected(e.presentation, it) }
        }

        override fun actionPerformed(e: AnActionEvent) {
            // Rename and "Find" put the keyboard focus in the page: the tab must be on screen.
            e.project?.let { OpenFileDescriptor(it, file).navigate(true) }
            host.run(item.id)
        }
    }

    private companion object {
        const val WAIT_MS = 400L
    }
}
