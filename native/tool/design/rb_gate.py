#!/usr/bin/env python3
"""The native UI rebuild gate's checks (docs/design/native-ui-rebuild/GOLDEN-STANDARD.md §8-§10).

Subcommands (gate_order.sh calls them in this order; each exits non-zero on a failure):
  static   --order NN          write set, numbers cited from Flutter, Material and kit-only rules, copy, signatures, commits
  fetch    --order NN          copy the order's Flutter goldens into looks-gate/flutter/<ref9>/
  modules  --order NN          print the Gradle modules whose desktopTest runs the order's gate cases
  chunks   --order NN          one line per part: id, golden count, the part's write globs (chunks.py prints it)
  RB_PARTS="part-a part-b"     restricts static (write set), fetch, evaluate and the manifest to those parts of NN
  evaluate --order NN          measure looks-out/gate trios (rasterisation-tolerant, RB_METRICS_JAVA), apply
                               gate_thresholds.tsv and waivers, check the matrix, post the tracker
Standard library only; run with python3 -I from the repository root.
"""
import argparse, fnmatch, glob, json, os, re, socket, subprocess, sys, urllib.request

FREF = '8302de53773adef45eaaad016df2bd3cd51d1ebe'
GREF = '1bbdc3f9c7779148a689186e6d5ca3cd59062ab5'
ROOT = subprocess.run(['git', 'rev-parse', '--show-toplevel'], capture_output=True, text=True).stdout.strip() or os.getcwd()
DOC = os.path.join(ROOT, 'docs/design/native-ui-rebuild')
FLUTTER_REPO = os.environ.get('FLUTTER_REPO', os.path.expanduser('~/wt/flutter-ref'))
GLASS_REPO = os.environ.get('GLASS_REPO', os.path.expanduser('~/Storage/Code/oc_app'))
BASE = os.environ.get('RB_BASE', 'native/looks-pass-2026-11-04')
DEV = os.environ.get('RB_DEV') == '1'
OUTG = os.path.join(ROOT, 'looks-out/gate')

MATERIAL_ALLOW = {
    'native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/KitTheme.kt',
    'native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/theme/MaterialBridge.kt',
    'native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/theme/KitThemePreview.kt',
    'native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitMotionScheme.kt',
    'native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitRefresh.kt',
    'native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitGrow.kt',
    'native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/KitStopAndSendPill.kt',
    'native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/transcript/TranscriptParts.kt',
}
SCREEN_FORBIDDEN = re.compile(
    r'(?<![\w.])(Text|BasicText|Icon|Image|Canvas|Surface|Card|Button|TextButton|OutlinedButton|IconButton|TextField|OutlinedTextField|'
    r'BasicTextField|Switch|Checkbox|RadioButton|Divider|HorizontalDivider|VerticalDivider|Scaffold|TopAppBar|AlertDialog|ModalBottomSheet|'
    r'DropdownMenu|Snackbar|CircularProgressIndicator|LinearProgressIndicator)\s*\(|'
    r'\.(background|border|clip|shadow|drawBehind|drawWithContent|drawWithCache|graphicsLayer|clickable|combinedClickable)\s*\(')
COPY = re.compile(r'(KitText\(\s*"|\b(text|label|title|supporting|message|hint|placeholder|contentDescription)\s*=\s*")[^"]*[A-Za-z][^"]*\s[^"]*"')
NUM = re.compile(r'(?<![\w.$])(0x[0-9A-Fa-f]+|\d+\.\d+|\.\d+|\d+)(?=[fFL]?\b|\.dp|\.sp|\.ms|\b)')
CITE = re.compile(r'//\s*(FG?|NV):\s*(.*)$')
MAIN_KT = re.compile(r'^native/.+/src/(commonMain|androidMain|desktopMain|iosMain)/.+\.kt$')
WAIVER_CODES = {'NO_NATIVE_STATE': 'screen', 'DEFERRED': 'screen', 'PLATFORM_ONLY': 'any', 'GALLERY_FOREIGN': 'gallery'}
WAIVER_CAP = {'kit': 0.05, 'screen': 0.25, 'gallery': 0.30}


def run(*a, check=True, cwd=ROOT):
    r = subprocess.run(list(a), capture_output=True, text=True, cwd=cwd)
    if check and r.returncode != 0:
        raise SystemExit(f'command failed: {" ".join(a)}\n{r.stderr}')
    return r.stdout


def order_file(nn):
    hits = sorted(glob.glob(os.path.join(DOC, 'orders', f'{nn}-*.md')))
    if not hits:
        raise SystemExit(f'no order file for {nn}')
    return hits[0]


def header(nn):
    h = {}
    for line in open(order_file(nn), encoding='utf-8'):
        line = line.rstrip('\n')
        if line == '---':
            break
        k, _, v = line.partition(': ')
        h[k] = v
    h['WRITES'] = h.get('WRITES', '').split()
    return h


