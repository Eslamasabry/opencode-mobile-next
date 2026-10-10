import 'dart:async';

import 'package:flutter/services.dart' show PlatformException;

import '../api/mcp_oauth.dart' show McpOAuthCallbackException;
import '../api/opencode_api.dart' show ApiException;
import '../api/product_repository.dart' show ProductException;
import '../api2/transport.dart' show Api2Error, Api2NetworkError;
import '../builtin/builtin_folders.dart'
    show FolderListException, FolderListProblem;
import '../builtin/builtin_linux.dart' show BuiltinLinuxException;
import '../state/local_server_controls.dart' show LocalServerControlFailure;
import '../state/profiles.dart' show SecureStorageUnavailable;
import '../termux/bridge.dart' show TermuxBridgeException;
import '../termux/local_agent_runtime.dart' show LocalAgentFailure;

/// Protocol-neutral presentation contract. Only [authoredMessage] may be
/// displayed as prose. [technicalDetails] is untrusted and MUST be redacted
/// before placing it in a Details fold, copying it, or attaching it to a report.
final class ProductFailure {
  const ProductFailure._({
    required this.category,
    this.authoredMessage,
    this.statusCode,
    this.technicalDetails,
  });

  factory ProductFailure.from(Object error) {
    final category = _classify(error);
    return ProductFailure._(
      category: category,
      authoredMessage: category == ProductFailureCategory.words
          ? _authoredMessage(error)
          : null,
      statusCode: _statusOf(error),
      technicalDetails: _technicalDetails(error),
    );
  }

  final ProductFailureCategory category;
  final String? authoredMessage;
  final int? statusCode;
  final String? technicalDetails;
}

// These local failures carry app-authored copy. Protocol error messages are
// deliberately absent: neither prose shape nor a server error tag makes a
// message safe to display.
String? _authoredMessage(Object error) => switch (error) {
  ProductException(:final message) => message,
  McpOAuthCallbackException(:final message) => message,
  SecureStorageUnavailable(:final message) => message,
  BuiltinLinuxException(:final message) => message.trim(),
  TermuxBridgeException(:final message) => message.trim(),
  LocalAgentFailure(:final message) => message.trim(),
  LocalServerControlFailure(:final message) => message.trim(),
  final String text => text,
  _ => null,
};

/// What kind of failure a thrown object (or a raw failure string) is, for
/// the presentation layer localizes. Never shown itself.
enum ProductFailureCategory {
  /// App-authored words for people: shown as they are.
  words,
  stagedRevert,
  folderNotInstalled,
  folderMissing,
  folderDenied,
  folderLinked,
  network,
  timedOut,
  certificate,
  signIn,
  notFound,
  conflict,

  /// The computer running the agent did not answer or did not do it
  /// (Paseo daemon): not a clash, not an OpenCode server fault.
  computer,
  busy,
  server,
  rejected,
  unexpected,
  device,
  storage,

  /// Termux did not finish a command the app sent it.
  termux,

  /// Nothing known about it: the generic connectivity line.
  unknown,
}

/// A raw failure string that is transport or exception text, not words for
/// people: an exception class name, an HTTP status, an OS error, a body.
final RegExp _technical = RegExp(
  r'\b[A-Z][A-Za-z0-9]*(Exception|Error)\b'
  r'|^\s*(Exception|Error|Bad state|Invalid argument)\b'
  r'|\bHTTP\s*[1-5]\d\d\b'
  r'|\b(status|answered|returned|responded|code)\D{0,12}\b[1-5]\d\d\b'
  r'|\bOS Error\b|\berrno\b|\bstack ?trace\b|^\s*#\d+\s'
  r'|^\s*[<{\[]'
  r'|\bCannot reach\b.*:',
  caseSensitive: false,
);

final RegExp _httpStatus = RegExp(
  r'\b(?:HTTP|status(?: code)?|answered|returned|responded)\D{0,12}\b([1-5]\d\d)\b',
  caseSensitive: false,
);

/// The kind of failure a status code says.
ProductFailureCategory _forStatus(int code) => switch (code) {
  401 || 403 => ProductFailureCategory.signIn,
  404 || 410 => ProductFailureCategory.notFound,
  408 => ProductFailureCategory.timedOut,
  409 => ProductFailureCategory.conflict,
  429 => ProductFailureCategory.busy,
  >= 500 => ProductFailureCategory.server,
  >= 400 => ProductFailureCategory.rejected,
  _ => ProductFailureCategory.unexpected,
};

