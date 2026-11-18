// Runs the script of T3PointerMedia against a fake CSSOM and prints one "name=value" line per check.
// T3PointerMediaScriptTest starts it with: node pointer-media-harness.js <script.js>.
'use strict';
const fs = require('fs');
const vm = require('vm');
const [scriptFile] = process.argv.slice(2);

class CSSMediaRule {
  constructor(mediaText, cssRules) { this.media = { mediaText }; this.cssRules = cssRules; }
}
class CSSStyleRule { constructor(selector) { this.selectorText = selector; } }
class CSSLayerBlockRule { constructor(name, cssRules) { this.name = name; this.cssRules = cssRules; } }
class Sheet { constructor(cssRules) { this.cssRules = cssRules; } }
class CrossOriginSheet { get cssRules() { throw new Error('SecurityError'); } }

const rule = (text) => new CSSMediaRule(text, [new CSSStyleRule('.x')]);
const hover = rule('(hover: hover)');
const anyHover = rule('(any-hover: hover)');
const fine = rule('(pointer: fine)');
const hoverNone = rule('(hover: none)');
const pointerNone = rule('(pointer: none)');
const coarse = rule('(pointer: coarse)');
const combined = rule('(hover: hover) and (min-width: 40rem)');
const width = rule('(min-width: 48rem)');
const layered = rule('(hover: hover)');
const nested = new CSSMediaRule('(min-width: 48rem)', [rule('(hover: hover)')]);
const later = rule('(hover: hover)');
const linked = rule('(hover: hover)');

const sheets = [
  new CrossOriginSheet(),
  new Sheet([hover, anyHover, fine, hoverNone, pointerNone, coarse, combined, width, nested,
    new CSSLayerBlockRule('utilities', [layered])]),
];
let observer = null;
let loadListener = null;
const document = {
  styleSheets: sheets,
  documentElement: {},
  addEventListener(type, f, capture) { if (type === 'load' && capture) loadListener = f; },
};
class MutationObserver {
  constructor(f) { this.f = f; observer = this; }
  observe(target, options) { this.target = target; this.options = options; }
}
// The browser of the IDE: no hover device, so only 'all' and the width query match.
const browserMatches = { 'all': true, '(min-width: 0px)': true };
const ctx = { document, MutationObserver, CSSMediaRule, WeakSet, requestAnimationFrame: (f) => f() };
ctx.matchMedia = function (query) {
  // As in Chromium, matchMedia needs the window as `this` (inside the vm context, the window is a proxy of ctx).
  if (!this || this.document !== document) throw new TypeError('Illegal invocation');
  return { media: query, matches: browserMatches[query] === true };
};
ctx.window = ctx;
vm.createContext(ctx);
const source = fs.readFileSync(scriptFile, 'utf8');
vm.runInContext(source, ctx);

const out = (name, value) => console.log(`${name}=${value}`);
out('hover', hover.media.mediaText);
out('anyHover', anyHover.media.mediaText);
out('pointerFine', fine.media.mediaText);
out('hoverNone', hoverNone.media.mediaText);
out('pointerNone', pointerNone.media.mediaText);
out('pointerCoarse', coarse.media.mediaText);
out('combined', combined.media.mediaText);
out('width', width.media.mediaText);
out('nested', `${nested.media.mediaText}|${nested.cssRules[0].media.mediaText}`);
out('layered', layered.media.mediaText);
out('observesSubtree', observer.target === document.documentElement && observer.options.subtree === true);

const mm = (query) => ctx.matchMedia(query).matches;
out('jsHover', mm('(hover: hover)'));
out('jsHoverCompact', mm('(HOVER:hover)'));
out('jsPointerFine', mm('(pointer: fine)'));
out('jsHoverNone', mm('(hover: none)'));
out('jsPointerNone', mm('(pointer: none)'));
out('jsPointerCoarse', mm('(pointer: coarse)'));
out('jsWidth', mm('(min-width: 0px)'));
out('jsCombined', mm('(hover: hover) and (min-width: 0px)'));

sheets.push(new Sheet([later]));
observer.f([]);
out('addedSheet', later.media.mediaText);

sheets.push(new Sheet([linked]));
loadListener({ target: { tagName: 'IMG' } });
out('imageLoad', linked.media.mediaText);
loadListener({ target: { tagName: 'LINK' } });
out('linkLoad', linked.media.mediaText);

hover.media.mediaText = '(hover: hover)';
vm.runInContext(source, ctx);
observer.f([]);
out('secondRunSkipsSeenSheets', hover.media.mediaText);