def selected_parts():
    """RB_PARTS: the parts a single-part chunk gates (space separated tracker ids); None means the whole order."""
    v = os.environ.get('RB_PARTS', '').split()
    return set(v) or None


def manifest(nn, whole=False):
    rows = []
    for line in open(os.path.join(DOC, 'orders', f'{nn}.gate.tsv'), encoding='utf-8'):
        if line.startswith('#') or not line.strip():
            continue
        rows.append(line.rstrip('\n').split('\t'))
    sel = None if whole else selected_parts()
    if sel:
        unknown = sel - {r[1] for r in rows if r[0] == 'part'}
        if unknown:
            print(f'RB_PARTS: not parts of order {nn}: {" ".join(sorted(unknown))}')
            sys.exit(2)
        rows = [r for r in rows if r[1] in sel]
    return rows


SHARED_GLOB = re.compile(r'/rb/o\d\d/|strings_rb\d\d\.xml$|/waivers/\d\d\.tsv$|/evidence/\d\d\.md$')


def part_writes(nn):
    """part id -> its write globs: the native files the order's table gives it, with the WRITES globs of their tests and
    screenshots (<Name>Test.kt, screenshots/<Name>/), plus the order's shared globs (its rb/o<NN> gate cases, strings,
    waivers and evidence). A folded part (verify only) gets the shared globs alone."""
    writes = header(nn)['WRITES']
    shared = [g for g in writes if SHARED_GLOB.search(g)]
    out = {}
    for line in open(order_file(nn), encoding='utf-8'):
        m = re.match(r'\|\s*\d+\s*\|\s*`(part-[\w-]+)`\s*\|', line)
        if not m:
            continue
        cells = [c.strip() for c in line.strip().strip('|').split('|')]
        files = re.findall(r'`(native/[^`]+)`', cells[6] if len(cells) > 6 else '')
        globs = []
        for f in files:
            stem = os.path.basename(f).rsplit('.', 1)[0]
            globs.append(f)
            globs += [g for g in writes if g.endswith(f'/{stem}Test.kt') or f'/screenshots/{stem}/' in g]
        out[m.group(1)] = list(dict.fromkeys(globs + shared))
    return out


def write_globs(nn):
    sel = selected_parts()
    if not sel:
        return header(nn)['WRITES']
    pw = part_writes(nn)
    return list(dict.fromkeys(g for pid in sorted(sel) for g in pw.get(pid, [])))


def cmd_chunks(nn):
    """One line per part of the order: <part id> TAB <golden count> TAB <write globs, space separated>."""
    man = manifest(nn, whole=True)
    pw = part_writes(nn)
    for r in man:
        if r[0] != 'part':
            continue
        g = sum(1 for x in man if x[0] == 'golden' and x[1] == r[1])
        print(f'{r[1]}\t{g}\t{" ".join(pw.get(r[1], []))}')
    return 0


def glob_rx(g):
    out, i = '', 0
    while i < len(g):
        if g.startswith('**/', i):
            out += '(?:.*/)?'; i += 3
        elif g.startswith('**', i):
            out += '.*'; i += 2
        elif g[i] == '*':
            out += '[^/]*'; i += 1
        else:
            out += re.escape(g[i]); i += 1
    return re.compile('^' + out + '$')


_src = {}


def flutter_lines(path, glass=False):
    key = (path, glass)
    if key not in _src:
        repo, ref = (GLASS_REPO, GREF) if glass else (FLUTTER_REPO, FREF)
        r = subprocess.run(['git', '-C', repo, 'show', f'{ref}:{path}'], capture_output=True, text=True)
        _src[key] = r.stdout.splitlines() if r.returncode == 0 else None
    return _src[key]


def norm(tok):
    t = tok.rstrip('fFL')
    try:
        return int(t, 16) if t.lower().startswith('0x') else float(t)
    except ValueError:
        return None


def nums_in(text):
    return {v for v in (norm(m.group(1)) for m in NUM.finditer(text)) if v is not None}


def strip_strings(code):
    return re.sub(r'"(?:\\.|[^"\\])*"', '""', code)


# ------------------------------------------------------------------ static
def added_lines(base):
    """(path, line_no, text) for every line added since the merge base, committed or not."""
    mb = run('git', 'merge-base', 'HEAD', base).strip()
    diff = run('git', 'diff', '-U0', mb, '--', '.')
    out, path, ln = [], None, 0
    for line in diff.splitlines():
        if line.startswith('+++ '):
            path = line[6:] if line.startswith('+++ b/') else None
        elif line.startswith('@@'):
            ln = int(re.search(r'\+(\d+)', line).group(1))
        elif line.startswith('+') and path:
            out.append((path, ln, line[1:]))
            ln += 1
    for u in run('git', 'ls-files', '--others', '--exclude-standard').splitlines():
        if u.startswith(('looks-gate/', 'looks-out/')) or not os.path.isfile(os.path.join(ROOT, u)):
            continue
        try:
            for i, t in enumerate(open(os.path.join(ROOT, u), encoding='utf-8').read().splitlines(), 1):
                out.append((u, i, t))
        except UnicodeDecodeError:
            pass
    return mb, out