/// The kind of failure raw transport or exception text describes, or null
/// when it names none of the known kinds.
ProductFailureCategory? _forText(String text) {
  final lower = text.toLowerCase();
  if (lower.contains('certificate') ||
      lower.contains('handshake') ||
      lower.contains('tls') && lower.contains('fail')) {
    return ProductFailureCategory.certificate;
  }
  if (lower.contains('timed out') ||
      lower.contains('timeout') ||
      lower.contains('took too long')) {
    return ProductFailureCategory.timedOut;
  }
  if (lower.contains('connection refused') ||
      lower.contains('unreachable') ||
      lower.contains('no route') ||
      lower.contains('host name not found') ||
      lower.contains('failed host lookup') ||
      lower.contains('name or service not known') ||
      lower.contains('connection dropped') ||
      lower.contains('connection reset') ||
      lower.contains('reset by peer') ||
      lower.contains('broken pipe') ||
      lower.contains('connection closed') ||
      lower.contains('no response') ||
      lower.contains('socketexception') ||
      lower.contains('clientexception') ||
      lower.contains('websocket') ||
      lower.contains('network') ||
      lower.contains('cannot reach')) {
    return ProductFailureCategory.network;
  }
  if (lower.contains('filesystemexception') ||
      lower.contains('pathnotfound') ||
      lower.contains('no space left') ||
      lower.contains('read-only file system')) {
    return ProductFailureCategory.storage;
  }
  if (lower.contains('platformexception') ||
      lower.contains('missingpluginexception')) {
    return ProductFailureCategory.device;
  }
  final status = _httpStatus.firstMatch(text);
  if (status != null) return _forStatus(int.parse(status.group(1)!));
  return null;
}

/// The IO types, matched by name so this file imports no `dart:io`.
const Set<String> _storageTypes = {
  'FileSystemException',
  'PathNotFoundException',
  'PathAccessException',
  'PathExistsException',
};

/// Codes whose [BuiltinLinuxException] message the app wrote for people.
const Set<String> _builtinWordCodes = {
  'unsupported_platform',
  'missing_plugin',
  'storage_unavailable',
  'removal_failed',
  'confirmation_required',
  'team_state_unavailable',
};

/// One short sentence for people, not native text or command output (a
/// Java message, a script's last lines, an exit code, a class name).
bool _isSentence(String message) =>
    message.isNotEmpty &&
    message.length <= 200 &&
    !message.contains('\n') &&
    RegExp(r'[.?!]$').hasMatch(message) &&
    !_technical.hasMatch(message) &&
    !RegExp(r'\b(exit|error)\s+-?\d+\b').hasMatch(message);

/// A built-in Linux failure carries the app's sentence, or native text and
/// command output.
ProductFailureCategory _builtin(BuiltinLinuxException error) {
  final message = error.message.trim();
  if (message.isEmpty) return ProductFailureCategory.device;
  if (_builtinWordCodes.contains(error.code)) {
    return ProductFailureCategory.words;
  }
  if (error.code == null && _isSentence(message)) {
    return ProductFailureCategory.words;
  }
  return _forText(message) ?? ProductFailureCategory.device;
}

/// A Termux bridge failure: the app's sentence (a setup check, a folder
/// name), or Termux's own output and native text.
ProductFailureCategory _termux(TermuxBridgeException error) {
  final message = error.message.trim();
  if (error.code != 'command_failed' && _isSentence(message)) {
    return ProductFailureCategory.words;
  }
  return _forText(message) ?? ProductFailureCategory.termux;
}

