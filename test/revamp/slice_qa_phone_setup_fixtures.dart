// Shared set-up for slice-qa-phone-setup (emulator QA 2026-09-28, B2 and
// B6): the phone setup screens under the app-wide status scope, with a saved
// server's "not answering" line, and a phone with a given total RAM.
import 'package:flutter/widgets.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_progress_screen.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_start_screen.dart';
import 'package:opencode_mobile/voice/device.dart';

/// The app-wide line the QA saw on every setup step (screens 97–117): a
/// saved server at 127.0.0.1 not answering, as `connectionKitStatus` builds
/// it (same keys, words and actions).
KitStatus otherServerNotAnswering({
  VoidCallback? onReconnect,
  VoidCallback? onDetails,
}) => KitStatus(
  kind: KitStatusKind.connection,
  id: 'connection:laptop',
  key: const ValueKey('connection-status-banner'),
  icon: AppIconography.cloudOff,
  tone: AppStatusTone.failure,
  message: "127.0.0.1 isn't answering",
  action: KitAction(
    key: const ValueKey('connection-banner-retry'),
    label: 'Reconnect to 127.0.0.1',
    onPressed: onReconnect ?? () {},
  ),
  more: [
    KitAction(
      key: const ValueKey('connection-banner-details'),
      label: 'Details',
      onPressed: onDetails ?? () {},
    ),
    KitAction(
      key: const ValueKey('connection-banner-change-server'),
      label: 'Switch server',
      onPressed: () {},
    ),
  ],
);

/// A phone with [memoryMb] of total RAM and room for the default setup.
VoiceDeviceInfo deviceWithMemory(int memoryMb) => VoiceDeviceInfo(
  availableStorageBytes: 20000000000,
  memoryClassMb: 256,
  totalMemoryMb: memoryMb,
  supportedAbis: const ['arm64-v8a'],
  hasMicrophone: true,
);

/// [child] under the app-wide conditions main.dart publishes.
Widget underAppConditions(List<KitStatus> conditions, Widget child) =>
    KitStatusScope(conditions: ValueNotifier(conditions), child: child);

/// Screen A on a phone with [memoryMb] of total RAM.
Widget startOnPhone({int memoryMb = 8192}) => PhoneSetupStartScreen(
  inAppProbe: () async => false,
  deviceProbe: () async => deviceWithMemory(memoryMb),
  openProgress: (_) async {},
);

/// The progress screen on the engine `pumpPhone` installs (read when it
/// builds, after the fake is in place).
Widget progressOnPhone() => Builder(
  builder: (_) => PhoneSetupProgressScreen(
    engine: PhoneSetup.engine,
    firstSetup: true,
    openReady: (_) async {},
  ),
);
