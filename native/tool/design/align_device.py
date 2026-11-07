#!/usr/bin/env python3
"""The alignment gate, device layer (rules: native/docs/kit/ALIGN-RULES.md; numbers: native/config/align/spec.json).

Takes a uiautomator dump of the screen on a device, turns its bounds from px into dp with the device's density, and
applies the same rules as the JVM layer (`:ui:gate` AlignRules) as far as an accessibility tree can show them.

  python3 -I native/tool/design/align_device.py --serial emulator-5554 --name home [--out DIR]
  python3 -I native/tool/design/align_device.py --xml dump.xml --density 420 [--name NAME]
  python3 -I native/tool/design/align_device.py --self-test

  --serial S     adb device to dump (uiautomator dump + wm density); read-only, no screenshots
  --xml F        a dump already on disk (then --density, the dpi, is required)
  --package P    the app's package (default: the package with the most nodes in the dump)
  --name N       the screen's name in the report (default: the time)
  --out DIR      where <name>.json and <name>.xml go (default: looks-out/align-device)
  --slack DP     tolerance in dp (default: spec.json's slack, 0.5)
  --strict       exit 1 when anything is found

What a dump cannot show, and so is not checked here: ellipses (the tree carries the full words), a glyph's asked-for
size (only its box), text alignment and ink, and boxes cut by a parent (bounds come clipped). A clickable row merges
its words into itself, so the start-edge rule reads standalone words and the row's own start.
Standard library only. Exit 0 = ran (findings are printed), 1 = --strict and findings, 2 = could not run.
"""
import argparse, json, os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
SPEC_FILE = os.path.join(HERE, '..', '..', 'config', 'align', 'spec.json')
PUA = range(0xE000, 0xF900)  # the private use area, where the Phosphor fonts put their glyphs
BOUNDS = re.compile(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]')
SIDE_SCROLLER_MAX_H = 120  # a scrollable this short (dp) is a sideways strip (a chip row), not a page


def load_spec(path=SPEC_FILE):
    with open(path, encoding='utf-8') as f:
        return json.load(f)


class Node:
    __slots__ = ('id', 'parent', 'box', 'kind', 'words', 'tag', 'tap', 'bleed', 'scroller', 'side', 'field', 'cls', 'pkg', 'kids')

    def __init__(self, **kw):
        self.kids = []
        for k, v in kw.items():
            setattr(self, k, v)


def fmt(v):
    return f'{v:.1f}'


def clip_words(words):
    return re.sub(r'\s+', ' ', re.sub(r'\d+', '#', words)).strip()[:40]


def parse(xml_text, density, package=None):
    """Nodes of the app's window in dp, parents first; returns (nodes, width, height)."""
    root = ET.fromstring(xml_text)
    scale = density / 160.0
    raw = []

    def walk(el, parent):
        for child in el:
            if child.tag != 'node':
                continue
            m = BOUNDS.match(child.get('bounds', ''))
            if not m:
                continue
            x0, y0, x1, y1 = (int(v) / scale for v in m.groups())
            nid = len(raw)
            raw.append((nid, parent, (x0, y0, x1, y1), child))
            walk(child, nid)

    walk(root, None)
    if not raw:
        return [], 0, 0
    pkgs = {}
    for _, _, _, el in raw:
        pkgs[el.get('package', '')] = pkgs.get(el.get('package', ''), 0) + 1
    package = package or max(pkgs, key=pkgs.get)
    app = [r for r in raw if r[3].get('package', '') == package]
    width = max(r[2][2] for r in app)
    height = max(r[2][3] for r in app)
    ids = {r[0] for r in app}
    nodes = []
    for nid, parent, (x0, y0, x1, y1), el in app:
        text = el.get('text', '') or ''
        desc = el.get('content-desc', '') or ''
        tap = el.get('clickable') == 'true' or el.get('long-clickable') == 'true'
        glyph = len(text) == 1 and ord(text) in PUA
        kind = 'glyph' if glyph else ('text' if text and not tap else 'other')
        scrollable = el.get('scrollable') == 'true'
        nodes.append(Node(
            id=nid, parent=parent if parent in ids else None, box=(x0, y0, x1, y1), kind=kind,
            words=text or desc, tag=el.get('resource-id') or None, tap=tap,
            bleed=tap and x0 <= 0.5 and x1 >= width - 0.5, scroller=scrollable,
            side=scrollable and (y1 - y0) <= SIDE_SCROLLER_MAX_H, field='EditText' in el.get('class', ''),
            cls=el.get('class', ''), pkg=package,
        ))
    by = {n.id: n for n in nodes}
    for n in nodes:
        if n.parent is not None:
            by[n.parent].kids.append(n)
    return nodes, width, height


