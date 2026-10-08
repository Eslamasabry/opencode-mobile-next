"""Private same-context QA producer adapter; caller owns native commit and lock.

Mock host checks do not qualify Android cgroup separation, native ownership or
orphan survival. Metadata is never a substitute for current kernel identity.
"""
import copy
import json
import re
import shlex
import time

try:
    import bb9_external_installer as E
except ModuleNotFoundError:
    from tool.qa import bb9_external_installer as E

COMPONENT = E.PACKAGE + '/.BuiltinInstallerProducerQaService'
ACTION_START = 'oc.bb9.producer.START'
ACTION_PERMIT = 'oc.bb9.producer.PERMIT'
ACTION_STOP = 'oc.bb9.producer.STOP'
METADATA = E.PRIVATE + '/files/bb9-producer-qa.json'
PRODUCER_COMMAND = E.PACKAGE + ':bb9producer'
PERMIT_SECONDS = 10
SERVICE_ERRORS = frozenset(['producer_action_invalid', 'producer_ticket_invalid', 'producer_ticket_changed', 'producer_already_started', 'producer_permit_invalid', 'producer_stopped', 'producer_unavailable', 'producer_identity_invalid', 'producer_root_invalid', 'producer_root_changed', 'producer_leader_invalid', 'producer_leader_changed', 'producer_ancestry_invalid', 'producer_app_changed', 'producer_child_exited', 'producer_header_invalid', 'producer_header_timeout', 'producer_output_overflow', 'producer_export_invalid', 'producer_command_invalid', 'producer_environment_invalid', 'producer_fixture_invalid', 'producer_writer_invalid', 'producer_metadata_invalid', 'producer_metadata_exists', 'producer_metadata_changed', 'producer_file_unavailable', 'producer_file_invalid', 'producer_file_changed', 'producer_kernel_invalid', 'producer_uid_invalid', 'producer_boot_invalid', 'producer_lifetime_expired', 'producer_system_timeout', 'producer_worker_unavailable']) | frozenset('producer_' + stage + '_' + category for stage in
    ['identity', 'export', 'app_identity', 'app_uid', 'command', 'environment', 'metadata', 'process', 'pid', 'kernel', 'header', 'proof'] for category in
    ['denied', 'missing', 'system', 'field', 'method', 'io', 'unavailable'])


def android_title_matches(raw, expected):
    """Only the two authored Android titles and an all-NUL tail are accepted.

    Matching strings contain only the constant ASCII title and NUL bytes, so
    the length bound is also exactly their encoded byte bound.
    """
    return (isinstance(raw, str) and expected in {PRODUCER_COMMAND, E.PACKAGE} and
            len(expected) < len(raw) <= E.PRIVATE_BYTES and raw.startswith(expected) and
            all(byte == '\x00' for byte in raw[len(expected):]))


