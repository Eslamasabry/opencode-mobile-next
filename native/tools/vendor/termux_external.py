#!/usr/bin/env python3
"""Derive the Termux base AAR that is completed by our licensed TerminalSession source fork."""
import hashlib
import io
import pathlib
import sys
import zipfile

root = pathlib.Path(sys.argv[1])
relative = pathlib.Path('maven/com/github/termux/termux-app/terminal-emulator')
source = root / 'third_party' / relative / 'v0.118.3/terminal-emulator-v0.118.3.aar'
output = root / 'third_party' / relative / 'v0.118.3-oc1/terminal-emulator-v0.118.3-oc1.aar'
expected_source = 'cbb915c4d7c51883f85397b46b74bf4b82fd32a8bbf0baeba1e0bc6d990d3e8a'
if hashlib.sha256(source.read_bytes()).hexdigest() != expected_source:
    raise SystemExit('The Termux source AAR does not match the pinned upstream hash.')


def archive(rows):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', compression=zipfile.ZIP_STORED) as target:
        for name, data in sorted(rows):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.external_attr = (0o40755 if name.endswith('/') else 0o100644) << 16
            info.compress_type = zipfile.ZIP_STORED
            target.writestr(info, data)
    return buffer.getvalue()


with zipfile.ZipFile(source) as original:
    rows = []
    for name in original.namelist():
        value = original.read(name)
        if name == 'classes.jar':
            with zipfile.ZipFile(io.BytesIO(value)) as classes:
                value = archive((entry, classes.read(entry)) for entry in classes.namelist()
                    if not entry.startswith('com/termux/terminal/TerminalSession$')
                    and entry != 'com/termux/terminal/TerminalSession.class')
        rows.append((name, value))
    derived = archive(rows)

output.parent.mkdir(parents=True, exist_ok=True)
expected = None
for line in (root / 'third_party/SHA256SUMS').read_text().splitlines():
    fields = line.split()
    if len(fields) == 2 and fields[1] == str(output.relative_to(root / 'third_party')):
        expected = fields[0]
        break
actual = hashlib.sha256(derived).hexdigest()
if expected is None:
    raise SystemExit('The derived Termux AAR has no pinned hash.')
if actual != expected:
    raise SystemExit('The derived Termux AAR does not match its pinned hash.')
output.write_bytes(derived)
print(actual, output.relative_to(root))
