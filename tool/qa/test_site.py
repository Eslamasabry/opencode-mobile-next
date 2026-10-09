"""Offline structural checks for the static page; no rendering or network proof.

Run from any directory: python3 tool/qa/test_site.py
Only the Python standard library is required.
"""
from collections import Counter
from dataclasses import dataclass, field
from html.parser import HTMLParser
from pathlib import Path
import re
import unittest
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[2]
SITE = ROOT / 'pages'
SCREENSHOTS = (
    '1_welcome.jpg',
    '2_setup_on_phone.jpg',
    '3_chat.jpg',
    '4_approve.png',
    '5_models.jpg',
    '6_ai_team.jpg',
)
DOWNLOAD = 'https://github.com/Eslamasabry/opencode-mobile-next/releases/latest'
VOID_TAGS = frozenset(('area', 'base', 'br', 'col', 'embed', 'hr', 'img',
                       'input', 'link', 'meta', 'param', 'source', 'track', 'wbr'))
CSS_URL = re.compile(r'''url\(\s*(?:"([^"]*)"|'([^']*)'|([^\s)]*))\s*\)''', re.I)
CSS_COMMENT = re.compile(r'/\*.*?\*/', re.S)


@dataclass
class Element:
    tag: str
    attrs: dict
    text: list = field(default_factory=list)

    @property
    def content(self):
        return ' '.join(' '.join(self.text).split())


class Document(HTMLParser):
    def __init__(self, text):
        super().__init__(convert_charrefs=True)
        self.elements = []
        self.stack = []
        self.feed(text)
        self.close()

    def handle_starttag(self, tag, attrs):
        element = Element(tag, dict(attrs))
        self.elements.append(element)
        if tag not in VOID_TAGS:
            self.stack.append(element)

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)
        if tag not in VOID_TAGS:
            self.handle_endtag(tag)

    def handle_endtag(self, tag):
        for index in range(len(self.stack) - 1, -1, -1):
            if self.stack[index].tag == tag:
                del self.stack[index:]
                return

    def handle_data(self, data):
        for element in self.stack:
            element.text.append(data)

    def tags(self, tag):
        return [element for element in self.elements if element.tag == tag]


def local_path(reference, source):
    """Resolve exactly as a file URL, rejecting external/root/escaping paths."""
    url = urlsplit(reference)
    if url.scheme or url.netloc or url.query:
        raise ValueError(f'Asset must use a local relative path: {reference}')
    path = unquote(url.path)
    if not path or path.startswith(('/', '\\')) or '\\' in path:
        raise ValueError(f'Asset path is not portable: {reference}')
    resolved = (source.parent / path).resolve()
    try:
        resolved.relative_to(SITE.resolve())
    except ValueError as error:
        raise ValueError(f'Asset leaves pages/: {reference}') from error
    if not resolved.is_file():
        raise ValueError(f'Local asset is missing: {reference}')
    return resolved


def reduced_motion_rules(css):
    """Extract nested reduced-motion media bodies without a CSS dependency."""
    bodies = []
    for match in re.finditer(r'@media[^{}]*prefers-reduced-motion\s*:\s*reduce[^{}]*\{', css, re.I):
        start = match.end()
        depth = 1
        for index in range(start, len(css)):
            depth += (css[index] == '{') - (css[index] == '}')
            if depth == 0:
                bodies.append(css[start:index])
                break
    return '\n'.join(bodies)


class StaticSiteTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.index = SITE / 'index.html'
        cls.document = Document(cls.index.read_text(encoding='utf-8'))
        cls.stylesheets = []
        for link in cls.document.tags('link'):
            if 'stylesheet' in (link.attrs.get('rel') or '').lower().split():
                path = local_path(link.attrs.get('href') or '', cls.index)
                cls.stylesheets.append((path, path.read_text(encoding='utf-8')))

    def test_document_has_language_mobile_viewport_and_semantic_title(self):
        roots = self.document.tags('html')
        self.assertEqual(len(roots), 1)
        self.assertRegex(roots[0].attrs.get('lang', ''), r'^[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)*$')
        self.assertTrue(any(element.content for element in self.document.tags('title')))
        self.assertTrue(any(element.content for element in self.document.tags('h1')))
        viewport = [meta.attrs.get('content', '') for meta in self.document.tags('meta')
                    if (meta.attrs.get('name') or '').lower() == 'viewport']
        self.assertTrue(any(re.search(r'width\s*=\s*device-width', value, re.I)
                            for value in viewport), 'Use the device width on mobile.')
        self.assertFalse(any(re.search(r'user-scalable\s*=\s*no|maximum-scale\s*=\s*1(?:\D|$)', value, re.I)
                             for value in viewport), 'Keep mobile zoom available.')

    def test_arabic_section_declares_language_direction_and_content(self):
        arabic = [element for element in self.document.elements
                  if element.attrs.get('lang', '').lower() == 'ar'
                  and element.attrs.get('dir', '').lower() == 'rtl']
        self.assertTrue(arabic, 'Arabic content needs lang="ar" and dir="rtl" together.')
        self.assertTrue(any(re.search(r'[\u0620-\u064a]', element.content) for element in arabic))

    def test_download_cta_points_to_the_project_latest_release(self):
        links = [element for element in self.document.tags('a')
                 if element.attrs.get('href', '').rstrip('/') == DOWNLOAD]
        self.assertTrue(any('download' in element.content.lower() for element in links),
                        'Include a named Download link to the latest GitHub release.')

    def test_page_has_no_javascript_or_embedded_rendering_dependency(self):
        for tag in ('script', 'iframe', 'object', 'embed', 'base'):
            self.assertFalse(self.document.tags(tag), f'Unexpected active/embedded tag: {tag}')
        for element in self.document.elements:
            for key, value in element.attrs.items():
                self.assertFalse(key.lower().startswith('on'), f'Inline event handler: {key}')
                if value:
                    self.assertFalse(value.lstrip().lower().startswith('javascript:'),
                                     'JavaScript URLs prevent a static no-JS page.')

    def test_all_rendering_assets_are_local_and_resolve_from_file_urls(self):
        self.assertTrue(self.stylesheets, 'The static page must include a local stylesheet.')
        for element in self.document.elements:
            for attribute in ('src', 'poster'):
                if element.attrs.get(attribute):
                    with self.subTest(tag=element.tag, attribute=attribute):
                        local_path(element.attrs[attribute], self.index)
            if element.tag == 'link' and element.attrs.get('href'):
                rel = set((element.attrs.get('rel') or '').lower().split())
                if rel.intersection({'stylesheet', 'icon', 'preload', 'prefetch', 'preconnect',
                                     'dns-prefetch', 'manifest', 'apple-touch-icon'}):
                    local_path(element.attrs['href'], self.index)
            if element.attrs.get('srcset'):
                for candidate in element.attrs['srcset'].split(','):
                    local_path(candidate.strip().split()[0], self.index)
            self._check_css_urls(element.attrs.get('style') or '', self.index)
        for style in self.document.tags('style'):
            self._check_css_urls(' '.join(style.text), self.index)
        for path, text in self.stylesheets:
            self._check_css_urls(text, path)

    def _check_css_urls(self, text, source):
        text = CSS_COMMENT.sub('', text)
        self.assertNotRegex(text, r'(?i)@import\b', 'Keep render dependencies directly local.')
        for match in CSS_URL.finditer(text):
            reference = next(group for group in match.groups() if group is not None)
            if reference.startswith('#'):
                continue  # Document-local SVG/filter reference does not fetch a file.
            local_path(reference, source)

    def test_exact_six_screenshots_are_each_displayed_once_with_alt_text(self):
        images = self.document.tags('img')
        screenshot_images = []
        for image in images:
            self.assertIn('alt', image.attrs,
                          f'Declare alternative text for image {image.attrs.get("src", "")}')
            path = local_path(image.attrs.get('src') or '', self.index)
            if path.parent == (SITE / 'assets' / 'screenshots').resolve():
                self.assertTrue((image.attrs.get('alt') or '').strip(),
                                f'Provide descriptive alt text for screenshot {path.name}')
                screenshot_images.append(path.name)
        self.assertEqual(Counter(screenshot_images), Counter(SCREENSHOTS))
        folder = SITE / 'assets' / 'screenshots'
        self.assertEqual({path.name for path in folder.iterdir() if path.is_file()}, set(SCREENSHOTS))
        for filename in SCREENSHOTS:
            signature = b'\x89PNG\r\n\x1a\n' if filename.endswith('.png') else b'\xff\xd8\xff'
            self.assertTrue((folder / filename).read_bytes().startswith(signature),
                            f'{filename} must be a local screenshot image.')

    def test_fragment_links_have_unique_existing_targets(self):
        ids = [element.attrs['id'] for element in self.document.elements if element.attrs.get('id')]
        self.assertEqual(len(ids), len(set(ids)), 'Duplicate IDs make anchor destinations ambiguous.')
        for link in self.document.tags('a'):
            href = link.attrs.get('href')
            self.assertTrue(href, 'Links must declare a destination.')
            url = urlsplit(href)
            if url.scheme or url.netloc:
                continue  # External navigation is allowed; it is not a render dependency.
            target = self.index if not url.path else local_path(href, self.index)
            if url.fragment and target == self.index:
                self.assertIn(unquote(url.fragment), ids, f'Missing anchor target: {href}')

    def test_css_supports_direction_keyboard_focus_and_reduced_motion(self):
        css = CSS_COMMENT.sub('', '\n'.join(text for _, text in self.stylesheets))
        self.assertRegex(css, r'(?i)\b(?:margin|padding|inset)-(?:inline|block)(?:-start|-end)?\s*:',
                         'Use logical spacing so RTL follows the document direction.')
        focus_rules = re.findall(r'([^{}]*:focus(?:-visible|-within)?[^{}]*)\{([^{}]*)\}', css, re.I)
        self.assertTrue(any(re.search(r'\b(?:outline|box-shadow)\s*:', declarations, re.I)
                            and not re.search(r'outline\s*:\s*(?:none|0\s*[;}])', declarations, re.I)
                            for _, declarations in focus_rules), 'Provide visible keyboard focus.')
        reduced = reduced_motion_rules(css)
        self.assertTrue(reduced, 'Honor the reduced-motion preference.')
        self.assertRegex(reduced, r'(?i)(?:scroll-behavior\s*:\s*auto|(?:animation|transition)(?:-duration)?\s*:\s*(?:none|0(?:s|ms)?|0\.0*1ms))',
                         'The reduced-motion stylesheet must reduce movement.')


if __name__ == '__main__':
    unittest.main()
