// Runs the sidebar mark script of T3SidebarMark against a fake sidebar, and prints one "name=value" line per check.
// T3SidebarMarkScriptTest starts it with: node sidebar-mark-harness.js <mark-a.js> <mark-b.js> <mark-null.js>.
'use strict';
const fs = require('fs');
const vm = require('vm');
const [markA, markB, markNull] = process.argv.slice(2);
const MARK = 'data-t3plugin-current';

class FakeElement {
  constructor(key) { this.attrs = key === undefined ? {} : { 'data-thread-item': key }; this.scrolls = 0; }
  getAttribute(n) { return n in this.attrs ? this.attrs[n] : null; }
  hasAttribute(n) { return n in this.attrs; }
  setAttribute(n, v) { this.attrs[n] = String(v); }
  removeAttribute(n) { delete this.attrs[n]; }
  scrollIntoView() { this.scrolls++; }
}
let rows = ['env:a', 'env2:a', 'env:b'].map((k) => new FakeElement(k));
const styles = [];
const frames = [];
let observer = null;
const document = {
  head: { appendChild: (s) => styles.push(s) },
  body: {},
  createElement: () => new FakeElement(),
  getElementById: (id) => styles.find((s) => s.id === id) || null,
  querySelectorAll: (sel) => (sel === 'li[data-thread-item]' ? rows.slice() : []),
  querySelector: (sel) => (sel === 'li[' + MARK + ']' ? rows.find((r) => r.hasAttribute(MARK)) || null : null),
};
class FakeMutationObserver { constructor(f) { this.f = f; observer = this; } observe() {} }
const ctx = { document, MutationObserver: FakeMutationObserver, requestAnimationFrame: (f) => frames.push(f) };
ctx.window = ctx;
vm.createContext(ctx);
const run = (file) => vm.runInContext(fs.readFileSync(file, 'utf8'), ctx);
const out = (name, value) => console.log(name + '=' + JSON.stringify(value));
const marked = () => rows.filter((r) => r.hasAttribute(MARK)).map((r) => r.getAttribute('data-thread-item'));
const scrolls = () => rows.reduce((n, r) => n + r.scrolls, 0);
const mutate = () => { observer.f([]); observer.f([]); const n = frames.length; while (frames.length) frames.shift()(); return n; };

run(markA);
out('firstMarked', marked());
out('firstScrolls', scrolls());
run(markB);
out('movedMarked', marked());
out('movedScrolls', scrolls());
run(markB);
out('sameScrolls', scrolls());
rows = [rows[0], rows[1], new FakeElement('env:b')];
const queued = mutate();
out('remarked', marked());
out('framesQueued', queued);
run(markNull);
out('clearedMarked', marked());
out('styles', styles.length);
