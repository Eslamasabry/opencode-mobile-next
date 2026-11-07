"""E00-S09 host-side Part C: what the update must keep, read from recorded dumpsys text (no device)."""
import unittest

from runner import device_state as ds

PKG = "io.github.eslamasabry.opencode_mobile"

PACKAGE_DUMP = f"""Packages:
  Package [{PKG}] (1a2b3c):
    versionCode=62 minSdk=24 targetSdk=36
    install permissions:
      android.permission.INTERNET: granted=true
      android.permission.FOREGROUND_SERVICE: granted=true
    User 0: ceDataInode=1234 installed=true
      runtime permissions:
        android.permission.POST_NOTIFICATIONS: granted=true, flags=[ USER_SET ]
        android.permission.RECORD_AUDIO: granted=true, flags=[ USER_SET ]
        android.permission.CAMERA: granted=false, flags=[ USER_SET ]
      enabledComponents:
"""

NOTIFICATION_DUMP = f"""  AppSettings: com.other.app (10100)
    NotificationChannel{{mId='other', mName=Other, mImportance=4, mBypassDnd=false}}
  AppSettings: {PKG} (10200)
    NotificationChannel{{mId='finished', mName=Finished replies, mDescription=, mImportance=3, mLights=false}}
    NotificationChannel{{mId='live', mName=Live, mImportance=2, mLights=false}}
  AppSettings: com.third (10300)
    NotificationChannel{{mId='x', mName=X, mImportance=1}}
"""

SHORTCUT_DUMP = f"""  Package: com.other.app  UID: 10100
      ShortcutInfo {{id=z, flags=0x1 [Dyn ], packageName=com.other.app}}
  Package: {PKG}  UID: 10200
    Shortcuts:
      ShortcutInfo {{id=ses_one, flags=0x3 [Dyn Pin ], packageName={PKG}, activity=...}}
      ShortcutInfo {{id=ses_two, flags=0x1 [Dyn ], packageName={PKG}}}
      ShortcutInfo {{id=new_chat, flags=0x2 [Pin ], packageName={PKG}}}
  Launcher: com.google.android.apps.nexuslauncher  Package: ...
"""

APPWIDGET_DUMP = f"""Providers:
  [0] provider ProviderId{{user:0, app:10200, cmp:ComponentInfo{{{PKG}/{PKG}.SessionsWidgetProvider}}}}
Widgets:
  [0] id=7
    host=HostId{{user:0, app:10079, hostId:1024, pkg:com.google.android.apps.nexuslauncher}}
    provider=ProviderId{{user:0, app:10200, cmp:ComponentInfo{{{PKG}/{PKG}.SessionsWidgetProvider}}}}
  [1] id=8
    host=HostId{{user:0, app:10079, hostId:1024, pkg:com.google.android.apps.nexuslauncher}}
    provider=ProviderId{{user:0, app:10100, cmp:ComponentInfo{{com.other.app/com.other.app.W}}}}
"""

ALARM_DUMP = f"""  Pending alarm batches: 3
    RTC_WAKEUP #0: Alarm{{1a2b3c type 0 origWhen 1762430000000 {PKG}}}
      tag=*walarm*:{PKG}/.ScheduledPromptReceiver
    RTC #1: Alarm{{4d5e6f type 1 origWhen 1762430000000 com.other.app}}
    ELAPSED #2: Alarm{{7a8b9c type 3 origWhen 123 {PKG}}}
"""


def state(**over):
    base = {
        "versionCode": 62, "runtimeGrants": {"android.permission.POST_NOTIFICATIONS": True},
        "installGrants": {}, "appops": {"SCHEDULE_EXACT_ALARM": "allow"},
        "channels": {"finished": 3}, "shortcuts": {"pinned": ["ses_one"], "dynamic": ["ses_one"]},
        "widgets": {"7": f"{PKG}.SessionsWidgetProvider"}, "tiles": [".AttentionTileService"],
        "alarms": 1, "linuxReady": True, "rootfsInodes": 51234, "voiceFiles": {"files/voice_models/a.onnx": 10},
    }
    base.update(over)
    return base