def fun_sigs(text):
    """public/top-level functions: name -> list of [(param, type, has_default)]."""
    sigs = {}
    for m in re.finditer(r'^[ \t]*((?:@\w+[^\n]*\n[ \t]*)*)((?:public |internal |private |protected |inline |override |suspend )*)fun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)\s*\(', text, re.M):
        if 'private' in m.group(2) or 'internal' in m.group(2):
            continue
        i, depth, j = m.end() - 1, 0, m.end() - 1
        for j in range(i, len(text)):
            if text[j] in '([{<':
                depth += 1 if text[j] != '<' or text[j - 1] != '-' else 0
            elif text[j] in ')]}>':
                if text[j] == '>' and text[j - 1] == '-':
                    continue
                depth -= 1
                if depth == 0:
                    break
        body = text[i + 1:j]
        parts, d, cur = [], 0, ''
        for ch in body:
            if ch in '([{<':
                d += 1
            elif ch in ')]}>':
                d -= 1
            if ch == ',' and d == 0:
                parts.append(cur); cur = ''
            else:
                cur += ch
        if cur.strip():
            parts.append(cur)
        ps = []
        for p in parts:
            p = re.sub(r'@\w+(\([^)]*\))?', '', p).strip()
            mm = re.match(r'(?:vararg |noinline |crossinline )*(\w+)\s*:\s*(.*)$', p, re.S)
            if not mm:
                continue
            typ, _, default = mm.group(2).partition('=')
            ps.append((mm.group(1), re.sub(r'\s+', '', typ), bool(default.strip())))
        sigs.setdefault(m.group(3), []).append(ps)
    return sigs


def compatible(old, new):
    names = [p[0] for p in new]
    pos = -1
    for name, typ, _ in old:
        if name not in names:
            return False
        k = names.index(name)
        if k < pos or new[k][1] != typ:
            return False
        pos = k
    olds = {p[0] for p in old}
    return all(p[2] for p in new if p[0] not in olds)