class Walk:
    def __init__(self, nodes, w, h, spec, slack=None):
        self.nodes, self.w, self.h, self.spec = nodes, w, h, spec
        self.s = spec['slack'] if slack is None else slack
        self.by = {n.id: n for n in nodes}
        self.found = []
        g, rail, tile = spec['gutter'], spec['rail'], spec['iconTile']
        self.edges = sorted({g, rail} | {g + c for c in spec['leadColumns']} | {rail + c for c in spec['leadColumns']})
        self.centres = [g + tile / 2, rail + tile / 2]

    def up(self, n):
        p = self.by.get(n.parent) if n.parent is not None else None
        while p is not None:
            yield p
            p = self.by.get(p.parent) if p.parent is not None else None

    def desc(self, n):
        for k in n.kids:
            yield k
            yield from self.desc(k)

    def visible(self, n):
        x0, y0, x1, y1 = n.box
        return x1 - x0 > self.s and y1 - y0 > self.s and x1 > 0 and x0 < self.w and y1 > 0 and y0 < self.h

    def row(self, n):
        return n.tap and n.bleed

    def row_of(self, n):
        return next((a for a in self.up(n) if self.row(a)), None)

    def control_of(self, n):
        return next((a for a in self.up(n) if (a.tap and not a.bleed) or a.field), None)

    def side(self, n):
        return any(a.side for a in self.up(n))

    def subject(self, n):
        if n.kind == 'glyph':
            label = self.label_of(n)
            base = f'icon by "{clip_words(label.words)}"' if label else 'icon'
        else:
            words = n.words or next((d.words for d in self.desc(n) if d.words), '')
            base = f'"{clip_words(words)}"' if words else '(unnamed)'
        return f'{base} [{n.tag}]' if n.tag else base

    def add(self, n, rule, detail):
        x0, y0, x1, y1 = n.box
        self.found.append({'rule': rule, 'subject': self.subject(n), 'detail': detail,
                           'bounds_dp': [round(x0, 1), round(y0, 1), round(x1, 1), round(y1, 1)]})

    def run(self):
        for n in self.nodes:
            if not self.visible(n):
                continue
            self.target(n)
            if n.kind == 'glyph':
                self.icon_size(n)
                self.icon_label(n)
        self.start_edges()
        self.gaps()
        self.rows()
        seen, out = set(), []
        for f in self.found:
            k = (f['rule'], f['subject'], f['detail'])
            if k not in seen:
                seen.add(k)
                out.append(f)
        return out

    # start edges: standalone words, glyphs and the start of non-row controls
    def leading(self):
        out = []
        for n in self.nodes:
            if not self.visible(n) or self.side(n) or n.side:
                continue
            if n.kind in ('text', 'glyph'):
                if self.control_of(n) is not None:
                    continue
            elif not (n.tap and not n.bleed and n.words and self.control_of(n) is None):
                continue
            x = n.box[0]
            if x > self.w / 2 or self.follows(n, x):  # the end half holds trailing parts, not columns
                continue
            out.append((n, x))
        return out

    def follows(self, n, x):
        r = self.row_of(n)
        scope = list(self.desc(r)) if r else (self.by[n.parent].kids if n.parent is not None else [])
        for o in scope:
            if o is n or o.kind != 'text' or not self.visible(o):
                continue
            if o.box[2] <= x + self.s and overlap_y(o.box, n.box) > min(o.box[3] - o.box[1], n.box[3] - n.box[1]) / 2:
                return True
        return False

    def on_edge(self, n, x):
        if any(abs(e - x) <= self.s for e in self.edges):
            return True
        if n.kind == 'glyph' and any(abs(c - (n.box[0] + n.box[2]) / 2) <= self.s for c in self.centres):
            return True
        # a control's box: a 48 dp icon button 4 dp from the edge puts its glyph on the gutter
        return n.kind == 'other' and abs(x - self.spec['grid']) <= self.s

    def start_edges(self):
        cols = ', '.join(fmt(e) for e in self.edges)
        for n, x in self.leading():
            if not self.on_edge(n, x):
                what = {'glyph': 'icon starts', 'text': 'words start'}.get(n.kind, 'control starts')
                self.add(n, 'start-edge', f"{what} at x {fmt(x)} dp; the page's columns are {cols}")

    def edge_counts(self):
        counts = {}
        for _, x in self.leading():
            k = round(x * 2) / 2
            counts[k] = counts.get(k, 0) + 1
        return dict(sorted(counts.items()))

    def gaps(self):
        for parent in self.nodes:
            blocks = sorted((k for k in parent.kids if self.visible(k) and k.kind != 'glyph' and not self.side(k)),
                            key=lambda k: (k.box[1], k.box[0]))
            for i, a in enumerate(blocks):
                below = next((c for c in blocks[i + 1:] if overlap_x(a.box, c.box) > self.s
                              and c.box[1] >= (a.box[1] + a.box[3]) / 2 and not nested(a.box, c.box)), None)
                if below is None or (self.row(a) and self.row(below)):
                    continue
                gap = below.box[1] - a.box[3]
                if self.s < gap <= self.spec['stepMax'] + self.s and not self.on_step(gap):
                    self.add(below, 'gap-step', f"{fmt(gap)} dp under {self.subject(a)}; spacing steps are multiples of "
                                                f"{fmt(self.spec['grid'])} (or the {fmt(self.spec['lineGap'])} dp line gap)")

    def on_step(self, gap):
        g = self.spec['grid']
        return abs(gap - round(gap / g) * g) <= self.s or abs(gap - self.spec['lineGap']) <= self.s

    def label_of(self, g):
        holder = self.row_of(g) or self.control_of(g)
        scope = list(self.desc(holder)) if holder else (self.by[g.parent].kids if g.parent is not None else [])
        best = None
        for t in scope:
            if t.kind != 'text' or not self.visible(t):
                continue
            dx = t.box[0] - g.box[2]
            if dx >= -self.s and dx <= self.spec['labelReach'] and overlap_y(t.box, g.box) > 0:
                if best is None or dx < best[0]:
                    best = (dx, t)
        return best[1] if best else None

    def icon_label(self, g):
        label = self.label_of(g)
        if label is None:
            return
        off = abs((g.box[1] + g.box[3]) / 2 - (label.box[1] + label.box[3]) / 2)
        if off > self.s:
            self.add(g, 'icon-label', f'icon centre y {fmt((g.box[1] + g.box[3]) / 2)}, label "{clip_words(label.words)}" '
                                      f'centre {fmt((label.box[1] + label.box[3]) / 2)}: {fmt(off)} dp off')

    def icon_size(self, g):
        x0, y0, x1, y1 = g.box
        size = min(x1 - x0, y1 - y0)
        if not any(abs(size - k) <= self.s for k in self.spec['iconSizes']):
            sizes = ', '.join(fmt(k) for k in self.spec['iconSizes'])
            self.add(g, 'icon-size', f'glyph box {fmt(x1 - x0)} x {fmt(y1 - y0)} dp; KitIconSize is {sizes} '
                                     '(smaller = squeezed and cut)')

    def target(self, n):
        x0, y0, x1, y1 = n.box
        t = self.spec['target']
        if n.tap and min(x1 - x0, y1 - y0) < t - self.s:
            self.add(n, 'target', f'{fmt(x1 - x0)} x {fmt(y1 - y0)} dp, needs {fmt(t)}')

    def rows(self):
        groups = {}
        for r in self.nodes:
            if self.row(r) and self.visible(r) and not self.side(r):
                groups.setdefault(r.parent, []).append(r)
        for rows in groups.values():
            ends = [(r, e) for r in rows if (e := self.trailing_end(r)) is not None]
            if len(ends) >= 2:
                common = mode([e for _, e in ends])
                for r, e in ends:
                    if abs(e - common) > self.s:
                        self.add(r, 'end-edge', f"trailing part ends at x {fmt(e)} dp, the list's rows at {fmt(common)}")
            kinds = {}
            for r in rows:
                kinds.setdefault((r.words.count('\n'), any(d.kind == 'glyph' for d in self.desc(r))), []).append(r)
            for same in kinds.values():
                if len(same) < 2:
                    continue
                common = mode([r.box[3] - r.box[1] for r in same])
                for r in same:
                    hgt = r.box[3] - r.box[1]
                    if abs(hgt - common) > self.s:
                        self.add(r, 'row-height', f"{fmt(hgt)} dp tall, the list's rows of this kind {fmt(common)}")

    def trailing_end(self, r):
        cx = (r.box[0] + r.box[2]) / 2
        ends = [d.box[2] for d in self.desc(r) if self.visible(d) and d.kind != 'other' and d.box[0] > cx]
        ends += [d.box[2] for d in self.desc(r) if self.visible(d) and d.tap and d.box[0] > cx]
        return max(ends) if ends else None


