package com.bicf.t3plugin.toolWindow

/**
 * Makes the hover and pointer media queries of the T3 CSS match a mouse in the IDE browser.
 *
 * The IDE browser renders off screen. In it, `(hover: hover)` and `(pointer: fine)` are false and `(pointer: none)`
 * is true, also with a mouse (verified with runIde and the DevTools protocol, IntelliJ IDEA 2026.2.3).
 * T3 uses Tailwind: each `hover:` and `group-hover:` style is in `@media (hover: hover)`, so without the plugin no
 * hover style of T3 applies. For example, the action row under a message of the user shows only with the focus.
 *
 * Rules:
 * - A media rule of the page CSS with one of [MOUSE_TRUE] applies always; one with [MOUSE_FALSE] never applies.
 *   The rule stays in its place and in its layer, so the order of the T3 styles does not change.
 * - Only these exact media texts change. A combined media query (for example with `and`) stays as T3 wrote it.
 *   `(pointer: coarse)` and `(any-pointer: coarse)` are already false, as for a mouse.
 * - Style sheets that T3 adds later (lazy chunks, a theme change) get the same change.
 * - Style sheets of another origin cannot be read and stay as they are.
 * - The JavaScript `matchMedia` of the page gives the same answer for the same media texts (spaces and case do not
 *   matter): T3 then reports `hover: true` in the device capabilities of a tool view. The answer never changes, so
 *   a change listener on it never runs. Other media texts go to the browser as before.
 * - A `matchMedia` call before the script runs (during the page load) still gets the answer of the browser.
 *   T3 0.1.9 reads `(hover: hover)` only when it shows a tool view, after the page load.
 */
internal object T3PointerMedia {

    /** The media texts (as Chromium writes them) that are true for a mouse. */
    val MOUSE_TRUE = listOf("(hover: hover)", "(any-hover: hover)", "(pointer: fine)", "(any-pointer: fine)")

    /** The media texts that are false for a mouse. */
    val MOUSE_FALSE = listOf("(hover: none)", "(any-hover: none)", "(pointer: none)", "(any-pointer: none)")

    fun script(): String {
        fun map(values: List<String>) = values.joinToString(",") { "'$it':1" }
        fun compact(values: List<String>) = values.joinToString(",") { "'${it.replace(" ", "")}':1" }
        return """
            (function(){if(window.__t3PointerMedia)return;window.__t3PointerMedia=true;
            var ON={${map(MOUSE_TRUE)}},OFF={${map(MOUSE_FALSE)}},seen=new WeakSet(),queued=false;
            var JS_ON={${compact(MOUSE_TRUE)}},JS_OFF={${compact(MOUSE_FALSE)}},media=window.matchMedia;
            if(typeof media==='function'){window.matchMedia=function(q){
              var k=String(q).replace(/\s+/g,'').toLowerCase();
              return media.call(window,JS_ON[k]===1?'all':JS_OFF[k]===1?'not all':q);};}
            function fix(rules){for(var i=0;i<rules.length;i++){var r=rules[i];
              if(r instanceof CSSMediaRule){var t=r.media.mediaText;
                if(ON[t]===1)r.media.mediaText='all';else if(OFF[t]===1)r.media.mediaText='not all';}
              if(r.cssRules)fix(r.cssRules);}}
            function scan(){queued=false;var list=document.styleSheets;for(var i=0;i<list.length;i++){var s=list[i];
              if(seen.has(s))continue;var rules;try{rules=s.cssRules;}catch(e){seen.add(s);continue;}
              if(!rules)continue;seen.add(s);fix(rules);}}
            function queue(){if(!queued){queued=true;requestAnimationFrame(scan);}}
            document.addEventListener('load',function(e){if(e.target&&e.target.tagName==='LINK')scan();},true);
            new MutationObserver(queue).observe(document.documentElement,{childList:true,subtree:true});scan();
            })();
        """.trimIndent()
    }
}
