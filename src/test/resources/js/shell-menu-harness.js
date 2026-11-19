// Runs the scripts of T3ShellWatch and T3ThreadMenu against a fake WebSocket and a fake T3 "Thread actions" button,
// and prints one "name=value" line per check.
// T3ShellMenuScriptTest starts it with:
//   node shell-menu-harness.js <shell.js> <list.js> <choose-pin.js> <choose-disabled.js> <choose-parent.js> <choose-child.js>
'use strict';
const fs = require('fs');
const vm = require('vm');
const [shellFile, listFile, pinFile, disabledFile, parentFile, childFile, routeFile] = process.argv.slice(2);
const out = (name, value) => console.log(name + '=' + JSON.stringify(value));
const read = (file) => fs.readFileSync(file, 'utf8');

// --- Shell watch ---------------------------------------------------------------------------------------------------
class FakeSocket {
  constructor() { this.listeners = []; this.sent = []; }
  addEventListener(type, f) { if (type === 'message') this.listeners.push(f); }
  receive(data) { const ev = { data }; this.listeners.forEach((f) => f(ev)); }
}
FakeSocket.prototype.send = function (t) { this.sent.push(t); };
const reports = [];
const shellCtx = { WebSocket: FakeSocket, JSON, send: (v) => reports.push(v) };
shellCtx.window = shellCtx;
vm.createContext(shellCtx);
vm.runInContext(read(shellFile), shellCtx);
vm.runInContext(read(shellFile), shellCtx); // A second run changes nothing.
const ws = new FakeSocket();
ws.send('{"_tag":"Request","id":"1","tag":"orchestration.subscribeShell"}');
ws.send('{"_tag":"Request","id":"2","tag":"other"}');
out('sentThrough', ws.sent.length);
out('listeners', ws.listeners.length);
const snapshot = {
  _tag: 'Chunk', requestId: '1', values: [{
    kind: 'snapshot',
    snapshot: {
      projects: [{ id: 'p1', workspaceRoot: '/work/demo', title: 'demo' }],
      threads: [
        { id: 't1', projectId: 'p1', worktreePath: null, creationSource: 'web', lineage: { parentThreadId: null } },
        { id: 't2', projectId: 'p1', worktreePath: '/wt/a', creationSource: 'mcp', lineage: { parentThreadId: null } },
      ],
      archivedThreads: [{ id: 't3', projectId: 'p1', worktreePath: null, creationSource: 'web', lineage: {} }],
    },
  }],
};
// The server splits a long message: each part but the last starts with U+001E.
const text = JSON.stringify(snapshot);
ws.receive('\u001e' + text.slice(0, 40));
ws.receive('\u001e' + text.slice(40, 90));
out('partialReports', reports.length);
ws.receive(text.slice(90));
ws.receive(JSON.stringify({ _tag: 'Chunk', requestId: '1', values: [{ kind: 'project.updated', project: { id: 'p2', workspaceRoot: '/work/new' } }] }));
ws.receive(JSON.stringify({ _tag: 'Chunk', requestId: '1', values: [
  { kind: 'thread.updated', location: 'active', thread: { id: 't4', projectId: 'p1', worktreePath: '/wt/b', creationSource: 'web', createdAt: '2026-11-19T17:46:11.000Z', lineage: { parentThreadId: null } } },
  { kind: 'thread.updated', location: 'active', thread: { id: 't5', projectId: 'p1', worktreePath: '/wt/c', creationSource: 'web', lineage: { parentThreadId: 't4', relationshipToParent: 'subagent' } } },
  { kind: 'thread.updated', location: 'active', thread: { id: 't6', projectId: 'p1', worktreePath: '/wt/d', creationSource: 'web', lineage: {}, delegatedFrom: { threadId: 't1' } } },
] }));
ws.receive(JSON.stringify({ _tag: 'Exit', requestId: '7', exit: { _tag: 'Success', value: { kind: 'other' } } }));
ws.receive('{"kind":"snapshot", broken');
ws.receive({ binary: true });
out('reports', reports);