def overlap_x(a, b):
    return min(a[2], b[2]) - max(a[0], b[0])


def overlap_y(a, b):
    return min(a[3], b[3]) - max(a[1], b[1])


def nested(a, b):
    def holds(o, i):
        return i[0] >= o[0] - .5 and i[2] <= o[2] + .5 and i[1] >= o[1] - .5 and i[3] <= o[3] + .5
    return holds(a, b) or holds(b, a)


def mode(values):
    counts = {}
    for v in values:
        k = round(v * 2) / 2
        counts[k] = counts.get(k, 0) + 1
    return sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))[0][0]


def check(xml_text, density, spec, package=None, slack=None):
    nodes, w, h = parse(xml_text, density, package)
    walk = Walk(nodes, w, h, spec, slack)
    return {'window_dp': [round(w, 1), round(h, 1)], 'density': density, 'edges': walk.edge_counts(), 'findings': walk.run()}


def adb(serial, *args):
    r = subprocess.run(['adb', '-s', serial, *args], capture_output=True, text=True, timeout=60)
    if r.returncode != 0:
        raise SystemExit(f'align_device: adb {" ".join(args)} failed: {r.stderr.strip()}')
    return r.stdout


def device_density(serial):
    out = adb(serial, 'shell', 'wm', 'density')
    override = re.search(r'Override density:\s*(\d+)', out)
    physical = re.search(r'Physical density:\s*(\d+)', out)
    m = override or physical
    if not m:
        raise SystemExit(f'align_device: could not read the density from: {out!r}')
    return int(m.group(1))


