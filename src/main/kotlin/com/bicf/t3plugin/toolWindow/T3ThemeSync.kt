package com.bicf.t3plugin.toolWindow

/**
 * Gives the colors of the IDE to the T3 web UI (T3ThemeSync in PLAN.md).
 *
 * The web UI reads custom themes from localStorage under [THEMES_KEY]: each theme has an id, a label, an appearance
 * (`light` or `dark`) and a map of colors. A color that the theme does not set keeps the T3 default of that appearance.
 * The script stores the theme [THEME_ID] with the IDE colors, selects it with [THEME_KEY] and [APPEARANCE_KEY],
 * and removes [HALVES_KEY], which selects one theme for each appearance.
 * Then it sends a `storage` event for each changed key, so the page changes colors without a reload.
 * JCEF keeps localStorage in the IDE profile: the T3 desktop app does not change.
 *
 * Trade-off: the panel always shows the IDE colors. A T3 theme that the user selects in the panel stays
 * until the next page load or IDE theme change.
 */
internal object T3ThemeSync {

    const val THEMES_KEY = "t3code:themes:v1"
    const val THEME_KEY = "t3code:theme"
    const val APPEARANCE_KEY = "t3code:theme-appearance-mode"
    const val HALVES_KEY = "t3code:theme-halves:v1"
    const val THEME_ID = "jetbrains-ide"
    const val THEME_LABEL = "JetBrains IDE"

    /** The color keys of a T3 theme (T3 Code 0.1.9). The web UI ignores other keys. */
    val COLOR_KEYS: Set<String> = setOf(
        "canvas", "chrome", "toolbar", "toolbarForeground", "toolbarBorder", "toolbarControl",
        "toolbarControlForeground", "toolbarControlHover", "surface", "surfaceRaised", "surfaceOverlay", "text",
        "textMuted", "border", "input", "focus", "accent", "accentForeground", "secondary", "secondaryForeground",
        "muted", "mutedForeground", "placeholder", "secondaryLabel", "iconMuted", "error", "errorForeground",
        "errorSurface", "warning", "warningForeground", "warningSurface", "update", "updateForeground",
        "updateSurface", "accentSurface", "accentSurfaceForeground", "messageSurface", "messageForeground",
        "messageAction", "messageActionForeground", "messageActionHover", "codeBackground", "codeForeground",
        "searchMatchBackground", "searchMatchForeground", "searchMatchActiveBackground",
        "searchMatchActiveForeground", "sidebar", "sidebarForeground", "sidebarMutedForeground",
        "sidebarControlSurface", "sidebarRowHover", "sidebarRowActive", "sidebarRowSelected", "sidebarBorder",
        "terminalBackground", "terminalForeground", "terminalCursor", "terminalSelection", "terminalScrollbar",
        "terminalScrollbarHover",
    )

    private val HEX_COLOR = Regex("#[0-9a-f]{6}(?:[0-9a-f]{2})?")

    /** [mode] is `light` or `dark`. [colors] maps a key of [COLOR_KEYS] to `#rrggbb` or `#rrggbbaa`. */
    data class Palette(val mode: String, val colors: Map<String, String>) {
        init {
            require(mode == "light" || mode == "dark") { "Unsupported theme mode: $mode" }
            for ((key, value) in colors) {
                require(key in COLOR_KEYS) { "Unknown T3 theme color: $key" }
                require(HEX_COLOR.matches(value)) { "Not a lowercase hex color: $value" }
            }
        }
    }

    /**
     * A script that stores and selects the IDE theme. It changes nothing when the page has the same theme already.
     * The values come only from [Palette], which accepts fixed keys and hex colors: the script needs no escaping.
     */
    fun script(palette: Palette): String {
        val colors = palette.colors.toSortedMap().entries.joinToString(",") { (key, value) -> "\"$key\":\"$value\"" }
        val theme = "{\"id\":\"$THEME_ID\",\"label\":\"$THEME_LABEL\",\"appearance\":\"${palette.mode}\",\"colors\":{$colors}}"
        return "(function(){try{var s=window.localStorage,t=$theme,changed=[];" +
            "function set(k,v){if(s.getItem(k)!==v){s.setItem(k,v);changed.push(k);}}" +
            "var list;try{list=JSON.parse(s.getItem('$THEMES_KEY')||'[]');}catch(e){list=[];}" +
            "if(!Array.isArray(list))list=[];" +
            "var old=list.filter(function(x){return x&&x.id===t.id;})[0];" +
            "if(!old||JSON.stringify(old)!==JSON.stringify(t)){" +
            "list=list.filter(function(x){return !(x&&x.id===t.id);});list.push(t);" +
            "s.setItem('$THEMES_KEY',JSON.stringify(list));changed.push('$THEMES_KEY');}" +
            "set('$THEME_KEY',t.id);set('$APPEARANCE_KEY',t.appearance);" +
            "if(s.getItem('$HALVES_KEY')!==null){s.removeItem('$HALVES_KEY');changed.push('$HALVES_KEY');}" +
            "changed.forEach(function(k){window.dispatchEvent(new StorageEvent('storage'," +
            "{key:k,newValue:s.getItem(k),storageArea:s}));});" +
            "}catch(e){}})();"
    }
}
