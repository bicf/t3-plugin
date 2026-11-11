package com.bicf.t3plugin.toolWindow

import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/** A centered text with one optional button. Shows the state of the connection when the browser is hidden. */
internal class T3StatusView {

    private val label = JBLabel().apply { horizontalAlignment = SwingConstants.CENTER }
    private val button = JButton().apply { isVisible = false }
    private var onClick: (() -> Unit)? = null

    val component: JComponent = JPanel(GridBagLayout()).apply {
        border = JBUI.Borders.empty(16)
        val constraints = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            insets = JBUI.insetsBottom(8)
        }
        add(label, constraints)
        constraints.gridy = 1
        add(button, constraints)
    }

    init {
        button.addActionListener { onClick?.invoke() }
    }

    /** Shows [text]. Shows the button only when [buttonText] is not null. A line break in [text] starts a new line. */
    fun show(@Nls text: String, @Nls buttonText: String? = null, onClick: (() -> Unit)? = null) {
        val html = StringUtil.escapeXmlEntities(text).replace("\n", "<br>")
        label.text = "<html><body style='width: ${JBUI.scale(260)}px; text-align: center'>$html</body></html>"
        this.onClick = onClick
        button.text = buttonText.orEmpty()
        button.isVisible = buttonText != null
    }
}
