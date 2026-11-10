/// The fixed words and ranges of the health and diagnostics telemetry
/// (docs/telemetry/health-and-diagnostics.md, the contract shared with the
/// native app). Nothing here is free text: every value is one of a listed
/// word, a listed range, or a number matched by a listed pattern.
///
/// [analytics_events.dart] puts these into [analyticsSchema]; the sanitizer
/// in [TelemetryService] drops anything that is not listed.
library;

/// Range buckets. Each `*Of` function returns one of its list's words, so a
/// call site cannot invent a value.
abstract final class HealthRanges {
  static String _pick(num value, List<num> cuts, List<String> words) {
    for (var i = 0; i < cuts.length; i++) {
      if (value < cuts[i]) return words[i];
    }
    return words.last;
  }

  /// `conn_attempt.ms_range`.
  static const connMs = ['<500', '500-2000', '2000-10000', '>10000'];
  static String connMsOf(Duration elapsed) =>
      _pick(elapsed.inMilliseconds, const [500, 2000, 10000], connMs);

  /// `send_result.first_reply_range`.
  static const firstReply = ['<2s', '2-10s', '10-30s', '>30s', 'none'];
  static String firstReplyOf(Duration? elapsed) => elapsed == null
      ? 'none'
      : _pick(elapsed.inMilliseconds, const [2000, 10000, 30000], firstReply);

  /// `turn_stall.minutes_range`.
  static const stallMinutes = ['<1', '1-5', '5-15', '15-60', '>60'];
  static String stallMinutesOf(Duration elapsed) =>
      _pick(elapsed.inSeconds / 60, const [1, 5, 15, 60], stallMinutes);

  /// `queue_flush.sent` and `.failed`: a count range.
  static const counts = ['0', '1', '2-5', '6-20', '>20'];
  static String countOf(int count) {
    if (count <= 0) return '0';
    if (count == 1) return '1';
    if (count <= 5) return '2-5';
    if (count <= 20) return '6-20';
    return '>20';
  }

  /// `linux_setup_step.ms_range`.
  static const setupMs = ['<10000', '10000-60000', '60000-300000', '>300000'];
  static String setupMsOf(Duration elapsed) =>
      _pick(elapsed.inMilliseconds, const [10000, 60000, 300000], setupMs);

  /// `app_start.ms_range`.
  static const appStartMs = [
    '<500',
    '500-1000',
    '1000-2000',
    '2000-5000',
    '>5000',
  ];
  static String appStartMsOf(Duration elapsed) =>
      _pick(elapsed.inMilliseconds, const [500, 1000, 2000, 5000], appStartMs);

  /// `jank.janky_pct_range`.
  static const jankPct = ['<1', '1-5', '5-15', '>15'];
  static String jankPctOf(double percent) =>
      _pick(percent, const [1, 5, 15], jankPct);

  /// `jank.refresh_hz`.
  static const refreshHz = ['60', '90', '120', 'other'];
  static String refreshHzOf(double hz) {
    final rounded = hz.round();
    return switch (rounded) {
      >= 58 && <= 62 => '60',
      >= 88 && <= 92 => '90',
      >= 118 && <= 122 => '120',
      _ => 'other',
    };
  }

  /// `ram_range` (GB).
  static const ram = ['<3', '3-4', '4-6', '6-8', '8-12', '>12'];
  static String ramOf(double gigabytes) =>
      _pick(gigabytes, const [3, 4, 6, 8, 12], ram);

  /// `free_storage_range` and `linux_setup_step.free_space_range` (GB).
  static const storage = ['<2', '2-8', '8-32', '>32'];
  static String storageOf(double gigabytes) =>
      _pick(gigabytes, const [2, 8, 32], storage);

  /// The custom key `connections`.
  static const connections = ['0', '1', '2-3', '4+'];
  static String connectionsOf(int count) {
    if (count <= 0) return '0';
    if (count == 1) return '1';
    if (count <= 3) return '2-3';
    return '4+';
  }
}

/// The words several events share.
abstract final class HealthWords {
  /// `conn_attempt.fail_kind`; `send_result` adds [sendOnlyFailKinds].
  static const failKinds = {
    'dns',
    'tls',
    'auth',
    'timeout',
    'refused',
    'server_5xx',
    'protocol',
    'offline',
    'other',
  };
  static const sendOnlyFailKinds = {'rejected', 'too_large'};

  static const serverKinds = {'phone', 'computer'};
  static const okFail = {'ok', 'fail'};

  static const dropReasons = {
    'network_change',
    'server_closed',
    'idle_timeout',
    'background',
    'other',
  };
  static const yesNo = {'yes', 'no'};
  static const stallStages = {
    'waiting_tool',
    'waiting_approval',
    'streaming',
    'other',
  };

  /// `linux_setup_step.step`.
  static const linuxSteps = {
    'download',
    'unpack',
    'essentials',
    'node',
    'opencode',
    'paseo',
    'agents',
    'done',
  };
  static const exitKinds = {'0', '1', '126', '127', 'signal', 'other'};
  static const signals = {
    'SIGILL',
    'SIGSEGV',
    'SIGBUS',
    'SIGKILL',
    'other',
    'none',
  };
  static const compatModes = {'none', 'armcap', 'threads', 'both'};

