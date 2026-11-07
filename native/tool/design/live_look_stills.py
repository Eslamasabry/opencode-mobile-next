"""Render live looks (E20-S28) from their Kotlin sources with Skia RuntimeEffect (SkSL, the desktop twin of AGSL).

Stills and 3 s frame folders for docs/design/live-backgrounds-native (frames -> GIF with ffmpeg).
Needs: pip install skia-python numpy.
usage: python3 live_look_stills.py <native dir> <out dir> <look ids comma|all> [--gif] [--home] [--scale S]
"""
import re, sys, os, time, struct
import skia
import numpy as np

NATIVE, OUT = sys.argv[1], sys.argv[2]
IDS = sys.argv[3]
GIF = '--gif' in sys.argv
HOME = '--home' in sys.argv
SCALE = float(sys.argv[sys.argv.index('--scale') + 1]) if '--scale' in sys.argv else 1.0
LIVE = os.path.join(NATIVE, 'ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live')

THEMES = {
    'dark': dict(ground=0xFF0B0C0E, s1=0xFF141518, s2=0xFF1B1C20, text1=0xFFF3F3F1, text2=0xFFA3A5AB,
                 accent=0xFF3DDC8A, hair=0x12FFFFFF, dark=1.0),
    'light': dict(ground=0xFFF3F3F1, s1=0xFFFAFAF8, s2=0xFFFFFFFF, text1=0xFF111214, text2=0xFF50535A,
                  accent=0xFF087F43, hair=0x14000000, dark=0.0),
}

prelude = re.search(r'PRELUDE: String =\s*"""(.*?)"""', open(os.path.join(LIVE, 'LiveShaderLib.kt')).read(), re.S).group(1)

def looks():
    out = {}
    for f in sorted(os.listdir(os.path.join(LIVE, 'looks'))):
        src = open(os.path.join(LIVE, 'looks', f)).read()
        lid = re.search(r'override val id: String = "(\w+)"', src).group(1)
        body = re.search(r'private const val BODY = """(.*?)"""', src, re.S).group(1)
        unis = {}
        for m in re.finditer(r'u\.float\("(\w+)", (\w+)\)', src):
            c = re.search(m.group(2) + r' = ([\d.]+)f', src).group(1)
            unis[m.group(1)] = float(c)
        out[lid] = (prelude + body, unis)
    return out

def col(argb):
    a = ((argb >> 24) & 255) / 255; r = ((argb >> 16) & 255) / 255; g = ((argb >> 8) & 255) / 255; b = (argb & 255) / 255
    return [r, g, b, a]

def make(effect, w, h, t, theme, intensity, density, extra):
    th = THEMES[theme]
    b = skia.RuntimeShaderBuilder(effect)
    b.setUniform('uRes', [float(w), float(h)])
    b.setUniform('uTime', t)
    b.setUniform('uIntensity', intensity)
    b.setUniform('uDensity', density)
    b.setUniform('uDark', th['dark'])
    b.setUniform('uGround', col(th['ground']))
    b.setUniform('uAccent', col(th['accent']))
    b.setUniform('uInk', col(th['text1']))
    for k, v in extra.items():
        b.setUniform(k, v)
    return b.makeShader()

def draw_home(canvas, w, h, d, theme):
    th = THEMES[theme]
    font = skia.Font(skia.Typeface('DejaVu Sans'), 16 * d)
    small = skia.Font(skia.Typeface('DejaVu Sans'), 13 * d)
    title = skia.Font(skia.Typeface('DejaVu Sans', skia.FontStyle.Bold()), 26 * d)
    p = skia.Paint(AntiAlias=True)
    p.setColor(th['text1'])
    canvas.drawString('Chats', 20 * d, 96 * d, title, p)
    rows = [('Fix the offline queue retry', 'opencode-mobile · 2 min ago'),
            ('Add Arabic copy for settings', 'opencode-mobile · 14 min ago'),
            ('Why is the build slow?', 'infra · 1 h ago'),
            ('Review the voice notes PR', 'opencode-mobile · 3 h ago'),
            ('Draft release notes 2.1.4', 'docs · yesterday'),
            ('Shader live backgrounds', 'native · yesterday'),
            ('Clean up stale branches', 'infra · Tue'),
            ('Explain the session log', 'protocol · Mon')]
    y = 128 * d
    for i, (a, b) in enumerate(rows):
        r = skia.RRect.MakeRectXY(skia.Rect.MakeXYWH(16 * d, y, w - 32 * d, 68 * d), 16 * d, 16 * d)
        sp = skia.Paint(AntiAlias=True); sp.setColor(th['s1'])
        canvas.drawRRect(r, sp)
        hp = skia.Paint(AntiAlias=True, Style=skia.Paint.kStroke_Style, StrokeWidth=1); hp.setColor(th['hair'])
        canvas.drawRRect(r, hp)
        dp = skia.Paint(AntiAlias=True); dp.setColor(th['accent'] if i == 0 else th['text2'])
        canvas.drawCircle(38 * d, y + 34 * d, 5 * d, dp)
        p.setColor(th['text1']); canvas.drawString(a, 56 * d, y + 30 * d, font, p)
        p.setColor(th['text2']); canvas.drawString(b, 56 * d, y + 52 * d, small, p)
        y += 80 * d
    # bare text directly on the background (the hardest case for contrast)
    p.setColor(th['text2'])
    canvas.drawString('Text straight on the background stays readable.', 20 * d, y + 30 * d, small, p)

def render(lid, src, unis, theme, t, intensity=1.0, w=1080, h=2400, d=2.625, home=False):
    w, h = int(w * SCALE), int(h * SCALE); d = d * SCALE
    eff = skia.RuntimeEffect.MakeForShader(src)
    if eff is None:
        raise SystemExit('compile failed: ' + lid)
    surf = skia.Surface(w, h)
    c = surf.getCanvas()
    pt = skia.Paint(); pt.setShader(make(eff, w, h, t, theme, intensity, d, unis))
    c.drawRect(skia.Rect.MakeWH(w, h), pt)
    if home:
        draw_home(c, w, h, d, theme)
    return surf.makeImageSnapshot()

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    L = looks()
    ids = list(L) if IDS == 'all' else IDS.split(',')
    for lid in ids:
        src, unis = L[lid]
        for theme in ('dark', 'light'):
            img = render(lid, src, unis, theme, 12.0, home=HOME)
            img.save(os.path.join(OUT, f'{lid}-{theme}.png'), skia.kPNG)
            if GIF:
                fdir = os.path.join(OUT, f'_frames_{lid}_{theme}')
                os.makedirs(fdir, exist_ok=True)
                for i in range(45):  # 3 s at 15 fps for the capture
                    render(lid, src, unis, theme, 12.0 + i / 15.0, home=HOME).save(os.path.join(fdir, f'f{i:03d}.png'), skia.kPNG)
        print('ok', lid)
