// spike build.js: deck.json -> deck.pdf (pdfkit) + deck.pptx (pptxgenjs). Usage: node build.js <slug|dir> [--selftest]
const fs = require('fs'), path = require('path');
const t0 = Date.now();
const PptxGenJS = require('pptxgenjs');
const PDFDocument = require('pdfkit');
const root = process.env.OC_DECKS || '/root/projects/presentations';
const arg = process.argv[2];
let dir, deck;
if (arg === '--selftest') {
  dir = fs.mkdtempSync(require('os').tmpdir() + '/deck-');
  deck = { schema: 1, stage: 'slides', title: 'Self test', lang: 'en', dir: 'ltr', theme: 'clean', slides: [{ layout: 'title', title: 'Self test' }] };
} else {
  dir = fs.existsSync(arg) ? arg : path.join(root, arg);
  deck = JSON.parse(fs.readFileSync(path.join(dir, 'deck.json'), 'utf8'));
}
const warn = [];
const LAY = ['title','bullets','two_column','image_left','quote','section','closing'];
const cut = (s, n, where) => { s = String(s ?? ''); if (s.length > n) { warn.push(`${where}: cut`); return s.slice(0, n - 1) + '…'; } return s; };
if (!Array.isArray(deck.slides) || !deck.slides.length) { console.error('deck.json: no slides'); process.exit(2); }
const rtl = deck.dir === 'rtl' || /^ar|^he|^fa|^ur/.test(deck.lang || '');
const th = { bg: 'FFFFFF', text: '1F2328', accent: '0F9D76', muted: '6B7280' };
const slides = deck.slides.map((s, i) => {
  const o = { layout: LAY.includes(s.layout) ? s.layout : 'bullets' };
  if (!LAY.includes(s.layout)) warn.push(`slide ${i + 1}: unknown layout ${s.layout}`);
  o.title = cut(s.title, 80, `slide ${i + 1} title`); o.subtitle = s.subtitle ? cut(s.subtitle, 120, `slide ${i + 1} subtitle`) : '';
  o.bullets = (s.bullets || []).slice(0, 6).map(b => cut(b, 120, `slide ${i + 1} bullet`));
  o.left = (s.left || []).slice(0, 6).map(b => cut(b, 120, 'left')); o.right = (s.right || []).slice(0, 6).map(b => cut(b, 120, 'right'));
  o.notes = s.notes ? cut(s.notes, 1500, 'notes') : '';
  return o;
});
// ---- PDF

