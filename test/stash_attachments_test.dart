import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/state/draft_attachments.dart';
import 'package:opencode_mobile/state/prompt_shelf.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';
import 'package:shared_preferences_platform_interface/types.dart';

// Synthetic backend/vault coverage only. No profiles, secure storage, provider
// requests, native channels or live controller activation are involved.
class _Backend extends InMemorySharedPreferencesStore {
  _Backend() : super.withData({});
  bool refuseWrite = false;
  bool throwWrite = false;
  bool refuseRemove = false;
  bool failReload = false;
  final refusedRemovals = <String>{};

  @override
  Future<bool> setValue(String valueType, String key, Object value) {
    if (throwWrite) throw StateError('synthetic write failure');
    if (refuseWrite) return Future.value(false);
    return super.setValue(valueType, key, value);
  }

  @override
  Future<bool> remove(String key) =>
      refuseRemove || refusedRemovals.contains(key)
      ? Future.value(false)
      : super.remove(key);

  @override
  Future<Map<String, Object>> getAllWithParameters(
    GetAllParameters parameters,
  ) {
    if (failReload) throw StateError('synthetic reload failure');
    return super.getAllWithParameters(parameters);
  }

  Future<Object?> onDisk(String key) async => (await super.getAllWithParameters(
    GetAllParameters(filter: PreferencesFilter(prefix: 'flutter.')),
  ))['flutter.$key'];
}

class _Vault extends DraftAttachmentVault {
  _Vault(Directory directory)
    : root = directory,
      super(directory: () async => directory);
  final Directory root;
  bool failStore = false;
  bool failAfterFirst = false;
  bool failCollect = false;
  int stores = 0;
  int collections = 0;
  Future<void> Function(String owner)? beforeStore;
  Completer<void>? storeGate;

  @override
  Future<List<DraftAttachmentRef>> store(
    String owner,
    List<PromptAttachment> attachments,
  ) async {
    stores++;
    await beforeStore?.call(owner);
    await storeGate?.future;
    if (failStore) throw const FileSystemException('synthetic full storage');
    if (failAfterFirst && attachments.isNotEmpty) {
      await super.store(owner, [attachments.first]);
      throw const FileSystemException('synthetic partial publication failure');
    }
    return super.store(owner, attachments);
  }

  @override
  Future<bool> collect(
    Map<String, Iterable<DraftAttachmentRef>> retained, {
    String? owner,
  }) async {
    collections++;
    if (failCollect) return false;
    return super.collect(retained, owner: owner);
  }
}

