package com.bicf.t3plugin.settings

import com.bicf.t3plugin.T3Bundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

/** Settings | Tools | T3 Code. Applying the page reconnects every open "T3 Code" tool window. */
class T3Configurable : BoundConfigurable(T3Bundle["settings.displayName"]) {

    private val state: T3Settings.State
        get() = T3Settings.getInstance().state

    override fun createPanel(): DialogPanel = panel {
        row(T3Bundle["settings.cliPath"]) {
            textFieldWithBrowseButton(
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withTitle(T3Bundle["settings.cliPath.title"]),
            )
                .bindText({ state.cliPath.orEmpty() }, { state.cliPath = it.trim().ifEmpty { null } })
                .align(AlignX.FILL)
                .comment(T3Bundle["settings.cliPath.comment"])
        }
        row(T3Bundle["settings.remoteUrl"]) {
            textField()
                .bindText({ state.remoteUrl.orEmpty() }, { state.remoteUrl = it.trim().ifEmpty { null } })
                .align(AlignX.FILL)
                .comment(T3Bundle["settings.remoteUrl.comment"])
                .validationOnApply {
                    if (it.text.isNotBlank() && T3Settings.normalizeOrigin(it.text) == null) {
                        error(T3Bundle["settings.remoteUrl.invalid"])
                    } else {
                        null
                    }
                }
        }
        row {
            checkBox(T3Bundle["settings.autoStart"])
                .bindSelected({ state.autoStartServer }, { state.autoStartServer = it })
                .comment(T3Bundle["settings.autoStart.comment"])
        }
        row {
            checkBox(T3Bundle["settings.openThreadsInEditor"])
                .bindSelected({ state.openThreadsInEditor }, { state.openThreadsInEditor = it })
                .comment(T3Bundle["settings.openThreadsInEditor.comment"])
        }
        row {
            checkBox(T3Bundle["settings.useIdeTools"])
                .bindSelected({ state.useIdeTools }, { state.useIdeTools = it })
                .comment(T3Bundle["settings.useIdeTools.comment"])
        }
    }

    override fun apply() {
        super.apply()
        ApplicationManager.getApplication().messageBus.syncPublisher(T3SettingsListener.TOPIC).settingsChanged()
    }
}
