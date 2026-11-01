/// Linux › Services › Add a service: a command of your own. Name, command,
/// the folder it runs in, an optional port, and whether to bring it back
/// after a crash. Each field says what is wrong in words, beside it.
/// **Save and start** saves the definition and keeps it running.
///
/// Kit parts only.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../builtin/linux_services/linux_services.dart';
import '../../../l10n/app_localizations.dart';
import '../../kit/kit.dart';
import 'linux_manager_common.dart' show linuxRails;
import 'linux_services_common.dart';

/// Names the app keeps for itself (the reserved list of the definition
/// format): the server, the supervisor, jobs and the app's own helpers.
bool linuxServiceNameReserved(String name) =>
    const {'server', 'svc', 'linux-job', 'phone-engine'}.contains(name) ||
    name.startsWith('agent-') ||
    name.startsWith('aiteam') ||
    name.startsWith('oc-');

final _namePattern = RegExp(r'^[a-z][a-z0-9-]{1,23}$');

/// The port the OpenCode server owns on this phone.
const linuxServerPort = 4097;

class LinuxServiceAddPage extends ConsumerStatefulWidget {
  const LinuxServiceAddPage({super.key});

  @override
  ConsumerState<LinuxServiceAddPage> createState() => _State();
}

class _State extends ConsumerState<LinuxServiceAddPage> {
  final _name = TextEditingController();
  final _command = TextEditingController();
  final _dir = TextEditingController(text: '/root');
  final _port = TextEditingController();
  bool _restart = true;
  bool _saving = false;
  List<LinuxServiceState> _existing = const [];

  String? _nameError;
  String? _commandError;
  String? _dirError;
  String? _portError;
  Object? _failure;

  @override
  void initState() {
    super.initState();
    unawaited(_loadExisting());
  }

  @override
  void dispose() {
    _name.dispose();
    _command.dispose();
    _dir.dispose();
    _port.dispose();
    super.dispose();
  }

  /// The services that exist, so a name or a port that is taken is said
  /// before saving. Not being able to read them is fine: saving checks too.
  Future<void> _loadExisting() async {
    try {
      final list = await ref.read(linuxServicesProvider).read();
      if (mounted) _existing = list;
    } catch (_) {}
  }

  /// Checks every field. Returns the def when all are fine.
  LinuxServiceDef? _validate(AppLocalizations l10n) {
    final name = _name.text.trim();
    final command = _command.text.trim();
    final dir = _dir.text.trim();
    final portText = _port.text.trim();

    String? nameError;
    if (!_namePattern.hasMatch(name)) {
      nameError = l10n.linuxSvcErrNameShape;
    } else if (linuxServiceNameReserved(name)) {
      nameError = l10n.linuxSvcErrNameReserved;
    } else {
      for (final s in _existing) {
        if (s.def.name == name) {
          nameError = l10n.linuxSvcErrNameUsedBy(s.def.title);
        }
      }
    }
    final commandError = command.isEmpty ? l10n.linuxSvcErrCommand : null;
    final dirError = dir.startsWith('/') ? null : l10n.linuxSvcErrFolder;

    String? portError;
    int? port;
    if (portText.isNotEmpty) {
      port = int.tryParse(portText);
      if (port == null || port > 65535 || port < 1) {
        portError = l10n.linuxSvcErrPortRange;
      } else if (port < 1024) {
        portError = l10n.linuxSvcErrPortLow;
      } else if (port == linuxServerPort) {
        portError = l10n.linuxSvcErrPortServer('$port');
      } else {
        for (final s in _existing) {
          if (s.def.port == port) {
            portError = l10n.linuxSvcErrPortUsedBy('$port', s.def.title);
          }
        }
      }
    }

    setState(() {
      _nameError = nameError;
      _commandError = commandError;
      _dirError = dirError;
      _portError = portError;
    });
    if (nameError != null ||
        commandError != null ||
        dirError != null ||
        portError != null) {
      return null;
    }
    return LinuxServiceDef(
      name: name,
      title: name,
      command: command,
      dir: dir,
      port: port,
      restart: _restart ? LinuxRestart.onFailure : LinuxRestart.never,
    );
  }

