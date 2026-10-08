import contextlib
import unittest

from proof import certify_removal, TARGETS, COPY, SERIAL, LOCK


NORMAL = dict(apk='/reviewed/oc-2197.apk', build=2197, sha256='a'*64,
              sourceRevision='b'*40, dartDefines={})


class FakePorts:
    emulator_serial = SERIAL
    emulator_lock_path = LOCK

    def __init__(self, target='codex', missing=False, confirmation=False,
                 change_claude=False, restore_fail=False, orphan=False):
        self.target, self.name = target, TARGETS[target][0]
        self.missing, self.confirmation = missing, confirmation
        self.change_claude, self.restore_fail, self.orphan = change_claude, restore_fail, orphan
        self.locked = False
        self.calls, self.records = [], []
        self.installed, self.removed = False, False
        self.page = 'agents'
        self.now = 0
        self.free = 1800000000

    @contextlib.contextmanager
    def locked_session(self):
        self.locked = True
        try:
            yield
        finally:
            self.locked = False

    def clock(self): return self.now
    def sleep(self, value): self.now += value
    def available_storage_bytes(self):
        assert self.locked
        return self.free
    def require_idle_setup(self):
        assert self.locked
    def verify_normal_artifact(self, receipt, signer):
        self.calls.append('verify')
        return True
    def restore_normal(self, receipt, signer):
        assert self.locked
        self.calls.append('restore')
        if self.restore_fail and self.calls.count('restore') == 2:
            raise RuntimeError('private restore details')
        return True
    def target_inventory(self, target):
        assert self.locked
        present = target == self.target and self.installed and not self.removed
        return dict(leftovers=present, targetPids=[4242] if self.orphan and self.removed else [],
                    ownedAllocatedBytes=8192 if present else 0, staging=[], lockPresent=False,
                    pinMatches=present, linkMatches=present)
    def install_through_app(self, target, name):
        assert self.locked
        assert target == self.target
        self.calls.append('app-install')
        self.installed = True
    def agent_state_projection(self, target):
        return dict(version=TARGETS[target][1], authState='probeUnsupported')
    def retention_projection(self):
        return dict(claudeReady=not (self.change_claude and self.removed), claudeSignedIn=True,
                    claudeChatCount=3, claudeChatIdsDigest='c'*64, phoneGateDigest='d'*64,
                    sharedNodePresent=True, sharedPaseoPresent=True, targetAccountHomePresent=False)
    def ui(self):
        if self.page == 'confirm':
            return ['Remove '+self.name+' from this phone?', COPY,
                    'Remove' if not self.confirmation else 'Delete account', 'Cancel']
        if self.removed:
            return [self.name+'\nNot installed', self.name+' removed. Freed 8 KB.']
        return [] if self.missing else ['Remove '+self.name, 'Claude Code\nReady']
    def text(self, node): return node
    def tap_node(self, node):
        self.calls.append(node)
        if node == 'Remove '+self.name:
            self.page = 'confirm'
        elif node == 'Remove':
            self.page = 'agents'
            self.removed = True
            self.free += 4096  # Device free-space delta is not allocated bytes.
        else:
            raise AssertionError('Forbidden public action')
    def capture(self, label): self.calls.append('capture')
    def record_result(self, value):
        assert self.locked
        self.records.append(dict(value))


