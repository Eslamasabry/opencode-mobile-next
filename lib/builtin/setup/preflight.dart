import 'dart:math' as math;

import '../../voice/device.dart';

/// Pre-flight checks before phone setup downloads anything (P0.8): the CPU
/// ABI the Ubuntu rootfs ships for, free space against what the chosen
/// components need, and total RAM. An unsupported phone is told why before
/// anything downloads; today the engine only recognises "no space" after a
/// failed download (setup_engine.dart's `_noSpace`), which stays as the
/// backstop for a device that changes state mid-install.
///
/// Reuses the device info the voice feature already collects
/// (lib/voice/device.dart's `oc/voice` channel, MainActivity.kt
/// `voiceDeviceInfo()`): no new native method for ABI, storage or RAM.

/// The Ubuntu rootfs BuiltinLinux.kt ships only for these two ABIs
/// (`BuiltinLinux.kt` `imageForDevice`, arm64 and amd64 tarballs). A
/// 32-bit-only device (`armeabi-v7a`, `x86`) or an unlisted one (riscv64)
/// has no rootfs to download.
const supportedSetupAbis = {'arm64-v8a', 'x86_64'};

/// Total RAM (never Android's per-app `memoryClass`) below which a phone
/// cannot hold Android, a proot Ubuntu userland and a Node/OpenCode process
/// at once without thrashing. A nominal "2 GB" phone reports less than 2048
/// MB of total memory (the kernel and firmware keep some: the emulator QA's
/// 2 GB device reported 1,972 MB), so the floor sits under that, at 1,800
/// MB, and a nominal 2 GB phone may set up (coordinator decision B2,
/// 2026-09-28).
const minimumSetupMemoryMb = 1800;

/// Total RAM under which setup is allowed but the person is told plainly
/// that OpenCode may be slow on this phone: nominal 2 GB and 3 GB phones,
/// which report a little under their label.
const comfortableSetupMemoryMb = 3072;

/// Bytes required on disk beyond a selection's own download size:
/// unpacking, apt/npm scratch space and the Ubuntu base itself take real
/// disk beyond what gets downloaded. Mirrors the order of magnitude the
/// Termux install script already asks for (`bridge.dart`'s
/// `INSTALL_REQUIRED_MB=2048` against "about 1 GB installed").
int requiredSetupFreeBytes(int downloadBytes) {
  const maximumBytes = 0x7fffffffffffffff;
  final nonnegative = math.max(downloadBytes, 0);
  final doubled = nonnegative > maximumBytes ~/ 2
      ? maximumBytes
      : nonnegative * 2;
  return math.max(doubled, 300 * 1000 * 1000);
}

enum SetupPreflightIssue { unsupportedAbi, lowMemory, lowSpace }

/// What the pre-flight found, or nothing wrong ([issue] null).
class SetupPreflightResult {
  const SetupPreflightResult._({
    this.issue,
    this.reportedAbi,
    this.totalMemoryMb,
    this.bytesToFree = 0,
  });

  const SetupPreflightResult.ok() : this._();

  /// Nothing blocks setup, but the phone has under
  /// [comfortableSetupMemoryMb] of total RAM: a plain "may be slow" note.
  const SetupPreflightResult.mayBeSlow(int totalMemoryMb)
    : this._(totalMemoryMb: totalMemoryMb);

  const SetupPreflightResult.unsupportedAbi(String abi)
    : this._(issue: SetupPreflightIssue.unsupportedAbi, reportedAbi: abi);

  const SetupPreflightResult.lowMemory(int totalMemoryMb)
    : this._(
        issue: SetupPreflightIssue.lowMemory,
        totalMemoryMb: totalMemoryMb,
      );

  const SetupPreflightResult.lowSpace(int bytesToFree)
    : this._(issue: SetupPreflightIssue.lowSpace, bytesToFree: bytesToFree);

  final SetupPreflightIssue? issue;

  /// Set for [SetupPreflightIssue.unsupportedAbi]: the device's own primary
  /// ABI, for an honest "this phone reports arm32" sentence.
  final String? reportedAbi;

  /// Set for [SetupPreflightIssue.lowMemory], and for a supported phone
  /// that [mayBeSlow].
  final int? totalMemoryMb;

  /// Set for [SetupPreflightIssue.lowSpace]: how many more bytes are needed.
  final int bytesToFree;

  bool get supported => issue == null;

  /// Setup may go ahead, with a note naming [totalMemoryMb]: the phone is
  /// over the floor but under [comfortableSetupMemoryMb].
  bool get mayBeSlow => issue == null && totalMemoryMb != null;
}

/// Checks storage alone so setup can repeat the same policy between installs.
/// An unavailable reading retains the initial preflight's allow-to-try policy.
SetupPreflightResult checkSetupStoragePreflight(
  int? availableBytes, {
  required int downloadBytes,
}) {
  if (availableBytes == null) return const SetupPreflightResult.ok();
  final required = requiredSetupFreeBytes(downloadBytes);
  if (availableBytes < required) {
    return SetupPreflightResult.lowSpace(required - availableBytes);
  }
  return const SetupPreflightResult.ok();
}

/// Checks [device] against what a selection needs ([downloadBytes], the sum
/// of its components' `downloadBytes`). ABI first (nothing else matters if
/// there is no rootfs for this CPU), then RAM, then free space. Total RAM
/// between [minimumSetupMemoryMb] and [comfortableSetupMemoryMb] blocks
/// nothing: the result is [SetupPreflightResult.mayBeSlow]. An unknown
/// reading (empty ABI list, null memory or storage — an old build's channel,
/// or a probe that failed) never blocks: the person can still try.
SetupPreflightResult checkSetupPreflight(
  VoiceDeviceInfo device, {
  required int downloadBytes,
}) {
  final abis = device.supportedAbis;
  if (abis.isNotEmpty && !abis.any(supportedSetupAbis.contains)) {
    return SetupPreflightResult.unsupportedAbi(abis.first);
  }
  final memory = device.totalMemoryMb;
  if (memory != null && memory < minimumSetupMemoryMb) {
    return SetupPreflightResult.lowMemory(memory);
  }
  final storage = checkSetupStoragePreflight(
    device.availableStorageBytes,
    downloadBytes: downloadBytes,
  );
  if (!storage.supported) return storage;
  if (memory != null && memory < comfortableSetupMemoryMb) {
    return SetupPreflightResult.mayBeSlow(memory);
  }
  return const SetupPreflightResult.ok();
}
