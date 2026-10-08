// The app's enable-flow handlers (coord-main; C20, C32): every
// docs/ux-system/capabilities.json `enableFlows` id resolves to the page or
// sheet that turns the capability on, so a KitCapabilityExplainer row,
// state or offer ("Voice typing isn't set up · Download voice model")
// leads somewhere real. The kit imports no screens; this file is where the
// app hands it the doors.
import 'package:flutter/widgets.dart';

import '../platform/camera.dart';
import '../platform/platform_capabilities.dart';
import '../state/connection.dart';
import '../state/phone_host.dart' show PhoneHostKind;
import '../state/profiles.dart' show ServerBackend;
import '../voice/device.dart';
import '../voice/model_manager.dart';
import '../voice/voice_ui.dart';
import 'kit/kit_capability_explainer.dart';
import 'kit/kit_page_route.dart';
import 'screens/agent_account_screen.dart';
import 'screens/keep_running_screen.dart';
import 'screens/library_screen.dart';
import 'screens/local_agent_screen.dart';
import 'screens/phone_setup/phone_setup_routes.dart';
import 'screens/phone_setup/phone_setup_termux_screen.dart';
import 'screens/project_hub_screen.dart';
import 'screens/projects_screen.dart';
import 'screens/servers_screen.dart' show ServersRouteRequest;
import 'screens/settings_screen.dart' show NotificationsSettingsScreen;
import 'screens/tailscale_setup_screen.dart';
import 'screens/team/team_page.dart';
import 'screens/this_phone_screen.dart';
import 'screens/capabilities_screen.dart';
import 'screens/usage_hub_screen.dart';
import 'widgets/pickers.dart';
import 'widgets/product_states.dart';
import 'widgets/team_host_form.dart';
import 'widgets/team_switch.dart' show editTeamAddress;

/// Registers a handler for every enable flow [platform] can run. A flow
/// the platform cannot run (setting up OpenCode on this phone, on a
/// computer) stays unregistered, so its explainer explains instead of
/// offering a button that leads nowhere (STATE-8, STATE-13).
///
/// Called once by the app shell with its live [controller]; a later call
/// replaces the handlers (tests, a new controller).
void registerCapabilityFlows(
  ConnectionController controller, {
  PlatformCapabilities? platform,
}) {
  final handlers = capabilityFlowHandlers(
    controller,
    platform: platform ?? platformCapabilities,
  );
  for (final MapEntry(:key, :value) in handlers.entries) {
    KitCapabilities.registerFlow(key, value);
  }
}

/// The handlers [registerCapabilityFlows] registers, by [KitEnableFlows]
/// id: all 21 on Android, the ones a computer can run elsewhere.
Map<String, KitEnableFlowHandler> capabilityFlowHandlers(
  ConnectionController controller, {
  required PlatformCapabilities platform,
}) {
  final phone = platform.supportsTermux;
  return {
    KitEnableFlows.addServer: _addServer,
    // These entry points already chose the kind; Back can change it.
    KitEnableFlows.addServerCodex: (context, request) =>
        _addServer(context, request, backend: ServerBackend.codex),
    KitEnableFlows.addServerPaseo: (context, request) =>
        _addServer(context, request, backend: ServerBackend.paseo),
    KitEnableFlows.serverGeneration: (context, request) =>
        _switchGeneration(context, request, phone: phone),
    KitEnableFlows.modelSignIn: (context, _) => _signIn(context, controller),
    // The team page: its intro and turn-on while the team is off (P3.4).
    KitEnableFlows.teamTurnOn: (context, _) =>
        openTeamPage(context, controller),
    KitEnableFlows.teamHostGuide: (context, _) => showTeamHostGuideSheet(
      context,
      enterAddress: () async {
        if (context.mounted) await editTeamAddress(context, controller);
      },
    ),
    KitEnableFlows.mcpAdd: (context, _) =>
        openTools(context, controller, section: ToolsSection.mcp),
    KitEnableFlows.projectChoose: (context, _) => pushKitPage<bool>(
      context,
      (_) => ProjectsScreen(controller: controller, selectedProjectID: null),
    ),
    // Project health holds "Make this a Git project" today; the offer at
    // the Git-needing action itself is the target (capabilities.json).
    KitEnableFlows.projectGitInit: (context, _) =>
        openProjectTool(context, controller, ProjectTool.health),
    KitEnableFlows.quotaCollectorGuide: (context, _) =>
        controller.profile == null
        ? _addServer(context, null)
        : pushKitPage<void>(
            context,
            (_) => UsageHubScreen(
              controller: controller,
              initialSection: UsageSection.remaining,
            ),
          ),
    KitEnableFlows.addExternalAgent: (context, _) =>
        openExternalAgents(context, controller),
    if (phone) ...{
      KitEnableFlows.phoneSetup: (context, _) => openPhoneSetupStart(context),
      KitEnableFlows.phoneSetupTermux: (context, _) =>
          openPhoneSetupTermux(context, firstSetup: true),
      KitEnableFlows.addToolClaude: (context, _) => pushKitPage<void>(
        context,
        (page) => LocalAgentScreen(
          onConnected: () =>
              Navigator.of(page).pushNamedAndRemoveUntil('/home', (_) => false),
        ),
      ),
    },
    if (platform.supportsVoice) ...{
      KitEnableFlows.voiceModelSetup: _voiceModel,
      KitEnableFlows.allowMicrophone: _microphone,
    },
    if (platform.supportsNotifications)
      KitEnableFlows.allowNotifications: (context, _) => pushKitPage<void>(
        context,
        (_) => NotificationsSettingsScreen(controller: controller),
      ),
    if (platform.supportsBackgroundService)
      KitEnableFlows.allowBackground: (context, _) =>
          openKeepRunningScreen(context),
    if (platform.supportsPromptPhotos || platform.supportsQrPairing)
      KitEnableFlows.allowCamera: _camera,
    if (platform.supportsTailscaleHandoff)
      KitEnableFlows.tailscaleSetup: (context, _) =>
          pushKitPage<void>(context, (_) => const TailscaleSetupScreen()),
  };
}

