// Saved profiles in memory and a connection that connects at once, for
// widget tests of the phone's own pages (no secure storage, no network).
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';

/// Saved profiles in memory: no secure storage in a widget test.
class MemoryProfileStore extends ProfileStore {
  MemoryProfileStore({required super.prefs, List<ServerProfile>? saved})
    : saved = saved ?? [];

  final List<ServerProfile> saved;
  String? selectedID;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(saved);

  @override
  String? get activeId => selectedID;

  @override
  Future<void> upsert(ServerProfile profile) async {
    final index = saved.indexWhere((item) => item.id == profile.id);
    if (index == -1) {
      saved.add(profile);
    } else {
      saved[index] = profile;
    }
  }

  @override
  Future<void> setActiveId(String? id) async => selectedID = id;
}

/// A connection that "connects" at once (or fails when [failConnect]).
class FakePhoneConnection extends ConnectionController {
  FakePhoneConnection(super.store);

  int connectCalls = 0;
  bool failConnect = false;
  ServerProfile? connectedTo;

  @override
  bool get hasConnectedServer => connectedTo != null;

  @override
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) async {
    connectCalls++;
    connectionAttemptRevision++;
    await store.setActiveId(profile.id);
    if (failConnect) {
      connectedTo = null;
      lastError = 'Could not connect to the phone server.';
    } else {
      connectedTo = profile;
    }
    notifyListeners();
  }

  @override
  Future<void> disconnect({
    bool keepActive = false,
    bool silent = false,
  }) async {
    connectedTo = null;
    if (!keepActive) await store.setActiveId(null);
    if (!silent) notifyListeners();
  }
}
