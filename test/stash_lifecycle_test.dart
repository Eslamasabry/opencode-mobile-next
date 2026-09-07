import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/draft_attachments.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/prompt_shelf.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';
import 'package:shared_preferences_platform_interface/types.dart';

// Local controller integration only: synthetic preferences, isolated private
// roots, mocked secure storage and no connected API/repository or live server.
class _Storage extends InMemorySharedPreferencesStore {
  _Storage()
    : super.withData({
        'flutter.oc.profiles': jsonEncode([
          for (final id in ['a', 'b'])
            {
              'id': id,
              'name': 'Synthetic $id',
              'baseUrl': 'https://$id.example',
            },
        ]),
        'flutter.oc.activeProfile': 'a',
      });

  final refusedWrites = <String>{};
  final refusedRemovals = <String>{};
  final writes = <String>[];
  final removals = <String>[];
  bool failReload = false;
  String? gatedWrite;
  Completer<void>? writeGate;
  Completer<void>? writeEntered;

  @override
  Future<bool> setValue(String valueType, String key, Object value) async {
    final name = key.replaceFirst('flutter.', '');
    writes.add(name);
    if (name == gatedWrite) {
      if (!(writeEntered?.isCompleted ?? true)) writeEntered!.complete();
      await writeGate?.future;
    }
    if (refusedWrites.contains(name)) return false;
    return super.setValue(valueType, key, value);
  }

  @override
  Future<bool> remove(String key) async {
    final name = key.replaceFirst('flutter.', '');
    removals.add(name);
    if (refusedRemovals.contains(name)) return false;
    return super.remove(key);
  }

  @override
  Future<Map<String, Object>> getAllWithParameters(
    GetAllParameters parameters,
  ) {
    if (failReload) throw StateError('Synthetic reload failure');
    return super.getAllWithParameters(parameters);
  }

  Future<Object?> onDisk(String key) async => (await super.getAllWithParameters(
    GetAllParameters(filter: PreferencesFilter(prefix: 'flutter.')),
  ))['flutter.$key'];
}

class _Root {
  _Root(this.directory);
  final Directory directory;
  int opens = 0;
  Future<Directory> open() async {
    opens++;
    return directory;
  }

  Future<List<File>> files() async {
    if (!await directory.exists()) return [];
    return directory
        .list(recursive: true, followLinks: false)
        .where((entry) => entry is File)
        .cast<File>()
        .toList();
  }
}

class _Vault extends DraftAttachmentVault {
  _Vault(_Root root) : super(directory: root.open);
  int stores = 0;
  int restores = 0;
  int collections = 0;
  bool failCollect = false;
  Completer<void>? storeEntered;
  Completer<void>? storeGate;
  Completer<void>? restoreEntered;
  Completer<void>? restoreGate;
  Completer<void>? collectEntered;
  Completer<void>? collectGate;

  @override
  Future<List<DraftAttachmentRef>> store(
    String owner,
    List<PromptAttachment> attachments,
  ) async {
    stores++;
    if (!(storeEntered?.isCompleted ?? true)) storeEntered!.complete();
    await storeGate?.future;
    return super.store(owner, attachments);
  }

  @override
  Future<DraftAttachmentRecovery> restore(
    String owner,
    List<DraftAttachmentRef> refs, {
    required bool sameLocation,
  }) async {
    restores++;
    if (!(restoreEntered?.isCompleted ?? true)) restoreEntered!.complete();
    await restoreGate?.future;
    return super.restore(owner, refs, sameLocation: sameLocation);
  }

  @override
  Future<bool> collect(
    Map<String, Iterable<DraftAttachmentRef>> retained, {
    String? owner,
  }) async {
    collections++;
    if (!(collectEntered?.isCompleted ?? true)) collectEntered!.complete();
    await collectGate?.future;
    if (failCollect) return false;
    return super.collect(retained, owner: owner);
  }
}