def dump(serial):
    path = '/sdcard/oc_align_dump.xml'
    adb(serial, 'shell', 'uiautomator', 'dump', path)
    text = adb(serial, 'exec-out', 'cat', path)
    adb(serial, 'shell', 'rm', '-f', path)
    return text


def report(name, result):
    lines = [f"align {name}: {len(result['findings'])} finding(s), window {result['window_dp'][0]} x {result['window_dp'][1]} dp "
             f"at {result['density']} dpi"]
    lines.append('  start edges: ' + (', '.join(f'{fmt(k)} ({v})' for k, v in result['edges'].items()) or '-'))
    by_rule = {}
    for f in result['findings']:
        by_rule.setdefault(f['rule'], []).append(f)
    for rule in sorted(by_rule):
        lines.append(f'  {rule} ({len(by_rule[rule])})')
        for f in by_rule[rule]:
            lines.append(f"    {f['subject']}: {f['detail']}")
    return '\n'.join(lines)


SELF_TEST_XML = '''<?xml version="1.0" encoding="UTF-8"?><hierarchy rotation="0">
<node class="android.widget.FrameLayout" package="app" bounds="[0,0][1050,2400]">
 <node class="android.view.View" package="app" bounds="[0,0][1050,2400]">
  <node text="Page edge" class="android.widget.TextView" package="app" bounds="[40,100][600,150]"/>
  <node text="Double margin" class="android.widget.TextView" package="app" bounds="[80,160][600,210]"/>
  <node text="Ten under" class="android.widget.TextView" package="app" bounds="[40,235][600,285]"/>
  <node class="android.view.View" clickable="true" package="app" bounds="[40,500][100,560]"/>
  <node class="android.view.View" package="app" bounds="[0,600][1050,1040]">
   <node text="Row one" class="android.view.View" clickable="true" package="app" bounds="[0,600][1050,740]">
    <node text="&#xE13A;" class="android.widget.TextView" package="app" bounds="[950,645][1000,695]"/>
   </node>
   <node text="Row two" class="android.view.View" clickable="true" package="app" bounds="[0,740][1050,900]">
    <node text="&#xE13A;" class="android.widget.TextView" package="app" bounds="[925,795][965,835]"/>
   </node>
   <node text="Row three" class="android.view.View" clickable="true" package="app" bounds="[0,900][1050,1040]">
    <node text="&#xE13A;" class="android.widget.TextView" package="app" bounds="[950,945][1000,995]"/>
   </node>
  </node>
 </node>
</node></hierarchy>'''


