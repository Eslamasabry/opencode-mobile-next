"""Private, bounded UI operations for the BD7 diagnostics device proof.

The caller owns the emulator flock and provides an exact-device command runner.
XML and image input stay in memory; only validated diagnostics crops are saved.
"""
from io import BytesIO
from pathlib import Path
import re
import xml.etree.ElementTree as ET

from PIL import Image


class Bd7UiFailure(ValueError):
    """Fixed, authored failure categories only."""


_BIDI = re.compile(r'[\u200e\u200f\u202a-\u202e\u2066-\u2069]')
_BOUNDS = re.compile(r'^\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]$')
_DEGRADED_COPY = {
    "Crash reports aren't available right now. Restart the app and try again.",
    "Couldn't update crash reports. Restart the app and try again.",
}
_FIXED = {
    'Report a problem', 'Crash reports', 'Recent app exits', 'Details', 'Close',
    'Dismiss', 'Hide details',
    'Preview crash report', 'Share report', 'OpenCode Mobile crash report',
    'Captured error categories and times only.',
    'No messages, stacks or conversations are included.',
    'Only error categories and times, no messages or conversations. Nothing leaves this phone until you tap Share report.',
    'Delete saved crash reports?', 'Delete crash reports',
    'Deletes the saved crash reports. Saving crash reports stays on.',
    'Save crash reports on this phone', 'Kept on this phone. Never sent automatically.',
    'Only what kind of problem happened and when is kept: no error messages, conversations or passwords. Keeps the latest 20 reports.',
    'No crash reports yet', 'One appears here if the app closes or stops responding.',
    'The app closed unexpectedly', 'The app stopped responding',
    'The app hit an unexpected error', "A screen couldn't be shown",
    'Kept on this phone only. It\'s sent only if you include saved errors in a problem report yourself.',
    'App stopped unexpectedly', 'App closed', 'App updated', 'App stopped',
    'Phone needed memory', 'Android ended the app', 'Source', 'Category',
    'Summary', 'Reason code', 'Importance', 'crash.native', 'crash.anr',
    'Copy source', 'Copy category', 'Copy summary', 'Copy reason code', 'Copy importance', 'Copy all',
    'Native application error', 'Android reported that the app stopped responding',
    'Invalid state', 'Invalid argument', 'Missing value', 'Unsupported operation',
    'Application error', 'Permission denied', 'Input/output failure', 'Interrupted operation',
    'Share saved crash reports', 'Turning this off deletes the saved crash reports.',
    'Turning this on clears the saved error above.',
    'Turning this off clears the saved error above.',
    'Turning this off deletes the saved crash reports and clears the saved error above.',
    'No unexpected closes recently.', 'Back', 'On', 'Off',
    'Android records each time the app closes. Nothing here is sent automatically.',
    'OpenCode Mobile', "OpenCode Mobile isn't responding", 'OpenCode Mobile isn’t responding',
    'Close app', 'Wait', 'App info',
} | _DEGRADED_COPY
_GENERATED = re.compile(
    r'^(?:[0-9]{1,5}|[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}(?::[0-9]{2})?'
    r'|(?:Today|Yesterday) [0-9]{2}:[0-9]{2}'
    r'|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) [0-9]{1,2} [0-9]{2}:[0-9]{2}'
    r'|Show all [0-9]{1,3}|[0-9]{1,3} other closes'
    r'|[0-9]{1,3} routine closes? \(updates, you closed it\)'
    r'|Turning this (?:on|off) clears the [0-9]{1,3} saved errors above\.'
    r'|Turning this off deletes the saved crash reports and clears the [0-9]{1,3} saved errors above\.'
    r'|Delete [0-9]{1,3} saved crash reports?)$')


def _rect(node):
    match = _BOUNDS.fullmatch(node.get('bounds', ''))
    if match is None:
        return None
    result = tuple(map(int, match.groups()))
    return result if result[2] > result[0] and result[3] > result[1] else None


def _normalize(value):
    return ' '.join(_BIDI.sub('', value).split())


def _safe_text(value):
    if re.fullmatch(
        r"[1-9][0-9]? reports? · [1-9][0-9]{0,4}(?:\.[0-9])? (?:B|KB)", value
    ):
        return True
    # Issued FD2 previews contain only fixed source/category plus ISO time.
    if re.fullmatch(
        r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}"
        r"(?:\.[0-9]{1,6})?Z? \| (?:flutter|platform|widget|native|anr) \| "
        r"(?:Invalid state|Invalid argument|Missing value|Unsupported operation|Application error|"
        r"Native application error|Permission denied|Input/output failure|Interrupted operation|"
        r"Android reported that the app stopped responding)",
        value,
    ):
        return True
    if value in _FIXED or _GENERATED.fullmatch(value):
        return True
    # Android can expose a disabled row's authored hint after an empty label.
    # Only these exact degraded-state hints qualify; arbitrary suffixes do not.
    if any(re.fullmatch(r'[, ]*' + re.escape(copy) + r'[, ]*', value)
           and len(value) <= len(copy) + 4 for copy in _DEGRADED_COPY):
        return True
    match = re.fullmatch(r'(Source|Category|Summary|Reason code|Importance): (.+)', value)
    if match is None:
        return False
    label, scalar = match.groups()
    if label in ('Reason code', 'Importance'):
        return re.fullmatch(r'[0-9]{1,5}', scalar) is not None
    if label == 'Source':
        return scalar in ('crash.native', 'crash.anr')
    return scalar in _FIXED


