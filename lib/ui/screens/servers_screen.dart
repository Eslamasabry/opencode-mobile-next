import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/product_repository.dart' show ProductException;
import '../../api/server_probe.dart';
import '../../builtin/builtin_server.dart' show looksLikeInAppServer;
import '../../feedback/bug_report.dart' show openBugReport;
import '../../domain/profile_monitor.dart' show ProfileAttentionSnapshot;
import '../../l10n/app_localizations.dart';
import '../widgets/setup_ui_messages.dart';
import '../../platform/connection_advice.dart';
import '../../platform/platform_capabilities.dart';
import '../../state/connection.dart';
import '../../state/codex_connection_probe.dart';
import '../../state/paseo_connection_probe.dart';
import '../../state/pairing.dart';
import '../../state/phone_host.dart' show PhoneHostKind;
import '../../state/profiles.dart';
import '../../state/queued_prompt_removal.dart';
import '../../state/first_run.dart';
import '../../termux/bridge.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import '../kit/scenes/servers_link_scene.dart';
import '../kit/scenes/servers_welcome_scene.dart';
import '../setup_commands.dart';
import '../widgets/product_states.dart'
    show productErrorDetails, productErrorText;
import '../widgets/team_host_form.dart';
import '../widgets/local_agent_server_entry.dart';
import '../widgets/phone_server_card.dart';
import '../widgets/queued_prompt_move_sheet.dart';
import '../widgets/termux_migration_entry.dart';
import '../../state/termux_running_server.dart';
import '../widgets/termux_running_server_entry.dart';
import '../widgets/safety_confirms.dart';
import '../../state/local_server_controls.dart';
import 'phone_setup/phone_setup_routes.dart';
import 'phone_setup/phone_setup_welcome_entry.dart';
import 'demo_screen.dart';
import 'guide_screen.dart' show GuideScreen;
import 'agent_account_screen.dart';
import 'pairing_scanner_screen.dart';
import 'tailscale_setup_screen.dart';
import 'this_phone_screen.dart' show openThisPhone;
import '../../state/tailscale_address.dart';
import 'capabilities_screen.dart' show openExternalAgents;

part 'servers/servers_state.dart';
part 'servers/servers_actions.dart';
part 'servers/server_rows.dart';
part 'servers/profile_editor.dart';
part 'servers/profile_editor_probe.dart';
part 'servers/profile_editor_pairing.dart';
part 'servers/profile_editor_fields.dart';
part 'servers/profile_editor_steps.dart';
part 'servers/profile_editor_widgets.dart';

/// What the servers list learns back from the editor's save: whether the
/// profile reached the store, and the product-facing failure to show inline
/// when connecting (or saving) did not work out.
/// A save's outcome: [failure] in plain words when it did not finish, and
/// [details], the redacted technical text for the Details fold under it.
typedef _SubmitOutcome = ({bool saved, String? failure, String? details});

/// Checks a Paseo daemon or a Codex app-server, the two socket backends.
typedef SocketAgentProbe =
    Future<CodexConnectionProbeResult> Function({
      required ServerBackend backend,
      required String baseUrl,
      required String secret,
      required String directory,
    });

Future<CodexConnectionProbeResult> _probeSocketAgent({
  required ServerBackend backend,
  required String baseUrl,
  required String secret,
  required String directory,
}) => backend == ServerBackend.paseo
    ? probePaseoConnection(
        baseUrl: baseUrl,
        password: secret,
        directory: directory,
      )
    : probeCodexConnection(
        baseUrl: baseUrl,
        token: secret,
        directory: directory,
      );

/// The socket-backend counterpart of [serverProbe]: replaceable so a widget
/// test can answer the connect screen's test without opening a socket.
@visibleForTesting
SocketAgentProbe socketAgentProbe = _probeSocketAgent;

/// How long the person must stop typing before the first-run connect screen
/// tests by itself. Long enough that an address is not probed half-typed,
/// short enough that the verdict is there when they look up.
@visibleForTesting
const autoTestPause = Duration(milliseconds: 800);

AppLocalizations _connectionL10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// What another surface asks the Servers screen to do as it opens, passed as
/// the `/servers` route argument.
///
/// The server switcher in the shell lists the same servers, but connecting,
/// credentials, adding and forgetting each have one implementation, here:
/// the runtime-choice detour, the editor that stays open until a connect
/// succeeds, and the inline failure card. The switcher hands the choice over
/// instead of keeping a second, thinner copy of that flow.
class ServersRouteRequest {
  /// Connect [profileID] through the list's connect flow. [detectedRunning]
  /// is the phone card's promise that this runtime is the live one.
  const ServersRouteRequest.connect(
    String this.profileID, {
    this.detectedRunning = false,
  }) : kind = ServersRouteRequestKind.connect,
       backend = null,
       initialUrl = null,
       openCode2 = false;

  /// Open a new server editor, optionally starting with the requested kind
  /// or, for a conversation link that carries one, the validated address
  /// ([initialUrl], never credentials). This is an editable choice, never
  /// permission to connect or save.
  const ServersRouteRequest.add({this.backend, this.initialUrl})
    : kind = ServersRouteRequestKind.add,
      profileID = null,
      detectedRunning = false,
      openCode2 = false;

  /// Ask for the phone server's sign-in, saved as [profileID] when it exists.
  const ServersRouteRequest.enterPhoneCredentials({
    this.profileID,
    required this.openCode2,
  }) : kind = ServersRouteRequestKind.enterPhoneCredentials,
       backend = null,
       initialUrl = null,
       detectedRunning = true;

  /// Confirm and forget the saved server [profileID].
  const ServersRouteRequest.forget(String this.profileID)
    : kind = ServersRouteRequestKind.forget,
      backend = null,
      initialUrl = null,
      detectedRunning = false,
      openCode2 = false;

  final ServersRouteRequestKind kind;
  final ServerBackend? backend;
  final String? initialUrl;
  final String? profileID;
  final bool detectedRunning;
  final bool openCode2;
}

enum ServersRouteRequestKind { connect, add, enterPhoneCredentials, forget }

/// Manage opencode server profiles and connect.
class ServersScreen extends ConsumerStatefulWidget {
  const ServersScreen({super.key});

  @override
  ConsumerState<ServersScreen> createState() => _ServersScreenState();
}
