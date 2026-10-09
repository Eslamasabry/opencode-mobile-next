"""Offline fake UI tests; never invokes ADB, Flutter, or a device."""
import unittest

from launch import run_launch, TARGETS


class FakeUI:
    def __init__(self, target="codex", *, missing=False, certified=False,
                 fix=True, delayed=0, private=False, back_failure=False):
        self.target = target
        self.name = TARGETS[target]
        self.page = "chats"
        self.stack = []
        self.taps = []
        self.now = 0
        self.missing = missing
        self.certified = certified
        self.fix = fix
        self.delayed = delayed
        self.private = private
        self.back_failure = back_failure
        self.draft = "existing private unsent draft"
        self.project = "/root/projects/private-name"

    def monotonic(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds

    def text(self, node):
        return node

    def ui(self):
        if self.delayed > self.now:
            return []
        if self.page == "chats":
            return ["New conversation"]
        if self.page == "new":
            return ["New conversation", "What should we work on?", "OpenCode"]
        if self.page == "picker":
            rows = ["Choose an agent", "Claude Code\nReady", "Use Claude Code"]
            if not self.missing:
                blocker = "Not certified on this version yet" if self.certified else "Sign in needed"
                rows += [self.name + "\n" + blocker]
                if self.fix:
                    rows += ["Sign in to " + self.name]
            if self.private:
                rows += ["private@example.invalid", "sk-unlogged", self.draft]
            return rows
        return ["Sign in with " + self.name,
                "Sign-in isn't ready on this phone yet", "Sign in again",
                "Choose an agent", "private@example.invalid"]

    def tap_node(self, node):
        self.taps.append(node)
        if node in {"Claude Code", "Use Claude Code", "Sign in again"} or node.startswith("Sign in with "):
            raise AssertionError("Unsafe agent/sign-in action")
        self.stack.append(self.page)
        self.page = {"New conversation": "new", "OpenCode": "picker",
                     "Sign in to " + self.name: "auth"}[node]

    def back(self):
        if self.back_failure:
            raise RuntimeError("private exception detail")
        self.page = self.stack.pop()


class LaunchTests(unittest.TestCase):
    def test_all_targets_observe_app_rejection_and_unwind(self):
        for target, name in TARGETS.items():
            with self.subTest(target=target):
                device = FakeUI(target)
                facts = run_launch(device, target, name)
                self.assertEqual(facts["state"], "app_rejection_with_way_forward")
                self.assertTrue(facts["appPathObserved"])
                self.assertTrue(facts["plainError"])
                self.assertTrue(facts["noHang"])
                self.assertFalse(facts["daemonLaunchObserved"])
                self.assertEqual(device.page, "chats")
                self.assertEqual(device.stack, [])
                self.assertEqual(device.draft, "existing private unsent draft")
                self.assertEqual(device.project, "/root/projects/private-name")
                self.assertEqual(device.taps, ["New conversation", "OpenCode", "Sign in to " + name])

    def test_current_selected_claude_chip_only_opens_picker_without_selecting_it(self):
        class SelectedClaude(FakeUI):
            def ui(self):
                if self.page == 'new':
                    return ['New conversation','What should we work on?','Claude Code']
                return super().ui()
            def tap_node(self,node):
                if node == 'Claude Code' and self.page == 'new':
                    self.taps.append(node)
                    self.stack.append(self.page)
                    self.page='picker'
                else:
                    super().tap_node(node)
        device=SelectedClaude('fx',certified=True)
        facts=run_launch(device,'fx','fx',deadlineSeconds=2)
        self.assertEqual(facts['state'],'blocked_by_certification')
        self.assertEqual(device.taps,['New conversation','Claude Code'])
        self.assertFalse(facts['daemonLaunchObserved'])
        self.assertEqual(device.page,'chats')

    def test_android_repeated_status_hint_stays_target_scoped(self):
        class RepeatedHint(FakeUI):
            def ui(self):
                rows=super().ui()
                return [row.replace('Not certified on this version yet',
                        'Not certified on this version yet, Not certified on this version yet')
                        if row.startswith(self.name+'\n') else row for row in rows]
        device=RepeatedHint('fx',certified=True)
        facts=run_launch(device,'fx','fx')
        self.assertEqual(facts['state'],'blocked_by_certification')
        self.assertEqual(facts['plainCopy'],['Not certified on this version yet'])
        self.assertFalse(facts['daemonLaunchObserved'])

    def test_missing_target_fails_closed_and_never_uses_claude(self):
        device = FakeUI(missing=True)
        facts = run_launch(device, "codex", "Codex")
        self.assertEqual(facts["code"], "target_missing")
        self.assertFalse(facts["appPathObserved"])
        self.assertFalse(facts["daemonLaunchObserved"])
        self.assertEqual(device.taps, ["New conversation", "OpenCode"])
        self.assertEqual(device.page, "chats")

    def test_certification_block_never_taps_fix_or_claims_launch(self):
        device = FakeUI(certified=True)
        facts = run_launch(device, "codex", "Codex")
        self.assertEqual(facts["state"], "blocked_by_certification")
        self.assertEqual(facts["plainCopy"], ["Not certified on this version yet"])
        self.assertEqual(len(device.taps), 2)
        self.assertFalse(facts["daemonLaunchObserved"])

    def test_generic_sign_in_cannot_substitute_for_scoped_target_action(self):
        device = FakeUI(fix=False)
        facts = run_launch(device, "codex", "Codex")
        self.assertEqual(facts["state"], "blocked_by_auth")
        self.assertEqual(facts["code"], "target_fix_action_missing")
        self.assertEqual(len(device.taps), 2)

    def test_small_delay_succeeds(self):
        device = FakeUI(delayed=.5)
        facts = run_launch(device, "codex", "Codex", deadlineSeconds=2)
        self.assertTrue(facts["noHang"])
        self.assertEqual(facts["elapsedSeconds"], .5)

    def test_long_delay_is_bounded_and_does_not_claim_no_hang(self):
        device = FakeUI(delayed=99)
        facts = run_launch(device, "codex", "Codex", deadlineSeconds=1)
        self.assertEqual(facts["code"], "deadline_exceeded")
        self.assertFalse(facts["noHang"])
        self.assertEqual(facts["elapsedSeconds"], 1)
        self.assertEqual(device.taps, [])

    def test_private_screen_copy_and_errors_never_escape(self):
        device = FakeUI(private=True)
        facts = run_launch(device, "codex", "Codex")
        self.assertNotIn("private", repr(facts))
        self.assertNotIn("sk-unlogged", repr(facts))
        device = FakeUI(back_failure=True)
        facts = run_launch(device, "codex", "Codex")
        self.assertEqual(facts["code"], "navigation_restore_failed")
        self.assertFalse(facts["navigationUnwound"])
        self.assertFalse(facts["noHang"])
        self.assertNotIn("private", repr(facts))

    def test_claude_and_mismatched_names_refused_before_device_read(self):
        for target, name in [("claude", "Claude Code"), ("codex", "Claude Code"),
                             ("codex", "Codex\nSign in to Claude Code")]:
            with self.subTest(target=target, name=name):
                device = FakeUI()
                with self.assertRaises(ValueError):
                    run_launch(device, target, name)
                self.assertEqual(device.taps, [])

    def test_invalid_deadlines_refused(self):
        for deadline in [0, -1, True, float("nan"), float("inf"), 121, "30"]:
            with self.subTest(deadline=deadline):
                with self.assertRaises(ValueError):
                    run_launch(FakeUI(), "codex", "Codex", deadline)

    def test_capture_hook_runs_before_auth_frame_is_unwound_and_discards_result(self):
        device = FakeUI()
        captured = []
        def capture():
            captured.append(device.page)
            return 'private screenshot path or account text'
        device.capture_launch_rejection = capture
        facts = run_launch(device, "codex", "Codex")
        self.assertEqual(captured, ['auth'])
        self.assertTrue(facts['rejectionCaptured'])
        self.assertEqual(device.page, 'chats')
        self.assertNotIn('private', repr(facts))

    def test_failed_capture_is_closed_and_still_unwinds(self):
        device = FakeUI()
        def capture():
            raise RuntimeError('private screenshot detail')
        device.capture_launch_rejection = capture
        facts = run_launch(device, 'codex', 'Codex')
        self.assertEqual(facts['code'], 'rejection_capture_failed')
        self.assertFalse(facts['rejectionCaptured'])
        self.assertFalse(facts['noHang'])
        self.assertEqual(device.page, 'chats')
        self.assertNotIn('private', repr(facts))


if __name__ == "__main__":
    unittest.main()
