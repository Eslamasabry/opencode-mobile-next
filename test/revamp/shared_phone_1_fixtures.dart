// Fakes shared by shared-phone-1's behaviour tests and goldens: a profile
// store and connection that never touch secure storage, OpenCode inside the
// app (BuiltinLinux).
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
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
    bool linuxGone = false,
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
    String? input,
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
