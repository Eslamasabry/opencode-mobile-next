import 'dart:typed_data';

/// Why a payload store refused to keep bytes.
enum DraftAttachmentStorageFailure {
  /// This app's own budget for one vault root is exhausted
  /// ([DraftAttachmentStorageException.message] says so in product words).
  limit,

  /// The platform refused the write for lack of space: a full disk on
  /// native, the origin's storage quota in a browser.
  quota,

  /// The backing store could not be opened or answered with an error that is
  /// not about space: no IndexedDB in this browsing mode, a blocked site,
  /// a database another tab is upgrading, a filesystem error.
  unavailable,
}

/// A payload store could not keep or return attachment bytes.
///
/// Every failure carries a sentence the composer can show next to the text
/// it kept, so a refused attachment never turns into silently dropped
/// input. Callers that only need a yes/no answer treat it like any other
/// exception; the vault's contract of "throw, and leave metadata untouched"
/// is unchanged.
class DraftAttachmentStorageException implements Exception {
  const DraftAttachmentStorageException(
    this.failure,
    this.message, {
    this.cause,
  });

  final DraftAttachmentStorageFailure failure;

  /// Plain-language, actionable, free of paths and payload content.
  final String message;
  final Object? cause;

  static const draftLimitMessage = 'Draft attachment storage limit reached';
  static const rootLimitMessage = 'Draft attachment storage is full';

  @override
  String toString() => message;
}

/// One payload a store holds, described without loading its bytes.
class DraftPayloadEntry {
  const DraftPayloadEntry({
    required this.ownerKey,
    required this.id,
    required this.bytes,
    this.committed = true,
  });

  /// [DraftAttachmentVault.ownerKey] of the profile that published it.
  final String ownerKey;

  /// Content hash of the payload, lowercase hex.
  final String id;
  final int bytes;

  /// False for a partial publication the store found (a temporary file the
  /// process died on). Such entries count against the budget until the next
  /// collection removes them; they are never restored.
  final bool committed;

  String get key => '$ownerKey/$id';
}

/// Where one vault root keeps its payload bytes.
///
/// The vault owns identity, hashing, size bounds, owner isolation, and
/// transaction ordering; a store only has to keep bytes under a
/// `<ownerKey>/<id>` key durably and atomically. Native builds keep them as
/// app-private files, browsers in the origin's IndexedDB. Both are opened
/// lazily: constructing a store touches nothing.
abstract class DraftAttachmentStorage {
  /// Every payload in this root, or only [ownerKey]'s when given.
  Future<List<DraftPayloadEntry>> list({String? ownerKey});

  /// Size of a committed payload, or null when there is none.
  Future<int?> length(String ownerKey, String id);

  /// The payload's bytes, which must be exactly [expectedBytes] long.
  ///
  /// Throws [FormatException] when the payload is missing or its size does
  /// not match: the caller's length check is not a bound, because the bytes
  /// can change between the two calls.
  Future<Uint8List> read(String ownerKey, String id, int expectedBytes);

  /// Publishes [bytes] atomically: a reader sees the previous payload or the
  /// new one, never a prefix.
  Future<void> write(String ownerKey, String id, Uint8List bytes);

  /// Removes a payload; a missing one is not an error.
  Future<void> delete(String ownerKey, String id);
}