class ParserTest(unittest.TestCase):
    def test_B_MIGR_013_runtime_and_install_grants_are_read_per_permission(self):
        self.assertEqual({"android.permission.POST_NOTIFICATIONS": True, "android.permission.RECORD_AUDIO": True,
                          "android.permission.CAMERA": False}, ds.parse_runtime_grants(PACKAGE_DUMP))
        self.assertEqual({"android.permission.INTERNET": True, "android.permission.FOREGROUND_SERVICE": True},
                         ds.parse_install_grants(PACKAGE_DUMP))
        self.assertEqual(62, ds.parse_version_code(PACKAGE_DUMP))

    def test_B_MIGR_013_channels_are_the_apps_own_with_importance(self):
        self.assertEqual({"finished": 3, "live": 2}, ds.parse_channels(NOTIFICATION_DUMP, PKG))

    def test_B_MIGR_013_shortcuts_split_into_pinned_and_dynamic(self):
        self.assertEqual({"pinned": ["new_chat", "ses_one"], "dynamic": ["ses_one", "ses_two"]},
                         ds.parse_shortcuts(SHORTCUT_DUMP, PKG))

    def test_B_MIGR_013_only_the_apps_placed_widgets_are_counted(self):
        self.assertEqual({"7": f"{PKG}.SessionsWidgetProvider"}, ds.parse_widgets(APPWIDGET_DUMP, PKG))

    def test_B_MIGR_013_tiles_keep_the_users_order(self):
        tiles = f"wifi,custom({PKG}/.PauseTileService),bt,custom(com.x/.T),custom({PKG}/.AttentionTileService)\n"
        self.assertEqual([".PauseTileService", ".AttentionTileService"], ds.parse_tiles(tiles, PKG))

    def test_B_MIGR_013_alarms_of_the_app_are_counted(self):
        self.assertEqual(2, ds.parse_alarm_count(ALARM_DUMP, PKG))

    def test_B_MIGR_013_appops_keep_the_mode_and_drop_times(self):
        out = "SCHEDULE_EXACT_ALARM: allow; time=+1h ago\nRUN_IN_BACKGROUND: ignore\nRECORD_AUDIO (uid): allow\n"
        self.assertEqual({"SCHEDULE_EXACT_ALARM": "allow", "RUN_IN_BACKGROUND": "ignore", "RECORD_AUDIO": "allow"},
                         ds.parse_appops(out))

    def test_B_MIGR_009_voice_files_are_listed_with_sizes(self):
        self.assertEqual({"files/voice_models/a.onnx": 1024, "files/voice_models/tokens.txt": 7},
                         ds.parse_file_sizes("1024 files/voice_models/a.onnx\n7 files/voice_models/tokens.txt\n"))

    def test_B_MIGR_008_inode_count_stays_on_one_filesystem_and_prunes_volatile_folders(self):
        cmd = ds.inode_count_command(PKG)
        self.assertIn("-xdev", cmd)
        self.assertIn(f"/data/data/{PKG}/files/linux/ubuntu", cmd)
        for folder in ("tmp", "var/log", "proc"):
            self.assertIn(f"/files/linux/ubuntu/{folder} ", cmd + " ")

    def test_capture_refuses_an_app_id_that_is_not_a_package_name(self):
        with self.assertRaises(ValueError):
            ds.capture(lambda *a, **k: "", "x; reboot")


