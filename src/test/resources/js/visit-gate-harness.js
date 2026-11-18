// Runs the visit gate script of T3VisitGate against a fake WebSocket and a fake Storage, and prints one
// "name=value" line per check. T3VisitGateScriptTest starts it with: node visit-gate-harness.js <hidden.js> <shown.js>.
'use strict';
const fs = require('fs');
const vm = require('vm');
const [hiddenFile, shownFile] = process.argv.slice(2);

class FakeStorage {
  constructor() { this.m = {}; }
  getItem(k) { return k in this.m ? this.m[k] : null; }
  setItem(k, v) { this.m[k] = String(v); }
}
class FakeMessageEvent { constructor(type, init) { this.type = type; this.data = init.data; } }
class FakeWebSocket {
  constructor() {
    this.readyState = 1; this.sent = []; this.listeners = []; this.received = [];
    this.addEventListener('message', (e) => this.received.push(e.data));
  }
  send(t) { this.sent.push(t); }
  addEventListener(type, f) { if (type === 'message') this.listeners.push(f); }
  dispatchEvent(e) { this.listeners.forEach((f) => f(e)); }
  receive(data) { this.dispatchEvent(new FakeMessageEvent('message', { data })); }
}

const storage = new FakeStorage();
const ctx = { WebSocket: FakeWebSocket, Storage: FakeStorage, MessageEvent: FakeMessageEvent, JSON, Date, isNaN, String, Object };
ctx.window = ctx;
ctx.window.localStorage = storage;
vm.createContext(ctx);
const run = (file) => vm.runInContext(fs.readFileSync(file, 'utf8'), ctx);
const out = (name, value) => console.log(name + '=' + JSON.stringify(value));
const visit = (id, thread, at) => JSON.stringify({ _tag: 'Request', id, tag: 'orchestration.dispatchCommand',
  payload: { type: 'thread.visit', threadId: thread, visitedAt: at }, headers: [] });
const other = (id) => JSON.stringify({ _tag: 'Request', id, tag: 'orchestration.subscribeThread', payload: { threadId: 't1' }, headers: [] });
const UI = 't3code:ui-state:v1';
const ids = (ws) => ws.sent.map((t) => JSON.parse(t).id);

run(hiddenFile);
const ws = new FakeWebSocket();
ws.send(visit(5, 't1', '2026-11-18T10:00:00Z'));
ws.send(visit(6, 't2', '2026-11-18T10:00:00Z'));
ws.send(other(9));
ws.receive('\u001e{"part":1');
ws.send(visit(7, 't1', '2026-11-18T10:00:10Z'));
out('hiddenSent', ids(ws));
out('exitDuringParts', ws.received.filter((d) => d.indexOf('"Exit"') >= 0).length);
ws.receive('"end":true}');
out('exitAfterParts', ws.received[ws.received.length - 1]);

storage.m[UI] = JSON.stringify({ projectOrder: ['a'], threadLastVisitedAtById: { k1: '2026-11-18T09:00:00Z' } });
FakeStorage.prototype.setItem.call(storage, UI, JSON.stringify({ projectOrder: ['b'],
  threadLastVisitedAtById: { k1: '2026-11-18T10:00:00Z', k2: '2026-11-18T10:00:00Z' } }));
out('hiddenStored', JSON.parse(storage.m[UI]));
FakeStorage.prototype.setItem.call(storage, UI, '{bad json');
out('badJsonStored', storage.m[UI]);
storage.m[UI] = JSON.stringify({ projectOrder: ['b'], threadLastVisitedAtById: { k1: '2026-11-18T09:00:00Z' } });

run(shownFile);
out('shownSent', ids(ws));
out('shownStored', JSON.parse(storage.m[UI]).threadLastVisitedAtById);
run(shownFile);
out('secondShowSent', ids(ws).length);
ws.send(visit(8, 't1', '2026-11-18T10:01:00Z'));
out('visibleSent', ids(ws));
out('log', ctx.__t3VisitLog);
