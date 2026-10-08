"""Offline UI privacy/navigation fixtures. No adb, sleeps or genuine accounts."""
from contextlib import redirect_stdout, redirect_stderr
from io import BytesIO, StringIO
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

from PIL import Image

from tool.qa.bd7_device_ui import Bd7Ui, Bd7UiFailure


def node(text='', bounds='[0,0][800,1200]', **attributes):
    return ET.Element('node', dict(text=text, bounds=bounds, **attributes))


def xml(*children):
    hierarchy = ET.Element('hierarchy', rotation='0')
    root = node()
    hierarchy.append(root)
    root.extend(children)
    return ET.tostring(hierarchy)


def png():
    output = BytesIO()
    Image.new('RGB', (800, 1200), 'white').save(output, format='PNG')
    return output.getvalue()


class UiFixture:
    def __init__(self, document):
        self.document = document
        self.commands = []

    def execute(self, command, *, timeout):
        self.commands.append(command)
        if command == ['exec-out', 'uiautomator', 'dump', '/dev/tty']:
            return self.document
        if command == ['exec-out', 'screencap', '-p']:
            return png()
        return b''


class Bd7UiTest(unittest.TestCase):
    def test_bidi_and_merged_semantics_select_smallest_visible_target(self):
        fixture = UiFixture(xml(
            node('Report a problem\n2 errors kept', '[10,100][790,400]'),
            node('\u2068Report a problem\u2069', '[100,160][350,210]'),
            node('Report a problem', '[0,0][10,10]', **{'visible-to-user': 'false'}),
        ))
        ui = Bd7Ui(fixture.execute)
        ui.tap('Report a problem')
        self.assertEqual(fixture.commands[-1], ['shell', 'input', 'tap', '225', '185'])
        self.assertEqual(ui.text(ui.find('Report a problem')), 'Report a problem')

    def test_original_node_keeps_native_checked_attribute(self):
        fixture = UiFixture(xml(node('Save crash reports on this phone', checked='true')))
        result = Bd7Ui(fixture.execute).find('Save crash reports on this phone')
        self.assertEqual(result.get('checked'), 'true')

    def test_reason_code_reads_merged_and_adjacent_semantics_as_numbers_only(self):
        for document in (
            xml(node('', '[10,200][790,260]', **{'content-desc': 'Reason code: \u20684\u2069'})),
            xml(node('Reason code\n6', '[10,200][790,260]')),
            xml(node('Reason code', '[10,200][200,260]'), node('4', '[220,200][300,260]')),
            xml(node('Reason code', '[10,200][200,240]'), node('6', '[10,250][200,290]')),
        ):
            result = Bd7Ui(UiFixture(document).execute).details_number()
            self.assertIn(result, (4, 6))
            self.assertIsInstance(result, int)

    def test_reason_code_rejects_private_or_ambiguous_values(self):
        for document in (
            xml(node('Reason code: synthetic-private-account')),
            xml(node('Reason code: 4'), node('Reason code: 6')),
        ):
            with self.assertRaisesRegex(Bd7UiFailure, '^ui_number_unavailable$') as failure:
                Bd7Ui(UiFixture(document).execute).details_number()
            self.assertNotIn('synthetic-private-account', str(failure.exception))

    def test_scroll_uses_current_window_geometry_and_navigation_is_capped(self):
        fixture = UiFixture(xml())
        ui = Bd7Ui(fixture.execute)
        with self.assertRaisesRegex(Bd7UiFailure, '^navigation_target_unavailable$'):
            ui.scroll_find('Report a problem', max_swipes=2)
        swipes = [command for command in fixture.commands if command[:3] == ['shell', 'input', 'swipe']]
        self.assertEqual(swipes, [['shell', 'input', 'swipe', '400', '960', '400', '240', '300']] * 2)
        with self.assertRaisesRegex(Bd7UiFailure, '^ui_navigation_budget_invalid$'):
            ui.scroll_find('Report a problem', max_swipes=11)

    def test_navigation_requires_the_real_consent_panel(self):
        fixture = UiFixture(xml(node('Settings', '[10,1000][300,1190]')))
        ui = Bd7Ui(fixture.execute)

        def execute(command, *, timeout):
            result = fixture.execute(command, timeout=timeout)
            if command[:3] == ['shell', 'input', 'tap']:
                if fixture.document == xml(node('Settings', '[10,1000][300,1190]')):
                    fixture.document = xml(node('Report a problem', '[10,100][790,200]'))
                else:
                    fixture.document = xml(node('Save crash reports on this phone', '[10,500][790,600]'))
            return result

        ui.execute = execute
        ui.navigate_report()
        self.assertIsNotNone(ui.find('Save crash reports on this phone'))
        self.assertEqual(sum(command[:3] == ['shell', 'input', 'tap'] for command in fixture.commands), 2)

    def test_xml_and_private_labels_never_reach_errors_or_output(self):
        private = 'synthetic-private-account-and-draft'
        fixture = UiFixture(xml(node(private)))
        captured = StringIO()
        with redirect_stdout(captured), redirect_stderr(captured):
            with self.assertRaises(Bd7UiFailure) as failure:
                Bd7Ui(fixture.execute).tap('Report a problem')
        self.assertNotIn(private, str(failure.exception) + captured.getvalue())
        self.assertNotIn('<hierarchy', str(failure.exception) + captured.getvalue())
        self.assertEqual(captured.getvalue(), '')

    def test_unavailable_xml_retries_are_bounded_and_fixed(self):
        fixture = UiFixture(b'synthetic-private-account <invalid>')
        with self.assertRaisesRegex(Bd7UiFailure, '^ui_unavailable$') as failure:
            Bd7Ui(fixture.execute).nodes()
        self.assertEqual(len(fixture.commands), 3)
        self.assertNotIn('synthetic-private-account', str(failure.exception))

    def test_safe_diagnostics_crop_excludes_private_draft_above_section(self):
        fixture = UiFixture(xml(
            node('synthetic-private-draft', '[10,20][790,180]'),
            node('Crash reports', '[10,300][790,350]'),
            node('Save crash reports on this phone', '[10,360][790,430]'),
            node('The app closed unexpectedly', '[10,450][790,500]'),
            node('2026-10-08 09:42', '[10,510][790,550]'),
            node('', '[10,560][790,610]', **{'content-desc': 'Source: crash.native'}),
            node('Recent app exits', '[10,800][790,850]'),
        ))
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'proof.jpg'
            result = Bd7Ui(fixture.execute).screenshot(path, section='Crash reports')
            self.assertLessEqual(result['bytes'], 100_000)
            self.assertLessEqual(result['width'], 480)
            self.assertEqual(result['section'], 'Crash reports')
            with Image.open(path) as image:
                self.assertEqual(image.format, 'JPEG')
                self.assertEqual(image.size, (480, 300))

    def test_private_text_inside_crop_and_non_diagnostics_screens_are_refused(self):
        for document in (
            xml(node('Settings'), node('synthetic-private-account', '[20,500][700,550]')),
            xml(node('Crash reports', '[10,300][790,350]'),
                node('synthetic-private-draft', '[10,500][790,600]')),
            xml(node('Crash reports', '[10,300][790,350]'),
                node('12345', '[10,500][790,600]', **{'class': 'android.widget.EditText'})),
        ):
            fixture = UiFixture(document)
            with tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / 'proof.jpg'
                with self.assertRaisesRegex(Bd7UiFailure, '^unsafe_screenshot$') as failure:
                    Bd7Ui(fixture.execute).screenshot(path)
                self.assertFalse(path.exists())
                self.assertNotIn('synthetic-private', str(failure.exception))

    def test_authored_consent_count_copy_is_safe_but_appended_private_values_are_not(self):
        copy = (
            'Turning this on clears the saved error above.',
            'Turning this off clears the saved error above.',
            'Turning this off deletes the saved crash reports and clears the saved error above.',
            'Turning this on clears the 12 saved errors above.',
            'Turning this off clears the 12 saved errors above.',
            'Turning this off deletes the saved crash reports and clears the 12 saved errors above.',
        )
        for words in copy:
            for appended in ('', ' synthetic-private-value'):
                fixture = UiFixture(xml(
                    node('Crash reports', '[10,300][790,350]'),
                    node(words + appended, '[10,500][790,600]'),
                ))
                with tempfile.TemporaryDirectory() as directory:
                    path = Path(directory) / 'consent.jpg'
                    ui = Bd7Ui(fixture.execute)
                    if appended:
                        with self.assertRaisesRegex(Bd7UiFailure, '^unsafe_screenshot$') as failure:
                            ui.screenshot(path)
                        self.assertFalse(path.exists())
                        self.assertNotIn('synthetic-private-value', str(failure.exception))
                    else:
                        self.assertEqual(ui.screenshot(path)['section'], 'Crash reports')
                        self.assertTrue(path.exists())

    def test_anr_crop_uses_dialog_card_and_excludes_account_background(self):
        card = node('', '[200,400][600,800]')
        card.extend([
            node("OpenCode Mobile isn't responding", '[220,430][580,500]'),
            node('Wait', '[220,600][350,660]'),
            node('Close app', '[360,600][580,660]'),
        ])
        fixture = UiFixture(xml(node('synthetic-private-account', '[0,50][190,300]'), card))
        with tempfile.TemporaryDirectory() as directory:
            result = Bd7Ui(fixture.execute).screenshot(Path(directory) / 'anr.jpg', section='anr')
            self.assertEqual((result['width'], result['height']), (400, 400))

    def test_exact_degraded_hints_are_safe_but_appended_private_text_is_refused(self):
        messages = (
            "Crash reports aren't available right now. Restart the app and try again.",
            "Couldn't update crash reports. Restart the app and try again.",
        )
        for message in messages:
            for prefix, suffix in (('', ''), (', ', ''), ('', ' ,'), (',', ' '), (' ', ',')):
                for appended in ('', ' synthetic-private-value'):
                    fixture = UiFixture(xml(
                        node('Crash reports', '[10,300][790,350]'),
                        node('', '[10,400][790,600]', **{
                            'content-desc': prefix + message + suffix + appended,
                            'enabled': 'false',
                        }),
                    ))
                    with tempfile.TemporaryDirectory() as directory:
                        path = Path(directory) / 'degraded.jpg'
                        captured = StringIO()
                        with redirect_stdout(captured), redirect_stderr(captured):
                            if appended:
                                with self.assertRaisesRegex(Bd7UiFailure, '^unsafe_screenshot$') as failure:
                                    Bd7Ui(fixture.execute).screenshot(path)
                                self.assertFalse(path.exists())
                                self.assertNotIn('synthetic-private-value', str(failure.exception))
                            else:
                                result = Bd7Ui(fixture.execute).screenshot(path)
                                self.assertEqual(result['section'], 'Crash reports')
                                self.assertTrue(path.exists())
                        self.assertEqual(captured.getvalue(), '')

    def test_share_preview_crop_accepts_only_category_time_report_and_excludes_background(
        self,
    ):
        lines = [
            "Preview crash report",
            "Only error categories and times, no messages or conversations. Nothing leaves this phone until you tap Share report.",
            "1 report · 221 B",
            "OpenCode Mobile crash report",
            "Captured error categories and times only.",
            "No messages, stacks or conversations are included.",
            "2026-10-09T12:30:15.123 | native | Native application error",
            "Share report",
        ]
        fixture = UiFixture(
            xml(
                node("synthetic-private-account", "[10,20][790,100]"),
                *(
                    node(line, f"[10,{200 + i * 70}][790,{250 + i * 70}]")
                    for i, line in enumerate(lines)
                ),
            )
        )
        with tempfile.TemporaryDirectory() as directory:
            result = Bd7Ui(fixture.execute).screenshot(
                Path(directory) / "share.jpg", section="share"
            )
            self.assertEqual(result["section"], "share")

    def test_share_preview_rejects_unknown_source_category_and_appended_private_text(
        self,
    ):
        for line in (
            "2026-10-09T12:30:15.123 | private | Native application error",
            "2026-10-09T12:30:15.123 | native | synthetic-private-error",
            "2026-10-09T12:30:15.123 | native | Native application error synthetic-private-value",
        ):
            fixture = UiFixture(
                xml(
                    node("Preview crash report", "[10,200][790,250]"),
                    node(line, "[10,300][790,400]"),
                )
            )
            with tempfile.TemporaryDirectory() as directory:
                destination = Path(directory) / "share.jpg"
                with self.assertRaisesRegex(Bd7UiFailure, "^unsafe_screenshot$"):
                    Bd7Ui(fixture.execute).screenshot(destination, section="share")
                self.assertFalse(destination.exists())


if __name__ == '__main__':
    unittest.main()