class CompareTest(unittest.TestCase):
    def verdicts(self, before, after):
        return {v.id: v for v in ds.compare(before, after)}

    def test_B_MIGR_004_identical_state_and_a_higher_version_pass_every_check(self):
        out = self.verdicts(state(), state(versionCode=10062))
        self.assertTrue(all(v.ok for v in out.values()), [v for v in out.values() if not v.ok])

    def test_B_MIGR_004_same_version_code_is_not_an_update(self):
        self.assertFalse(self.verdicts(state(), state())["updated-in-place"].ok)

    def test_B_MIGR_008_a_changed_rootfs_inode_count_fails(self):
        v = self.verdicts(state(), state(versionCode=10062, rootfsInodes=51000))["linux-inodes"]
        self.assertFalse(v.ok)
        self.assertIn("51234 -> 51000", v.detail)

    def test_B_MIGR_008_no_linux_before_the_update_is_a_failure_not_a_skip(self):
        self.assertFalse(self.verdicts(state(rootfsInodes=-1), state(rootfsInodes=-1))["linux-inodes"].ok)

    def test_B_MIGR_009_a_voice_file_downloaded_again_or_lost_fails(self):
        v = self.verdicts(state(), state(voiceFiles={}))["voice-files"]
        self.assertFalse(v.ok)
        self.assertIn("missing after", v.detail)

    def test_B_MIGR_013_a_lost_widget_tile_or_pinned_shortcut_fails(self):
        out = self.verdicts(state(), state(widgets={}, tiles=[], shortcuts={"pinned": [], "dynamic": []}))
        self.assertFalse(out["widgets"].ok)
        self.assertFalse(out["tiles"].ok)
        self.assertFalse(out["shortcuts"].ok)

    def test_B_MIGR_013_republished_dynamic_shortcuts_may_grow(self):
        after = state(shortcuts={"pinned": ["ses_one"], "dynamic": ["ses_one", "ses_two"]})
        self.assertTrue(self.verdicts(state(), after)["shortcuts"].ok)

    def test_B_MIGR_013_no_armed_schedule_after_the_update_fails(self):
        self.assertFalse(self.verdicts(state(), state(alarms=0))["schedule-armed"].ok)

    def test_B_153_a_seed_part_a_could_not_make_is_not_run_never_pass(self):
        before = state(widgets={}, shortcuts={"pinned": [], "dynamic": []}, alarms=0)
        out = self.verdicts(before, state(versionCode=10062, widgets={}, alarms=0,
                                          shortcuts={"pinned": [], "dynamic": []}))
        for vid in ("widgets", "shortcuts", "schedule-armed"):
            self.assertTrue(out[vid].not_run, vid)
            self.assertFalse(out[vid].ok, vid)
            self.assertEqual("NOT RUN", out[vid].word)
            self.assertIn("B-153", out[vid].detail)

    def test_B_153_a_seeded_widget_lost_by_the_update_is_still_a_failure(self):
        v = self.verdicts(state(), state(versionCode=10062, widgets={}))["widgets"]
        self.assertFalse(v.not_run)
        self.assertEqual("FAIL", v.word)

    def test_B_MIGR_013_a_changed_channel_importance_fails(self):
        v = self.verdicts(state(), state(channels={"finished": 4}))["channels"]
        self.assertFalse(v.ok)
        self.assertIn("finished 3 -> 4", v.detail)

    def test_B_150_channels_native_adds_pass_when_every_old_channel_keeps_id_and_importance(self):
        # The 2.1.4 run: native adds four channels of its own; every Flutter channel is unchanged.
        before = {"finished": 3, "live": 2}
        after = dict(before, opencode_builtin_server=2, opencode_coding_action=4,
                     opencode_coding_action_quiet=2, opencode_coding_status=2)
        v = self.verdicts(state(channels=before), state(channels=after))["channels"]
        self.assertTrue(v.ok)
        self.assertIn("new after: opencode_builtin_server", v.detail)

    def test_B_150_channels_a_removed_old_channel_fails(self):
        v = self.verdicts(state(channels={"finished": 3, "live": 2}),
                          state(channels={"finished": 3, "opencode_builtin_server": 2}))["channels"]
        self.assertFalse(v.ok)
        self.assertIn("missing after: live", v.detail)

    def test_B_150_channels_a_changed_importance_fails_even_with_additions(self):
        v = self.verdicts(state(channels={"finished": 3}),
                          state(channels={"finished": 2, "opencode_coding_status": 2}))["channels"]
        self.assertFalse(v.ok)
        self.assertIn("finished 3 -> 2", v.detail)

    def test_B_MIGR_013_a_lost_grant_or_app_op_fails(self):
        self.assertFalse(self.verdicts(state(), state(runtimeGrants={}))["grants"].ok)
        self.assertFalse(self.verdicts(state(), state(appops={"SCHEDULE_EXACT_ALARM": "default"}))["grants"].ok)


if __name__ == "__main__":
    unittest.main()