def self_test():
    """Each rule the device can read fires on the fixture above (400 dpi: 2.5 px per dp)."""
    spec = load_spec()
    result = check(SELF_TEST_XML, 400, spec)
    rules = {(f['rule'], f['subject']) for f in result['findings']}
    expect = {
        ('start-edge', '"Double margin"'),  # 32 dp
        ('gap-step', '"Ten under"'),  # 94 - 84 = 10 dp under "Double margin"
        ('target', '(unnamed)'),  # 24 x 24 dp
        ('icon-size', 'icon'),  # 16 dp glyph box in row two
        ('end-edge', '"Row two"'),  # chevron ends at 386 dp, row one's at 400
        ('row-height', '"Row two"'),  # 64 dp against 56 dp
    }
    missing = {e for e in expect if not any(r == e[0] and s.startswith(e[1].split(' [')[0]) for r, s in rules)}
    unexpected = {r for r, s in rules if s.startswith('"Page edge"')}
    assert result['edges'] == {16.0: 2, 32.0: 1}, result['edges']
    if missing or unexpected:
        print('self-test FAILED', 'missing', missing, 'unexpected', unexpected, json.dumps(result, indent=1))
        return 1
    print(f"self-test passed: {len(result['findings'])} findings, rules {sorted({r for r, _ in rules})}")
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--serial')
    ap.add_argument('--xml')
    ap.add_argument('--density', type=int)
    ap.add_argument('--package')
    ap.add_argument('--name', default=time.strftime('%Y%m%d-%H%M%S'))
    ap.add_argument('--out', default=os.path.join(HERE, '..', '..', '..', 'looks-out', 'align-device'))
    ap.add_argument('--slack', type=float)
    ap.add_argument('--strict', action='store_true')
    ap.add_argument('--self-test', action='store_true')
    a = ap.parse_args()
    if a.self_test:
        return self_test()
    if a.xml:
        if not a.density:
            print('align_device: --xml needs --density (dpi)', file=sys.stderr)
            return 2
        with open(a.xml, encoding='utf-8') as f:
            xml_text = f.read()
        density = a.density
    elif a.serial:
        density = a.density or device_density(a.serial)
        xml_text = dump(a.serial)
    else:
        print('align_device: give --serial or --xml (or --self-test)', file=sys.stderr)
        return 2
    result = check(xml_text, density, load_spec(), a.package, a.slack)
    result['name'] = a.name
    os.makedirs(a.out, exist_ok=True)
    with open(os.path.join(a.out, f'{a.name}.json'), 'w', encoding='utf-8') as f:
        json.dump(result, f, indent=1, ensure_ascii=False)
    if a.serial:
        with open(os.path.join(a.out, f'{a.name}.xml'), 'w', encoding='utf-8') as f:
            f.write(xml_text)
    print(report(a.name, result))
    print(f"wrote {os.path.normpath(os.path.join(a.out, a.name + '.json'))}")
    return 1 if a.strict and result['findings'] else 0


if __name__ == '__main__':
    sys.exit(main())