// --- Route watch: the project of a saved draft ---------------------------------------------------------------------
function routeReports(path, drafts) {
  const sent = [];
  const store = { 't3code:composer-drafts:v1': JSON.stringify(drafts), other: '{}' };
  const keys = Object.keys(store);
  const localStorage = { length: keys.length, key: (i) => keys[i], getItem: (k) => (k in store ? store[k] : null) };
  const ctx = {
    JSON, localStorage, send: (v) => sent.push(v), setInterval: () => 0, setTimeout: () => 0, Date,
    location: { pathname: path }, history: { state: { __TSR_index: 2 } }, decodeURIComponent,
    document: { querySelector: () => null },
  };
  ctx.window = ctx;
  vm.createContext(ctx);
  vm.runInContext(read(routeFile), ctx);
  return sent;
}
const drafts = { state: { draftThreadsByThreadKey: {
  'env-1:d1': { threadId: 'd1', environmentId: 'env-1', projectId: 'p1', worktreePath: null },
  'env-1:d2': { threadId: 'd2', environmentId: 'env-1', projectId: 'p2', worktreePath: '/wt/b' },
} }, version: 3 };
out('routeDraft', routeReports('/draft/d2', drafts).concat(routeReports('/draft/d1', drafts)));
out('routeThread', routeReports('/env-1/t1', drafts));

// --- Thread menu ---------------------------------------------------------------------------------------------------
const ITEMS = [
  { id: 'rename', label: 'Rename thread', icon: 'pencil' },
  { id: 'pin', label: 'Pin thread', icon: 'pin' },
  { id: 'archive', label: 'Archive thread', disabled: true, separatorBefore: true },
  { id: 'copy', label: 'Copy', children: [{ id: 'copy-path', label: 'Path' }, { id: 'copy-thread-id', label: 'Thread ID' }] },
  { id: 'auto-settle', label: 'Auto-settle behavior', children: [{ id: 'auto-settle:enabled', label: 'Follow settings', checked: true }] },
  { id: 'delete', label: 'Delete', destructive: true },
];
let fallbackMenus = 0;
const chosen = [];
const menuReports = [];
let button = null;
const menuCtx = { JSON, Promise, send: (v) => menuReports.push(v) };
menuCtx.window = menuCtx;
// As T3 0.1.9: show() checks window.desktopBridge in its body; the click handler calls it at once.
const api = { contextMenu: { show: async (items, pos) => (menuCtx.desktopBridge ? menuCtx.desktopBridge.showContextMenu(items, pos) : (fallbackMenus++, null)) } };
async function tryPromise(f) { try { return { _tag: 'Success', value: await f() }; } catch (e) { return { _tag: 'Failure' }; } }
function openMenu() { (async () => { const r = await tryPromise(() => api.contextMenu.show(ITEMS, { x: 1, y: 2 })); if (r._tag === 'Success' && r.value !== null) chosen.push(r.value); })(); }
menuCtx.document = { querySelector: (sel) => (sel === 'button[aria-label^="Thread actions for "]' ? button : null) };
vm.createContext(menuCtx);
const runMenu = (file) => vm.runInContext(read(file), menuCtx);

(async () => {
  button = { click: openMenu };
  runMenu(listFile);
  out('listReports', menuReports);
  out('bridgeAfterList', Object.prototype.hasOwnProperty.call(menuCtx, 'desktopBridge'));
  out('fallbackMenus', fallbackMenus);
  menuCtx.desktopBridge = { showContextMenu: async () => 'native', other: 1 };
  runMenu(pinFile);
  await new Promise((r) => setTimeout(r, 0));
  out('bridgeRestored', menuCtx.desktopBridge.other === 1);
  delete menuCtx.desktopBridge;
  runMenu(disabledFile);
  runMenu(parentFile);
  runMenu(childFile);
  await new Promise((r) => setTimeout(r, 0));
  out('chosen', chosen);
  button = null;
  menuReports.length = 0;
  runMenu(listFile);
  out('noButton', menuReports);
})();
