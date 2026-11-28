package com.bicf.t3plugin.toolWindow

/**
 * A "Scroll to end" button for a conversation that is scrolled up, when T3 shows no button of its own.
 *
 * T3 0.1.9 scrolls the timeline of a conversation in the element with the class `messages-timeline-scroll`. T3 has its
 * own "Scroll to end" button (`[data-scroll-to-end]`), but it does not show it in all states: for example, T3 hides it
 * while the find bar is open (verified in the web bundle).
 *
 * Rules:
 * - The button shows when a visible timeline is more than [END_GAP] px from its end and T3 shows no button of its own
 *   over that timeline. It hides at the end of the timeline, and while T3 shows its button: the page never has 2 buttons.
 * - The button is centered on the timeline, 8 px above the composer (or above the bottom edge of the timeline when the
 *   composer does not cover it). It uses the colors of the T3 theme.
 * - A click scrolls the timeline to its end. The script repeats the scroll for at most 10 frames, while the timeline
 *   grows (rows that T3 renders late). The click does not take the focus from the composer.
 * - The script checks the state on each scroll, on each window resize and every 500 ms.
 */
internal object T3ScrollToEnd {

    const val BUTTON_ATTRIBUTE = "data-t3plugin-scroll-to-end"

    /** Distance in px from the end of the timeline under which the timeline is at its end. */
    const val END_GAP = 48

    private const val TIMELINE = ".messages-timeline-scroll"
    private const val T3_BUTTON = "[data-scroll-to-end]"
    private const val COMPOSER = "[data-workspace-area=\"composer\"]"

    fun script(): String = """
        (function(){if(window.__t3ScrollToEnd)return;window.__t3ScrollToEnd=true;
        var ATTR='$BUTTON_ATTRIBUTE',GAP=$END_GAP,b=null,queued=false;
        var css='['+ATTR+']{position:fixed;z-index:30;transform:translateX(-50%);display:none;align-items:center;gap:4px;'+
          'height:24px;padding:0 10px;border-radius:9999px;border:1px solid var(--border,rgba(128,128,128,.35));'+
          'background:var(--popover,var(--background,#fff));color:var(--foreground,currentColor);font:inherit;font-size:12px;'+
          'font-weight:500;line-height:1;cursor:pointer;box-shadow:0 4px 12px rgba(0,0,0,.18);}'+
          '['+ATTR+']:hover{background:var(--accent,var(--popover,#eee));}['+ATTR+'] svg{width:14px;height:14px;}';
        function visible(el){return el.clientHeight>0&&window.getComputedStyle(el).visibility==='visible';}
        function timeline(){var list=document.querySelectorAll('$TIMELINE');
          for(var i=0;i<list.length;i++){if(visible(list[i]))return list[i];}return null;}
        function atEnd(s){return s.scrollHeight-s.scrollTop-s.clientHeight<=GAP;}
        function over(el,r){var e=el.getBoundingClientRect();return el.getClientRects().length>0&&e.height>0&&
          window.getComputedStyle(el).visibility==='visible'&&e.left<r.right&&e.right>r.left;}
        function find(sel,r){var list=document.querySelectorAll(sel);
          for(var i=0;i<list.length;i++){if(over(list[i],r))return list[i];}return null;}
        function create(){var s=document.createElement('style');s.textContent=css;document.head.appendChild(s);
          b=document.createElement('button');b.type='button';b.setAttribute(ATTR,'');b.setAttribute('aria-label','Scroll to end');
          b.innerHTML='<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" '+
            'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 5v14"/><path d="m19 12-7 7-7-7"/></svg>'+
            '<span>Scroll to end</span>';
          b.addEventListener('pointerdown',function(e){e.preventDefault();});
          b.addEventListener('mousedown',function(e){e.preventDefault();});
          b.addEventListener('click',function(e){e.preventDefault();e.stopPropagation();toEnd();});
          document.body.appendChild(b);}
        function place(s){var r=s.getBoundingClientRect(),bottom=r.bottom,c=find('$COMPOSER',r);
          if(c){var cr=c.getBoundingClientRect();if(cr.top>r.top&&cr.top<bottom)bottom=cr.top;}
          b.style.left=(r.left+r.width/2)+'px';b.style.bottom=(window.innerHeight-bottom+8)+'px';}
        function update(){queued=false;var s=timeline();
          if(!s||atEnd(s)||find('$T3_BUTTON',s.getBoundingClientRect())){if(b)b.style.display='none';return;}
          if(!b)create();place(s);b.style.display='inline-flex';}
        function queue(){if(!queued){queued=true;window.requestAnimationFrame(update);}}
        function toEnd(){var s=timeline();if(!s)return;var n=0;
          (function step(){s.scrollTop=s.scrollHeight;if(++n<10&&!atEnd(s))window.requestAnimationFrame(step);else queue();})();}
        document.addEventListener('scroll',queue,true);window.addEventListener('resize',queue);
        window.setInterval(queue,500);queue();
        })();
    """.trimIndent()
}
