import 'dart:math';

/// Receipt means admission, never completion of the assistant's work.
enum CommandReceiptState { sent, uncertain, confirmed, failed }

class CommandReceiptException implements Exception {
  const CommandReceiptException();
  @override
  String toString() => 'Could not safely record or check this request.';
}

/// Metadata only: never persist prompt bodies, credentials or raw errors.
class CommandReceipt {
  const CommandReceipt({
    required this.commandID,
    required this.receiptID,
    required this.sessionID,
    required this.tabID,
    required this.scope,
    required this.createdAt,
    required this.state,
  });
  final String commandID, receiptID, sessionID, tabID, scope;
  final int createdAt;
  final CommandReceiptState state;

  CommandReceipt copyWith({CommandReceiptState? state}) => CommandReceipt(
    commandID: commandID,
    receiptID: receiptID,
    sessionID: sessionID,
    tabID: tabID,
    scope: scope,
    createdAt: createdAt,
    state: state ?? this.state,
  );

  Map<String, Object> toJson() => {
    'commandID': commandID,
    'receiptID': receiptID,
    'sessionID': sessionID,
    'tabID': tabID,
    'scope': scope,
    'createdAt': createdAt,
    'state': state.name,
  };

  factory CommandReceipt.fromJson(Map<String, dynamic> json) {
    if (json.length != 7 ||
        json['createdAt'] is! int ||
        (json['createdAt'] as int) < 0) {
      throw const CommandReceiptException();
    }
    String opaque(String key) {
      final value = json[key];
      if (value is! String ||
          RegExp(r'^[a-zA-Z0-9_:-]{1,256}$').firstMatch(value)?.end !=
              value.length) {
        throw const CommandReceiptException();
      }
      return value;
    }

    final scope = json['scope'];
    if (scope is! String ||
        RegExp(r'^[a-f0-9]{64}$').firstMatch(scope)?.end != scope.length) {
      throw const CommandReceiptException();
    }
    final state = CommandReceiptState.values.where(
      (s) => s.name == json['state'],
    );
    if (state.length != 1) throw const CommandReceiptException();
    return CommandReceipt(
      commandID: opaque('commandID'),
      receiptID: opaque('receiptID'),
      sessionID: opaque('sessionID'),
      tabID: opaque('tabID'),
      scope: scope,
      createdAt: json['createdAt'] as int,
      state: state.single,
    );
  }
}

abstract interface class CommandReceiptJournal {
  List<CommandReceipt> read();
  Future<bool> claim(CommandReceipt receipt);
  Future<void> update(CommandReceipt receipt);
}

/// At most one dispatch per durable command ID. Retry only reads the server.
class CommandReceiptController {
  const CommandReceiptController(this.journal);
  final CommandReceiptJournal journal;

  Future<CommandReceipt> send(
    CommandReceipt receipt,
    Future<void> Function(String receiptID) dispatch,
  ) async {
    if (receipt.state != CommandReceiptState.sent) {
      throw const CommandReceiptException();
    }
    if (!await journal.claim(receipt)) {
      final old = journal.read().singleWhere(
        (r) => r.commandID == receipt.commandID,
      );
      if (old.scope != receipt.scope ||
          old.sessionID != receipt.sessionID ||
          old.tabID != receipt.tabID) {
        throw const CommandReceiptException();
      }
      return old;
    }
    CommandReceipt outcome;
    try {
      await dispatch(receipt.receiptID);
      outcome = receipt.copyWith(state: CommandReceiptState.confirmed);
    } catch (_) {
      // No transport status proves a mutation did not happen.
      outcome = receipt.copyWith(state: CommandReceiptState.uncertain);
    }
    await journal.update(outcome);
    return outcome;
  }

  Future<CommandReceipt> retry(
    String commandID,
    Future<bool> Function(CommandReceipt receipt) lookup,
  ) async {
    final receipt = journal.read().singleWhere((r) => r.commandID == commandID);
    if (receipt.state == CommandReceiptState.confirmed ||
        receipt.state == CommandReceiptState.failed) {
      return receipt;
    }
    var found = false;
    try {
      found = await lookup(receipt);
    } catch (_) {
      /* Still unknown. */
    }
    final outcome = receipt.copyWith(
      state: found
          ? CommandReceiptState.confirmed
          : CommandReceiptState.uncertain,
    );
    await journal.update(outcome);
    return outcome;
  }
}

final _receiptRandom = Random.secure();
String createCommandReceiptID({bool session = false}) {
  var timestamp =
      (DateTime.now().millisecondsSinceEpoch * 4096) & 0xffffffffffff;
  if (session) timestamp = 0xffffffffffff ^ timestamp;
  final time = timestamp.toRadixString(16).padLeft(12, '0');
  const alphabet =
      '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';
  return '${session ? 'ses' : 'msg'}_$time${List.generate(14, (_) => alphabet[_receiptRandom.nextInt(62)]).join()}';
}