def cmd_static(nn):
    h = header(nn)
    fails, warns = [], []
    rxs = [glob_rx(g) for g in write_globs(nn)]
    mb, added = added_lines(BASE)
    changed = set(run('git', 'diff', '--name-only', mb).split()) | {
        l[3:] for l in run('git', 'status', '--porcelain').splitlines() if l[3:] and not l[3:].startswith(('looks-gate/', 'looks-out/'))}
    for p in sorted(changed):
        if p.startswith(('looks-gate/', 'looks-out/')):
            fails.append(f'W0 generated gate output is committed: {p}')
        elif not any(r.match(p) for r in rxs):
            fails.append(f'W1 outside the write set: {p}')
    dirty = [l for l in run('git', 'status', '--porcelain').splitlines() if not l[3:].startswith(('looks-gate/', 'looks-out/'))]
    if dirty and not DEV:
        fails.append(f'W2 uncommitted changes ({len(dirty)} paths): commit first, or set RB_DEV=1 while iterating')
    nv = 0
    for path, ln, text in added:
        if not MAIN_KT.match(path):
            continue
        code, _, comment = text.partition('//')
        code_s = strip_strings(code)
        stripped = text.strip()
        if stripped.startswith(('import ', 'package ', '*', '/*', '//', '@file:')):
            continue
        cm = CITE.search(text)
        if cm and cm.group(1) == 'NV':
            nv += 1
        if 'androidx.compose.material3' in text or re.search(r'\bMaterialTheme\b', code_s):
            if path not in MATERIAL_ALLOW:
                fails.append(f'M1 Material outside the bridge: {path}:{ln}')
        if re.match(r'^native/(feature/|ui/shell/)', path) and SCREEN_FORBIDDEN.search(code_s):
            fails.append(f'K1 screens arrange kit parts only, found a drawing call: {path}:{ln}: {stripped[:90]}')
        if COPY.search(code) and not (cm and cm.group(1) == 'NV'):
            fails.append(f'C1 user-visible words typed in code (use ui/words strings): {path}:{ln}')
        found = {n for n in nums_in(code_s) if n not in (0.0, 1.0)}
        if not found:
            continue
        if not cm:
            fails.append(f'L1 number without a Flutter citation: {path}:{ln}: {stripped[:90]}')
            continue
        if cm.group(1) == 'NV':
            continue
        cited = set()
        for ref in re.findall(r'([\w./-]+\.(?:dart|json|arb)):([\d,\-]+)', cm.group(2)):
            lines = flutter_lines(ref[0], glass=cm.group(1) == 'FG')
            if lines is None:
                fails.append(f'L2 cited file not at the ref: {path}:{ln}: {ref[0]}')
                continue
            for part in ref[1].split(','):
                a, _, b = part.partition('-')
                for k in range(int(a), int(b or a) + 1):
                    if 1 <= k <= len(lines):
                        cited |= nums_in(lines[k - 1])
        missing = [n for n in found if n not in cited]
        if missing:
            fails.append(f'L3 number not on the cited Flutter line(s): {path}:{ln}: {missing}')
    if nv > 20:
        fails.append(f'L4 {nv} "// NV:" lines (at most 20 per order)')
    # signatures of pre-existing public functions
    for p in sorted(changed):
        if not p.endswith('.kt') or not MAIN_KT.match(p):
            continue
        old = subprocess.run(['git', 'show', f'{mb}:{p}'], capture_output=True, text=True, cwd=ROOT)
        if old.returncode != 0:
            continue
        new_text = open(os.path.join(ROOT, p), encoding='utf-8').read() if os.path.exists(os.path.join(ROOT, p)) else ''
        o_s, n_s = fun_sigs(old.stdout), fun_sigs(new_text)
        for name, overloads in o_s.items():
            for ov in overloads:
                if not any(compatible(ov, nv_) for nv_ in n_s.get(name, [])):
                    fails.append(f'S1 existing signature broken: {p}: fun {name}({", ".join(x[0] for x in ov)})')
    # commits
    if not DEV:
        oid = os.path.basename(order_file(nn))[:-3]
        log = run("git", "log", "--no-merges", "--format=%H%x00%s%x00%b%x01", f"{mb}..HEAD")
        for entry in filter(None, (e.strip() for e in log.split('\x01'))):
            sha, subj, body = (entry.split('\x00') + ['', ''])[:3]
            if not re.match(r'^(feat|fix|test|refactor|docs|chore)\([a-z0-9-]+\): \S.* \[skip ci\]$', subj):
                fails.append(f'G1 commit subject: {sha[:9]} "{subj}"')
            if f'Order: {oid}' not in body or not re.search(r'^Engine: (sonnet|codex|commandcode)$', body, re.M):
                fails.append(f'G2 commit trailers (Order: {oid} / Engine: ...): {sha[:9]}')
    for f in fails:
        print('FAIL', f)
    for w in warns:
        print('WARN', w)
    print(f'static: {len(fails)} failures')
    return 1 if fails else 0


# ------------------------------------------------------------------ fetch / modules
def ref_dir(ref):
    return os.path.join(ROOT, 'looks-gate/flutter', (GREF if ref == 'glass' else FREF)[:9])


def cmd_fetch(nn):
    n = 0
    for r in manifest(nn):
        if r[0] != 'golden':
            continue
        ref, path = r[2], r[3]
        d = ref_dir(ref)
        os.makedirs(d, exist_ok=True)
        dst = os.path.join(d, os.path.basename(path))
        if os.path.exists(dst):
            continue
        repo, sha = (GLASS_REPO, GREF) if ref == 'glass' else (FLUTTER_REPO, FREF)
        data = subprocess.run(['git', '-C', repo, 'show', f'{sha}:{path}'], capture_output=True)
        if data.returncode != 0:
            print(f'FAIL F1 golden not found at the ref: {path} ({ref})')
            return 1
        open(dst, 'wb').write(data.stdout)
        n += 1
    print(f'fetch: {n} new goldens')
    return 0


def cmd_modules(nn):
    """Every Gradle module that has gate cases for this order (a desktopTest package ending in rb/o<NN>).
    The manifest's module column says where each case is expected; evaluate fails a golden no module rendered."""
    mods = []
    for d in sorted(glob.glob(os.path.join(ROOT, 'native', '*', '*', 'src', 'desktopTest', 'kotlin', '**', 'rb', f'o{nn}'), recursive=True)):
        rel = os.path.relpath(d, os.path.join(ROOT, 'native')).split(os.sep)
        m = f':{rel[0]}:{rel[1]}'
        if m not in mods:
            mods.append(m)
    print(' '.join(mods))
    return 0


# ------------------------------------------------------------------ evaluate
def thresholds():
    """tier -> (box %, blob px, ink band, shift px). Older 3-column files read with the 2026-11-05 defaults."""
    t = {}
    for line in open(os.path.join(ROOT, 'native/tool/design/gate_thresholds.tsv'), encoding='utf-8'):
        if line.startswith('#') or not line.strip():
            continue
        c = line.rstrip('\n').split('\t')
        ink = float(c[3]) if len(c) > 4 and re.fullmatch(r'[0-9.]+', c[3]) else 0.10
        shift = float(c[4]) if len(c) > 4 and re.fullmatch(r'[0-9.]+', c[4]) else 1.75
        t[c[0]] = (float(c[1]), int(c[2]), ink, shift)
    return t


