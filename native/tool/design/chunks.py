#!/usr/bin/env python3
"""chunks.py NN: one line per part of order NN, for single-part chunk work orders (RB_PARTS="<id>" gate_order.sh NN).

Output, tab separated, one part per line:  <part id>  <golden count>  <write globs, space separated>
The globs are the part's native files with their <Name>Test.kt and screenshots/<Name>/ globs from the order's WRITES,
plus the order's shared globs (rb/o<NN> gate cases, strings_rb<NN>.xml, waivers/<NN>.tsv, evidence/<NN>.md).
Run from the repository root: python3 -I native/tool/design/chunks.py 26
"""
import os, subprocess, sys

if len(sys.argv) != 2:
    sys.exit('usage: chunks.py NN')
here = os.path.dirname(os.path.abspath(__file__))
sys.exit(subprocess.run([sys.executable, '-I', os.path.join(here, 'rb_gate.py'), 'chunks', '--order', sys.argv[1]]).returncode)