const bidiFactory = require('bidi-js'); const bidi = bidiFactory();
const AR = /[؀-ۿݐ-ݿﭐ-﷿ﹰ-﻿]/, AIN = /[٠-٩۰-۹]/;
function runsOf(line, base) {
  const lv = bidi.getEmbeddingLevels(line, base).levels; const runs = [];
  for (let i = 0; i < line.length; i++) { const l = lv[i]; if (runs.length && runs[runs.length-1].level === l) runs[runs.length-1].t += line[i]; else runs.push({ t: line[i], level: l }); }
  const max = Math.max(...runs.map(r => r.level)), minOdd = Math.min(...runs.filter(r => r.level % 2).map(r => r.level), 99);
  for (let l = max; l >= minOdd && l >= 1; l--) { for (let i = 0; i < runs.length; ) { if (runs[i].level >= l) { let j = i; while (j < runs.length && runs[j].level >= l) j++; const seg = runs.slice(i, j).reverse(); runs.splice(i, j - i, ...seg); i = j; } else i++; } }
  return runs;
}
function mono(doc, fonts, run) { // pick font and pre-reverse digit-only LTR runs of Arabic-Indic digits
  let t = run.t, f = fonts.lat;
  if (AR.test(t) || AIN.test(t)) { f = fonts.ar; }
  if (run.level % 2) t = [...t].map(c => bidi.getMirroredCharacter(c) || c).join('');
  return { t, f, level: run.level };
}
function tw(doc, fonts, q) { const toks = q.t.match(/[\u0600-\u06FF\u0750-\u077F\uFB50-\uFDFF\uFE70-\uFEFF]+|[^\u0600-\u06FF\u0750-\u077F\uFB50-\uFDFF\uFE70-\uFEFF]+/g) || []; return toks.reduce((a, z) => { doc.font(AR.test(z) || AIN.test(z) ? fonts.ar : fonts.lat); return a + doc.widthOfString(z); }, 0); }
function bidiText(doc, text, x, y, w, size, fonts, color) {
  doc.fontSize(size); const words = text.split(' '); const lines = []; let cur = '';
  const wid = s => { const r = runsOf(s, 'rtl'); return r.reduce((a, q) => { return a + tw(doc, fonts, mono(doc, fonts, q)); }, 0); };
  words.forEach(wd => { const tr = cur ? cur + ' ' + wd : wd; if (cur && wid(tr) > w) { lines.push(cur); cur = wd; } else cur = tr; });
  lines.push(cur);
  doc.fillColor(color);
  lines.forEach(l => { const rs = runsOf(l, 'rtl').map(q => ({ ...mono(doc, fonts, q) })); let tot = 0; rs.forEach(q => { q.w = tw(doc, fonts, q); tot += q.w; });
    let cx = x + w - tot; rs.forEach(q => { const odd = q.level % 2; let toks = q.t.match(/[\u0600-\u06FF\u0750-\u077F\uFB50-\uFDFF\uFE70-\uFEFF]+|[^\u0600-\u06FF\u0750-\u077F\uFB50-\uFDFF\uFE70-\uFEFF]+/g) || []; if (odd) toks.reverse();
      toks.forEach(z => { const isAr = AR.test(z) || AIN.test(z); const ff = isAr ? fonts.ar : fonts.lat; if (odd && !isAr) z = [...z].reverse().join(''); if (isAr && /^[\u0660-\u0669\u06F0-\u06F9]+$/.test(z)) z = [...z].reverse().join(''); if (!odd && !isAr && false) z = z;
        doc.font(ff); const ww = doc.widthOfString(z); const asc = doc._font.ascender * size / 1000; doc.font(fonts.ar); const asc0 = doc._font.ascender * size / 1000; doc.font(ff);
        doc.text(z, cx, y + (asc0 - asc), { lineBreak: false }); cx += ww; }); }); y += size * 1.4; });
  return y;
}
const W = 960, H = 540;
const F = rtl ? 'fonts/NotoSansArabic-Regular.ttf' : 'fonts/NotoSans-Regular.ttf';
const FB = rtl ? 'fonts/NotoSansArabic-Bold.ttf' : 'fonts/NotoSans-Bold.ttf';
const here = __dirname;
const doc = new PDFDocument({ size: [W, H], margin: 0, info: { Title: deck.title || 'Deck' } });
doc.pipe(fs.createWriteStream(path.join(dir, 'deck.pdf')));
const fr = path.join(here, F), fb = path.join(here, FB);
const align = rtl ? 'right' : 'left';
const opt = (w) => ({ width: w, align, features: undefined, ...(rtl ? { features: ['rtla'] } : {}) });
const fontsR = { ar: fr, lat: path.join(here, 'fonts/NotoSans-Regular.ttf') }, fontsB = { ar: fb, lat: path.join(here, 'fonts/NotoSans-Bold.ttf') };
const T = (text, x, y, w, size, bold, color) => {
  if (rtl) return bidiText(doc, text, x, y, w, size, bold ? fontsB : fontsR, '#' + color);
  doc.fillColor('#' + color).font(bold ? fb : fr).fontSize(size).text(text, x, y, { width: w, align: 'left' }); return doc.y + size * 0.2;
};
slides.forEach((s, i) => {
  if (i) doc.addPage({ size: [W, H], margin: 0 });
  doc.rect(0, 0, W, H).fill('#' + th.bg);
  doc.rect(rtl ? W - 24 : 0, 0, 24, H).fill('#' + th.accent);
  const x = 80, w = W - 160;
  if (s.layout === 'title' || s.layout === 'section' || s.layout === 'closing') {
    let y = T(s.title, x, 190, w, 44, true, th.text);
    if (s.subtitle) T(s.subtitle, x, y + 12, w, 22, false, th.muted);
  } else if (s.layout === 'quote') {
    let y = T(s.title, x, 170, w, 30, false, th.accent);
    if (s.subtitle) T(s.subtitle, x, y + 12, w, 18, false, th.muted);
  } else {
    T(s.title, x, 50, w, 32, true, th.text);
    doc.fillColor('#' + th.accent).rect(rtl ? x + w - 60 : x, 110, 60, 4).fill();
    const col = (items, cx, cw) => { let y = 140; items.forEach(b => { const ind = 22; doc.fillColor('#' + th.accent).circle(rtl ? cx + cw - 5 : cx + 5, y + 13, 4).fill(); y = T(b, rtl ? cx : cx + ind, y, cw - ind, 20, false, th.text) + 12; }); };
    if (s.layout === 'two_column') { const a = [s.left, x, w / 2 - 20], b = [s.right, x + w / 2 + 20, w / 2 - 20]; (rtl ? [b, a] : [a, b]).forEach(c => col(...c)); }
    else col(s.bullets, x, w);
  }
});
doc.end();
// ---- PPTX
const p = new PptxGenJS(); p.layout = 'LAYOUT_16x9'; p.title = deck.title || 'Deck';
const pf = 'Arial';
slides.forEach(s => {
  const sl = p.addSlide(); sl.background = { color: th.bg };
  const t = { fontFace: pf, rtlMode: rtl, align: rtl ? 'right' : 'left', lang: deck.lang };
  if (['title','section','closing'].includes(s.layout)) {
    sl.addText(s.title, { ...t, x: 0.7, y: 2.0, w: 8.6, h: 1.2, fontSize: 36, bold: true, color: th.text });
    if (s.subtitle) sl.addText(s.subtitle, { ...t, x: 0.7, y: 3.3, w: 8.6, h: 0.8, fontSize: 20, color: th.muted });
  } else if (s.layout === 'quote') {
    sl.addText(s.title, { ...t, x: 0.7, y: 1.8, w: 8.6, h: 1.6, fontSize: 28, italic: true, color: th.accent });
  } else {
    sl.addText(s.title, { ...t, x: 0.7, y: 0.4, w: 8.6, h: 0.8, fontSize: 28, bold: true, color: th.text });
    const bl = (items, x, w) => sl.addText(items.map(b => ({ text: b, options: { bullet: true, breakLine: true } })), { ...t, x, y: 1.5, w, h: 3.6, fontSize: 18, color: th.text, valign: 'top' });
    if (s.layout === 'two_column') { bl(s.left, 0.7, 4.1); bl(s.right, 5.2, 4.1); } else bl(s.bullets, 0.7, 8.6);
  }
  if (s.notes) sl.addNotes(s.notes);
});
p.writeFile({ fileName: path.join(dir, 'deck.pptx') }).then(() => {
  warn.forEach(w => console.error('warn: ' + w));
  const ms = Date.now() - t0;
  console.log(`build ${ms} ms`); try { console.error('peak_rss_mb ' + Math.round(+/VmHWM:\s+(\d+)/.exec(fs.readFileSync('/proc/self/status', 'utf8'))[1] / 1024)); } catch (e) {}
  console.log('OC_DECK ' + JSON.stringify({ schema: 1, slug: path.basename(dir), version: deck.version || 1, slides: slides.length }));
});
