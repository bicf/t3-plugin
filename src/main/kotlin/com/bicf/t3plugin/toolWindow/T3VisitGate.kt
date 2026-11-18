package com.bicf.t3plugin.toolWindow

/**
 * Holds back the "thread visited" command of a T3 page that the user does not see.
 *
 * T3 0.1.9 (verified in the web bundle): each mounted thread page runs a visit tracker. When the thread changes and its
 * last run completed after the last visit, the tracker sends at once the command `thread.visit` (an RPC request with
 * `"type":"thread.visit"` in the payload, `orchestration.dispatchCommand`). The server then clears the unread state ("Completed", "Failed", the unread dot) of
 * the thread in the sidebar. The T3 app mounts only the thread on screen. The plugin keeps one page per editor tab, and
 * a page in "T3 Workspace": without this gate, a thread in a tab in the background lost its unread state when its run
 * completed, without a click of the user.
 *
 * Rules:
 * - The plugin installs the gate when the page starts to load, before the T3 client creates its WebSocket (the client
 *   keeps the `send` function of the socket, see [com.bicf.t3plugin.ide.T3IdeBridge]).
 * - The gate holds a `thread.visit` request while the page is hidden, and sends it unchanged when the page shows.
 *   An editor tab is shown while its component is on screen (the selected tab of an editor window).
 *   The page of "T3 Workspace" never shows for the gate: the tab of the same thread sends the visit.
 * - A held request goes to its socket, or to the last socket of the page when its socket closed. The T3 client ignores
 *   the answer to a request of a closed socket. Without an open socket, the gate drops the request.
 * - The gate changes no other message. The client keeps waiting for the answer of a held request; the tracker does not
 *   send the same visit again.
 * - `window.__t3VisitLog` keeps the last 20 gate events (held, sent, dropped; never content) for diagnosis.
 */
internal object T3VisitGate {

    /** The script that installs the gate (once per page) and sets the visibility of the page to [visible]. */
    fun script(visible: Boolean): String = """
        (function(){if(!window.__t3VisitGate){
        var shown=false,held=[],P=WebSocket.prototype,next=P.send,last=null,LOG=window.__t3VisitLog=[];
        function dbg(s){if(LOG.length>=20)LOG.shift();LOG.push(s);}
        function visit(o,d){if(!o||typeof o!=='object'||d>3)return false;if(o.type==='thread.visit')return true;
          for(var k in o)if(visit(o[k],d+1))return true;return false;}
        function isVisit(t){if(typeof t!=='string'||t.indexOf('"thread.visit"')<0)return false;
          try{var m=JSON.parse(t);return !!m&&m._tag==='Request'&&visit(m.payload,0);}catch(x){return false;}}
        P.send=function(t){if(typeof t==='string'&&t.indexOf('"Request"')>=0)last=this;
          if(!shown&&isVisit(t)){held.push({ws:this,t:t});dbg('held');return;}
          return next.call(this,t);};
        window.__t3VisitGate=function(v){shown=v===true;if(!shown||!held.length)return;var h=held;held=[];
          h.forEach(function(x){var ws=x.ws.readyState===1?x.ws:(last&&last.readyState===1?last:null);
            if(!ws){dbg('dropped');return;}try{next.call(ws,x.t);dbg('sent');}catch(e){dbg('dropped');}});};}
        window.__t3VisitGate($visible);})();
    """.trimIndent()
}