  /// `agent_start.agent`.
  static const agents = {
    'claude_code',
    'codex',
    'gemini',
    'antigravity',
    'opencode',
    'other',
  };
  static const agentFailKinds = {
    'not_installed',
    'needs_sign_in',
    'crashed',
    'timeout',
    'other',
  };

  static const bgReasons = {
    'os_limit',
    'user_stopped',
    'idle',
    'crashed',
    'other',
  };

  static const updateKinds = {'in_app', 'patch'};
  static const updateResults = {'installed', 'failed', 'declined'};
  static const updateFailKinds = {
    'download',
    'checksum',
    'signer',
    'version',
    'installer',
    'other',
  };

  static const startKinds = {'cold', 'warm'};
  static const memoryLevels = {'moderate', 'low', 'critical'};

  /// Android's ApplicationExitInfo reasons, as the spec names them.
  static const exitReasons = {
    'crash',
    'crash_native',
    'anr',
    'low_memory',
    'excessive_resource',
    'signaled',
    'user_requested',
    'dependency_died',
    'other',
  };

  static const buildKinds = {'store', 'sideload', 'ci', 'local'};
  static const keyServerKinds = {'phone', 'computer', 'none'};
  static const engineStates = {'stopped', 'starting', 'ready', 'failing'};
  static const abis = {'arm64', 'arm32', 'x86_64'};
  static const userServerKinds = {'phone', 'computer', 'both'};
}

/// Numbers and versions that cannot be listed word by word. Each pattern
/// matches digits and dots only, so none can carry text.
abstract final class HealthPatterns {
  static final appVersion = RegExp(r'^\d{1,2}\.\d{1,2}\.\d{1,3}$');
  static final majorMinor = RegExp(r'^\d{1,2}\.\d{1,2}$');
  static final number = RegExp(r'^\d{1,3}$');
  static final reportCode = RegExp(r'^[0-9a-f]{8}-\d{1,5}$');
}

/// The Crashlytics custom keys (section A) and what each may hold: a set of
/// words, or (in [healthKeyPatterns]) a numeric pattern. Any other key or
/// value is dropped.
final Map<String, Set<String>> healthKeyWords = {
  'build_kind': HealthWords.buildKinds,
  'server_kind': HealthWords.keyServerKinds,
  'engine_state': HealthWords.engineStates,
  'connections': {...HealthRanges.connections},
  'exit_reason': HealthWords.exitReasons,
};

final Map<String, RegExp> healthKeyPatterns = {
  'app_version': HealthPatterns.appVersion,
  'oc_server_version': HealthPatterns.majorMinor,
  'report_code': HealthPatterns.reportCode,
};

/// The user properties (section D).
final Map<String, Set<String>> healthPropertyWords = {
  'ram_range': {...HealthRanges.ram},
  'free_storage_range': {...HealthRanges.storage},
  'abi': HealthWords.abis,
  'notif_allowed': HealthWords.yesNo,
  'server_kind': HealthWords.userServerKinds,
};

final Map<String, RegExp> healthPropertyPatterns = {
  'webview_major': HealthPatterns.number,
  'oc_server_version': HealthPatterns.majorMinor,
};

/// The breadcrumb words (section F). A crumb is `area.action`, or
/// `area.action:word` for the four areas that name something.
abstract final class BreadcrumbWords {
  static const plain = {
    'app.start',
    'app.foreground',
    'app.background',
    'chat.send',
    'chat.stop',
    'chat.approve',
    'chat.deny',
    'chat.tool_wait',
    'conn.connect',
    'conn.drop',
    'conn.reconnect',
    'linux.fail',
    'agent.fail',
    'update.offer',
    'update.install',
    'update.fail',
  };

  /// `settings.change:<setting group word>`: the Settings hub's groups.
  static const settingGroups = {
    'connections',
    'ai',
    'chat',
    'this_app',
    'help',
    'privacy',
    'appearance',
    'notifications',
    'other',
  };

  /// `area.action:` prefixes and the words allowed after each.
  static final Map<String, Set<String>> named = {
    'nav.open': {
      'conversations',
      'chat',
      'new_chat',
      'servers',
      'this_phone',
      'setup',
      'privacy',
      'appearance',
      'integrations',
      'settings',
      'files',
      'git',
      'review',
      'team',
      'usage',
      'agents',
      'about',
      'other',
    },
    'linux.step': HealthWords.linuxSteps,
    'agent.start': HealthWords.agents,
    'settings.change': settingGroups,
  };

  /// Whether [crumb] is one of the listed breadcrumbs.
  static bool isListed(String crumb) {
    if (plain.contains(crumb)) return true;
    final colon = crumb.indexOf(':');
    if (colon <= 0) return false;
    return named[crumb.substring(0, colon)]?.contains(
          crumb.substring(colon + 1),
        ) ??
        false;
  }
}
