// Runs the script of T3ScrollToEnd against a fake DOM, and prints one "name=value" line per check.
// T3ScrollToEndScriptTest starts it with: node scroll-to-end-harness.js <script.js>.
'use strict';
const fs = require('fs');
const vm = require('vm');
const [scriptFile] = process.argv.slice(2);

class FakeElement {
  constructor(tag) {
    this.tag = tag; this.attrs = {}; this.style = {}; this.listeners = {}; this.children = [];
    this.rect = { top: 0, left: 0, width: 0, height: 0, bottom: 0 }; this.shown = true; this.visibility = 'visible';
  }
  setAttribute(k, v) { this.attrs[k] = String(v); }
  getAttribute(k) { return k in this.attrs ? this.attrs[k] : null; }
  addEventListener(type, f) { (this.listeners[type] = this.listeners[type] || []).push(f); }
  dispatch(type) {
    const e = { type, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; }, stopPropagation() {} };
    (this.listeners[type] || []).forEach((f) => f(e));
    return e;
  }
  appendChild(c) { this.children.push(c); return c; }
  getBoundingClientRect() { return this.rect; }
  getClientRects() { return this.shown ? [this.rect] : []; }
}

const timeline = new FakeElement('div');
Object.assign(timeline, { clientHeight: 500, scrollHeight: 3000, scrollTop: 2500 });
timeline.rect = { top: 100, left: 200, width: 600, height: 500, bottom: 600, right: 800 };
const composer = new FakeElement('div');
composer.rect = { top: 520, left: 200, width: 600, height: 120, bottom: 640, right: 800 };
// A composer of another column (a side chat): the button never uses it.
const otherComposer = new FakeElement('div');
otherComposer.rect = { top: 300, left: 900, width: 300, height: 100, bottom: 400, right: 1200 };
const t3Button = new FakeElement('button');
t3Button.rect = { top: 480, left: 450, width: 100, height: 24, bottom: 504, right: 550 };
t3Button.shown = false;
// A T3 button with visibility:hidden (a hidden page part): it does not count.
const hiddenT3Button = new FakeElement('button');
hiddenT3Button.rect = t3Button.rect;
hiddenT3Button.visibility = 'hidden';

const nodes = {
  '.messages-timeline-scroll': [timeline],
  '[data-scroll-to-end]': [hiddenT3Button, t3Button],
  '[data-workspace-area="composer"]': [otherComposer, composer],
};
const documentListeners = {};
const document = {
  head: new FakeElement('head'),
  body: new FakeElement('body'),
  createElement: (tag) => new FakeElement(tag),
  querySelectorAll: (s) => nodes[s] || [],
  querySelector: (s) => (nodes[s] || [])[0] || null,
  addEventListener: (type, f) => { (documentListeners[type] = documentListeners[type] || []).push(f); },
};
let frames = [];
const win = {
  innerHeight: 800,
  getComputedStyle: (el) => ({ visibility: el.visibility }),
  requestAnimationFrame: (f) => { frames.push(f); return frames.length; },
  setInterval: () => 1,
  addEventListener: () => {},
};
const ctx = { document, String, Number };
ctx.window = Object.assign(ctx, win);
vm.createContext(ctx);

const flush = () => { for (let i = 0; i < 50 && frames.length; i++) { const f = frames; frames = []; f.forEach((g) => g()); } };
const scroll = () => { (documentListeners.scroll || []).forEach((f) => f()); flush(); };
const button = () => document.body.children.find((c) => c.getAttribute('data-t3plugin-scroll-to-end') !== null);
const state = () => { const b = button(); return b ? (b.style.display || 'none') : 'absent'; };
const out = (name, value) => console.log(name + '=' + JSON.stringify(value));

vm.runInContext(fs.readFileSync(scriptFile, 'utf8'), ctx);
flush();
out('atEnd', state());

timeline.scrollTop = 0;
scroll();
out('scrolledUp', state());
out('left', button().style.left);
out('bottom', button().style.bottom);
out('label', button().getAttribute('aria-label'));

t3Button.shown = true;
scroll();
out('t3Shown', state());
t3Button.shown = false;
scroll();
out('t3Hidden', state());

const press = button().dispatch('pointerdown');
out('pointerdownPrevented', press.defaultPrevented);
button().dispatch('click');
flush();
out('afterClickScrollTop', timeline.scrollTop);
out('afterClick', state());

timeline.scrollTop = 0;
timeline.visibility = 'hidden';
scroll();
out('hiddenTimeline', state());

vm.runInContext(fs.readFileSync(scriptFile, 'utf8'), ctx);
out('buttons', document.body.children.length);