  Future<void> _save() async {
    final l10n = AppLocalizations.of(context);
    final def = _validate(l10n);
    if (def == null) return;
    final api = ref.read(linuxServicesProvider);
    setState(() {
      _saving = true;
      _failure = null;
    });
    try {
      await api.save(def);
    } on LinuxServiceException catch (error) {
      if (!mounted) return;
      // The supervisor knows rules this page cannot: show its answer on the
      // field it is about.
      setState(() {
        _saving = false;
        switch (error.problem) {
          case LinuxServiceProblem.invalidName:
            _nameError = l10n.linuxSvcErrNameShape;
          case LinuxServiceProblem.nameTaken:
            _nameError = l10n.linuxSvcErrNameTaken;
          case LinuxServiceProblem.invalidCommand:
            _commandError = l10n.linuxSvcErrCommand;
          case LinuxServiceProblem.reservedPort:
            _portError = l10n.linuxSvcErrPortReserved;
          case LinuxServiceProblem.portTaken:
            _portError = l10n.linuxSvcErrPortTaken;
          default:
            _failure = error;
        }
      });
      return;
    } catch (error) {
      if (mounted) {
        setState(() {
          _saving = false;
          _failure = error;
        });
      }
      return;
    }
    Object? startProblem;
    try {
      await api.start(def.name);
    } catch (error) {
      startProblem = error;
    }
    if (!mounted) return;
    Navigator.of(context).pop(startProblem);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final gutter = EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter);
    final gap = SizedBox(height: tokens.space3);
    return KitScreen(
      topBar: KitTopBar(title: l10n.linuxSvcAddTitle),
      width: KitScreenWidth.reading,
      loading: _saving,
      loadingLabel: l10n.linuxSvcSaving,
      bottom: KitActionBlock(
        primary: KitAction(
          key: const ValueKey('linux-service-save'),
          label: l10n.linuxSvcSaveStart,
          onPressed: _saving ? null : () => unawaited(_save()),
        ),
      ),
      body: ListView(
        key: const ValueKey('linux-service-form'),
        padding: EdgeInsetsDirectional.only(
          top: tokens.space2,
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          if (_failure != null)
            Padding(
              padding: linuxRails(context),
              child: KitNotice.error(
                key: const ValueKey('linux-service-save-error'),
                message: linuxServiceProblemText(l10n, _failure),
                details: linuxServiceDetails(_failure),
              ),
            ),
          Padding(
            padding: gutter,
            child: KitField(
              fieldKey: const ValueKey('linux-service-name'),
              label: l10n.linuxSvcFieldName,
              controller: _name,
              kind: KitFieldKind.mono,
              maxLength: 24,
              helper: l10n.linuxSvcFieldNameHelper,
              error: _nameError,
              enabled: !_saving,
              disabledReason: l10n.linuxSvcSaving,
              onChanged: (_) {
                if (_nameError != null) setState(() => _nameError = null);
              },
            ),
          ),
          gap,
          Padding(
            padding: gutter,
            child: KitField(
              fieldKey: const ValueKey('linux-service-command'),
              label: l10n.linuxSvcFieldCommand,
              controller: _command,
              kind: KitFieldKind.mono,
              helper: l10n.linuxSvcFieldCommandHelper,
              error: _commandError,
              enabled: !_saving,
              disabledReason: l10n.linuxSvcSaving,
              onChanged: (_) {
                if (_commandError != null) {
                  setState(() => _commandError = null);
                }
              },
            ),
          ),
          gap,
          Padding(
            padding: gutter,
            child: KitField(
              fieldKey: const ValueKey('linux-service-dir'),
              label: l10n.linuxSvcFieldFolder,
              controller: _dir,
              kind: KitFieldKind.path,
              error: _dirError,
              enabled: !_saving,
              disabledReason: l10n.linuxSvcSaving,
              onChanged: (_) {
                if (_dirError != null) setState(() => _dirError = null);
              },
            ),
          ),
          gap,
          Padding(
            padding: gutter,
            child: KitField(
              fieldKey: const ValueKey('linux-service-port'),
              label: l10n.linuxSvcFieldPort,
              controller: _port,
              kind: KitFieldKind.number,
              maxLength: 5,
              helper: l10n.linuxSvcFieldPortHelper,
              error: _portError,
              optional: true,
              enabled: !_saving,
              disabledReason: l10n.linuxSvcSaving,
              onChanged: (_) {
                if (_portError != null) setState(() => _portError = null);
              },
            ),
          ),
          gap,
          KitRowGroup(
            children: [
              KitSwitchRow(
                switchKey: const ValueKey('linux-service-restart-switch'),
                title: l10n.linuxSvcRestartSwitch,
                supporting: l10n.linuxSvcRestartSwitchDetail,
                value: _restart,
                onChanged: _saving ? null : (v) => setState(() => _restart = v),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