# Rasterisation-tolerant metrics (lead calibration 2026-11-05). Flutter's test renderer and Compose desktop draw the same
# glyphs with different anti-aliasing, gamma and sub-pixel placement, so a raw per-pixel diff flags matching text. This
# measure forgives what a 3x3 neighbourhood explains and keeps three checks for real misses: the largest cluster of
# unexplained pixels (a missing dot, a wrong icon or colour), each text line's ink mass (a heavier or lighter weight, a
# missing word) and each line's ink centroid (a block placed 2 px off). Proven on planted mistakes in the commit message.
RB_METRICS_JAVA = r'''
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * Rasterisation-tolerant golden metrics for the native UI rebuild gate (rb_gate.py evaluate).
 * stdin: "shot\tflutter.png\tnative.png[\tdiff.png]" per line.
 * stdout: shot, size, box %, blob px, worst line ink ratio, lines, worst line shift px, differing px.
 *
 * A pixel differs only when neither image can explain it from the other's 3x3 neighbourhood: for each channel the
 * pixel must lie inside [min-T, max+T] of the other image's 3x3 window (anti-aliasing, gamma and 1 px glyph placement
 * differences fall inside; a 2 px move, a wrong colour or a missing mark do not). Box %: differing pixels over the
 * ink box. Blob: the largest 8-connected cluster of differing pixels (no erosion). Lines: the ink box split at blank
 * rows; per line, native ink mass over Flutter's (a heavier or lighter weight, a missing word) and how far its ink
 * centroid moved (a block placed 2 px off), since both survive the neighbourhood tolerance.
 */
public class RbMetrics {
    static final int T = Integer.getInteger("rb.tol", 24);
    static final int R = Integer.getInteger("rb.radius", 1);
    static final double MIN_BAND_INK = Double.parseDouble(System.getProperty("rb.minBandInk", "4000"));

    static boolean rowInk(int[] f, int[] n, int w, int x0, int x1, int y, int bg) {
        for (int x = x0; x <= x1; x++) { int i = y * w + x; if (dist(f[i], bg) > T || dist(n[i], bg) > T) return true; }
        return false;
    }

    public static void main(String[] a) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = in.readLine()) != null) {
            String[] c = line.split("\t");
            if (c.length < 3) continue;
            try { System.out.println(c[0] + "\t" + measure(ImageIO.read(new File(c[1])), ImageIO.read(new File(c[2])), c.length > 3 ? c[3] : null)); }
            catch (Exception e) { System.out.println(c[0] + "\terror " + e); }
        }
    }

    static int[] px(BufferedImage im) {
        int w = im.getWidth(), h = im.getHeight();
        return im.getRGB(0, 0, w, h, null, 0, w);
    }

    static int ch(int p, int s) { return (p >> s) & 0xFF; }

    static int background(int[] f) {
        HashMap<Integer, Integer> n = new HashMap<>();
        for (int i = 0; i < f.length; i += 7) n.merge(f[i] & 0xFFFFFF, 1, Integer::sum);
        return Collections.max(n.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    static int dist(int p, int q) {
        return Math.max(Math.abs(ch(p, 16) - ch(q, 16)), Math.max(Math.abs(ch(p, 8) - ch(q, 8)), Math.abs(ch(p, 0) - ch(q, 0))));
    }

    /** True when [p] is explained by [o]'s window around (x, y). */
    static boolean explained(int p, int[] o, int w, int h, int x, int y) {
        for (int s = 0; s <= 16; s += 8) {
            int lo = 255, hi = 0, v = ch(p, s);
            for (int dy = -R; dy <= R; dy++) for (int dx = -R; dx <= R; dx++) {
                int xx = x + dx, yy = y + dy;
                if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                int q = ch(o[yy * w + xx], s);
                if (q < lo) lo = q;
                if (q > hi) hi = q;
            }
            if (v < lo - T || v > hi + T) return false;
        }
        return true;
    }

    static double lum(int p) { return 0.2126 * ch(p, 16) + 0.7152 * ch(p, 8) + 0.0722 * ch(p, 0); }

    static String measure(BufferedImage fi, BufferedImage ni, String diffOut) throws IOException {
        int w = fi.getWidth(), h = fi.getHeight();
        if (ni.getWidth() != w || ni.getHeight() != h)
            return "size " + ni.getWidth() + "x" + ni.getHeight() + " vs " + w + "x" + h + "\t100\t999999\t0\t0\t999\t999999";
        int[] f = px(fi), n = px(ni);
        int bg = background(f);
        double bgL = lum(bg);
        int x0 = w, y0 = h, x1 = -1, y1 = -1;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            if (dist(f[i], bg) > T || dist(n[i], bg) > T) {
                if (x < x0) x0 = x; if (y < y0) y0 = y; if (x > x1) x1 = x; if (y > y1) y1 = y;
            }
        }
        if (x1 < 0) return "same-size\t0.00\t0\t1.000\t0\t0.00\t0";
        x0 = Math.max(0, x0 - 4); y0 = Math.max(0, y0 - 4); x1 = Math.min(w - 1, x1 + 4); y1 = Math.min(h - 1, y1 + 4);
        boolean[] d = new boolean[w * h];
        long diff = 0;
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            int i = y * w + x;
            if (dist(f[i], n[i]) <= T) continue;
            if (explained(f[i], n, w, h, x, y) && explained(n[i], f, w, h, x, y)) continue;
            d[i] = true; diff++;
        }
        // largest 8-connected cluster
        int best = 0;
        boolean[] seen = new boolean[w * h];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < d.length; i++) {
            if (!d[i] || seen[i]) continue;
            int size = 0; q.add(i); seen[i] = true;
            while (!q.isEmpty()) {
                int k = q.poll(); size++;
                int kx = k % w, ky = k / w;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int xx = kx + dx, yy = ky + dy;
                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                    int j = yy * w + xx;
                    if (d[j] && !seen[j]) { seen[j] = true; q.add(j); }
                }
            }
            best = Math.max(best, size);
        }
        // ink mass per text line band (rows where either image has ink, split at blank rows): native over Flutter.
        // Anti-aliasing moves ink between neighbouring pixels but keeps a line's mass; a heavier or lighter weight, a
        // missing word or a different size changes it.
        double worst = 1.0, shift = 0; int bands = 0;
        int by = y0;
        while (by <= y1) {
            while (by <= y1 && !rowInk(f, n, w, x0, x1, by, bg)) by++;
            if (by > y1) break;
            int ey = by;
            while (ey + 1 <= y1 && rowInk(f, n, w, x0, x1, ey + 1, bg)) ey++;
            double fm = 0, nm = 0;
            for (int y = by; y <= ey; y++) for (int x = x0; x <= x1; x++) {
                int i = y * w + x;
                fm += Math.abs(lum(f[i]) - bgL); nm += Math.abs(lum(n[i]) - bgL);
            }
            if (Math.max(fm, nm) >= MIN_BAND_INK) {
                bands++;
                double r = (nm + 1) / (fm + 1);
                if (Math.abs(Math.log(r)) > Math.abs(Math.log(worst))) worst = r;
                // where the line sits: ink-weighted centroid, Flutter vs native (a moved block moves it whole pixels)
                double fx = 0, fy = 0, nx = 0, ny = 0, fw = 0, nw = 0;
                for (int y = Math.max(0, by - 3); y <= Math.min(h - 1, ey + 3); y++) for (int x = x0; x <= x1; x++) {
                    int i = y * w + x;
                    double a = Math.abs(lum(f[i]) - bgL), b = Math.abs(lum(n[i]) - bgL);
                    fx += a * x; fy += a * y; fw += a; nx += b * x; ny += b * y; nw += b;
                }
                if (fw > 0 && nw > 0) shift = Math.max(shift, Math.max(Math.abs(fx / fw - nx / nw), Math.abs(fy / fw - ny / nw)));
            }
            by = ey + 1;
        }
        if (diffOut != null) {
            BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int i = 0; i < f.length; i++) {
                int g = (int) (lum(n[i]) / 3 + 40);
                o.setRGB(i % w, i / w, d[i] ? 0xE61E1E : (g << 16 | g << 8 | g));
            }
            ImageIO.write(o, "png", new File(diffOut));
        }
        double area = (double) (x1 - x0 + 1) * (y1 - y0 + 1);
        return String.format(Locale.ROOT, "same-size\t%.2f\t%d\t%.3f\t%d\t%.2f\t%d", diff * 100.0 / area, best, worst, bands, shift, diff);
    }
}
'''