/// Add server, optionally carrying the kind selected by the entry point.
Future<void> _addServer(
  BuildContext context,
  KitEnableRequest? _, {
  ServerBackend? backend,
}) async {
  await Navigator.of(
    context,
  ).pushNamed('/servers', arguments: ServersRouteRequest.add(backend: backend));
}

/// OpenCode 2: on this phone it is the This phone card's switch; on a
/// computer, Add server finds which generation answers.
Future<void> _switchGeneration(
  BuildContext context,
  KitEnableRequest request, {
  required bool phone,
}) {
  final kind = switch (request.host) {
    KitHost.thisPhone => PhoneHostKind.inApp,
    KitHost.termux => PhoneHostKind.termux,
    _ => null,
  };
  if (phone && kind != null) return openThisPhone(context, kind: kind);
  return _addServer(context, request);
}

/// Signing in to a model: the server's providers, or its own account
/// (Codex), else the model picker, which says what the server offers.
Future<void> _signIn(BuildContext context, ConnectionController controller) {
  final capabilities = controller.capabilities;
  if (capabilities.serverCatalog) {
    return pushKitPage<void>(
      context,
      (_) => IntegrationsScreen(
        controller: controller,
        mode: IntegrationsMode.providers,
      ),
    );
  }
  if (controller.isConnected && capabilities.agentAccount) {
    return pushKitPage<void>(
      context,
      (_) => AgentAccountScreen(connection: controller),
    );
  }
  return showModelPicker(context);
}

Future<void> _voiceModel(BuildContext context, KitEnableRequest _) async {
  try {
    final models = await VoiceModelManager.shared();
    if (!context.mounted) return;
    await showVoiceModelSetupSheet(context, models);
  } catch (error) {
    if (context.mounted) showProductError(context, error);
  }
}

/// Android asks; once it will no longer ask, its settings page is the way.
Future<void> _microphone(BuildContext context, KitEnableRequest _) async {
  try {
    final permission = await voiceDevicePlatform.requestMicrophonePermission();
    if (permission == VoiceMicrophonePermission.permanentlyDenied) {
      await voiceDevicePlatform.openAppSettings();
    }
  } catch (error) {
    if (context.mounted) showProductError(context, error);
  }
}

Future<void> _camera(BuildContext context, KitEnableRequest _) async {
  try {
    final permission = await cameraPlatform.requestCameraPermission();
    if (permission == CameraPermission.permanentlyDenied) {
      await cameraPlatform.openAppSettings();
    }
  } catch (error) {
    if (context.mounted) showProductError(context, error);
  }
}
