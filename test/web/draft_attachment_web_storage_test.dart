// Runs only in a browser: `flutter test --platform chrome test/web`.
// Exercises the real IndexedDB payload store; no dart:io anywhere.
@TestOn('browser')
library;

import 'dart:async';
import 'dart:convert';
import 'dart:js_interop';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/state/draft_attachment_storage_web.dart';
import 'package:opencode_mobile/state/draft_attachments.dart';
import 'package:opencode_mobile/state/prompt_shelf.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:web/web.dart' as web;

const _note = PromptAttachment(
  filename: 'ملاحظة.txt',
  mime: 'text/plain',
  url: 'data:text/plain;base64,SGVsbG8g2YXYsdit2KjYpyDwn4yN',
);
const _other = PromptAttachment(
  filename: 'other.txt',
  mime: 'text/plain',
  url: 'data:text/plain;base64,d29ybGQ=',
);

/// Removes a database this test created. Open connections receive
/// `versionchange`, which the store answers by closing, so deletion is not
/// blocked by a vault that is still alive.
Future<void> _deleteDatabase(String name) {
  final completer = Completer<void>();
  final request = web.window.indexedDB.deleteDatabase(name);
  request.onsuccess = ((web.Event _) {
    if (!completer.isCompleted) completer.complete();
  }).toJS;
  request.onerror = ((web.Event _) {
    if (!completer.isCompleted) {
      completer.completeError(StateError('Could not delete $name'));
    }
  }).toJS;
  return completer.future.timeout(const Duration(seconds: 10));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late String root;
  late IndexedDbDraftAttachmentStorage storage;
  late DraftAttachmentVault vault;

  setUp(() {
    root = 'test-${DateTime.now().microsecondsSinceEpoch}';
    storage = IndexedDbDraftAttachmentStorage(root);
    vault = DraftAttachmentVault(storage: storage);
    addTearDown(() => _deleteDatabase(storage.databaseName));
  });

  test(
    'a payload survives reopening its store and never lands in metadata',
    () async {
      final refs = await vault.store('server-a', [_note]);
      expect(jsonEncode(refs.single.toJson()), isNot(contains('data:')));

      // A new store over the same root is what a reload constructs.
      final restarted = DraftAttachmentVault(
        storage: IndexedDbDraftAttachmentStorage(root),
      );
      final recovered = await restarted.restore(
        'server-a',
        refs,
        sameLocation: true,
      );
      expect(recovered.unavailable, isEmpty);
      expect(recovered.attachments.single.url, _note.url);
      expect(recovered.attachments.single.filename, _note.filename);
      expect(
        (await restarted.restore(
          'server-b',
          refs,
          sameLocation: true,
        )).unavailable,
        [_note.filename],
      );
    },
  );

  test('deleting a profile removes only its payloads', () async {
    final a = await vault.store('a', [_note, _other]);
    final b = await vault.store('b', [_note]);
    expect(await vault.collect({}, owner: 'a'), isTrue);
    expect((await vault.restore('a', a, sameLocation: true)).unavailable, [
      _note.filename,
      _other.filename,
    ]);
    expect(
      (await vault.restore('b', b, sameLocation: true)).attachments,
      hasLength(1),
    );
    final remaining = await IndexedDbDraftAttachmentStorage(root).list();
    expect(remaining.map((e) => e.ownerKey), [
      DraftAttachmentVault.ownerKey('b'),
    ]);
    expect(remaining.single.bytes, b.single.bytes);
  });

  test(
    'changed bytes are unavailable until the next save repairs them',
    () async {
      final refs = await vault.store('a', [_note]);
      final ref = refs.single;
      await IndexedDbDraftAttachmentStorage(root).write(
        DraftAttachmentVault.ownerKey('a'),
        ref.blob!,
        Uint8List.fromList(List.filled(ref.bytes, 120)),
      );
      expect((await vault.restore('a', refs, sameLocation: true)).unavailable, [
        _note.filename,
      ]);
      await vault.store('a', [_note]);
      expect(
        (await vault.restore(
          'a',
          refs,
          sameLocation: true,
        )).attachments.single.url,
        _note.url,
      );
    },
  );

  test('a missing payload is reported, not invented', () async {
    final refs = await vault.store('a', [_note]);
    await storage.delete(DraftAttachmentVault.ownerKey('a'), refs.single.blob!);
    expect((await vault.restore('a', refs, sameLocation: true)).unavailable, [
      _note.filename,
    ]);
    expect(
      await storage.length(
        DraftAttachmentVault.ownerKey('a'),
        refs.single.blob!,
      ),
      isNull,
    );
  });

  test(
    'the stash shelf keeps attachments in the browser by default and clears them with the profile',
    () async {
      SharedPreferences.setMockInitialValues({});
      addTearDown(
        () => _deleteDatabase(
          '${IndexedDbDraftAttachmentStorage.databasePrefix}'
          '${PromptShelfStore.attachmentDirectoryName}',
        ),
      );
      final prefs = await SharedPreferences.getInstance();
      final shelf = PromptShelfStore.withAttachmentFiles(prefs);
      await shelf.stash(
        'a',
        const StashedPrompt(
          id: 'first',
          text: 'keep',
          createdAt: 1,
          attachments: [_note],
        ),
      );
      final raw = prefs.getString('oc.promptStash.a.first')!;
      expect(raw, isNot(contains('data:')));

      final restarted = PromptShelfStore.withAttachmentFiles(prefs);
      final recovery = await restarted.restoreAttachments(
        'a',
        'first',
        sameLocation: true,
      );
      expect(recovery.unavailable, isEmpty);
      expect(recovery.attachments.single.url, _note.url);

      expect(await restarted.clearForProfile('a'), isTrue);
      final stashStore = IndexedDbDraftAttachmentStorage(
        PromptShelfStore.attachmentDirectoryName,
      );
      expect(
        await stashStore.list(ownerKey: DraftAttachmentVault.ownerKey('a')),
        isEmpty,
      );
      expect(prefs.getKeys().where((k) => k.endsWith('.a')), isEmpty);
    },
  );
}
