import 'dart:collection';
import 'dart:convert';

import 'package:crypto/crypto.dart';

import '../../api/models.dart';
import '../agent_tools/agent_tool_adapter.dart';
import '../server_gateway.dart' show ProductException;
import 'gen_ui_answers.dart';
import 'gen_ui_asks.dart';
import 'gen_ui_nodes.dart';
import 'gen_ui_types.dart';

part 'gen_ui_validation.dart';
part 'gen_ui_mapping.dart';

/// The caller supplies the assistant-owned transport part and trusted scope.
/// Missing/partial tool input remains an ordinary tool until reconciliation.
GenUiParse? genUiFromPart(
  Part part, {
  required GenUiScope scope,
  required String sessionID,
  required String messageID,
}) {
  if (part.type != 'tool' ||
      part.synthetic ||
      !AgentToolAdapters.cardShowNames.contains(part.toolName) ||
      part.toolState.status != 'completed' ||
      !part.toolState.executed ||
      part.toolState.input.isEmpty ||
      part.callID == null ||
      part.callID!.isEmpty ||
      messageID.isEmpty ||
      sessionID.isEmpty ||
      scope.profileID.isEmpty ||
      scope.sourceId.isEmpty ||
      scope.directory.isEmpty ||
      (part.messageID != null && part.messageID != messageID)) {
    return null;
  }
  try {
    final normalized = _normalize(part.toolState.input);
    return GenUiParsed(
      GenUiCard(
        scope: scope,
        id: normalized['id'] as String,
        title: normalized['title'] as String,
        sessionID: sessionID,
        callID: part.callID!,
        messageID: messageID,
        revision: sha256
            .convert(utf8.encode(jsonEncode(normalized)))
            .toString(),
        body: (normalized['body'] as List)
            .map((n) => _toNode(n as Map<String, dynamic>))
            .toList(),
        ask: normalized['ask'] == null
            ? null
            : _toAsk(normalized['ask'] as Map<String, dynamic>),
      ),
    );
  } on _Invalid catch (failure) {
    return GenUiUnreadable(reason: failure.reason);
  } catch (_) {
    return const GenUiUnreadable(reason: GenUiProblem.badValue);
  }
}

/// [transcript] must be the scoped, chronological authoritative projection,
/// excluding optimistic rows and any staged-revert suffix.
GenUiCardState genUiStateFor(
  GenUiCard card,
  List<MessageWithParts> transcript, {
  required bool tailComplete,
}) {
  var found = false;
  var superseded = false;
  final seenMessages = <String>{};
  for (final message in transcript) {
    if (message.info.sessionID != card.sessionID ||
        message.info.id.isEmpty ||
        !seenMessages.add(message.info.id)) {
      return GenUiCardState.unknown;
    }
    if (found && _authoritativeUser(message)) {
      // A later running turn can make the tail stale without undoing the
      // delivered answer already present in this authoritative transcript.
      if (!superseded && genUiAnswerIn(message, card) != null) {
        return GenUiCardState.answered;
      }
      if (!tailComplete) return GenUiCardState.unknown;
      return card.ask == null
          ? GenUiCardState.report
          : GenUiCardState.passedOver;
    }
    if (message.info.role != 'assistant') continue;
    for (final part in message.parts) {
      final parsed = genUiFromPart(
        part,
        scope: card.scope,
        sessionID: card.sessionID,
        messageID: message.info.id,
      );
      if (parsed is! GenUiParsed) continue;
      final candidate = parsed.card;
      if (candidate.identity == card.identity) {
        if (found || candidate.revision != card.revision) {
          return GenUiCardState.unknown;
        }
        found = true;
      } else if (found && candidate.ask != null) {
        superseded = true;
      }
    }
  }
  if (!found || !tailComplete) return GenUiCardState.unknown;
  if (card.ask == null) return GenUiCardState.report;
  return superseded ? GenUiCardState.passedOver : GenUiCardState.waiting;
}

bool _authoritativeUser(MessageWithParts message) =>
    message.info.role == 'user' &&
    message.info.id.isNotEmpty &&
    message.info.sessionID.isNotEmpty &&
    message.parts.isNotEmpty &&
    message.parts.any(
      (p) => !p.synthetic && (p.type == 'text' || p.type == 'file'),
    ) &&
    !message.parts.any((p) => p.synthetic);

({String summary, Map<String, dynamic> envelope})? _receipt(
  MessageWithParts message,
) {
  if (!_authoritativeUser(message)) return null;
  final text = message.parts
      .where((p) => p.type == 'text')
      .map((p) => p.text)
      .join('\n');
  if (text.length > 34000) return null;
  try {
    final lines = text.split('\n');
    if (lines.length != 2) return null;
    final match = RegExp(
      r'^\[oc-ui answer ([a-z0-9-]{1,48})\] (.+)$',
    ).firstMatch(lines[0]);
    if (match == null) return null;
    final summary = match.group(2)!;
    if (_scalars(summary) > 240 ||
        summary.contains('\t') ||
        _text(summary) != summary) {
      return null;
    }
    if (utf8.encode(lines[1]).length > 32768) return null;
    final decoded = jsonDecode(lines[1]);
    _bounded(decoded);
    final envelope = _obj(
      decoded,
      ['v', 'cardId', 'callId', 'value'],
      ['v', 'cardId', 'callId', 'value'],
    );
    if (envelope['v'] != 1 ||
        envelope['v'] is! num ||
        envelope['cardId'] != match.group(1) ||
        envelope['callId'] is! String ||
        (envelope['callId'] as String).isEmpty) {
      return null;
    }
    _structuralAnswer(envelope['value']);
    return (summary: summary, envelope: envelope);
  } catch (_) {
    return null;
  }
}

