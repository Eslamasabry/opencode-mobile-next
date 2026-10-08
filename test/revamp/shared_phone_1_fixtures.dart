// Fakes shared by shared-phone-1's behaviour tests and goldens: a profile
// store and connection that never touch secure storage, OpenCode inside the
// app (BuiltinLinux), and the Termux AI team runtime.
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/termux/team_runtime.dart';
import 'package:opencode_mobile/state/queued_prompt_removal.dart';

final l10n = AppLocalizationsEn();

class PhoneStore extends ProfileStore {
  PhoneStore({required super.prefs, List<ServerProfile> seeded = const []})
    : saved = List.of(seeded);

  final List<ServerProfile> saved;
  String? selected;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(saved);

  @override
  String? get activeId => selected;

  @override
  Future<void> setActiveId(String? id) async => selected = id;

  @override
  Future<void> upsert(ServerProfile profile) async {
    saved
      ..removeWhere((item) => item.id == profile.id)
      ..add(profile);
  }
}

class PhoneConnection extends ConnectionController {
  PhoneConnection(PhoneStore super.store);

  final deleted = <String>[];

  @override
  Future<void> disconnect({
    bool keepActive = false,
    bool silent = false,
  }) async {
    api = null;
    notifyListeners();
  }

  @override
  Future<DeleteProfileResult> deleteProfileAndLocalData(
    String profileId, {
    QueuedPromptRemovalPlan? queuedPrompts,
    bool keepQueuedPrompts = false,
  }) async {
    deleted.add(profileId);
    (store as PhoneStore).saved.removeWhere((p) => p.id == profileId);
    return const DeleteProfileResult();
  }
}

/// OpenCode inside the app: installed, running unless told otherwise.
class PhoneLinux extends BuiltinLinux {
  PhoneLinux({this.running = true, this.bytesUsed = 734003200});

  bool installed = true;
  bool running;
  int? bytesUsed;
  String log = 'opencode server listening\nwarning: slow disk\n';
  final calls = <String>[];

  @override
  Future<BuiltinLinuxStatus> status() async => BuiltinLinuxStatus(
    installed: installed,
    phase: installed ? BuiltinLinuxPhase.ready : BuiltinLinuxPhase.idle,
    serverRunning: running,
    bytesUsed: bytesUsed,
  );

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async => const BuiltinLinuxRunResult(exitCode: 0, output: '');

  @override
  Future<void> startServer(
    String script, {
    int port = 4097,
    BuiltinServerRestoreRecipe? restoreRecipe,
  }) async {
    calls.add('start');
    running = true;
  }

  @override
  Future<void> stopServer() async {
    calls.add('stop');
    running = false;
  }

  @override
  Future<String> serverLog({int tailBytes = 32768}) async => log;

  /// The remove sheet's reading: the runtime and the projects kept apart.
  @override
  Future<BuiltinProjectStorage> projectStorage() async =>
      const BuiltinProjectStorage(
        runtimeBytes: 734003200,
        projectsBytes: 52428800,
        measuredAtMilliseconds: 0,
      );

  @override
  Future<void> uninstall() async {
    calls.add('uninstall');
    installed = false;
    running = false;
  }
}

ServerProfile phoneProfile() => ServerProfile(
  id: 'phone',
  name: 'This phone, built-in (OpenCode 1)',
  baseUrl: BuiltinLinux.serverUrl,
  username: BuiltinLinux.serverUsername,
  password: 'secret',
  serverVersion: '1.18.29',
);

TeamRuntimeStatus teamStatus(
  TeamRuntimePhase phase, {
  bool installed = false,
  bool busy = false,
  String city = '',
  String project = '',
  int? agents,
  String? health,
  bool killed = false,
  String? lastError,
  Map<String, String> versions = const {},
}) => TeamRuntimeStatus(
  phase: phase,
  rawPhase: phase.name,
  verb: '',
  installed: installed,
  busy: busy,
  city: city,
  project: project,
  agents: agents,
  health: health,
  killedByAndroid: killed,
  lastError: lastError,
  versions: versions,
);

TeamRuntimeStatus teamReady({int agents = 2, bool killed = false}) =>
    teamStatus(
      TeamRuntimePhase.ready,
      installed: true,
      city: 'phone',
      project: '/root/projects/calc',
      agents: killed ? null : agents,
      health: killed ? null : 'ok',
      killed: killed,
      versions: const {'gc': '1.4.1', 'bd': '1.2.2', 'dolt': '2.3.3'},
    );

class TeamRuntime extends TermuxTeamRuntime {
  TeamRuntime({this.supported = true, this.totalBytes = 0})
    : super(
        runner: (_, {timeout = Duration.zero}) async => '',
        manifestLoader: () async => null,
        archProbe: () async => 'aarch64',
      );

  final bool supported;
  final int totalBytes;
  TeamRuntimeStatus current = teamStatus(TeamRuntimePhase.idle);
  final calls = <String>[];
  final results = <String, TeamRuntimeStatus>{};

  @override
  Future<bool> get supportsAiTeam async => supported;

  @override
  Future<TeamRuntimeManifest?> manifest() async => TeamRuntimeManifest(
    json: '{}',
    arch: 'arm64',
    gascity: '',
    beads: '',
    dolt: '',
    baseUrl: '',
    totalBytes: totalBytes,
  );

  @override
  Future<TeamRuntimeStatus> status() async => current;

  Future<TeamRuntimeStatus> _verb(String verb) async {
    calls.add(verb);
    return current = results[verb] ?? current;
  }

  @override
  Future<TeamRuntimeStatus> start() => _verb('start');

  @override
  Future<TeamRuntimeStatus> stop() => _verb('stop');

  @override
  Future<TeamRuntimeStatus> remove() => _verb('remove');
}

ServerProfile teamProfile({bool on = true}) => ServerProfile(
  id: 'termux',
  name: 'My phone',
  baseUrl: TermuxBridge.managedServerUrl,
  password: 'phone-secret',
  flavor: ServerFlavor.v2,
  orchestration: on
      ? OrchestrationConfig(
          provider: OrchestrationProvider.gascity,
          url: TermuxBridge.aiteamSupervisorUrl,
          city: 'phone',
          hostMode: OrchestrationHostMode.phone,
          enabledAt: DateTime.utc(2026, 9, 11),
        )
      : null,
);
