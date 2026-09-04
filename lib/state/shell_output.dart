import 'package:flutter/foundation.dart';

import '../domain/managed_shell.dart';

/// State for one visible output viewer. It owns no polling or persisted jobs;
/// the route decides when to refresh and discards this state on close.
class ShellOutputController extends ChangeNotifier {
  ShellOutputController({required this.gateway, required this.shell});
  ManagedShellGateway gateway;
  ManagedShell shell;
  static const maxCharacters = 128 * 1024;
  String text = '';
  int cursor = 0;
  int size = 0;
  bool trimmed = false;
  bool available = true;
  bool serverRestarted = false;
  bool refreshing = false;
  Object? error;
  String? _serverIdentity;
  bool _readIdentity = false;
  bool _disposed = false;
  int _generation = 0;
  bool get hasMore => cursor < size;

  void bind(ManagedShellGateway next) {
    if (identical(next, gateway)) return;
    gateway = next;
    _generation++;
    refreshing = false;
  }

  bool _current(int generation) => !_disposed && generation == _generation;

  Future<void> refresh({bool reconcileServer = false}) async {
    if (_disposed || refreshing) return;
    final generation = _generation;
    final repository = gateway;
    refreshing = true;
    notifyListeners();
    try {
      if (reconcileServer || !_readIdentity) {
        final identity = await repository.managedShellServerIdentity();
        if (!_current(generation)) return;
        if (_serverIdentity != null &&
            identity != null &&
            identity != _serverIdentity) {
          serverRestarted = true;
        }
        _serverIdentity = identity;
        _readIdentity = true;
      }
      final current = await repository.getManagedShell(shell.id);
      if (!_current(generation)) return;
      available = current != null;
      if (current == null) {
        error = null;
        return;
      }
      shell = current;
      var page = await repository.readManagedShellOutput(
        shell.id,
        cursor: cursor,
      );
      if (!_current(generation)) return;
      var reset = false;
      if (page.cursor < cursor || page.size < cursor) {
        // A reset/truncated capture must not duplicate the old output or keep
        // requesting an obsolete offset after reconnect.
        page = await repository.readManagedShellOutput(shell.id, cursor: 0);
        if (!_current(generation)) return;
        reset = true;
      }
      final baseCursor = reset ? 0 : cursor;
      if (page.cursor < baseCursor ||
          page.cursor > page.size ||
          (page.cursor == baseCursor && page.text.isNotEmpty)) {
        throw const FormatException('Invalid command output cursor');
      }
      if (reset) {
        text = '';
        trimmed = false;
      }
      if (page.cursor > baseCursor) text += page.text;
      cursor = page.cursor;
      size = page.size;
      if (text.length > maxCharacters) {
        var start = text.length - maxCharacters;
        // Avoid splitting a surrogate pair when bounding the rendered tail.
        if (text.codeUnitAt(start) >= 0xDC00 &&
            text.codeUnitAt(start) <= 0xDFFF) {
          start++;
        }
        text = text.substring(start);
        trimmed = true;
      }
      error = null;
    } catch (failure) {
      if (_current(generation)) error = failure;
    } finally {
      if (_current(generation)) {
        refreshing = false;
        notifyListeners();
      }
    }
  }

  String get displayText => text
      .replaceAll(RegExp(r'\x1B\][^\x07]*(?:\x07|\x1B\\)'), '')
      .replaceAll(RegExp(r'\x1B\[[0-?]*[ -/]*[@-~]'), '')
      .replaceAll('\r\n', '\n')
      .replaceAll('\r', '\n');

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
