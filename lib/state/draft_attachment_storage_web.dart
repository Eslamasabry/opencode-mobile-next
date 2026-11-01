/// Browser payload store for draft attachments: one IndexedDB database per
/// vault root inside the app's own origin.
///
/// Selected by the conditional import in `draft_attachments.dart` whenever
/// `dart:js_interop` exists (dart2js and dart2wasm); the file backend is the
/// default the analyzer and native builds see. Nothing here touches
/// `dart:io`.
@JS()
library;

import 'dart:async';
import 'dart:js_interop';
import 'dart:typed_data';

import 'package:web/web.dart' as web;

import 'draft_attachment_storage.dart';

/// A browser has no directory to hand a vault; the type exists only so the
/// `DraftAttachmentVault(directory:)` parameter keeps compiling on both
/// platforms. Passing one in a browser is a programming error.
typedef VaultDirectory = Object;

/// The store for a named root: an IndexedDB database in this origin, opened
/// on first use so constructing a vault touches nothing.
DraftAttachmentStorage defaultDraftAttachmentStorage(String root) =>
    IndexedDbDraftAttachmentStorage(root);

/// Directories do not exist in a browser; native tests are the only caller.
DraftAttachmentStorage directoryDraftAttachmentStorage(
  Future<VaultDirectory> Function() directory,
) => throw UnsupportedError(
  'A browser keeps draft attachments in IndexedDB, not in a directory',
);

extension type _MetaRecord._(JSObject _) implements JSObject {
  external factory _MetaRecord({String owner, String id, int bytes});
  external String get owner;
  external String get id;
  external int get bytes;
}

extension type _PayloadRecord._(JSObject _) implements JSObject {
  external factory _PayloadRecord({int bytes, JSUint8Array data});
  external int get bytes;
  external JSUint8Array get data;
}

/// Payloads under `<ownerKey>/<id>` keys in two object stores: `meta`
/// (owner, id, size) so listing never loads bytes, and `payloads` (the
/// bytes). Every write and delete updates both in one transaction, which
/// IndexedDB commits or aborts as a unit — the browser equivalent of the
/// temporary-file-and-rename publication the native store does.
///
/// Durability beyond a reload is the browser's: on the first write the store
/// asks for persistent storage so the origin is exempt from best-effort
/// eviction where the browser grants that ([persisted] records the answer).
class IndexedDbDraftAttachmentStorage implements DraftAttachmentStorage {
  IndexedDbDraftAttachmentStorage(String root) : _name = '$databasePrefix$root';

  /// Every database this app creates for attachments starts with this, so
  /// diagnostics and tests can find them without knowing each root.
  static const databasePrefix = 'oc.draftAttachments.';
  static const _meta = 'meta';
  static const _payloads = 'payloads';
  static const _version = 1;

  final String _name;
  Future<web.IDBDatabase>? _opening;
  Future<void>? _persisting;

  /// Whether the browser granted persistent storage for this origin: null
  /// until the first write asked, false when it declined or the API is
  /// missing. A declined request still stores durably; the origin merely
  /// stays eligible for eviction under storage pressure.
  bool? persisted;

  /// The database name, for tests that inspect or delete it.
  String get databaseName => _name;

  Future<web.IDBDatabase> _database() async {
    final pending = _opening;
    if (pending != null) return pending;
    final opening = _open();
    _opening = opening;
    try {
      return await opening;
    } catch (_) {
      if (identical(_opening, opening)) _opening = null;
      rethrow;
    }
  }

  Future<web.IDBDatabase> _open() async {
    final web.IDBOpenDBRequest request;
    try {
      request = web.window.indexedDB.open(_name, _version);
    } catch (error) {
      throw _failure(error);
    }
    request.onupgradeneeded = ((web.Event _) {
      final db = request.result as web.IDBDatabase;
      if (!db.objectStoreNames.contains(_meta)) db.createObjectStore(_meta);
      if (!db.objectStoreNames.contains(_payloads)) {
        db.createObjectStore(_payloads);
      }
    }).toJS;
    final db = (await _settle(request)) as web.IDBDatabase;
    // Another tab upgrading or the browser closing the connection: drop it
    // and reopen on the next call rather than failing every later write.
    db.onversionchange = ((web.Event _) {
      db.close();
      _opening = null;
    }).toJS;
    db.onclose = ((web.Event _) {
      _opening = null;
    }).toJS;
    return db;
  }

  web.IDBTransaction _transaction(
    web.IDBDatabase db,
    List<String> stores,
    String mode,
  ) {
    try {
      return db.transaction([for (final s in stores) s.toJS].toJS, mode);
    } catch (error) {
      // A closing connection throws synchronously; forget it so the next
      // call opens a fresh one.
      _opening = null;
      throw _failure(error);
    }
  }

  static Future<JSAny?> _settle(web.IDBRequest request) {
    final completer = Completer<JSAny?>();
    request.onsuccess = ((web.Event _) {
      if (!completer.isCompleted) completer.complete(request.result);
    }).toJS;
    request.onerror = ((web.Event _) {
      if (!completer.isCompleted) {
        completer.completeError(_failure(request.error));
      }
    }).toJS;
    return completer.future;
  }

  /// Resolves when the transaction has committed; rejects when it aborted,
  /// including after a request inside it failed (a full quota surfaces as
  /// the aborting error, so no per-request handler is needed).
  static Future<void> _committed(web.IDBTransaction transaction) {
    final completer = Completer<void>();
    transaction.oncomplete = ((web.Event _) {
      if (!completer.isCompleted) completer.complete();
    }).toJS;
    transaction.onabort = ((web.Event _) {
      if (!completer.isCompleted) {
        completer.completeError(_failure(transaction.error));
      }
    }).toJS;
    return completer.future;
  }