({String summary, Object? value})? genUiAnswerIn(
  MessageWithParts message,
  GenUiCard card,
) {
  if (message.info.sessionID != card.sessionID ||
      message.info.id == card.messageID ||
      card.ask == null) {
    return null;
  }
  final receipt = _receipt(message);
  if (receipt == null ||
      receipt.envelope['cardId'] != card.id ||
      receipt.envelope['callId'] != card.callID) {
    return null;
  }
  try {
    final value = _validatedAnswer(card.ask!, receipt.envelope['value']);
    return (summary: receipt.summary, value: value);
  } catch (_) {
    return null;
  }
}

String genUiAnswerText(GenUiCard card, GenUiAnswer answer) {
  try {
    if (card.ask == null ||
        card.callID.isEmpty ||
        card.messageID.isEmpty ||
        card.sessionID.isEmpty ||
        card.revision.isEmpty ||
        card.scope.profileID.isEmpty ||
        card.scope.sourceId.isEmpty ||
        card.scope.directory.isEmpty) {
      _fail();
    }
    _id(card.id, r'^[a-z0-9-]{1,48}$');
    final raw = switch (answer) {
      GenUiChoiceAnswer(:final ids) => <String, dynamic>{'choice': ids},
      GenUiFormAnswer(:final values) => <String, dynamic>{'form': values},
      GenUiConfirmAnswer(:final value) => <String, dynamic>{'confirm': value},
      GenUiPhotoAnswer(:final count) => <String, dynamic>{'photo': count},
    };
    _bounded(raw);
    final value = _validatedAnswer(card.ask!, raw);
    final summary = _answerSummary(card.ask!, value);
    final envelope = {
      'v': 1,
      'cardId': card.id,
      'callId': card.callID,
      'value': value,
    };
    _bounded(envelope);
    return '[oc-ui answer ${card.id}] $summary\n${jsonEncode(envelope)}';
  } catch (_) {
    throw const ProductException('This card answer is invalid.');
  }
}

/// Structural display recognition only. Settlement still requires the original
/// card, matching call, validated answer, and authoritative chronological tail.
({String cardId, String summary})? genUiAnswerEnvelope(
  MessageWithParts message,
) {
  final receipt = _receipt(message);
  return receipt == null
      ? null
      : (
          cardId: receipt.envelope['cardId'] as String,
          summary: receipt.summary,
        );
}

void _structuralAnswer(Object? raw) {
  final o = _obj(raw, ['choice', 'form', 'confirm', 'photo']);
  if (o.length != 1) _fail();
  if (o.containsKey('choice')) {
    final ids = _arr(
      o['choice'],
      1,
      8,
    ).map((v) => _id(v, r'^[a-zA-Z0-9_\-]{1,32}$')).toList();
    _unique(ids);
  } else if (o.containsKey('form')) {
    if (o['form'] is! Map) _fail();
    final form = o['form'] as Map;
    if (form.length > 12) _fail();
    for (final entry in form.entries) {
      _id(entry.key, r'^[a-zA-Z0-9_]{1,32}$');
      final value = entry.value;
      if (value is String) {
        if (_scalars(value) > 2000) _fail();
      } else if (value is num) {
        _num(value);
      } else if (value is! bool) {
        _fail();
      }
    }
  } else if (o.containsKey('confirm')) {
    _bool(o['confirm']);
  } else {
    _int(o['photo'], 1, 4);
  }
}

Map<String, dynamic> _validatedAnswer(GenUiAsk ask, Object? raw) {
  _structuralAnswer(raw);
  final value = raw as Map;
  switch (ask) {
    case GenUiChoiceAsk(:final options, :final multi):
      final ids = _arr(
        value['choice'],
        1,
        multi ? options.length : 1,
      ).map((v) => v as String).toList();
      if (ids.any((id) => !options.any((o) => o.id == id))) _fail();
      return {'choice': ids};
    case GenUiFormAsk(:final fields):
      final form = _obj(value['form'], fields.map((f) => f.id).toList());
      final result = <String, Object>{};
      for (final f in fields) {
        if (!form.containsKey(f.id)) {
          if (f.required) _fail();
          continue;
        }
        result[f.id] = _fieldValue(form[f.id], {
          'type': f.type.name,
          'required': f.required,
          if (f.min != null) 'min': f.min,
          if (f.max != null) 'max': f.max,
          'options': f.options.map((o) => {'id': o.id}).toList(),
        });
      }
      return {'form': result};
    case GenUiConfirmAsk():
      return {'confirm': _bool(value['confirm'])};
    case GenUiPhotoAsk(:final max):
      return {'photo': _int(value['photo'], 1, max)};
  }
}

String _answerSummary(GenUiAsk ask, Map<String, dynamic> value) {
  final raw = switch (ask) {
    GenUiChoiceAsk(:final options) =>
      (value['choice'] as List)
          .map((id) => options.firstWhere((o) => o.id == id).label)
          .join(', '),
    GenUiFormAsk() => 'Submitted ${(value['form'] as Map).length} fields',
    GenUiConfirmAsk(:final confirmLabel, :final cancelLabel) =>
      value['confirm'] == true
          ? (confirmLabel ?? 'Confirmed')
          : (cancelLabel ?? 'Declined'),
    GenUiPhotoAsk() =>
      'Submitted ${value['photo']} photo${value['photo'] == 1 ? '' : 's'}',
  };
  final clean = raw
      .replaceAll(_controls, '')
      .replaceAll(RegExp(r'\s+'), ' ')
      .trim();
  return String.fromCharCodes(clean.runes.take(240));
}