ProductFailureCategory _classify(Object error) {
  if (error is BuiltinLinuxException) return _builtin(error);
  if (error is TermuxBridgeException) return _termux(error);
  if (error is LocalServerControlFailure) {
    final message = error.message.trim();
    if (message.isEmpty) return ProductFailureCategory.termux;
    if (_isSentence(message)) return ProductFailureCategory.words;
    return _forText(message) ?? ProductFailureCategory.termux;
  }
  if (error is LocalAgentFailure) {
    final message = error.message.trim();
    if (_isSentence(message)) return ProductFailureCategory.words;
    return _forText(message) ?? ProductFailureCategory.termux;
  }
  if (error is FolderListException) {
    return switch (error.problem) {
      FolderListProblem.timedOut => ProductFailureCategory.timedOut,
      FolderListProblem.invalid ||
      FolderListProblem.failed => ProductFailureCategory.storage,
      FolderListProblem.notInstalled =>
        ProductFailureCategory.folderNotInstalled,
      FolderListProblem.missing => ProductFailureCategory.folderMissing,
      FolderListProblem.denied => ProductFailureCategory.folderDenied,
      FolderListProblem.linked => ProductFailureCategory.folderLinked,
    };
  }
  if (error is ProductException ||
      error is McpOAuthCallbackException ||
      error is SecureStorageUnavailable) {
    return ProductFailureCategory.words;
  }
  if (error is ApiException) {
    // The app's own sentence for a staged revert (connection, gateway).
    if (error.errorTag == 'SessionRevertPending') {
      return ProductFailureCategory.stagedRevert;
    }
    final code = error.statusCode;
    if (code != null && code >= 400) return _forStatus(code);
    if (const {
      'Paseounavailable',
      'Paseodisconnected',
      'PaseoinvalidResponse',
      'PaseohostRefused',
      'PaseoinvalidEndpoint',
    }.contains(error.errorTag)) {
      return ProductFailureCategory.computer;
    }
    return _forText(error.message) ?? ProductFailureCategory.unexpected;
  }
  if (error is Api2NetworkError) {
    return error.timedOut
        ? ProductFailureCategory.timedOut
        : ProductFailureCategory.network;
  }
  if (error is Api2Error) {
    final code = error.statusCode;
    if (code != null && code >= 400) return _forStatus(code);
    return _forText(error.message) ?? ProductFailureCategory.unexpected;
  }
  if (error is PlatformException) return ProductFailureCategory.device;
  if (error is TimeoutException) return ProductFailureCategory.timedOut;
  final type = error.runtimeType.toString();
  if (_storageTypes.contains(type)) return ProductFailureCategory.storage;
  if (type == 'HandshakeException' || type == 'TlsException') {
    return ProductFailureCategory.certificate;
  }
  if (const {
    'SocketException',
    'HttpException',
    'ClientException',
    'WebSocketException',
    'WebSocketChannelException',
  }.contains(type)) {
    return ProductFailureCategory.network;
  }
  if (error is String) {
    if (error.trim().isEmpty) return ProductFailureCategory.unknown;
    if (!_technical.hasMatch(error)) return ProductFailureCategory.words;
    return _forText(error) ?? ProductFailureCategory.unknown;
  }
  return ProductFailureCategory.unknown;
}

int? _statusOf(Object error) => switch (error) {
  ApiException(:final statusCode) => statusCode,
  Api2Error(:final statusCode) => statusCode,
  final String text => int.tryParse(
    _httpStatus.firstMatch(text)?.group(1) ?? '',
  ),
  _ => null,
};

String? _technicalDetails(Object? error) {
  final String raw;
  switch (error) {
    case null:
      return null;
    case ProductException(:final cause):
      if (cause == null) return null;
      raw = '$cause';
    case SecureStorageUnavailable(:final cause):
      if (cause == null) return null;
      raw = '$cause';
    case McpOAuthCallbackException():
      return null;
    case BuiltinLinuxException(:final message, :final code):
      if (_builtin(error) == ProductFailureCategory.words) return null;
      raw = [message, ?code].join('\n');
    case TermuxBridgeException(:final message, :final code):
      if (_termux(error) == ProductFailureCategory.words) return null;
      raw = '$message\n$code';
    case LocalServerControlFailure(:final message):
      if (_classify(error) == ProductFailureCategory.words) return null;
      raw = message;
    case LocalAgentFailure(:final message, :final kind):
      if (_classify(error) == ProductFailureCategory.words) return null;
      raw = '$message\n${kind.name}';
    case FolderListException(:final problem, :final detail):
      raw = detail.isEmpty ? problem.name : detail;
    case ApiException(:final message, :final statusCode, :final errorTag):
      raw = [
        message,
        if (statusCode != null && !message.contains('$statusCode'))
          'HTTP $statusCode',
        ?errorTag,
      ].join('\n');
    case PlatformException(:final code, :final message, :final details):
      raw = [
        'PlatformException($code)',
        ?message,
        if (details != null) '$details',
      ].join('\n');
    case final String text:
      if (_classify(text) == ProductFailureCategory.words) return null;
      raw = text;
    default:
      raw = '${error.runtimeType}: $error';
  }
  final trimmed = raw.trim();
  return trimmed.isEmpty ? null : trimmed;
}