class ServiceInstaller(E.ExternalInstaller):
    """A fixed native Service reads its original export; no host child/stdio."""

    def __init__(self, device):
        super().__init__(device)
        self.producer = None
        self.launch_requested = False
        self.stop_requested = False
        self.metadata_state = None
        self.metadata_error = None
        self.recovered_reader = None
        self.recovered_reader_cgroup = None
        self.producer_checks = self._empty_producer_checks()

    @staticmethod
    def _empty_producer_checks():
        return {key: False for key in ['producerKernelIdentityMatches', 'producerUidMatches',
                                      'producerTitleExactOneNulMatches', 'producerTitleCanonicalMatches']}

    def _intent(self, action, ticket=False):
        command = ['shell', 'am', 'start-foreground-service' if action == ACTION_START else 'startservice',
                   '-n', COMPONENT, '-a', action]
        if ticket:
            command += ['--es', 'ticketId', self.export['ticketId']]
        try:
            result = self.device.adb(*command, timeout=self._remaining())
            E.require(result.returncode == 0 and isinstance(result.stdout, str) and
                      len(result.stdout.encode()) <= E.PRIVATE_BYTES and
                      not re.search(r'(?im)^\s*(?:error|exception|securityexception)\b', result.stdout),
                      'service_intent_failed')
        except E.Refused:
            raise
        except Exception:
            raise E.Refused('service_intent_failed') from None

    def _metadata(self):
        # Fixed path only, bounded content, owned regular file and stable opened
        # inode. Never delete it: native cleanup owns the final ticket/type proof.
        script = ('p=' + shlex.quote(METADATA) + '; '
                  'if [ ! -e "$p" ] && [ ! -L "$p" ]; then printf "OC-BB9-METADATA-MISSING-1"; exit 0; fi; '
                  '[ ! -L "$p" ] && [ -f "$p" ] || exit 71; '
                  'a=$(stat -c "%u %i %s %F" "$p") || exit 71; '
                  f'[ "${{a%% *}}" = {self.export["uid"]} ] || exit 71; '
                  'exec 3< "$p" || exit 71; '
                  'b=$(stat -Lc "%u %i %s %F" /proc/$$/fd/3) || exit 71; '
                  '[ "$a" = "$b" ] && [ ! -L "$p" ] || exit 71; '
                  f'head -c {E.PRIVATE_BYTES + 1} <&3')
        raw = self._shell(script, E.PRIVATE_BYTES)
        E.require(raw != 'OC-BB9-METADATA-MISSING-1', 'service_metadata_missing')
        try:
            value = json.loads(raw)
        except Exception:
            raise E.Refused('service_metadata_invalid') from None
        required = {'version', 'ticketId', 'uid', 'producer', 'root', 'leader', 'state'}
        E.require(isinstance(value, dict) and required <= set(value) <= required | {'error'} and
                  type(value['version']) is int and value['version'] == 1 and
                  value['ticketId'] == self.export['ticketId'] and type(value['uid']) is int and
                  value['uid'] == self.export['uid'] and
                  value['state'] in {'waiting', 'running', 'failed', 'stopped'}, 'service_metadata_invalid')
        E.identity_map(value['producer'])
        for key in ['root', 'leader']:
            if value[key] is not None:
                E.identity_map(value[key])
        error = value.get('error')
        E.require(error is None or isinstance(error, str) and re.fullmatch('[a-z][a-z0-9_]{0,63}', error),
                  'service_metadata_invalid')
        self.metadata_state = value['state']
        self.metadata_error = error if error in SERVICE_ERRORS else 'unknown' if error is not None else None
        return value

    def _main_app(self):
        app = self._identity(self.export['app']['pid'])
        E.require(app and app['state'] not in {'Z', 'X'} and E.wire_identity(app) == self.export['app'] and
                  self._uid(app['pid']) == [self.export['uid']] * 4, 'external_app_changed')

    def _prove_producer(self, value):
        # Preserve only booleans from this proof attempt, including its failure.
        # A later diagnostic never prints or retains the raw kernel title.
        self.producer_checks = self._empty_producer_checks()
        producer = value['producer']
        actual = self._identity(producer['pid'])
        self.producer_checks['producerKernelIdentityMatches'] = bool(actual and actual['state'] not in {'Z', 'X'} and
            E.wire_identity(actual) == producer and producer['pid'] != self.export['app']['pid'])
        if self.producer_checks['producerKernelIdentityMatches']:
            self.producer_checks['producerUidMatches'] = self._uid(producer['pid']) == [self.export['uid']] * 4
            if self.producer_checks['producerUidMatches']:
                title = self._read(producer['pid'], 'cmdline', E.PRIVATE_BYTES)
                self.producer_checks['producerTitleExactOneNulMatches'] = title == PRODUCER_COMMAND + '\x00'
                self.producer_checks['producerTitleCanonicalMatches'] = android_title_matches(title, PRODUCER_COMMAND)
        E.require(self.producer_checks['producerKernelIdentityMatches'] and self.producer_checks['producerUidMatches'] and
                  self.producer_checks['producerTitleCanonicalMatches'],
                  'service_producer_unproven')
        E.require(self.producer is None or self.producer == producer, 'service_producer_changed')
        E.require(E.same_process(actual, self._identity(producer['pid'])), 'service_producer_changed')
        self.producer = copy.deepcopy(producer)

    def _prove_root(self):
        E.require(self.root and E.same_process(self.root, self._identity(self.root['pid'])),
                  'external_root_changed')
        pid = self.root['pid']
        current = self._identity(pid)
        E.require(current and current['state'] not in {'Z', 'X'} and
                  current['group'] == self.root['group'] and current['session'] == self.root['session'] and
                  pid not in {self.export['app']['pid'], self.producer['pid']} and
                  self._uid(pid) == [self.export['uid']] * 4 and self._nonce(pid),
                  'external_root_owner_invalid')
        argv = self._read(pid, 'cmdline', E.PRIVATE_BYTES).rstrip('\x00').split('\x00')
        original = self.export['command']
        proot = next(i for i, value in enumerate(original) if value.endswith('/libproot.so'))
        E.require(argv == original or original[0].endswith('/libaiteam_sandbox.so') and argv == original[proot:],
                  'external_root_command_changed')
        E.require(E.same_process(self.root, self._identity(pid)), 'external_root_changed')
        self.root_proven = True
        self.witnessed[pid] = copy.deepcopy(self.root)

    def _inventory(self):
        rows = super()._inventory()
        # ProcessBuilder inherits the producer's Android session. Exclude only
        # that exact corroborated reader, never all Android process names/UIDs.
        if self.producer and self.producer['pid'] in rows:
            self._prove_producer({'producer': self.producer})
            rows.pop(self.producer['pid'])
        if self.recovered_reader:
            # Revalidate even when ps omitted it: a reused acknowledged PID
            # must never silently become an admitted reader.
            self._prove_recovered_reader()
            rows.pop(self.recovered_reader['pid'], None)
        return rows

    def _cgroup(self, pid):
        E.require(type(pid) is int and pid > 1, 'service_reader_identity_invalid')
        value = self._shell(f'head -c 4097 /proc/{pid}/cgroup', 4096)
        E.require(value and len(value.encode()) <= 4096 and all(
            re.fullmatch(r'[0-9]+:[A-Za-z0-9_,.-]*:/[^\x00-\x1f\x7f]*', line)
            for line in value.splitlines()), 'service_reader_cgroup_invalid')
        return value

    def _prove_recovered_reader(self):
        expected = self.recovered_reader
        E.require(expected is not None, 'service_reader_unacknowledged')
        pid = expected['pid']
        current = self._identity(pid)
        E.require(current and current['state'] not in {'Z', 'X'} and E.wire_identity(current) == expected and
                  self._uid(pid) == [self.export['uid']] * 4 and
                  android_title_matches(self._read(pid, 'cmdline', E.PRIVATE_BYTES), E.PACKAGE) and
                  not any(item.startswith('OC_RUNTIME_OWNER=') for item in
                          self._read(pid, 'environ', E.PRIVATE_BYTES).split('\x00')) and
                  current['session'] != self.leader['session'], 'service_reader_changed')
        E.require(self._cgroup(pid) == self.recovered_reader_cgroup, 'service_reader_cgroup_changed')
        E.require(E.same_process(current, self._identity(pid)), 'service_reader_changed')

    def acknowledge_recovered_app(self, identity, native_pid, native_ticks):
        """Accept one exact post-cold-start native Verify acknowledgement.

        The caller must obtain native PID/ticks from actual read-only native
        Verify after product recovery, prior-good activation and quiescence.
        Metadata callbacks and host mock outcomes are not native proof.
        """
        E.require(self.committed is not None and self.permitted and self.root_proven and self.leader,
                  'service_reader_ack_too_early')
        self.deadline = time.monotonic() + PERMIT_SECONDS
        E.require(isinstance(identity, dict) and set(identity) in
                  (E.IDENTITY_KEYS, E.IDENTITY_KEYS | {'state'}), 'service_reader_identity_invalid')
        expected = E.identity_map(E.wire_identity(identity))
        E.require(type(native_pid) is int and type(native_ticks) is int and
                  native_pid == expected['pid'] and native_ticks == expected['startTicks'],
                  'service_reader_native_proof_mismatch')
        E.require(expected['pid'] not in {self.export['app']['pid'], self.producer['pid']} | set(self.witnessed) and
                  expected['session'] != self.leader['session'], 'service_reader_owned_identity')
        E.require(all(not E.same_process(old, self._identity(pid)) for pid, old in self.witnessed.items()),
                  'service_reader_owned_tree_still_live')
        E.require(self.recovered_reader is None or self.recovered_reader == expected, 'service_reader_ack_changed')
        if self.recovered_reader is not None:
            self._prove_recovered_reader()
            return
        # Do not retain a failed acknowledgement as a usable exemption.
        previous, previous_cgroup = self.recovered_reader, self.recovered_reader_cgroup
        try:
            self.recovered_reader = copy.deepcopy(expected)
            self.recovered_reader_cgroup = self._cgroup(expected['pid'])
            self._prove_recovered_reader()
        except Exception:
            self.recovered_reader, self.recovered_reader_cgroup = previous, previous_cgroup
            raise

    def _prove_metadata_tree(self, value):
        self._prove_producer(value)
        E.require(value['root'] and value['leader'], 'service_tree_missing')
        E.require(E.wire_identity(self.root) == value['root'] and E.wire_identity(self.leader) == value['leader'],
                  'service_tree_changed')
        E.require(self.root['parent'] == self.producer['pid'], 'service_root_ancestry_invalid')
        self._prove_root()
        leader = self._identity(self.leader['pid'])
        E.require(leader and leader['state'] not in {'Z', 'X'} and
                  E.wire_identity(leader) == value['leader'] and
                  leader['pid'] not in {self.root['pid'], self.producer['pid'], self.export['app']['pid']} and
                  leader['pid'] == leader['group'] == leader['session'] and
                  self._uid(leader['pid']) == [self.export['uid']] * 4 and self._nonce(leader['pid']),
                  'external_leader_owner_invalid')
        E.require(leader['pid'] in self._capture() and E.same_process(leader, self._identity(leader['pid'])),
                  'external_leader_ancestry_invalid')

    def acknowledge_native_drain(self, native_verified, native_owned_writer_gone):
        """Accept actual native cold recovery, without adopting its new runtime.

        Both literal True flags must come from this fixture's read-only native
        Verify and actual native owned-writer drainage. Producer absence is an
        additional host guard; final native fixture cleanup independently
        requires producer ESRCH. Host mocks alone cannot qualify native drainage. The
        previously acknowledged Main reader is revalidated throughout; no UID
        inventory, metadata PID signalling, or new-tree discovery occurs here.
        """
        E.require(native_verified is True and native_owned_writer_gone is True,
                  'service_native_drain_proof_missing')
        E.require(self.committed is not None and self.permitted and self.root_proven and
                  self.root and self.leader and self.producer and self.recovered_reader,
                  'service_native_drain_too_early')
        self.deadline = time.monotonic() + PERMIT_SECONDS
        E.require(len(self.witnessed) <= 128, 'service_native_drain_originals_invalid')
        originals = {}
        # Retain both the committed observations and every later captured child.
        for value in [self.root, self.leader, *self.witnessed.values(), *self.committed['observed']]:
            item = E.identity_map(E.wire_identity(value))
            old = originals.get(item['pid'])
            E.require(old is None or old['startTicks'] == item['startTicks'],
                      'service_native_drain_originals_invalid')
            originals[item['pid']] = item
        E.require(len(originals) <= 130 and not set(originals) &
                  {self.producer['pid'], self.recovered_reader['pid']},
                  'service_native_drain_originals_invalid')

        def require_originals_absent():
            for pid, old in originals.items():
                try:
                    current = self._identity(pid)
                    if current is not None:
                        E.identity_map(E.wire_identity(current))
                except Exception:
                    raise E.Refused('service_native_drain_original_unproven') from None
                E.require(current is None or current['pid'] == pid and current['startTicks'] == old['startTicks'],
                          'service_native_drain_original_changed')
                E.require(current is None, 'service_native_drain_original_live')

        def require_same_boot():
            E.require(self._shell('cat /proc/sys/kernel/random/boot_id', 128).strip() ==
                      self.committed['ownership']['boot'], 'external_boot_changed')

        require_same_boot()
        while True:
            self._prove_recovered_reader()
            require_originals_absent()
            if self._producer_gone():
                # Recheck after the producer observation so a PID reuse or Main
                # replacement during the bounded wait never becomes an exemption.
                require_originals_absent()
                self._prove_recovered_reader()
                require_same_boot()
                self.drained = True
                return
            time.sleep(min(.1, self._remaining()))

    def start(self, exported):
        E.require(not self.launch_requested and self.export is None, 'external_already_started')
        self.export = E.validate_export(exported)
        self.deadline = time.monotonic() + E.START_SECONDS
        self._main_app()
        self.launch_requested = True  # Partial service handoff still requires cleanup.
        self._intent(ACTION_START)
        while True:
            try:
                value = self._metadata()
            except E.Refused as error:
                if str(error) != 'service_metadata_missing':
                    raise
                time.sleep(min(.1, self._remaining()))
                continue
            E.require(value['state'] not in {'failed', 'stopped', 'running'}, 'service_start_state_invalid')
            self.root = copy.deepcopy(value['root'])
            self.leader = copy.deepcopy(value['leader'])
            if self.root and self.leader:
                self._prove_metadata_tree(value)
                self._main_app()
                return {'root': copy.deepcopy(self.root), 'leader': copy.deepcopy(self.leader)}
            self._prove_producer(value)
            time.sleep(min(.1, self._remaining()))

    def permit(self):
        E.require(self.committed is not None and not self.permitted, 'external_permit_requires_commit')
        self.deadline = time.monotonic() + PERMIT_SECONDS
        value = self._metadata()
        E.require(value['state'] == 'waiting', 'service_permit_state_invalid')
        self._prove_metadata_tree(value)
        E.require(self._durable() == self.committed, 'external_durable_ticket_mismatch')
        E.require(self._shell('cat /proc/sys/kernel/random/boot_id', 128).strip() ==
                  self.committed['ownership']['boot'], 'external_boot_changed')
        self._main_app()
        # Intent delivery can succeed even if adb later fails. Never permit a
        # fallback producer after any possible partial handoff.
        self.permitted = True
        self._intent(ACTION_PERMIT, ticket=True)
        while True:
            value = self._metadata()
            E.require(value['state'] in {'waiting', 'running'}, 'service_permit_state_invalid')
            self._prove_metadata_tree(value)
            self._main_app()
            if value['state'] == 'running':
                return
            time.sleep(min(.1, self._remaining()))

    def _service_absent(self):
        try:
            result = self.device.adb('shell', 'dumpsys', 'activity', 'services', COMPONENT,
                                     timeout=self._remaining())
            E.require(result.returncode == 0 and isinstance(result.stdout, str) and
                      len(result.stdout.encode()) <= E.PRIVATE_BYTES, 'service_stop_unproven')
            return 'ServiceRecord{' not in result.stdout
        except E.Refused:
            raise
        except Exception:
            raise E.Refused('service_stop_unproven') from None

    def _producer_gone(self):
        E.require(self.producer is not None, 'service_stop_unproven')
        # This bounded host observation is additional to the final native
        # ESRCH proof. A metadata PID is never host signal authority.
        try:
            current = self._identity(self.producer['pid'])
        except Exception:
            raise E.Refused('service_stop_unproven') from None
        if current is None:
            return True
        E.require(E.same_process(self.producer, current), 'service_producer_changed')
        return False

    def drain(self):
        if self.drained or not self.launch_requested:
            return
        self.deadline = time.monotonic() + E.DRAIN_SECONDS
        # Recover only fixed metadata and exact kernel proof after partial start.
        if not self.root_proven:
            value = self._metadata()
            E.require(value['root'] and value['leader'], 'external_cleanup_unproven')
            self.root, self.leader = copy.deepcopy(value['root']), copy.deepcopy(value['leader'])
            self._prove_metadata_tree(value)
        for signal in ['TERM', 'KILL']:
            live = self._capture()
            for pid in sorted(live, key=lambda pid: pid == self.root['pid']):
                item = self.witnessed[pid]
                E.require(pid not in {self.export['app']['pid'], self.producer['pid']} and
                          self._uid(pid) == [self.export['uid']] * 4 and
                          E.same_process(item, self._identity(pid)), 'external_cleanup_identity_changed')
                self._signal(item, signal)
            time.sleep(min(.1, self._remaining()))
        E.require(not self._capture(), 'external_cleanup_not_drained')
        self.stop_requested = True
        self._intent(ACTION_STOP, ticket=True)
        while True:
            value = self._metadata()
            E.require(value['producer'] == self.producer and
                      value['root'] == E.wire_identity(self.root) and
                      value['leader'] == E.wire_identity(self.leader), 'service_tree_changed')
            E.require(not self._capture(), 'external_cleanup_not_drained')
            if value['state'] in {'stopped', 'failed'} and self._service_absent() and self._producer_gone():
                self.drained = True
                return
            time.sleep(min(.1, self._remaining()))
        # Exact producer disappearance does not qualify UID-wide quiescence.
        # Native proof still owns the full inventory and metadata cleanup.

    def failure_diagnostics(self):
        original_app_live = False
        try:
            self.deadline = time.monotonic() + 2
            current = self._identity(self.export['app']['pid']) if self.export else None
            original_app_live = bool(current and current['state'] not in {'Z', 'X'} and
                                     E.same_process(self.export['app'], current))
        except Exception:
            pass
        return {'originalAppIdentityStillLive': original_app_live, **self.producer_checks,
                **({'serviceError_' + self.metadata_error: True} if self.metadata_error is not None else {}),
                'rootIdentityCaptured': self.root is not None,
                'rootOwnershipProven': bool(self.root_proven),
                'updatePermitted': bool(self.permitted),
                'serviceLaunchRequested': bool(self.launch_requested),
                'serviceStopRequested': bool(self.stop_requested),
                'serviceWaiting': self.metadata_state == 'waiting',
                'serviceRunning': self.metadata_state == 'running',
                'serviceFailed': self.metadata_state == 'failed',
                'serviceStopped': self.metadata_state == 'stopped',
                'ownedTreeDrained': bool(self.drained)}