  static String _key(String ownerKey, String id) => '$ownerKey/$id';

  @override
  Future<List<DraftPayloadEntry>> list({String? ownerKey}) async {
    final db = await _database();
    final transaction = _transaction(db, const [_meta], 'readonly');
    final query = ownerKey == null
        ? null
        : web.IDBKeyRange.bound('$ownerKey/'.toJS, '$ownerKey/￿'.toJS);
    final result = await _settle(transaction.objectStore(_meta).getAll(query));
    final records = (result as JSArray<_MetaRecord>).toDart;
    return [
      for (final record in records)
        DraftPayloadEntry(
          ownerKey: record.owner,
          id: record.id,
          bytes: record.bytes,
        ),
    ];
  }

  @override
  Future<int?> length(String ownerKey, String id) async {
    final db = await _database();
    final transaction = _transaction(db, const [_meta], 'readonly');
    final result = await _settle(
      transaction.objectStore(_meta).get(_key(ownerKey, id).toJS),
    );
    if (result.isUndefinedOrNull) return null;
    return (result as _MetaRecord).bytes;
  }

  @override
  Future<Uint8List> read(String ownerKey, String id, int expectedBytes) async {
    final db = await _database();
    final transaction = _transaction(db, const [_payloads], 'readonly');
    final result = await _settle(
      transaction.objectStore(_payloads).get(_key(ownerKey, id).toJS),
    );
    if (result.isUndefinedOrNull) {
      throw const FormatException('Attachment missing');
    }
    final record = result as _PayloadRecord;
    final data = record.data.toDart;
    if (record.bytes != expectedBytes || data.length != expectedBytes) {
      throw const FormatException('Attachment size changed');
    }
    return data;
  }

  @override
  Future<void> write(String ownerKey, String id, Uint8List bytes) async {
    // A persistence permission prompt must not hold a draft save open.
    unawaited(_requestPersistence());
    final db = await _database();
    final transaction = _transaction(db, const [_meta, _payloads], 'readwrite');
    final committed = _committed(transaction);
    final key = _key(ownerKey, id).toJS;
    try {
      transaction
          .objectStore(_meta)
          .put(_MetaRecord(owner: ownerKey, id: id, bytes: bytes.length), key);
      transaction
          .objectStore(_payloads)
          .put(_PayloadRecord(bytes: bytes.length, data: bytes.toJS), key);
    } catch (error) {
      try {
        transaction.abort();
      } catch (_) {}
      await committed.catchError((Object _) {});
      throw _failure(error);
    }
    try {
      await committed;
    } on DraftAttachmentStorageException catch (error) {
      if (error.failure != DraftAttachmentStorageFailure.quota) rethrow;
      throw await _quotaFailure(error.cause);
    }
  }

  @override
  Future<void> delete(String ownerKey, String id) async {
    final db = await _database();
    final transaction = _transaction(db, const [_meta, _payloads], 'readwrite');
    final committed = _committed(transaction);
    final key = _key(ownerKey, id).toJS;
    try {
      transaction.objectStore(_meta).delete(key);
      transaction.objectStore(_payloads).delete(key);
    } catch (error) {
      try {
        transaction.abort();
      } catch (_) {}
      await committed.catchError((Object _) {});
      throw _failure(error);
    }
    await committed;
  }

  /// Best effort and once: a browser that declines still stores the bytes,
  /// and one without the API is not an error.
  Future<void> _requestPersistence() => _persisting ??= Future<void>(() async {
    try {
      persisted = (await web.window.navigator.storage.persist().toDart).toDart;
    } catch (_) {
      persisted = false;
    }
  });

  static const unavailableMessage =
      'The browser is not letting this page keep attachments. Private '
      'browsing or blocked site data can cause this; allow site data for '
      'this page, then try again.';
  static const quotaMessage =
      'The browser has no storage space left for this page. Remove stashed '
      'prompts or attachments, or free space in the browser’s site data '
      'settings, then try again.';

  /// Quota failures name the numbers when the browser reports them, so
  /// the person can tell "a little over" from "the site data is huge".
  static Future<DraftAttachmentStorageException> _quotaFailure(
    Object? cause,
  ) async {
    String message = quotaMessage;
    try {
      final estimate = await web.window.navigator.storage.estimate().toDart;
      final usage = estimate.usage;
      final quota = estimate.quota;
      if (quota > 0) {
        message =
            'The browser has no storage space left for this page '
            '(${_megabytes(usage)} of ${_megabytes(quota)} in use). Remove '
            'stashed prompts or attachments, or free space in the browser’s '
            'site data settings, then try again.';
      }
    } catch (_) {}
    return DraftAttachmentStorageException(
      DraftAttachmentStorageFailure.quota,
      message,
      cause: cause,
    );
  }

  static String _megabytes(int bytes) {
    final megabytes = bytes / (1024 * 1024);
    return megabytes >= 100
        ? '${megabytes.round()} MB'
        : '${megabytes.toStringAsFixed(1)} MB';
  }

  static DraftAttachmentStorageException _failure(Object? error) {
    var name = '';
    try {
      final jsError = error as JSAny?;
      if (jsError != null && jsError.isA<web.DOMException>()) {
        name = (jsError as web.DOMException).name;
      }
    } catch (_) {}
    if (name == 'QuotaExceededError') {
      return DraftAttachmentStorageException(
        DraftAttachmentStorageFailure.quota,
        quotaMessage,
        cause: error,
      );
    }
    return DraftAttachmentStorageException(
      DraftAttachmentStorageFailure.unavailable,
      unavailableMessage,
      cause: error,
    );
  }
}
