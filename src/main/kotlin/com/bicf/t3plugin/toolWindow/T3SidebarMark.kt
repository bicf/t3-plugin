package com.bicf.t3plugin.toolWindow

/**
 * Marks in the T3 sidebar of the "T3 Code" tool window the thread of the selected T3 editor tab.
 *
 * T3 0.1.9 (verified on a T3 server): a thread row of the sidebar is `li[data-thread-item="<key>"]`, and the key is
 * `<environmentId>:<threadId>`, for example `6de4d82c-…:mcp:e870489c-…`. The row of the thread on screen has the
 * background `--sidebar-row-active`. In the tool window the page is [T3Routes.NAVIGATOR_PATH], so T3 shows no row as
 * active.
 *
 * Rules:
 * - Only the row with the full key of the thread gets the mark: the same thread id in another environment does not.
 * - The marked row has the background of the T3 active row and a bar of 2 px on the left (`--primary`).
 * - The mark follows the sidebar: a row that T3 creates again (scroll, filter, a new group) gets the mark again.
 * - When the mark moves to another thread, the sidebar scrolls the row into view (only when it is out of view).
 * - A null thread (a draft, an app page, no T3 tab) removes the mark.
 */
internal object T3SidebarMark {

    const val MARK_ATTRIBUTE = "data-t3plugin-current"

    /** The sidebar key of [thread], or null when an id is not a safe segment (the key goes into a script). */
    fun key(thread: T3Routes.ThreadRoute?): String? {
        if (thread == null) return null
        if (!T3Routes.isSafeSegment(thread.environmentId) || !T3Routes.isSafeSegment(thread.threadId)) return null
        return "${thread.environmentId}:${thread.threadId}"
    }

    /** The script that installs the mark (once per page) and marks [thread], or no thread when it is null. */
    fun script(thread: T3Routes.ThreadRoute?): String {
        val key = key(thread)
        val value = if (key == null) "null" else "'$key'"
        return """
            (function(){if(!window.__t3SidebarMark){var MARK='$MARK_ATTRIBUTE',cur=null,queued=false;
            var css='li['+MARK+']>[data-sidebar-row],li['+MARK+']>*>[data-sidebar-row]{background-color:var(--sidebar-row-active)!important;box-shadow:inset 2px 0 0 var(--primary,#3574f0);}';
            function style(){if(document.getElementById('t3plugin-sidebar-mark'))return;
              var s=document.createElement('style');s.id='t3plugin-sidebar-mark';s.textContent=css;document.head.appendChild(s);}
            function apply(){queued=false;style();document.querySelectorAll('li[data-thread-item]').forEach(function(li){
              var on=cur!==null&&li.getAttribute('data-thread-item')===cur;if(on===li.hasAttribute(MARK))return;
              if(on)li.setAttribute(MARK,'');else li.removeAttribute(MARK);});}
            new MutationObserver(function(){if(!queued){queued=true;requestAnimationFrame(apply);}}).observe(document.body,{childList:true,subtree:true});
            window.__t3SidebarMark=function(key){var moved=key!==cur;cur=key;apply();
              var li=moved&&cur?document.querySelector('li['+MARK+']'):null;if(li)li.scrollIntoView({block:'nearest'});};}
            window.__t3SidebarMark($value);})();
        """.trimIndent()
    }
}
