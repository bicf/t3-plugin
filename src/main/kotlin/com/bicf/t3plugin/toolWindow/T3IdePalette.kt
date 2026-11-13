package com.bicf.t3plugin.toolWindow

import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color

/**
 * Reads the colors of the current IDE theme and editor color scheme for [T3ThemeSync].
 *
 * Rules:
 * - The T3 window background (canvas, toolbar, sidebar) is the tool window background.
 * - When the editor scheme and the IDE theme are both light or both dark, cards and the composer (surface) use the
 *   editor background, and code and terminal areas use the editor background, foreground, caret and selection.
 * - Otherwise cards and the composer use the panel background, and code and terminal areas keep the T3 defaults:
 *   T3 picks syntax and terminal colors for the light or dark mode, and they must stay readable.
 * - Text, muted text, borders, hover and selection rows use the IDE label, context help, border and list colors.
 * - Buttons and links use the default button color and the focus color.
 * - Error, warning and search colors keep the T3 defaults.
 */
internal object T3IdePalette {

    fun current(): T3ThemeSync.Palette {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val window = JBUI.CurrentTheme.ToolWindow.background()
        val panel = UIUtil.getPanelBackground()
        val editor = scheme.defaultBackground
        val editorText = scheme.defaultForeground
        val bright = JBColor.isBright()
        val editorMatches = ColorUtil.isDark(editor) != bright
        val text = UIUtil.getLabelForeground()
        val mutedText = UIUtil.getContextHelpForeground()
        val border = JBColor.border()
        val inputBorder = JBColor.namedColor("Component.borderColor", border)
        val hover = JBUI.CurrentTheme.ActionButton.hoverBackground()
        val selectedRow = JBUI.CurrentTheme.List.Selection.background(false)
        val focus = JBUI.CurrentTheme.Focus.focusColor()
        val accent = JBColor.namedColor("Button.default.startBackground", focus)
        val accentText = JBColor.namedColor("Button.default.foreground", text)
        val caret = scheme.getColor(EditorColors.CARET_COLOR) ?: editorText
        val selection = scheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR) ?: selectedRow

        val colors = linkedMapOf<String, Color>()
        fun put(color: Color, vararg keys: String) = keys.forEach { colors[it] = color }
        put(window, "canvas", "chrome", "toolbar", "sidebar")
        put(panel, "toolbarControl", "surfaceRaised", "secondary", "muted", "sidebarControlSurface")
        if (editorMatches) {
            put(editor, "surface", "surfaceOverlay", "codeBackground", "terminalBackground")
            put(editorText, "codeForeground", "terminalForeground")
            put(caret, "terminalCursor")
            put(selection, "terminalSelection")
        } else {
            put(panel, "surface", "surfaceOverlay")
        }
        put(
            text, "text", "toolbarForeground", "toolbarControlForeground", "secondaryForeground",
            "accentSurfaceForeground", "messageForeground", "sidebarForeground",
        )
        put(mutedText, "textMuted", "mutedForeground", "placeholder", "secondaryLabel", "iconMuted", "sidebarMutedForeground")
        put(border, "border", "toolbarBorder", "sidebarBorder")
        put(inputBorder, "input")
        put(hover, "toolbarControlHover", "sidebarRowHover", "accentSurface", "messageSurface")
        put(selectedRow, "sidebarRowActive", "sidebarRowSelected")
        put(focus, "focus")
        put(accent, "accent", "messageAction", "messageActionHover", "update", "updateForeground")
        put(accentText, "accentForeground", "messageActionForeground")

        val mode = if (bright) "light" else "dark"
        return T3ThemeSync.Palette(mode, colors.mapValues { (_, color) -> hex(color) })
    }

    /** `#rrggbb`, or `#rrggbbaa` when the color is not opaque. */
    fun hex(color: Color): String {
        val rgb = "#%02x%02x%02x".format(color.red, color.green, color.blue)
        return if (color.alpha == 255) rgb else rgb + "%02x".format(color.alpha)
    }
}
