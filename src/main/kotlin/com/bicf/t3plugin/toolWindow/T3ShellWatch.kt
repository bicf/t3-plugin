package com.bicf.t3plugin.toolWindow

/**
 * Reports the projects and threads of the T3 sidebar to the plugin (see [com.bicf.t3plugin.project.T3ShellModel]).
 *
 * T3 0.1.9 (verified in the web bundle): the sidebar subscribes to `orchestration.subscribeShell` on the WebSocket of
 * the page. The stream sends `{kind:"snapshot",snapshot:{projects,threads,archivedThreads}}`, then
 * `{kind:"project.updated",project}` and `{kind:"thread.updated",thread}`. A project has `id` and `workspaceRoot`;
 * a thread has `id`, `projectId`, `worktreePath`, `creationSource` (`web`, `mobile`, `mcp`, `provider`, `server`),
 * `lineage.parentThreadId` and `delegatedFrom`.
 *
 * Rules:
 * - The plugin installs the script when the page of the "T3 Code" tool window starts to load: the T3 client keeps the
 *   `send` function of its WebSocket when it creates the socket (see [com.bicf.t3plugin.ide.T3IdeBridge]).
 * - The script reads the messages that the server sends, and joins the parts of a long message (a part starts with
 *   U+001E; the last frame of a message does not). It parses only a message with one of the 3 kinds above.
 * - It sends [PREFIX] + `{"snap":true,"p":[…],"t":[…]}` for a snapshot, and `{"p":[…]}` or `{"t":[…]}` for an update.
 *   A project is `[id, workspaceRoot]`; a thread is `[id, projectId, worktreePath|null, user]`, where `user` is true for a
 *   top-level thread from a T3 client (no parent, not delegated, `creationSource` `web` or `mobile`).
 * - The script changes no message.
 */
internal object T3ShellWatch {

    const val PREFIX = "shell:"

    fun script(report: (String) -> String): String = """
        (function(){if(window.__t3ShellWatch)return;window.__t3ShellWatch=true;
        var P=WebSocket.prototype,send0=P.send,RX=/"kind":\s*"(snapshot|project\.updated|thread\.updated)"/;
        function str(v){return typeof v==='string'?v:null;}
        function proj(p){return [String(p.id),str(p.workspaceRoot)];}
        function thr(t){var l=t.lineage||{},s=t.creationSource;
          return [String(t.id),String(t.projectId),str(t.worktreePath),
            !l.parentThreadId&&!t.delegatedFrom&&(s==='web'||s==='mobile')];}
        function emit(o){var r='$PREFIX'+JSON.stringify(o);${report("r")};}
        function visit(o,d){if(!o||typeof o!=='object'||d>6)return;
          if(Array.isArray(o)){for(var i=0;i<o.length;i++)visit(o[i],d+1);return;}
          if(o.kind==='snapshot'&&o.snapshot&&typeof o.snapshot==='object'){var s=o.snapshot;
            emit({snap:true,p:(s.projects||[]).map(proj),t:(s.threads||[]).concat(s.archivedThreads||[]).map(thr)});return;}
          if(o.kind==='project.updated'&&o.project&&typeof o.project==='object'){emit({p:[proj(o.project)]});return;}
          if(o.kind==='thread.updated'&&o.thread&&typeof o.thread==='object'){emit({t:[thr(o.thread)]});return;}
          for(var k in o){if(o[k]&&typeof o[k]==='object')visit(o[k],d+1);}}
        function attach(ws){if(ws.__t3shell)return;var s=ws.__t3shell={buf:'',partial:false};
          ws.addEventListener('message',function(ev){if(ev.__t3ide||ev.__t3gate||typeof ev.data!=='string')return;
            if(ev.data.charCodeAt(0)===30){s.partial=true;s.buf+=ev.data.slice(1);return;}
            var full=s.partial?s.buf+ev.data:ev.data;s.buf='';s.partial=false;
            if(!RX.test(full))return;try{visit(JSON.parse(full),0);}catch(x){}});}
        P.send=function(){try{attach(this);}catch(x){}return send0.apply(this,arguments);};
        })();
    """.trimIndent()
}
