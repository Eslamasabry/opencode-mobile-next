import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../domain/command_receipts.dart';

/// Durable command identities only: never stores a prompt, credential or body.
/// One writer owns each profile in this preferences instance. Completed receipts
/// remain as tombstones; reaching the limit blocks new commands, never evicts.
class PendingCommandJournal implements CommandReceiptJournal {
  PendingCommandJournal._(this._prefs, this._profileId);

  static const maxEntries = 1000;
  static const maxBytes = 1024 * 1024;
  static final _owners = Expando<Map<String, PendingCommandJournal>>();
  static final _closed = Expando<Set<String>>();

  static PendingCommandJournal forProfile(
    SharedPreferences prefs,
    String profileId,
  ) {
    if (RegExp(r'^[a-zA-Z0-9_-]{1,128}$').firstMatch(profileId)?.end !=
            profileId.length ||
        (_closed[prefs]?.contains(profileId) ?? false) ||
        !_hasProfile(prefs, profileId)) {
      throw const CommandReceiptException();
    }
    return (_owners[prefs] ??= {}).putIfAbsent(
      profileId,
      () => PendingCommandJournal._(prefs, profileId),
    );
  }

  static bool _hasProfile(SharedPreferences prefs, String profileId) {
    try {
      final raw = prefs.getString('oc.profiles');
      final rows = raw == null ? null : jsonDecode(raw);
      return rows is List &&
          rows.any((row) => row is Map && row['id'] == profileId);
    } catch (_) {
      return false;
    }
  }

  final SharedPreferences _prefs;
  final String _profileId;
  Future<void> _tail = Future<void>.value();
  bool _admitted = true;
  bool _storageAvailable = true;
  String get _key => 'oc.pendingCommands.$_profileId';

  void _checkAdmission() {
    if (!_admitted || !_storageAvailable || !_hasProfile(_prefs, _profileId)) {
      throw const CommandReceiptException();
    }
  }

  @override
  List<CommandReceipt> read() {
    _checkAdmission();
    try {
      final raw = _prefs.getString(_key);
      if (raw == null) return const [];
      if (utf8.encode(raw).length > maxBytes) {
        throw const CommandReceiptException();
      }
      final envelope = jsonDecode(raw);
      if (envelope is! Map ||
          envelope.length != 2 ||
          envelope['schemaVersion'] != 1 ||
          envelope['commands'] is! List) {
        throw const CommandReceiptException();
      }
      final entries = envelope['commands'] as List;
      if (entries.length > maxEntries) throw const CommandReceiptException();
      final commands = <CommandReceipt>[];
      final ids = <String>{};
      for (final entry in entries) {
        if (entry is! Map<String, dynamic>) {
          throw const CommandReceiptException();
        }
        final command = CommandReceipt.fromJson(entry);
        if (!ids.add(command.commandID)) throw const CommandReceiptException();
        commands.add(command);
      }
      return List.unmodifiable(commands);
    } catch (_) {
      _storageAvailable = false;
      throw const CommandReceiptException();
    }
  }

  /// The caller may transmit ONLY after this future returns true. Existing IDs
  /// return false, including failed and confirmed receipts; they never replace.
  @override
  Future<bool> claim(CommandReceipt receipt) => _enqueue(() async {
    await _reload();
    final commands = read();
    if (commands.any((entry) => entry.commandID == receipt.commandID)) {
      return false;
    }
    final checked = _validated(receipt);
    if (checked.state != CommandReceiptState.sent ||
        commands.length >= maxEntries) {
      throw const CommandReceiptException();
    }
    await _write([...commands, checked]);
    return true;
  });

  /// Changes the state of a known command without changing its server/tab
  /// identity. An absent receipt is never silently created by reconciliation.
  @override
  Future<void> update(CommandReceipt receipt) => _enqueue(() async {
    await _reload();
    final commands = read().toList();
    final checked = _validated(receipt);
    final index = commands.indexWhere(
      (entry) => entry.commandID == checked.commandID,
    );
    if (index < 0 ||
        !_sameIdentity(commands[index], checked) ||
        !_validTransition(commands[index].state, checked.state)) {
      throw const CommandReceiptException();
    }
    commands[index] = checked;
    await _write(commands);
  });

  static bool _validTransition(
    CommandReceiptState previous,
    CommandReceiptState next,
  ) => switch (previous) {
    CommandReceiptState.sent => true,
    CommandReceiptState.uncertain => next != CommandReceiptState.sent,
    CommandReceiptState.confirmed ||
    CommandReceiptState.failed => next == previous,
  };

  static bool _sameIdentity(CommandReceipt a, CommandReceipt b) =>
      a.commandID == b.commandID &&
      a.receiptID == b.receiptID &&
      a.sessionID == b.sessionID &&
      a.tabID == b.tabID &&
      a.scope == b.scope &&
      a.createdAt == b.createdAt;

  static CommandReceipt _validated(CommandReceipt receipt) {
    try {
      return CommandReceipt.fromJson(receipt.toJson());
    } catch (_) {
      throw const CommandReceiptException();
    }
  }

  Future<T> _enqueue<T>(Future<T> Function() action) {
    try {
      _checkAdmission();
      final next = _tail.then((_) {
        _checkAdmission();
        return action();
      });
      _tail = next.then<void>((_) {}, onError: (Object _, StackTrace _) {});
      return next;
    } catch (_) {
      return Future<T>.error(const CommandReceiptException());
    }
  }

  Future<void> _reload() async {
    _checkAdmission();
    try {
      await _prefs.reload();
    } catch (_) {
      _storageAvailable = false;
      throw const CommandReceiptException();
    }
    _checkAdmission();
  }

  Future<void> _write(List<CommandReceipt> commands) async {
    final encoded = jsonEncode({
      'schemaVersion': 1,
      'commands': commands.map((entry) => entry.toJson()).toList(),
    });
    if (utf8.encode(encoded).length > maxBytes) {
      throw const CommandReceiptException();
    }
    _checkAdmission();
    try {
      if (!await _prefs.setString(_key, encoded)) {
        throw const CommandReceiptException();
      }
      _checkAdmission();
      await _prefs.reload();
      _checkAdmission();
      if (_prefs.getString(_key) != encoded) {
        throw const CommandReceiptException();
      }
    } catch (_) {
      // The preferences cache is optimistic. After an unverified write this
      // process must not grant a send even if the cache contains the receipt.
      _storageAvailable = false;
      throw const CommandReceiptException();
    }
  }

  /// Closes new admission immediately, then drains all admitted operations.
  /// The profile deletion owner awaits this BEFORE key discovery and removal.
  /// Existing facades stay closed even if a deletion is later cancelled.
  static Future<void> closeProfile(SharedPreferences prefs, String profileId) {
    (_closed[prefs] ??= {}).add(profileId);
    final owner = _owners[prefs]?[profileId];
    if (owner == null) return Future<void>.value();
    owner._admitted = false;
    return owner._tail;
  }
}