class Bd7Ui:
    def __init__(self, execute):
        self.execute = execute
        self._parents = {}
        self._window = None

    @staticmethod
    def text(node):
        return _normalize(node.get('text') or node.get('content-desc') or '')

    def nodes(self):
        for _ in range(3):
            try:
                raw = self.execute(['exec-out', 'uiautomator', 'dump', '/dev/tty'], timeout=12)
                if not isinstance(raw, bytes) or len(raw) > 2_000_000:
                    raise ValueError()
                start, end = raw.find(b'<hierarchy'), raw.rfind(b'</hierarchy>')
                if start < 0 or end < start or b'<!DOCTYPE' in raw or b'<!ENTITY' in raw:
                    raise ValueError()
                root = ET.fromstring(raw[start:end + len(b'</hierarchy>')])
                values = root.findall('.//node')
                self._parents = {child: parent for parent in root.iter() for child in parent}
                self._window = next((_rect(node) for node in values if _rect(node)), None)
                if self._window is None:
                    raise ValueError()
                return values
            except Exception:
                continue
        raise Bd7UiFailure('ui_unavailable') from None

    def _matches(self, node, label, contains=False):
        wanted = _normalize(label)
        for field in ('text', 'content-desc'):
            raw = node.get(field, '')
            candidates = [_normalize(raw), *(_normalize(line) for line in raw.splitlines())]
            if any(wanted in candidate if contains else wanted == candidate for candidate in candidates):
                return True
        return False

    def _find(self, label, nodes, contains=False):
        found = [node for node in nodes if _rect(node) is not None and
                 node.get('visible-to-user', 'true') != 'false' and
                 self._window[0] <= self.centre(node)[0] < self._window[2] and
                 self._window[1] <= self.centre(node)[1] < self._window[3] and
                 self._matches(node, label, contains)]
        return min(found, key=lambda node: (_rect(node)[2] - _rect(node)[0]) *
                   (_rect(node)[3] - _rect(node)[1]), default=None)

    def find(self, label):
        return self._find(label, self.nodes())

    @staticmethod
    def centre(node):
        bounds = _rect(node)
        if bounds is None:
            raise Bd7UiFailure('ui_bounds_unavailable')
        return ((bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2)

    def tap(self, label, contains=False):
        node = self._find(label, self.nodes(), contains)
        if node is None:
            raise Bd7UiFailure('navigation_target_unavailable')
        x, y = self.centre(node)
        try:
            self.execute(['shell', 'input', 'tap', str(x), str(y)], timeout=5)
        except Exception:
            raise Bd7UiFailure('ui_action_failed') from None

    def scroll(self, direction):
        if direction not in ('down', 'up'):
            raise Bd7UiFailure('ui_direction_invalid')
        self.nodes()
        left, top, right, bottom = self._window
        x = (left + right) // 2
        high, low = top + (bottom - top) // 5, top + (bottom - top) * 4 // 5
        start, end = (low, high) if direction == 'down' else (high, low)
        try:
            self.execute(['shell', 'input', 'swipe', str(x), str(start), str(x), str(end), '300'], timeout=5)
        except Exception:
            raise Bd7UiFailure('ui_action_failed') from None

    def scroll_find(self, label, max_swipes=10):
        if not isinstance(max_swipes, int) or not 0 <= max_swipes <= 10:
            raise Bd7UiFailure('ui_navigation_budget_invalid')
        for attempt in range(max_swipes + 1):
            node = self.find(label)
            if node is not None:
                return node
            if attempt < max_swipes:
                self.scroll('down')
        raise Bd7UiFailure('navigation_target_unavailable')

    def navigate_report(self):
        self.tap('Settings')
        self.scroll_find('Report a problem')
        self.tap('Report a problem')
        # The page must contain the real consent section; a Settings search
        # result or unrelated Report action alone cannot qualify navigation.
        self.scroll_find('Save crash reports on this phone')

    def details_number(self, label='Reason code'):
        if label not in ('Reason code', 'Importance'):
            raise Bd7UiFailure('ui_number_unavailable')
        nodes = self.nodes()
        values = set()
        expression = re.compile(r'^' + re.escape(label) + r'[: ,]+([0-9]{1,5})$')
        for entry in nodes:
            if _rect(entry) is None or entry.get('visible-to-user', 'true') == 'false':
                continue
            for field in ('text', 'content-desc'):
                match = expression.fullmatch(_normalize(entry.get(field, '')))
                if match is not None:
                    values.add(int(match[1]))
        if not values:
            title = self._find(label, nodes)
            if title is not None:
                bounds = _rect(title)
                adjacent = []
                for entry in nodes:
                    position = _rect(entry)
                    number = self.text(entry)
                    if position is None or not re.fullmatch(r'[0-9]{1,5}', number):
                        continue
                    overlap = min(bounds[3], position[3]) - max(bounds[1], position[1])
                    if overlap > 0 and position[0] >= bounds[2]:
                        adjacent.append((position[0] - bounds[2], int(number)))
                    elif (position[1] >= bounds[3] and
                          position[1] - bounds[3] <= max(16, bounds[3] - bounds[1]) and
                          min(bounds[2], position[2]) > max(bounds[0], position[0])):
                        adjacent.append((position[1] - bounds[3], int(number)))
                if adjacent:
                    closest = min(distance for distance, _ in adjacent)
                    values = {number for distance, number in adjacent if distance == closest}
        if len(values) != 1:
            raise Bd7UiFailure('ui_number_unavailable')
        return values.pop()

    def _crop(self, section, nodes):
        if section in ('Crash reports', 'Recent app exits'):
            title = self._find(section, nodes)
        elif section == 'share':
            title = self._find('Preview crash report', nodes)
        elif section == 'preview':
            choices = [node for node in nodes if _rect(node) and any(
                self._matches(node, label) for label in
                ('The app closed unexpectedly', 'The app stopped responding'))]
            title = max(choices, key=lambda node: _rect(node)[1], default=None)
        elif section == 'anr':
            title = self._find("OpenCode Mobile isn't responding", nodes)
            if title is None:
                title = self._find('OpenCode Mobile isn’t responding', nodes)
        else:
            raise Bd7UiFailure('unsafe_screenshot')
        if title is None:
            raise Bd7UiFailure('unsafe_screenshot')
        left, _, right, bottom = self._window
        top = _rect(title)[1]
        if section == 'Crash reports':
            next_section = self._find('Recent app exits', nodes)
            if next_section is not None and _rect(next_section)[1] > top:
                bottom = _rect(next_section)[1]
        if section == 'anr':
            # Crop the system dialog card, including its actions, rather than
            # the underlying app/account screen around the dialog.
            parent = self._parents.get(title)
            while parent is not None:
                bounds = _rect(parent)
                if bounds and any(self._matches(node, 'Close app') for node in parent.iter('node')):
                    left, top, right, bottom = bounds
                    break
                parent = self._parents.get(parent)
            else:
                raise Bd7UiFailure('unsafe_screenshot')
        crop = (left, top, right, bottom)
        if right <= left or bottom <= top:
            raise Bd7UiFailure('unsafe_screenshot')
        for node in nodes:
            bounds = _rect(node)
            if bounds is None or node.get('visible-to-user', 'true') == 'false':
                continue
            if bounds[2] <= left or bounds[0] >= right or bounds[3] <= top or bounds[1] >= bottom:
                continue
            if node.get('class') == 'android.widget.EditText' or node.get('editable') == 'true':
                raise Bd7UiFailure('unsafe_screenshot')
            for field in ('text', 'content-desc'):
                raw = node.get(field, '')
                for line in raw.splitlines():
                    value = _normalize(line)
                    if value and not _safe_text(value):
                        raise Bd7UiFailure('unsafe_screenshot')
        return crop

    def screenshot(self, path, section='Crash reports'):
        try:
            for layout_retries in range(2):
                first = self._crop(section, self.nodes())
                raw = self.execute(['exec-out', 'screencap', '-p'], timeout=10)
                if not isinstance(raw, bytes) or len(raw) > 8_000_000:
                    raise ValueError()
                second = self._crop(section, self.nodes())
                if first == second:
                    break
                # Only two independently validated, safe rectangles that
                # moved can retry. Invalid text/editable input raises above.
                # Discard the old PNG; the retry captures a new guarded frame.
                raw = None
                if layout_retries == 1:
                    raise ValueError()
            with Image.open(BytesIO(raw)) as original:
                if original.format != 'PNG' or original.width * original.height > 16_000_000:
                    raise ValueError()
                if first[2] > original.width or first[3] > original.height:
                    raise ValueError()
                image = original.crop(first).convert('RGB')
            image.thumbnail((480, 960))
            for quality in (75, 60, 45, 30):
                output = BytesIO()
                image.save(output, format='JPEG', quality=quality)
                if output.tell() <= 100_000:
                    destination = Path(path)
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    destination.write_bytes(output.getvalue())
                    return {'bytes': output.tell(), 'width': image.width,
                            'height': image.height, 'section': section,
                            'layout_retries': layout_retries}
            raise ValueError()
        except Exception:
            raise Bd7UiFailure('unsafe_screenshot') from None
