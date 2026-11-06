#!/usr/bin/env python3
"""Convert a dumped FlutterSharedPreferences.xml into prefs.json for commonTest readers (plan 05 §10.4 step 8).

    python3 -I xml_to_prefs_json.py <dump-dir>

Reads <dump-dir>/shared_prefs.tar (member shared_prefs/FlutterSharedPreferences.xml) or <dump-dir>/fsp.xml and writes
<dump-dir>/prefs.json: {"<physical key>": {"type": "string|boolean|int|long|float|set", "value": ...}} in file order.
Values are copied as stored (Dart's encoded lists stay strings); nothing is decoded or printed.
"""
import json
import sys
import tarfile
import xml.etree.ElementTree as ET
from pathlib import Path

MEMBER = 'shared_prefs/FlutterSharedPreferences.xml'


def read_xml(dump: Path) -> bytes:
    tar = dump / 'shared_prefs.tar'
    if tar.is_file():
        with tarfile.open(tar) as t:
            for m in t.getmembers():
                if m.isfile() and m.name.lstrip('./') == MEMBER:
                    return t.extractfile(m).read()
        raise SystemExit(f'{MEMBER} is not in {tar}')
    fsp = dump / 'fsp.xml'
    if fsp.is_file():
        return fsp.read_bytes()
    raise SystemExit(f'no shared_prefs.tar or fsp.xml in {dump}')


def convert(xml: bytes) -> dict:
    root = ET.fromstring(xml)
    if root.tag != 'map':
        raise ValueError('not an Android preferences file')
    out = {}
    for el in root:
        name = el.get('name')
        if name is None:
            raise ValueError('entry without a name')
        if el.tag == 'string':
            out[name] = {'type': 'string', 'value': el.text or ''}
        elif el.tag == 'boolean':
            out[name] = {'type': 'boolean', 'value': el.get('value') == 'true'}
        elif el.tag in ('int', 'long'):
            out[name] = {'type': el.tag, 'value': int(el.get('value'))}
        elif el.tag == 'float':
            out[name] = {'type': 'float', 'value': float(el.get('value'))}
        elif el.tag == 'set':
            out[name] = {'type': 'set', 'value': sorted(s.text or '' for s in el)}
        else:
            raise ValueError(f'unknown entry type {el.tag}')
    return out


def main(argv):
    if len(argv) != 2:
        raise SystemExit(__doc__)
    dump = Path(argv[1])
    prefs = convert(read_xml(dump))
    (dump / 'prefs.json').write_text(json.dumps(prefs, indent=1, ensure_ascii=False) + '\n', encoding='utf-8')
    print(f'{len(prefs)} keys -> {dump / "prefs.json"}')


if __name__ == '__main__':
    main(sys.argv)