class ProofTests(unittest.TestCase):
    def run_proof(self, ports, **kwargs):
        return certify_removal(ports.target, ports, NORMAL, clock=ports.clock,
                               sleep=ports.sleep, **kwargs)

    def test_six_targets_use_public_remove_and_retain_claude(self):
        for target in TARGETS:
            with self.subTest(target=target):
                ports = FakePorts(target)
                result = self.run_proof(ports)
                self.assertEqual(result['state'], 'pass')
                self.assertTrue(result['normalRestored'])
                self.assertTrue(result['retainedClaudeAndSharedHost'])
                self.assertEqual(result['removedAllocatedBytes'], 8192)
                self.assertEqual(result['freeSpaceDeltaBytes'], 4096)
                self.assertFalse(ports.locked)
                self.assertEqual(ports.calls.count('restore'), 2)
                self.assertIn('Remove '+TARGETS[target][0], ports.calls)
                self.assertIn('Remove', ports.calls)

    def test_missing_public_action_remains_partial_without_shell_fallback(self):
        ports = FakePorts(missing=True)
        result = self.run_proof(ports)
        self.assertEqual(result['code'], 'public_remove_action_missing')
        self.assertEqual(result['state'], 'partial')
        self.assertFalse(result['deviceProof'])
        self.assertFalse(ports.removed)

    def test_wrong_confirmation_never_confirms(self):
        ports = FakePorts(confirmation=True)
        with self.assertRaisesRegex(RuntimeError, 'proof_failed'):
            self.run_proof(ports)
        self.assertNotIn('Remove', ports.calls)
        self.assertFalse(ports.removed)
        self.assertTrue(ports.records[-1]['normalRestored'])

    def test_retained_claude_change_invalidates_success(self):
        ports = FakePorts(change_claude=True)
        with self.assertRaisesRegex(RuntimeError, 'proof_failed'):
            self.run_proof(ports)
        self.assertEqual(ports.records[-1]['state'], 'fail')
        self.assertNotIn('capture', ports.calls)

    def test_restore_failure_records_and_releases_lock_before_raising(self):
        ports = FakePorts(restore_fail=True)
        with self.assertRaisesRegex(RuntimeError, 'normal_restore_failed'):
            self.run_proof(ports)
        self.assertFalse(ports.locked)
        self.assertEqual(ports.records[-1]['code'], 'normal_restore_failed')
        self.assertNotIn('private', repr(ports.records))

    def test_orphan_process_prevents_pass_with_bounded_wait(self):
        ports = FakePorts(orphan=True)
        with self.assertRaisesRegex(RuntimeError, 'proof_failed'):
            self.run_proof(ports, timeout_seconds=1)
        self.assertEqual(ports.now, 1)
        self.assertEqual(ports.records[-1]['state'], 'fail')

    def test_low_real_storage_refuses_before_artifact_restore_or_install(self):
        ports = FakePorts(); ports.free = 799999999
        with self.assertRaisesRegex(RuntimeError, 'proof_failed'):
            self.run_proof(ports)
        self.assertEqual(ports.calls, [])
        self.assertFalse(ports.locked)

    def test_existing_target_preflight_refuses_before_restore(self):
        ports = FakePorts(); ports.installed = True
        with self.assertRaisesRegex(RuntimeError, 'proof_failed'):
            self.run_proof(ports)
        self.assertEqual(ports.calls, ['verify'])

    def test_no_claude_target_or_unapproved_guard_artifact(self):
        ports = FakePorts()
        with self.assertRaisesRegex(RuntimeError, 'unsupported_agent'):
            certify_removal('claude', ports, NORMAL)
        guarded = dict(NORMAL, dartDefines={'OC_QA_AGENT_INSTALL_MIN_FREE_BYTES': 8589934592})
        with self.assertRaisesRegex(RuntimeError, 'normal_guard_not_off'):
            certify_removal('codex', ports, guarded)
        self.assertEqual(ports.calls, [])

    def test_wrong_emulator_or_lock_refuses_before_device_access(self):
        for field, value in [('emulator_serial', 'other-device'),
                             ('emulator_lock_path', '/tmp/unshared.lock')]:
            with self.subTest(field=field):
                ports = FakePorts(); setattr(ports, field, value)
                with self.assertRaisesRegex(RuntimeError, 'wrong_device_or_lock'):
                    self.run_proof(ports)
                self.assertEqual(ports.calls, [])


if __name__ == '__main__':
    unittest.main()
