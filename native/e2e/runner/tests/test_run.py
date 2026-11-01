"""Runner guards: one verified emulator serial, never another device; hooks are allowlisted."""
import unittest

from runner import hooks, run


class FakeAdb:
    def __init__(self, props, devices="emulator-5584\tdevice\n", avd="OCN_A"):
        self.props, self.devices, self.avd, self.calls = props, devices, avd, []

    def __call__(self, args, check=True, timeout=60):
        self.calls.append(list(args))
        if args[:1] == ["devices"]:
            return "List of devices attached\n" + self.devices
        if "getprop" in args:
            return self.props.get(args[-1], "") + "\n"
        if args[-3:] == ["emu", "avd", "name"] or args[-2:] == ["avd", "name"]:
            return self.avd + "\nOK\n"
        if "get-state" in args:
            return "device\n"
        return ""


class GuardTest(unittest.TestCase):
    def test_default_serial_is_emulator_5584(self):
        self.assertEqual("emulator-5584", run.parse_args(["--app", "flutter", "--flows", "E2E-01"]).serial)

    def test_non_emulator_serial_refused(self):
        with self.assertRaises(run.Refused):
            run.verify_device("R5CT1234ABC", FakeAdb({"ro.kernel.qemu": "1"}), expect_avd=None)

    def test_device_that_is_not_qemu_refused(self):
        with self.assertRaises(run.Refused):
            run.verify_device("emulator-5584", FakeAdb({}), expect_avd=None)

    def test_missing_serial_refused(self):
        with self.assertRaises(run.Refused):
            run.verify_device("emulator-5584", FakeAdb({"ro.kernel.qemu": "1"}, devices="emulator-5554\tdevice\n"), None)

    def test_wrong_avd_refused(self):
        with self.assertRaises(run.Refused):
            run.verify_device("emulator-5584", FakeAdb({"ro.kernel.qemu": "1"}, avd="Pixel_6"), "OCN_A")

    def test_verified_emulator_passes_and_every_call_names_the_serial(self):
        adb = FakeAdb({"ro.kernel.qemu": "1"})
        run.verify_device("emulator-5584", adb, "OCN_A")
        for call in adb.calls:
            if call[:1] != ["devices"]:
                self.assertEqual(["-s", "emulator-5584"], call[:2], call)

    def test_scrub_replaces_secrets(self):
        self.assertEqual("pw=<redacted> k=<redacted>", run.scrub("pw=hunter2x k=sk-e2e-FAKE-0123456789abcdef", ["hunter2x"]))


class HookTest(unittest.TestCase):
    def test_unknown_hook_refused(self):
        with self.assertRaises(hooks.HookError):
            hooks.plan("rm-rf", {}, app_id="x.y")

    def test_net_hook_builds_svc_commands(self):
        cmds = hooks.plan("net", {"wifi": "off", "data": "on"}, app_id="x.y")
        self.assertIn(["shell", "svc", "wifi", "disable"], cmds)
        self.assertIn(["shell", "svc", "data", "enable"], cmds)

    def test_permission_hook_validates_names(self):
        self.assertEqual([["shell", "pm", "revoke", "x.y", "android.permission.CAMERA"]],
                         hooks.plan("permission", {"perm": "CAMERA", "grant": False}, app_id="x.y"))
        with self.assertRaises(hooks.HookError):
            hooks.plan("permission", {"perm": "CAMERA; reboot", "grant": False}, app_id="x.y")

    def test_share_text_is_shell_quoted(self):
        cmds = hooks.plan("share-text", {"text": "hi'; reboot"}, app_id="x.y")
        joined = " ".join(cmds[0])
        self.assertIn("'hi'\"'\"'; reboot'", joined)

    def test_every_hook_is_documented(self):
        for name, h in hooks.HOOKS.items():
            self.assertTrue(h.doc, name)


if __name__ == "__main__":
    unittest.main()