const _data = PromptAttachment(
  filename: 'note.txt',
  mime: 'text/plain',
  url: 'data:text/plain;base64,aGVsbG8=',
);
const _serverFile = PromptAttachment(
  filename: 'server.txt',
  mime: 'text/plain',
  url: 'file:///workspace/server.txt',
);
const _reference = ReviewReference(
  id: 'reference',
  kind: ReviewReferenceKind.hunk,
  path: 'lib/example.dart',
  scope: ReviewReferenceScope.workingTree,
  snippet: '+synthetic',
  comment: 'Review this',
);
StashedPrompt _prompt(
  String id, {
  List<PromptAttachment> attachments = const [_data],
}) => StashedPrompt(
  id: id,
  text: 'Keep this draft — مرحبا',
  createdAt: 123,
  directory: '/workspace',
  workspace: 'workspace-a',
  attachments: attachments,
  references: const [_reference],
);
String _key(String profile, String id) => 'oc.promptStash.$profile.$id';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late _Backend backend;
  late SharedPreferences prefs;
  late Directory temporary;
  late _Vault vault;
  late PromptShelfStore shelf;

  setUp(() async {
    final previous = SharedPreferencesStorePlatform.instance;
    backend = _Backend();
    SharedPreferencesStorePlatform.instance = backend;
    SharedPreferences.resetStatic();
    addTearDown(() {
      SharedPreferencesStorePlatform.instance = previous;
      SharedPreferences.resetStatic();
    });
    temporary = await Directory.systemTemp.createTemp('ocmn-stash-fixture-');
    addTearDown(() => temporary.delete(recursive: true));
    prefs = await SharedPreferences.getInstance();
    vault = _Vault(Directory('${temporary.path}/stash'));
    shelf = PromptShelfStore.withAttachmentFiles(prefs, vault: vault);
  });

  test(
    'file backend keeps payloads out of preferences and preserves restart metadata',
    () async {
      await shelf.stash(
        'a',
        _prompt('first', attachments: [_data, _serverFile]),
      );
      final raw = await backend.onDisk(_key('a', 'first')) as String;
      expect(raw, isNot(contains(_data.url)));
      final metadata = jsonDecode(raw) as Map<String, dynamic>;
      expect(metadata['attachments'], isEmpty);
      expect(metadata['attachmentFormat'], 1);
      final reloaded = PromptShelfStore.withAttachmentFiles(
        prefs,
        vault: _Vault(vault.root),
      );
      final saved = reloaded.stashes('a').single;
      expect(saved.attachmentCount, 2);
      expect(saved.attachmentNames, ['note.txt', 'server.txt']);
      expect(saved.directory, '/workspace');
      expect(saved.workspace, 'workspace-a');
      expect(saved.createdAt, 123);
      expect(saved.text, _prompt('first').text);
      expect(saved.references.single.comment, 'Review this');
      expect(saved.locationBound, isTrue);
      final recovery = await reloaded.restoreAttachments(
        'a',
        'first',
        sameLocation: true,
      );
      expect(recovery.attachments.map((a) => a.url), [
        _data.url,
        _serverFile.url,
      ]);
      expect(recovery.unavailable, isEmpty);
    },
  );

  test('explicit legacy writer preserves inline compatibility', () async {
    final existing = PromptShelfStore(prefs);
    await existing.stash('a', _prompt('legacy'));
    expect(await backend.onDisk(_key('a', 'legacy')), contains(_data.url));
    expect(await vault.root.exists(), isFalse);
  });

  test(
    'legacy-only consumer refuses file-backed records instead of dropping attachments',
    () async {
      await shelf.stash('a', _prompt('files'));
      final legacy = PromptShelfStore(prefs);
      expect(() => legacy.stashes('a'), throwsStateError);
      await expectLater(legacy.remove('a', 'files'), throwsStateError);
      expect(
        (await shelf.restoreAttachments(
          'a',
          'files',
          sameLocation: true,
        )).attachments.single.url,
        _data.url,
      );
    },
  );

  test(
    'migration replaces only acknowledged metadata and is idempotent',
    () async {
      final original = _prompt('legacy', attachments: [_serverFile, _data]);
      await prefs.setString(_key('a', 'legacy'), jsonEncode(original.toJson()));
      expect(await shelf.migrateAttachments('a'), isEmpty);
      final first = await backend.onDisk(_key('a', 'legacy'));
      expect(first, isNot(contains(_data.url)));
      expect(await shelf.migrateAttachments('a'), isEmpty);
      expect(await backend.onDisk(_key('a', 'legacy')), first);
      final restored = await shelf.restoreAttachments(
        'a',
        'legacy',
        sameLocation: true,
      );
      expect(restored.attachments.map((a) => a.url), [
        _serverFile.url,
        _data.url,
      ]);
      expect(shelf.stashes('a').single.id, 'legacy');
    },
  );

  for (final throws in [false, true]) {
    test(
      'metadata ${throws ? 'exception' : 'refusal'} preserves literal legacy data and permits retry',
      () async {
        final original = '${jsonEncode(_prompt('legacy').toJson())}\n';
        await prefs.setString(_key('a', 'legacy'), original);
        await prefs.setBool(PromptShelfStore.attachmentOwnerKey('a'), true);
        backend.refuseWrite = !throws;
        backend.throwWrite = throws;
        expect(await shelf.migrateAttachments('a'), ['legacy']);
        expect(await backend.onDisk(_key('a', 'legacy')), original);
        expect(shelf.stashes('a').single.attachments.single.url, _data.url);
        expect(
          vault.stores,
          1,
          reason: 'the metadata write, not admission, failed',
        );
        backend.refuseWrite = false;
        backend.throwWrite = false;
        expect(await shelf.migrateAttachments('a'), isEmpty);
        expect(
          (await shelf.restoreAttachments(
            'a',
            'legacy',
            sameLocation: true,
          )).attachments.single.url,
          _data.url,
        );
      },
    );
  }

  test(
    'file failure never removes legacy input or acknowledges a new stash',
    () async {
      final original = jsonEncode(_prompt('legacy').toJson());
      await prefs.setString(_key('a', 'legacy'), original);
      vault.failStore = true;
      expect(await shelf.migrateAttachments('a'), ['legacy']);
      expect(await backend.onDisk(_key('a', 'legacy')), original);
      await expectLater(
        shelf.stash('a', _prompt('new')),
        throwsA(isA<FileSystemException>()),
      );
      expect(await backend.onDisk(_key('a', 'new')), isNull);
      expect(shelf.stashes('a').map((p) => p.id), ['legacy']);
    },
  );

  test(
    'uncertain preference cache suppresses GC until a successful reload',
    () async {
      final original = jsonEncode(_prompt('legacy').toJson());
      await prefs.setString(_key('a', 'legacy'), original);
      await prefs.setBool(PromptShelfStore.attachmentOwnerKey('a'), true);
      backend.refuseWrite = true;
      backend.failReload = true;
      expect(await shelf.migrateAttachments('a'), ['legacy']);
      expect(await backend.onDisk(_key('a', 'legacy')), original);
      expect(vault.collections, 0);
      expect(await vault.root.exists(), isTrue);
      backend.refuseWrite = false;
      backend.failReload = false;
      expect(await shelf.migrateAttachments('a'), isEmpty);
      expect(
        (await shelf.restoreAttachments(
          'a',
          'legacy',
          sameLocation: true,
        )).unavailable,
        isEmpty,
      );
    },
  );

  test(
    'partial file publication collects only orphans and preserves acknowledged owners',
    () async {
      const another = PromptAttachment(
        filename: 'another.txt',
        mime: 'text/plain',
        url: 'data:text/plain;base64,d29ybGQ=',
      );
      final original = jsonEncode(
        _prompt('legacy', attachments: [another, _data]).toJson(),
      );
      await prefs.setString(_key('a', 'legacy'), original);
      await shelf.stash('a', _prompt('keeper'));
      vault.failAfterFirst = true;
      expect(await shelf.migrateAttachments('a'), ['legacy']);
      expect(await backend.onDisk(_key('a', 'legacy')), original);
      expect(
        (await shelf.restoreAttachments(
          'a',
          'keeper',
          sameLocation: true,
        )).attachments.single.url,
        _data.url,
      );
      final files = await Directory(
        '${vault.root.path}/${DraftAttachmentVault.ownerKey('a')}',
      ).list().where((entry) => entry is File).toList();
      expect(
        files,
        hasLength(1),
        reason: 'only the acknowledged keeper blob remains',
      );
    },
  );

  test(
    'stash and ordinary draft garbage collectors cannot delete each other payloads',
    () async {
      final drafts = DraftAttachmentVault(
        directory: () async => Directory('${temporary.path}/drafts'),
      );
      final draftRefs = await drafts.store('a', [_data]);
      await shelf.stash('a', _prompt('one'));
      await shelf.stash('a', _prompt('two'));
      await shelf.stash('b', _prompt('other'));
      await drafts.collect({});
      expect(
        (await shelf.restoreAttachments(
          'a',
          'one',
          sameLocation: true,
        )).attachments.single.url,
        _data.url,
      );
      await drafts.store('a', [_data]);
      await shelf.remove('a', 'one');
      expect(
        (await shelf.restoreAttachments(
          'a',
          'two',
          sameLocation: true,
        )).attachments.single.url,
        _data.url,
      );
      expect(await shelf.clearForProfile('a'), isTrue);
      expect(
        (await shelf.restoreAttachments(
          'b',
          'other',
          sameLocation: true,
        )).attachments.single.url,
        _data.url,
      );
      expect(
        (await drafts.restore(
          'a',
          draftRefs,
          sameLocation: true,
        )).attachments.single.url,
        _data.url,
      );
    },
  );

  test(
    'missing files and wrong-location references are named without consuming source',
    () async {
      await shelf.stash(
        'a',
        _prompt('mixed', attachments: [_data, _serverFile]),
      );
      final stored = shelf.stashes('a').single;
      final blob = stored.attachmentRefs.first.blob!;
      await File(
        '${vault.root.path}/${DraftAttachmentVault.ownerKey('a')}/$blob.blob',
      ).delete();
      final restored = await shelf.restoreAttachments(
        'a',
        'mixed',
        sameLocation: false,
      );
      expect(restored.attachments, isEmpty);
      expect(restored.unavailable, ['note.txt', 'server.txt']);
      expect(shelf.stashes('a').single.attachmentCount, 2);
      expect(shelf.stashes('a').single.text, _prompt('mixed').text);
      expect(await backend.onDisk(_key('a', 'mixed')), isNotNull);
    },
  );

  test(
    'metadata removal failure retains files and a cleanup failure stays retryable',
    () async {
      await shelf.stash('a', _prompt('one'));
      await shelf.recordSent('a', 'sent text');
      await shelf.stash('b', _prompt('keeper'));
      backend.refuseRemove = true;
      expect(await shelf.clearForProfile('a'), isFalse);
      expect(
        (await shelf.restoreAttachments(
          'a',
          'one',
          sameLocation: true,
        )).attachments.single.url,
        _data.url,
      );
      backend.refuseRemove = false;
      vault.failCollect = true;
      expect(await shelf.clearForProfile('a'), isFalse);
      expect(await backend.onDisk(_key('a', 'one')), isNull);
      expect(await backend.onDisk('oc.promptHistory.a'), isNull);
      expect(
        await backend.onDisk(PromptShelfStore.attachmentOwnerKey('a')),
        isTrue,
      );
      // Metadata is already gone, so only the durable marker survives restart.
      final restarted = PromptShelfStore.withAttachmentFiles(
        prefs,
        vault: _Vault(vault.root),
      );
      expect(await restarted.clearForProfile('a'), isTrue);
      expect(
        await backend.onDisk(PromptShelfStore.attachmentOwnerKey('a')),
        isNull,
      );
      expect(
        (await shelf.restoreAttachments(
          'b',
          'keeper',
          sameLocation: true,
        )).unavailable,
        isEmpty,
      );
    },
  );

  test(
    'corrupt bytes remain unavailable until an explicit save repairs the shared blob',
    () async {
      await shelf.stash('a', _prompt('one'));
      final ref = shelf.stashes('a').single.attachmentRefs.single;
      final file = File(
        '${vault.root.path}/${DraftAttachmentVault.ownerKey('a')}/${ref.blob}.blob',
      );
      await file.writeAsString(_data.url.replaceFirst('aGVsbG8=', 'aGVsbG9='));
      final recovery = await shelf.restoreAttachments(
        'a',
        'one',
        sameLocation: true,
      );
      expect(recovery.attachments, isEmpty);
      expect(recovery.unavailable, ['note.txt']);
      expect(shelf.stashes('a').single.text, _prompt('one').text);
      await shelf.stash('a', _prompt('two'));
      expect(
        (await shelf.restoreAttachments(
          'a',
          'one',
          sameLocation: true,
        )).attachments.single.url,
        _data.url,
      );
    },
  );

  test(
    'oversized legacy attachment counts defer without truncation or eviction',
    () async {
      expect(PromptShelfStore.maxAttachmentBytes, 32 * 1024 * 1024);
      expect(PromptShelfStore.maxDiskBytes, 256 * 1024 * 1024);
      expect(
        PromptShelfStore.attachmentDirectoryName,
        isNot('draft-attachments-v1'),
      );
      final original = jsonEncode(
        _prompt('legacy', attachments: List.filled(6, _data)).toJson(),
      );
      await prefs.setString(_key('a', 'legacy'), original);
      expect(await shelf.migrateAttachments('a'), ['legacy']);
      expect(await backend.onDisk(_key('a', 'legacy')), original);
      await expectLater(
        shelf.stash('a', _prompt('new', attachments: List.filled(6, _data))),
        throwsStateError,
      );
      expect(await backend.onDisk(_key('a', 'new')), isNull);
    },
  );

  test(
    'input is snapshotted before queued file writes and clear drains prior saves',
    () async {
      final gate = Completer<void>();
      vault.storeGate = gate;
      addTearDown(() {
        if (!gate.isCompleted) gate.complete();
      });
      final input = [_data];
      final pending = shelf.stash('a', _prompt('frozen', attachments: input));
      input.clear();
      gate.complete();
      await pending;
      expect(shelf.stashes('a').single.attachmentCount, 1);
      final next = shelf.stash('a', _prompt('next'));
      final clear = shelf.clearForProfile('a');
      await next;
      expect(await clear, isTrue);
      expect(shelf.stashes('a'), isEmpty);
    },
  );

  test(
    'unknown metadata versions cannot turn retained payloads into garbage',
    () async {
      await shelf.stash('a', _prompt('one'));
      final original = shelf.stashes('a').single;
      final raw =
          jsonDecode(prefs.getString(_key('a', 'one'))!)
              as Map<String, dynamic>;
      raw['attachmentFormat'] = 2;
      await prefs.setString(_key('a', 'one'), jsonEncode(raw));
      shelf.forget('a');
      expect(() => shelf.stashes('a'), throwsFormatException);
      await expectLater(
        shelf.stash('a', _prompt('new')),
        throwsFormatException,
      );
      expect(
        (await vault.restore(
          'a',
          original.attachmentRefs,
          sameLocation: true,
        )).attachments.single.url,
        _data.url,
      );
    },
  );

  test(
    'a corrupt blob reference suspends collection until explicit profile cleanup',
    () async {
      await shelf.stash('a', _prompt('one'));
      await shelf.stash('a', _prompt('two'));
      final original = shelf
          .stashes('a')
          .firstWhere((prompt) => prompt.id == 'one');
      final raw =
          jsonDecode(prefs.getString(_key('a', 'one'))!)
              as Map<String, dynamic>;
      ((raw['attachmentRefs'] as List).single as Map)['blob'] =
          'invalid-identity';
      await prefs.setString(_key('a', 'one'), jsonEncode(raw));
      await expectLater(shelf.remove('a', 'two'), throwsStateError);
      expect(
        (await vault.restore(
          'a',
          original.attachmentRefs,
          sameLocation: true,
        )).attachments.single.url,
        _data.url,
      );
      expect(await shelf.clearForProfile('a'), isTrue);
      expect(
        (await vault.restore(
          'a',
          original.attachmentRefs,
          sameLocation: true,
        )).unavailable,
        ['note.txt'],
      );
    },
  );

  test('unused shelves never ask the vault to store or collect', () async {
    expect(await shelf.migrateAttachments('a'), isEmpty);
    expect(await shelf.clearForProfile('a'), isTrue);
    await shelf.stash('a', _prompt('text', attachments: []));
    await shelf.stash('a', _prompt('url', attachments: [_serverFile]));
    expect(await shelf.migrateAttachments('a'), isEmpty);
    await shelf.remove('a', 'url');
    await shelf.recordSent('a', 'synthetic history');
    expect(await shelf.clearForProfile('a'), isTrue);
    expect(vault.stores, 0);
    expect(vault.collections, 0);
    expect(await vault.root.exists(), isFalse);
    expect(
      await backend.onDisk(PromptShelfStore.attachmentOwnerKey('a')),
      isNull,
    );
  });

  for (final throws in [false, true]) {
    test(
      'owner marker ${throws ? 'exception' : 'refusal'} prevents file publication',
      () async {
        final original = '${jsonEncode(_prompt('legacy').toJson())}\n';
        await prefs.setString(_key('a', 'legacy'), original);
        backend.refuseWrite = !throws;
        backend.throwWrite = throws;
        expect(await shelf.migrateAttachments('a'), ['legacy']);
        await expectLater(shelf.stash('a', _prompt('new')), throwsStateError);
        expect(await backend.onDisk(_key('a', 'legacy')), original);
        expect(await backend.onDisk(_key('a', 'new')), isNull);
        expect(
          await backend.onDisk(PromptShelfStore.attachmentOwnerKey('a')),
          isNull,
        );
        expect(vault.stores, 0);
        expect(vault.collections, 0);
        expect(await vault.root.exists(), isFalse);
        backend.refuseWrite = false;
        backend.throwWrite = false;
        expect(await shelf.migrateAttachments('a'), isEmpty);
        expect(
          (await shelf.restoreAttachments(
            'a',
            'legacy',
            sameLocation: true,
          )).attachments.single.url,
          _data.url,
        );
      },
    );
  }

  test(
    'owner marker is durable before the first payload can be published',
    () async {
      vault.beforeStore = (owner) async {
        expect(
          await backend.onDisk(PromptShelfStore.attachmentOwnerKey(owner)),
          isTrue,
        );
        expect(await backend.onDisk(_key(owner, 'first')), isNull);
      };
      await shelf.stash('a', _prompt('first'));
      expect(vault.stores, 1);
      expect(await shelf.clearForProfile('a'), isTrue);
      expect(
        await backend.onDisk(PromptShelfStore.attachmentOwnerKey('a')),
        isNull,
      );
    },
  );

  test(
    'pre-marker file references cannot be deleted when ownership admission fails',
    () async {
      await shelf.stash('a', _prompt('old-files'));
      await prefs.remove(PromptShelfStore.attachmentOwnerKey('a'));
      final original = await backend.onDisk(_key('a', 'old-files'));
      backend.refuseWrite = true;
      await expectLater(shelf.remove('a', 'old-files'), throwsStateError);
      expect(await shelf.clearForProfile('a'), isFalse);
      expect(await backend.onDisk(_key('a', 'old-files')), original);
      expect(
        (await shelf.restoreAttachments(
          'a',
          'old-files',
          sameLocation: true,
        )).attachments.single.url,
        _data.url,
      );
      backend.refuseWrite = false;
      vault.failCollect = true;
      expect(await shelf.clearForProfile('a'), isFalse);
      expect(await backend.onDisk(_key('a', 'old-files')), isNull);
      expect(
        await backend.onDisk(PromptShelfStore.attachmentOwnerKey('a')),
        isTrue,
      );
      final restarted = PromptShelfStore.withAttachmentFiles(
        prefs,
        vault: _Vault(vault.root),
      );
      expect(await restarted.clearForProfile('a'), isTrue);
      expect(
        await backend.onDisk(PromptShelfStore.attachmentOwnerKey('a')),
        isNull,
      );
    },
  );

  test(
    'failed marker retirement stays discoverable and is retried after restart',
    () async {
      await shelf.stash('a', _prompt('one'));
      final marker = PromptShelfStore.attachmentOwnerKey('a');
      backend.refusedRemovals.add('flutter.$marker');
      expect(await shelf.clearForProfile('a'), isFalse);
      expect(await backend.onDisk(_key('a', 'one')), isNull);
      expect(await backend.onDisk(marker), isTrue);
      expect(prefs.getBool(marker), isTrue);
      expect(shelf.stashes('a'), isEmpty);
      backend.refusedRemovals.clear();
      final restarted = PromptShelfStore.withAttachmentFiles(
        prefs,
        vault: _Vault(vault.root),
      );
      expect(await restarted.clearForProfile('a'), isTrue);
      expect(await backend.onDisk(marker), isNull);
    },
  );

  test(
    'an optimistic unacknowledged owner marker never permits new file publication',
    () async {
      await shelf.stash('a', _prompt('old-files'));
      await prefs.remove(PromptShelfStore.attachmentOwnerKey('a'));
      backend.refuseWrite = true;
      backend.failReload = true;
      // Pre-save collection tries to adopt the old reference. Its refused
      // marker is now only in the optimistic cache, not in durable storage.
      await expectLater(shelf.stash('a', _prompt('new')), throwsStateError);
      expect(vault.stores, 1, reason: 'only the original acknowledged file');
      expect(await backend.onDisk(_key('a', 'new')), isNull);
      expect(
        await backend.onDisk(PromptShelfStore.attachmentOwnerKey('a')),
        isNull,
      );
      backend.refuseWrite = false;
      backend.failReload = false;
      final restarted = PromptShelfStore.withAttachmentFiles(
        prefs,
        vault: _Vault(vault.root),
      );
      await restarted.reload();
      expect(
        (await restarted.restoreAttachments(
          'a',
          'old-files',
          sameLocation: true,
        )).attachments.single.url,
        _data.url,
      );
      expect(await restarted.clearForProfile('a'), isTrue);
    },
  );
}