def tolerant_metrics(shots):
    """shot -> [size, box %, blob px, ink ratio, lines, shift px, differing px] for every shot with both PNGs in OUTG."""
    pairs = []
    for shot in shots:
        f, n = (os.path.join(OUTG, f'{shot}-{k}.png') for k in ('flutter', 'native'))
        if os.path.exists(f) and os.path.exists(n):
            pairs.append(f'{shot}\t{f}\t{n}\t{os.path.join(OUTG, shot + "-tdiff.png")}')
    if not pairs:
        return {}
    import tempfile
    d = tempfile.mkdtemp(prefix='rbm')
    src = os.path.join(d, 'RbMetrics.java')
    open(src, 'w', encoding='utf-8').write(RB_METRICS_JAVA)
    java = os.path.join(os.environ.get('JAVA_HOME') or os.path.expanduser('~/wt/native-repair-20261102/tools/jdk'), 'bin', 'java')
    if not os.path.exists(java):
        java = 'java'
    p = subprocess.run([java, src], input='\n'.join(pairs) + '\n', capture_output=True, text=True)
    out = {}
    for line in p.stdout.splitlines():
        c = line.split('\t')
        if len(c) >= 8:
            out[c[0]] = c[1:]
    return out


def post(url, body):
    try:
        urllib.request.urlopen(urllib.request.Request(url, json.dumps(body).encode(), {'Content-Type': 'application/json'}), timeout=5).read()
    except Exception:
        pass