const _data = PromptAttachment(
  filename: 'synthetic.txt',
  mime: 'text/plain',
  url: 'data:text/plain;base64,aGVsbG8=',
);
const _serverFile = PromptAttachment(
  filename: 'workspace.txt',
  mime: 'text/plain',
  url: 'file:///workspace/workspace.txt',
);
StashedPrompt _prompt(
  String id, {
  List<PromptAttachment> attachments = const [_data],
  String? directory = '/workspace',
  String? workspace = 'work-a',
}) => StashedPrompt(
  id: id,
  text: 'Synthetic unsent work',
  createdAt: 123,
  attachments: attachments,
  directory: directory,
  workspace: workspace,
);
String _key(String owner, String id) => 'oc.promptStash.$owner.$id';
String _marker(String owner) => PromptShelfStore.attachmentOwnerKey(owner);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const secureChannel = MethodChannel(
    'plugins.it_nomads.com/flutter_secure_storage',
  );
  late _Storage disk;
  late SharedPreferences prefs;
  late _Root stashRoot;
  late _Root draftRoot;
  late _Vault vault;
  late List<ConnectionController> controllers;
  late List<String> secureDeletes;

  setUp(() async {
    final previous = SharedPreferencesStorePlatform.instance;
    disk = _Storage();
    SharedPreferencesStorePlatform.instance = disk;
    SharedPreferences.resetStatic();
    controllers = [];
    secureDeletes = [];
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(secureChannel, (call) async {
      if (call.method == 'delete') {
        secureDeletes.add((call.arguments as Map)['key'] as String);
      }
      return null;
    });
    messenger.setMockMethodCallHandler(
      const MethodChannel('oc/background'),
      (_) async => null,
    );
    final temporary = await Directory.systemTemp.createTemp(
      'ocmn-stash-lifecycle-',
    );
    stashRoot = _Root(Directory('${temporary.path}/stash'));
    draftRoot = _Root(Directory('${temporary.path}/draft'));
    vault = _Vault(stashRoot);
    prefs = await SharedPreferences.getInstance();
    addTearDown(() async {
      for (final controller in controllers) {
        controller.dispose();
      }
      await temporary.delete(recursive: true);
      messenger.setMockMethodCallHandler(secureChannel, null);
      messenger.setMockMethodCallHandler(
        const MethodChannel('oc/background'),
        null,
      );
      SharedPreferencesStorePlatform.instance = previous;
      SharedPreferences.resetStatic();
    });
  });

  Future<ConnectionController> boot({DraftAttachmentVault? stashVault}) async {
    prefs = await SharedPreferences.getInstance();
    final profiles = ProfileStore(prefs: prefs);
    await profiles.load();
    final controller =
        ConnectionController(
            profiles,
            apiFactory: (_) => throw StateError('Unexpected server transport'),
            v2GatewayFactory: (_) =>
                throw StateError('Unexpected server transport'),
            draftAttachmentVault: DraftAttachmentVault(
              directory: draftRoot.open,
            ),
            stashAttachmentVault: stashVault ?? vault,
          )
          ..directory = '/workspace'
          ..workspace = 'work-a';
    controllers.add(controller);
    return controller;
  }

  Completer<void> gate() {
    final value = Completer<void>();
    addTearDown(() {
      if (!value.isCompleted) value.complete();
    });
    return value;
  }

  Future<void> changeScope(
    ConnectionController controller,
    String change,
  ) async {
    switch (change) {
      case 'location':
        controller.locationRevision++;
      case 'directory':
        controller.directory = '/elsewhere';
      case 'workspace':
        controller.workspace = 'work-b';
      case 'profile':
        await controller.store.setActiveId('b');
      case 'disposal':
        controller.dispose();
    }
  }

  test(
    'live controller enables file-backed save, restart, recovery and owner deletion',
    () async {
      final controller = await boot();
      await controller.savePromptStash(_prompt('saved'), locationRevision: 0);
      await controller.rememberSentPrompt('a', 'Synthetic sent history');
      expect(await disk.onDisk(_key('a', 'saved')), isNot(contains(_data.url)));
      expect(await disk.onDisk(_marker('a')), isTrue);
      expect(controller.promptStash.single.attachmentCount, 1);
      expect(await stashRoot.files(), hasLength(1));
      expect(draftRoot.opens, 0);
      controller.dispose();
      SharedPreferences.resetStatic();
      final restarted = await boot(stashVault: _Vault(stashRoot));
      expect(restarted.sentPromptHistory, ['Synthetic sent history']);
      expect(await restarted.preparePromptStash(locationRevision: 0), isEmpty);
      final recovered = await restarted.restorePromptStashAttachments(
        'saved',
        locationRevision: 0,
      );
      expect(recovered.attachments.single.url, _data.url);
      expect(recovered.unavailable, isEmpty);
      expect(restarted.promptStash.single.text, 'Synthetic unsent work');
      final result = await restarted.deleteProfileAndLocalData('a');
      expect(result.complete, isTrue);
      expect(secureDeletes, ['pw.a']);
      expect(await stashRoot.files(), isEmpty);
      expect(await disk.onDisk(_marker('a')), isNull);
      expect(await disk.onDisk(_key('a', 'saved')), isNull);
      expect(await disk.onDisk('oc.promptHistory.a'), isNull);
      expect(restarted.promptStash, isEmpty);
      SharedPreferences.resetStatic();
      final afterDeletion = await boot();
      expect(afterDeletion.store.profiles.map((p) => p.id), ['b']);
      expect(afterDeletion.store.profileScopedPreferenceKeys('a'), isEmpty);
    },
  );

  test(
    'empty and text-only shelves do no file I/O, including profile deletion',
    () async {
      final controller = await boot();
      final writes = disk.writes.length;
      expect(controller.promptStash, isEmpty);
      expect(await controller.preparePromptStash(locationRevision: 0), isEmpty);
      expect(disk.writes.length, writes);
      await controller.savePromptStash(
        _prompt('text', attachments: []),
        locationRevision: 0,
      );
      await controller.removePromptStash('text', locationRevision: 0);
      expect(
        (await controller.deleteProfileAndLocalData('a')).complete,
        isTrue,
      );
      expect(vault.stores, 0);
      expect(vault.collections, 0);
      expect(stashRoot.opens, 0);
      expect(draftRoot.opens, 0);
      expect(await disk.onDisk(_marker('a')), isNull);
    },
  );

  test(
    'preparation migrates legacy data once and returns deferred IDs without truncation',
    () async {
      final legacy = '${jsonEncode(_prompt('legacy').toJson())}\n';
      final oversized =
          '${jsonEncode(_prompt('oversized', attachments: List.filled(6, _data)).toJson())}\n';
      await prefs.setString(_key('a', 'legacy'), legacy);
      await prefs.setString(_key('a', 'oversized'), oversized);
      final controller = await boot();
      var notifications = 0;
      controller.addListener(() => notifications++);
      expect(await controller.preparePromptStash(locationRevision: 0), [
        'oversized',
      ]);
      expect(notifications, 1);
      expect(
        await disk.onDisk(_key('a', 'legacy')),
        isNot(contains(_data.url)),
      );
      expect(await disk.onDisk(_key('a', 'oversized')), oversized);
      expect(vault.stores, 1);
      expect(await controller.preparePromptStash(locationRevision: 0), [
        'oversized',
      ]);
      expect(vault.stores, 1);
      controller.dispose();
      SharedPreferences.resetStatic();
      final restarted = await boot(stashVault: _Vault(stashRoot));
      expect(
        (await restarted.restorePromptStashAttachments(
          'legacy',
          locationRevision: 0,
        )).attachments.single.url,
        _data.url,
      );
      expect(
        restarted.promptStash
            .firstWhere((p) => p.id == 'oversized')
            .attachments,
        hasLength(6),
      );
    },
  );

  test(
    'refused stash metadata has no phantom save and failed reload can recover',
    () async {
      final controller = await boot();
      await prefs.setBool(_marker('a'), true);
      disk.refusedWrites.add(_key('a', 'new'));
      disk.failReload = true;
      await expectLater(
        controller.savePromptStash(_prompt('new'), locationRevision: 0),
        throwsStateError,
      );
      expect(await disk.onDisk(_key('a', 'new')), isNull);
      expect(vault.stores, 1);
      expect(await stashRoot.files(), hasLength(1));
      expect(() => controller.promptStash, throwsStateError);
      disk.refusedWrites.clear();
      disk.failReload = false;
      expect(await controller.preparePromptStash(locationRevision: 0), isEmpty);
      expect(controller.promptStash, isEmpty);
      await controller.savePromptStash(_prompt('new'), locationRevision: 0);
      expect(controller.promptStash.single.id, 'new');
      expect(
        (await controller.restorePromptStashAttachments(
          'new',
          locationRevision: 0,
        )).attachments.single.url,
        _data.url,
      );
      expect(secureDeletes, isEmpty);
    },
  );

  test(
    'recovery checks actual directory and workspace and never consumes its source',
    () async {
      final controller = await boot();
      await controller.savePromptStash(
        _prompt('bound', attachments: [_serverFile, _data]),
        locationRevision: 0,
      );
      controller.directory = '/workspace/';
      expect(
        (await controller.restorePromptStashAttachments(
          'bound',
          locationRevision: 0,
        )).attachments,
        hasLength(2),
      );
      final restores = vault.restores;
      controller.workspace = 'work-b';
      await expectLater(
        controller.restorePromptStashAttachments('bound', locationRevision: 0),
        throwsStateError,
      );
      controller.workspace = 'work-a';
      controller.directory = '/elsewhere';
      await expectLater(
        controller.restorePromptStashAttachments('bound', locationRevision: 0),
        throwsStateError,
      );
      expect(
        vault.restores,
        restores,
        reason: 'wrong-location reads must not touch the vault',
      );
      expect(controller.promptStash.single.attachmentCount, 2);
      expect(await disk.onDisk(_key('a', 'bound')), isNotNull);
      await controller.savePromptStash(
        _prompt('portable'),
        locationRevision: 0,
      );
      expect(
        (await controller.restorePromptStashAttachments(
          'portable',
          locationRevision: 0,
        )).attachments.single.url,
        _data.url,
      );
      await expectLater(
        controller.restorePromptStashAttachments(
          'missing',
          locationRevision: 0,
        ),
        throwsStateError,
      );
    },
  );

  for (final damage in ['missing', 'corrupt']) {
    test(
      '$damage stash bytes are reported without consuming saved work',
      () async {
        final controller = await boot();
        await controller.savePromptStash(_prompt('saved'), locationRevision: 0);
        final original = await disk.onDisk(_key('a', 'saved'));
        final file = (await stashRoot.files()).single;
        if (damage == 'missing') {
          await file.delete();
        } else {
          await file.writeAsString('Synthetic corrupt bytes');
        }
        final recovered = await controller.restorePromptStashAttachments(
          'saved',
          locationRevision: 0,
        );
        expect(recovered.attachments, isEmpty);
        expect(recovered.unavailable, ['synthetic.txt']);
        expect(controller.promptStash.single.text, 'Synthetic unsent work');
        expect(controller.promptStash.single.attachmentCount, 1);
        expect(await disk.onDisk(_key('a', 'saved')), original);
        expect(secureDeletes, isEmpty);
      },
    );
  }

  test(
    'invalid initial scope rejects every shelf action without file I/O',
    () async {
      final controller = await boot();
      for (final operation in [
        () => controller.preparePromptStash(locationRevision: 1),
        () => controller.restorePromptStashAttachments(
          'saved',
          locationRevision: 1,
        ),
        () => controller.savePromptStash(_prompt('saved'), locationRevision: 1),
        () => controller.removePromptStash('saved', locationRevision: 1),
      ]) {
        await expectLater(operation(), throwsStateError);
      }
      controller.dispose();
      expect(controller.canUsePromptShelf, isFalse);
      expect(controller.promptStash, isEmpty);
      expect(controller.sentPromptHistory, isEmpty);
      await expectLater(
        controller.preparePromptStash(locationRevision: 0),
        throwsStateError,
      );
      await expectLater(
        controller.restorePromptStashAttachments('saved', locationRevision: 0),
        throwsStateError,
      );
      await controller.rememberSentPrompt('a', 'After disposal');
      expect(disk.writes, isEmpty);
      expect(disk.removals, isEmpty);
      expect(stashRoot.opens, 0);
      expect(secureDeletes, isEmpty);
    },
  );

  for (final change in [
    'location',
    'directory',
    'workspace',
    'profile',
    'disposal',
  ]) {
    test(
      'a pending save rejects stale $change before metadata publication',
      () async {
        final controller = await boot();
        vault.storeGate = gate();
        vault.storeEntered = Completer<void>();
        var notifications = 0;
        controller.addListener(() => notifications++);
        final rejected = expectLater(
          controller.savePromptStash(_prompt('pending'), locationRevision: 0),
          throwsStateError,
        );
        await vault.storeEntered!.future;
        await changeScope(controller, change);
        vault.storeGate!.complete();
        await rejected;
        expect(notifications, 0);
        expect(await disk.onDisk(_key('a', 'pending')), isNull);
        expect(await disk.onDisk(_key('b', 'pending')), isNull);
        expect(await stashRoot.files(), isEmpty);
        expect(secureDeletes, isEmpty);
      },
    );

    test(
      'a pending recovery rejects stale $change without consuming the stash',
      () async {
        final controller = await boot();
        await controller.savePromptStash(_prompt('saved'), locationRevision: 0);
        vault.restoreGate = gate();
        vault.restoreEntered = Completer<void>();
        final rejected = expectLater(
          controller.restorePromptStashAttachments(
            'saved',
            locationRevision: 0,
          ),
          throwsStateError,
        );
        await vault.restoreEntered!.future;
        await changeScope(controller, change);
        vault.restoreGate!.complete();
        await rejected;
        expect(await disk.onDisk(_key('a', 'saved')), isNotNull);
        expect(await disk.onDisk(_key('b', 'saved')), isNull);
        expect(await stashRoot.files(), hasLength(1));
      },
    );
  }

  test(
    'a save already committing on a stale location stays recoverable only by its owner',
    () async {
      final controller = await boot();
      disk.gatedWrite = _key('a', 'committing');
      disk.writeEntered = Completer<void>();
      disk.writeGate = gate();
      final rejected = expectLater(
        controller.savePromptStash(_prompt('committing'), locationRevision: 0),
        throwsStateError,
      );
      await disk.writeEntered!.future;
      await controller.store.setActiveId('b');
      disk.writeGate!.complete();
      await rejected;
      expect(controller.promptStash, isEmpty);
      expect(await disk.onDisk(_key('b', 'committing')), isNull);
      controller.dispose();
      SharedPreferences.resetStatic();
      final restarted = await boot(stashVault: _Vault(stashRoot));
      await restarted.store.setActiveId('a');
      expect(
        (await restarted.restorePromptStashAttachments(
          'committing',
          locationRevision: 0,
        )).attachments.single.url,
        _data.url,
      );
    },
  );

  test(
    'stale queued removal cannot delete a stash when an older recovery releases',
    () async {
      final controller = await boot();
      await controller.savePromptStash(_prompt('saved'), locationRevision: 0);
      vault.restoreGate = gate();
      vault.restoreEntered = Completer<void>();
      final recovery = expectLater(
        controller.restorePromptStashAttachments('saved', locationRevision: 0),
        throwsStateError,
      );
      await vault.restoreEntered!.future;
      final removal = expectLater(
        controller.removePromptStash('saved', locationRevision: 0),
        throwsStateError,
      );
      controller.locationRevision++;
      vault.restoreGate!.complete();
      await recovery;
      await removal;
      expect(await disk.onDisk(_key('a', 'saved')), isNotNull);
      expect(disk.removals, isNot(contains(_key('a', 'saved'))));
    },
  );

  test(
    'a queued recovery cannot substitute a new stash that reused the source ID',
    () async {
      final controller = await boot();
      await controller.savePromptStash(_prompt('saved'), locationRevision: 0);
      vault.storeGate = gate();
      vault.storeEntered = Completer<void>();
      final blocker = controller.savePromptStash(
        _prompt('blocker'),
        locationRevision: 0,
      );
      await vault.storeEntered!.future;
      final removal = controller.removePromptStash(
        'saved',
        locationRevision: 0,
      );
      final replacement = controller.savePromptStash(
        _prompt('saved'),
        locationRevision: 0,
      );
      final rejected = expectLater(
        controller.restorePromptStashAttachments('saved', locationRevision: 0),
        throwsStateError,
      );
      vault.storeGate!.complete();
      await blocker;
      await removal;
      await replacement;
      await rejected;
      expect(vault.restores, 0);
      expect(
        controller.promptStash.map((p) => p.id),
        containsAll(['blocker', 'saved']),
      );
    },
  );

  for (final action in ['save', 'migration', 'recovery']) {
    test(
      'profile deletion closes admission and drains pending $action before secret deletion',
      () async {
        final controller = await boot();
        Future<void> rejected;
        Completer<void> entered;
        Completer<void> release;
        if (action == 'recovery') {
          await controller.savePromptStash(
            _prompt('pending'),
            locationRevision: 0,
          );
          vault.restoreEntered = entered = Completer<void>();
          vault.restoreGate = release = gate();
          rejected = expectLater(
            controller.restorePromptStashAttachments(
              'pending',
              locationRevision: 0,
            ),
            throwsStateError,
          );
        } else {
          if (action == 'migration') {
            await prefs.setString(
              _key('a', 'pending'),
              jsonEncode(_prompt('pending').toJson()),
            );
            await prefs.setString(
              _key('a', 'second'),
              jsonEncode(_prompt('second').toJson()),
            );
          }
          vault.storeEntered = entered = Completer<void>();
          vault.storeGate = release = gate();
          rejected = expectLater(
            action == 'migration'
                ? controller.preparePromptStash(locationRevision: 0)
                : controller.savePromptStash(
                    _prompt('pending'),
                    locationRevision: 0,
                  ),
            throwsStateError,
          );
        }
        await entered.future;
        final deletion = controller.deleteProfileAndLocalData('a');
        expect(
          identical(controller.deleteProfileAndLocalData('a'), deletion),
          isTrue,
        );
        expect(controller.canUsePromptShelf, isFalse);
        expect(controller.promptStash, isEmpty);
        expect(secureDeletes, isEmpty);
        await expectLater(
          controller.savePromptStash(_prompt('late'), locationRevision: 0),
          throwsStateError,
        );
        await expectLater(
          controller.preparePromptStash(locationRevision: 0),
          throwsStateError,
        );
        await expectLater(
          controller.restorePromptStashAttachments(
            'pending',
            locationRevision: 0,
          ),
          throwsStateError,
        );
        await controller.rememberSentPrompt('a', 'Late callback');
        release.complete();
        await rejected;
        expect((await deletion).complete, isTrue);
        expect(secureDeletes, ['pw.a']);
        expect(await stashRoot.files(), isEmpty);
        expect(await disk.onDisk(_marker('a')), isNull);
        expect(await disk.onDisk(_key('a', 'pending')), isNull);
        expect(await disk.onDisk(_key('a', 'late')), isNull);
        expect(await disk.onDisk('oc.promptHistory.a'), isNull);
        if (action == 'migration') expect(vault.stores, 1);
        await controller.rememberSentPrompt('a', 'After deletion');
        expect(await disk.onDisk('oc.promptHistory.a'), isNull);
      },
    );
  }

  test(
    'failed stash cleanup retains its marker and profile, then restart retries orphan deletion',
    () async {
      final controller = await boot();
      await controller.savePromptStash(_prompt('saved'), locationRevision: 0);
      await controller.rememberSentPrompt('a', 'Old sent history');
      await prefs.setString('oc.agent.a', 'build');
      await controller.store.setActiveId('b');
      await controller.savePromptStash(_prompt('saved'), locationRevision: 0);
      await controller.store.setActiveId('a');
      expect(controller.promptStash.single.id, 'saved');
      expect(controller.sentPromptHistory, isNotEmpty);
      vault.failCollect = true;
      final failed = await controller.deleteProfileAndLocalData('a');
      expect(failed.complete, isFalse);
      expect(failed.removedProfile, isFalse);
      expect(failed.failures, ['stashed prompts and attachments']);
      expect(failed.removedPreferenceKeys, isEmpty);
      expect(secureDeletes, isEmpty);
      expect(await disk.onDisk('oc.profiles'), contains('"a"'));
      expect(await disk.onDisk('oc.activeProfile'), 'a');
      expect(await disk.onDisk('oc.agent.a'), 'build');
      expect(await disk.onDisk(_marker('a')), isTrue);
      expect(disk.removals, isNot(contains(_marker('a'))));
      expect(await disk.onDisk(_key('a', 'saved')), isNull);
      expect(controller.promptStash, isEmpty);
      expect(controller.sentPromptHistory, isEmpty);
      expect(controller.canUsePromptShelf, isTrue);
      expect(await stashRoot.files(), hasLength(2));
      controller.dispose();
      SharedPreferences.resetStatic();
      final restarted = await boot(stashVault: _Vault(stashRoot));
      expect((await restarted.deleteProfileAndLocalData('a')).complete, isTrue);
      expect(secureDeletes, ['pw.a']);
      expect(await disk.onDisk(_marker('a')), isNull);
      expect(await stashRoot.files(), hasLength(1));
      await restarted.store.setActiveId('b');
      expect(
        (await restarted.restorePromptStashAttachments(
          'saved',
          locationRevision: 0,
        )).attachments.single.url,
        _data.url,
      );
    },
  );

  test(
    'failed removal cleanup notifies the actual shelf and keeps retry ownership',
    () async {
      final controller = await boot();
      await controller.savePromptStash(_prompt('saved'), locationRevision: 0);
      final observedCounts = <int>[];
      controller.addListener(
        () => observedCounts.add(controller.promptStash.length),
      );
      vault.failCollect = true;
      await expectLater(
        controller.removePromptStash('saved', locationRevision: 0),
        throwsStateError,
      );
      expect(observedCounts, [0]);
      expect(controller.promptStash, isEmpty);
      expect(await disk.onDisk(_marker('a')), isTrue);
      expect(await disk.onDisk(_key('a', 'saved')), isNull);
      expect(await stashRoot.files(), hasLength(1));
      expect(secureDeletes, isEmpty);
      vault.failCollect = false;
      expect(
        (await controller.deleteProfileAndLocalData('a')).complete,
        isTrue,
      );
      expect(await stashRoot.files(), isEmpty);
      expect(await disk.onDisk(_marker('a')), isNull);
    },
  );

  test(
    'pre-marker references survive failed admission and cannot lose their profile or secret',
    () async {
      final controller = await boot();
      await controller.savePromptStash(_prompt('old'), locationRevision: 0);
      await prefs.remove(_marker('a'));
      final original = await disk.onDisk(_key('a', 'old'));
      disk.refusedWrites.add(_marker('a'));
      final failed = await controller.deleteProfileAndLocalData('a');
      expect(failed.removedProfile, isFalse);
      expect(secureDeletes, isEmpty);
      expect(await disk.onDisk(_key('a', 'old')), original);
      expect(controller.promptStash.single.attachmentCount, 1);
      expect(
        (await controller.restorePromptStashAttachments(
          'old',
          locationRevision: 0,
        )).attachments.single.url,
        _data.url,
      );
      disk.refusedWrites.clear();
      expect(
        (await controller.deleteProfileAndLocalData('a')).complete,
        isTrue,
      );
      expect(secureDeletes, ['pw.a']);
      expect(await stashRoot.files(), isEmpty);
    },
  );

  test(
    'partial metadata cleanup reloads the surviving shelf and history without phantom entries',
    () async {
      final controller = await boot();
      await controller.savePromptStash(_prompt('first'), locationRevision: 0);
      await controller.savePromptStash(_prompt('second'), locationRevision: 0);
      await controller.rememberSentPrompt('a', 'Sent history');
      expect(controller.promptStash, hasLength(2));
      disk.refusedRemovals.add(_key('a', 'second'));
      final failed = await controller.deleteProfileAndLocalData('a');
      expect(failed.removedProfile, isFalse);
      expect(await disk.onDisk(_key('a', 'first')), isNull);
      expect(await disk.onDisk(_key('a', 'second')), isNotNull);
      expect(controller.promptStash.map((p) => p.id), ['second']);
      expect(controller.sentPromptHistory, ['Sent history']);
      expect(secureDeletes, isEmpty);
      disk.refusedRemovals.clear();
      expect(
        (await controller.deleteProfileAndLocalData('a')).complete,
        isTrue,
      );
      expect(await stashRoot.files(), isEmpty);
    },
  );

  test(
    'failed marker retirement and a later settings refusal never delete the secret early',
    () async {
      final controller = await boot();
      await controller.savePromptStash(_prompt('saved'), locationRevision: 0);
      await prefs.setString('oc.agent.a', 'build');
      disk.refusedRemovals.add(_marker('a'));
      var result = await controller.deleteProfileAndLocalData('a');
      expect(result.removedProfile, isFalse);
      expect(await disk.onDisk(_marker('a')), isTrue);
      expect(await stashRoot.files(), isEmpty);
      expect(secureDeletes, isEmpty);
      disk.refusedRemovals
        ..clear()
        ..add('oc.agent.a');
      result = await controller.deleteProfileAndLocalData('a');
      expect(result.failures, ['1 saved setting']);
      expect(await disk.onDisk(_marker('a')), isNull);
      expect(controller.promptStash, isEmpty);
      expect(secureDeletes, isEmpty);
      disk.refusedRemovals.clear();
      expect(
        (await controller.deleteProfileAndLocalData('a')).complete,
        isTrue,
      );
      expect(secureDeletes, ['pw.a']);
    },
  );

  test(
    'disposal during authorized deletion permits cleanup without post-disposal notifications',
    () async {
      final controller = await boot();
      await controller.savePromptStash(_prompt('saved'), locationRevision: 0);
      vault.collectEntered = Completer<void>();
      vault.collectGate = gate();
      final deletion = controller.deleteProfileAndLocalData('a');
      await vault.collectEntered!.future;
      controller.dispose();
      await expectLater(
        controller.savePromptStash(_prompt('late'), locationRevision: 0),
        throwsStateError,
      );
      vault.collectGate!.complete();
      expect((await deletion).complete, isTrue);
      expect(await stashRoot.files(), isEmpty);
      expect(secureDeletes, ['pw.a']);
    },
  );
}
