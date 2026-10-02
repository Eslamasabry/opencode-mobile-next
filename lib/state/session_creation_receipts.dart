import 'dart:convert';
import 'package:crypto/crypto.dart';

import '../api/command_receipt_transport.dart';
import '../domain/command_receipts.dart';
import '../domain/server_gateway.dart';
import 'pending_command_journal.dart';
import 'profiles.dart';

/// Optional backend seam for the session-create owner. No implicit retry,
/// selected-tab mutation, or new server transport. The caller retains commandID.
class SessionCreationReceipts {
  SessionCreationReceipts({
    required this.store,
    required this.gateway,
    required this.profileID,
  }) : _transport = CommandReceiptTransport(gateway) {
    _scope = _currentScope();
  }

  final ProfileStore store;
  final ServerGateway gateway;
  final String profileID;
  final CommandReceiptTransport _transport;
  late final String _scope;

  bool get supported => _transport.supportsSessionCreation;

  String _currentScope() {
    final profile = store.profiles.where((p) => p.id == profileID);
    if (profile.length != 1) throw const CommandReceiptException();
    if (!_transport.matchesEndpoint(profile.single.baseUrl)) {
      throw const CommandReceiptException();
    }
    return sha256
        .convert(
          utf8.encode(
            jsonEncode([
              profileID,
              profile.single.baseUrl,
              gateway.directory,
              gateway.workspace,
            ]),
          ),
        )
        .toString();
  }

  bool get _bindingMatches {
    try {
      return supported && !gateway.isClosed && _currentScope() == _scope;
    } catch (_) {
      return false;
    }
  }

  void _checkBinding() {
    if (!supported || gateway.isClosed || _currentScope() != _scope) {
      throw const CommandReceiptException();
    }
  }

  Future<CommandReceipt> send({
    required String commandID,
    required String tabID,
  }) async {
    _checkBinding();
    final journal = PendingCommandJournal.forProfile(store.prefs, profileID);
    final key = 'create:$commandID';
    final previous = journal.read().where((r) => r.commandID == key);
    final id = previous.isEmpty
        ? createCommandReceiptID(session: true)
        : previous.single.receiptID;
    return CommandReceiptController(journal).send(
      CommandReceipt(
        commandID: key,
        receiptID: id,
        sessionID: id,
        tabID: tabID,
        scope: _scope,
        createdAt: DateTime.now().millisecondsSinceEpoch,
        state: CommandReceiptState.sent,
      ),
      (id) async {
        _checkBinding();
        journal.read(); // Deletion closes admission before any new dispatch.
        await _transport.createSession(id);
      },
    );
  }

  /// Returns the confirmed session for the original tab to open. Never creates
  /// a replacement. Unknown/removed/location-changed sessions remain null.
  Future<Session?> check(String commandID) async {
    try {
      if (!_bindingMatches) return null;
      final journal = PendingCommandJournal.forProfile(store.prefs, profileID);
      final key = 'create:$commandID';
      final receipt = journal.read().singleWhere((r) => r.commandID == key);
      if (receipt.scope != _scope) return null;
      final checked = await CommandReceiptController(journal).retry(key, (
        r,
      ) async {
        final found = await _transport.lookupSession(r.sessionID);
        _checkBinding();
        return found;
      });
      if (!_bindingMatches) return null;
      if (checked.state != CommandReceiptState.confirmed) return null;
      final session = await gateway.session(checked.sessionID);
      if (!_bindingMatches) return null;
      return session.id == checked.sessionID ? session : null;
    } on CommandReceiptException {
      rethrow;
    } catch (_) {
      return null;
    }
  }
}