def tracker_url():
    for host in ('127.0.0.1', '100.126.15.6'):
        try:
            socket.create_connection((host, 8768), timeout=2).close()
            return f'http://{host}:8768/ui'
        except OSError:
            continue
    return None


def cmd_evaluate(nn):
    h = header(nn)
    oid = os.path.basename(order_file(nn))[:-3]
    engine = h.get('ENGINE', '')
    T = thresholds()
    rows = {}
    for f in glob.glob(os.path.join(OUTG, 'rows', '*.tsv')):
        for line in open(f, encoding='utf-8'):
            c = line.rstrip('\n').split('\t')
            if len(c) >= 7:
                rows[c[0]] = c
    waivers = {}
    wf = os.path.join(DOC, 'waivers', f'{nn}.tsv')
    if os.path.exists(wf):
        for line in open(wf, encoding='utf-8'):
            if line.strip() and not line.startswith('#'):
                c = line.rstrip('\n').split('\t')
                waivers[c[0]] = c[1:]
    man = manifest(nn)
    kind = 'screen' if any(r[0] == 'golden' and r[4] == 'T5' for r in man) else ('gallery' if nn in ('29', '30', '34', '36', '37') else 'kit')
    fails, per_part, results = [], {}, []
    goldens = [r for r in man if r[0] == 'golden']
    waived = 0
    tm = tolerant_metrics([os.path.basename(r[3])[:-4] for r in goldens])
    for r in goldens:
        pid, ref, path, tier = r[1], r[2], r[3], r[4]
        shot = os.path.basename(path)[:-4]
        box_max, blob_max, ink_band, shift_max = T[tier]
        row = rows.get(shot)
        status, why, box = 'FAIL', 'no gate row (no test rendered it)', None
        m = tm.get(shot) if row else None
        if m:
            size, box, blob, ink, shift, npx = m[0], float(m[1]), int(m[2]), float(m[3]), float(m[5]), int(m[6])
            lo, hi = 1 - ink_band, 1 / (1 - ink_band)
            bad_ = []
            # box counts only past a few scattered pixels: one-line shots have tiny ink boxes
            if box > box_max and npx > 4 * blob_max:
                bad_.append(f'box {box:.2f}% (max {box_max})')
            if blob > blob_max:
                bad_.append(f'blob {blob} px (max {blob_max})')
            if not lo <= ink <= hi:
                bad_.append(f'line ink {ink:.2f}x (allowed {lo:.2f}-{hi:.2f})')
            if shift > shift_max:
                bad_.append(f'line moved {shift:.1f} px (max {shift_max})')
            if size != 'same-size':
                why = f'size {size}'
            elif bad_:
                why = ', '.join(bad_)
            else:
                status, why = 'PASS', f'box {box:.2f}% blob {blob} px ink {ink:.2f}x moved {shift:.1f} px'
        elif row:
            box, blob, size = float(row[3]), int(row[4]), row[6]
            if size != 'same-size':
                why = f'size {size}'
            elif box > box_max or blob > blob_max:
                why = f'box {box:.2f}% (max {box_max}) blob {blob} px (max {blob_max}) (raw rows: no PNG trio to measure)'
            else:
                status, why = 'PASS', f'box {box:.2f}% blob {blob} px'
        if status == 'FAIL' and shot in waivers:
            code = waivers[shot][0] if waivers[shot] else ''
            scope = WAIVER_CODES.get(code)
            if scope and (scope == 'any' or scope == kind) and len(waivers[shot]) > 1 and waivers[shot][1].strip():
                status, why = 'WAIVED', f'{code}: {waivers[shot][1][:80]}'
                waived += 1
            else:
                why += f' (waiver rejected: code {code!r} not allowed for {kind} orders or no reason)'
        results.append((status, shot, why))
        agg = per_part.setdefault(pid, {'ok': True, 'worst': -1.0, 'shot': None})
        if status == 'FAIL':
            agg['ok'] = False
        if box is not None and box > agg['worst']:
            agg['worst'], agg['shot'] = box, shot
    if goldens and waived > WAIVER_CAP[kind] * len(goldens):
        fails.append(f'V1 {waived} waivers over the cap of {int(WAIVER_CAP[kind] * 100)}% of {len(goldens)} goldens')
    # matrix
    for r in man:
        if r[0] != 'part' or not r[4].startswith('own'):
            continue
        pid = r[1]
        if r[4] == 'own:logic':
            pascal = ''.join(x[:1].upper() + x[1:] for x in pid.split('-', 1)[1].replace('glass-', '').split('_'))
            if not glob.glob(os.path.join(ROOT, 'native', '**', f'rb/o{nn}', f'{pascal}ParityTest.kt'), recursive=True):
                fails.append(f'P1 {pid}: no rb/o{nn}/{pascal}ParityTest.kt')
            continue
        mf = os.path.join(OUTG, 'matrix', f'{pid}.tsv')
        if not os.path.exists(mf):
            fails.append(f'X1 {pid}: no matrix rows (FlutterGoldenGate.matrix was not run for it)')
            per_part.setdefault(pid, {'ok': True, 'worst': -1.0, 'shot': None})['ok'] = False
            continue
        styles, renders, caps_seen, other = [], set(), {}, []
        for line in open(mf, encoding='utf-8'):
            c = line.rstrip('\n').split('\t')
            if line.startswith('# styles:'):
                styles = line.split(':', 1)[1].strip().split(',')
            elif c[0] == 'render':
                renders.add(tuple(c[2:6]))
                if c[6] != 'PASS':
                    other.append(f'render {"/".join(c[2:6])}: {c[7] if len(c) > 7 else ""}')
            elif c[0] == 'cap':
                caps_seen[c[2]] = c
                if c[6] != 'PASS':
                    other.append(f'cap {c[2]} painted {c[4]} dp > {c[5]} dp')
            elif c[0] in ('target', 'label') and c[3] != 'PASS':
                other.append(f'{c[0]} {c[2]}')
        need = {(s, m, sc, d) for s in styles for m in ('light', 'dark') for sc in ('1.0', '1.3', '2.0') for d in ('ltr', 'rtl')}
        if len(styles) < 5 or need - renders:
            other.append(f'matrix incomplete: {len(need - renders)} of {len(need)} renders missing, styles {styles}')
        for cr in man:
            if cr[0] == 'cap' and cr[1] == pid:
                c = caps_seen.get(cr[3])
                if not c:
                    other.append(f'cap {cr[3]} ({cr[4]}) not measured')
                elif c[3] != cr[4]:
                    other.append(f'cap {cr[3]} measured as {c[3]}, contract says {cr[4]}')
        for o in other:
            fails.append(f'X2 {pid}: {o}')
        if other:
            per_part.setdefault(pid, {'ok': True, 'worst': -1.0, 'shot': None})['ok'] = False
    # print
    for st, shot, why in sorted(results):
        print(f'{st:6} {shot}  {why}')
    bad = [r for r in results if r[0] == 'FAIL']
    for f in fails:
        print('FAIL', f)
    print(f'evaluate {oid}: {len(results)} goldens, {len(bad)} failed, {waived} waived, {len(fails)} other failures')
    # tracker
    url = os.environ.get('OC_UI_URL') or tracker_url()
    if url:
        for pid, agg in per_part.items():
            if not pid.startswith('part-'):
                continue
            body = {'id': pid, 'state': 'gate-pass' if agg['ok'] else 'gate-fail', 'agent': f'rb-{nn}-{engine}', 'engine': engine,
                    'note': f'{oid} gate'}
            if agg['worst'] >= 0:
                body['diff'] = round(agg['worst'], 2)
                s = agg['shot']
                body['images'] = {k: f'rb-{nn}/{s}-{"tdiff" if k == "diff" and os.path.exists(os.path.join(OUTG, s + "-tdiff.png")) else k}.png'
                                  for k in ('flutter', 'native', 'diff')}
            post(url, body)
        if kind == 'gallery' and nn in ('34', '36', '37'):
            for sch in ('solid', 'soft', 'brutal', 'paper', 'signal', 'glass'):
                for mode in ('light', 'dark'):
                    mine = [r for r in results if re.search(rf'_{sch}(_ar)?(_text2)?(_\d+x\d+)?_{mode}$', r[1])]
                    if mine:
                        post(url, {'id': f'school-{sch}-{mode}', 'state': 'gate-fail' if any(r[0] == 'FAIL' for r in mine) else 'gate-pass',
                                   'agent': f'rb-{nn}-{engine}', 'engine': engine, 'note': f'{len(mine)} gallery shots'})
    return 1 if (bad or fails) else 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('cmd', choices=['static', 'fetch', 'modules', 'evaluate', 'chunks'])
    ap.add_argument('--order', required=True)
    a = ap.parse_args()
    nn = f'{int(a.order):02d}'
    sys.exit({'static': cmd_static, 'fetch': cmd_fetch, 'modules': cmd_modules, 'evaluate': cmd_evaluate, 'chunks': cmd_chunks}[a.cmd](nn))


if __name__ == '__main__':
    main()
