package com.bicf.t3plugin.toolWindow

/**
 * Marks in the T3 sidebar of the "T3 Code" tool window the thread of the selected T3 editor tab.
 *
 * T3 0.1.9 (verified in the web bundle): a thread row of the sidebar is `li[data-thread-item="<thread key>"]`, and the
 * key ends with the thread id. The row of the thread on screen has the background `--sidebar-row-active`. In the tool
 * window the page is [T3Routes.NAVIGATOR_PATH], so T3 shows no row as active.
 *
 * Rules:
 * - The marked row has the background of the T3 active row and a bar of 2 px on the left (`--primary`).
 * - The mark follows the sidebar: a row that T3 creates again (scroll, filter, a new group) gets the mark again.
 * - When the mark moves to another thread, the sidebar scrolls the row into view (only when it is out of view).
 * - A null thread (a draft, an app page, no T3 tab) removes the mark.
 */
internal object T3SidebarMark {

    const val MARK_ATTRIBUTE = "data-t3plugin-current"

    /** The script that installs the mark (once per page) and marks [threadId], or no thread when it is null. */
    fun script(threadId: String?): String {
        val id = threadId?.takeIf(T3Routes::isSafeSegment)
        val value = if (id == null) "null" else "'$id'"
        return """
            (function(){if(!window.__t3SidebarMark){var MARK='$MARK_ATTRIBUTE',cur=null,queued=false;
            var css='li['+MARK+']>[data-sidebar-row],li['+MARK+']>*>[data-sidebar-row]{background-color:var(--sidebar-row-active)!important;box-shadow:inset 2px 0 0 var(--primary,#3574f0);}';
            function style(){if(document.getElementById('t3plugin-sidebar-mark'))return;
              var s=document.createElement('style');s.id='t3plugin-sidebar-mark';s.textContent=css;document.head.appendChild(s);}
            function match(v){if(!cur||!v)return false;if(v===cur)return true;
              if(v.length<=cur.length||v.slice(-cur.length)!==cur)return false;return !/[A-Za-z0-9_.-]/.test(v.charAt(v.length-cur.length-1));}
            function apply(){queued=false;style();document.querySelectorAll('li[data-thread-item]').forEach(function(li){
              var on=match(li.getAttribute('data-thread-item'));if(on===li.hasAttribute(MARK))return;
              if(on)li.setAttribute(MARK,'');else li.removeAttribute(MARK);});}
            new MutationObserver(function(){if(!queued){queued=true;requestAnimationFrame(apply);}}).observe(document.body,{childList:true,subtree:true});
            window.__t3SidebarMark=function(id){var moved=id!==cur;cur=id;apply();
              var li=moved&&cur?document.querySelector('li['+MARK+']'):null;if(li)li.scrollIntoView({block:'nearest'});};}
            window.__t3SidebarMark($value);})();
        """.trimIndent()
    }
}
