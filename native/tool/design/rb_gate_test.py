#!/usr/bin/env python3
"""Tests for rb_gate.py's scoreboard 2026-11-07 lead items (0.1 layout rows, 0.2 done ledger).

Run from the repository root: python3 -I native/tool/design/rb_gate_test.py
"""
import contextlib, importlib.util, io, os, sys, tempfile, unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('rb_gate', os.path.join(HERE, 'rb_gate.py'))
rb = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rb)


class LayoutRows(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix='rbg')
        os.makedirs(os.path.join(self.tmp, 'layout'))
        os.makedirs(os.path.join(self.tmp, 'rows'))

    def write(self, name, verdict, detail):
        with open(os.path.join(self.tmp, 'layout', f'{name}.tsv'), 'w', encoding='utf-8') as fh:
            fh.write(f'{name}\t{verdict}\t{detail}\n')

    def test_G0_03_layout_rows_are_read_per_gate_case(self):
        self.write('kit_a', 'PASS', '-')
        self.write('kit_b', 'FAIL', 'layout guard: kit_b breaks 1 layout rule(s): Phone edge-text "Flush": x 0')
        rows = rb.layout_rows(self.tmp)
        self.assertEqual(rows['kit_a'], ('PASS', '-'))
        self.assertEqual(rows['kit_b'][0], 'FAIL')
        self.assertIn('edge-text', rows['kit_b'][1])

    def test_G0_04_evaluate_reports_a_layout_finding_as_its_own_failing_row(self):
        self.write('kit_confirm_neutral', 'FAIL', 'layout guard: kit_confirm_neutral breaks 1 layout rule(s): Phone tight "x"')
        old = rb.OUTG
        os.environ['OC_UI_URL'] = 'http://127.0.0.1:9/ui'  # the tracker post fails fast and is ignored
        rb.OUTG = self.tmp
        out = io.StringIO()
        try:
            with contextlib.redirect_stdout(out):
                rc = rb.cmd_evaluate('12')
        finally:
            rb.OUTG = old
        text = out.getvalue()
        self.assertEqual(rc, 1)
        self.assertIn('FAIL L1 kit_confirm_neutral: layout guard', text)
        self.assertIn('layout guard: 1 gate cases checked, 1 with findings', text)


class Coverage(unittest.TestCase):
    def test_G0_14_gate_case_names_are_read_exactly_and_as_templates(self):
        root = tempfile.mkdtemp(prefix='rbc')
        d = os.path.join(root, 'native', 'ui', 'kit', 'src', 'desktopTest', 'kotlin', 'rb')
        os.makedirs(d)
        with open(os.path.join(d, 'XGateTest.kt'), 'w', encoding='utf-8') as fh:
            fh.write('class X { fun a() { FlutterGoldenGate.check("kit_row_default") {}\n'
                     '  FlutterGoldenGate.check("kit_setup_steps_$name") {}\n  val t = "$x" } }\n')
        exact, rx = rb.case_names(root)
        self.assertIn('kit_row_default', exact)
        self.assertTrue(any(x.match('kit_setup_steps_running_ar') for x in rx))
        self.assertFalse(any(x.match('kit_row_default_ar') for x in rx), 'a bare "$x" is no template')


class DoneLedger(unittest.TestCase):
    def test_G0_05_a_ledger_order_is_done_without_a_state_marker(self):
        os.environ.pop('RB_STATE', None)
        old_state, old_ledger = rb.STATE, rb.DONE_LEDGER
        tmp = tempfile.mkdtemp(prefix='rbs')
        rb.STATE = os.path.join(tmp, 'state')  # no markers at all
        rb.DONE_LEDGER = os.path.join(tmp, 'done.tsv')
        try:
            with open(rb.DONE_LEDGER, 'w', encoding='utf-8') as fh:
                fh.write('# order\tcommit\tnote\n8\tabc\tmerged\n15\tdef\tmerged\n')
            self.assertTrue(rb.order_done('08'))
            self.assertTrue(rb.order_done('15'))
            self.assertFalse(rb.order_done('16'))
        finally:
            rb.STATE, rb.DONE_LEDGER = old_state, old_ledger

    def test_G0_06_the_committed_ledger_lists_the_scoreboard_orders(self):
        for o in ('07', '08', '09', '12', '15'):
            self.assertIn(o, rb.ledger_done(), f'order {o} is merged into the integration head (scoreboard item 0.2)')


if __name__ == '__main__':
    sys.exit(unittest.main(verbosity=2))
