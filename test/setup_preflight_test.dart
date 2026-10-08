// Pure thresholds for P0.8's pre-flight (lib/builtin/setup/preflight.dart):
// CPU ABI, total RAM and free space, checked before phone setup downloads
// anything. See test/phone_setup_start_screen_test.dart's "P0.8 pre-flight"
// group and test/phone_setup_customize_sheet_test.dart for the UI these
// thresholds drive.

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/preflight.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/state/download_size.dart';
import 'package:opencode_mobile/voice/device.dart';

const _download = 165000000; // 165 MB, the default registry's total.

VoiceDeviceInfo _device({
  List<String> abis = const ['arm64-v8a'],
  int? memoryMb = 4096,
  int? availableBytes = 2000000000,
}) => VoiceDeviceInfo(
  availableStorageBytes: availableBytes,
  memoryClassMb: 256,
  totalMemoryMb: memoryMb,
  supportedAbis: abis,
  hasMicrophone: false,
);

void main() {
  group('requiredSetupFreeBytes', () {
    test('is twice the download size once that clears the floor', () {
      expect(requiredSetupFreeBytes(_download), 330000000);
      expect(requiredSetupFreeBytes(500 * 1000 * 1000), 1000 * 1000 * 1000);
    });

    test('never drops below the floor for a tiny or zero download', () {
      expect(requiredSetupFreeBytes(0), 300 * 1000 * 1000);
      expect(requiredSetupFreeBytes(1000), 300 * 1000 * 1000);
    });

    test(
      'known extracted payload counts with retained download and scratch',
      () {
        expect(
          requiredSetupFreeBytes(120000000, installedBytes: 350000000),
          120000000 + 350000000 + 64 * 1024 * 1024,
        );
        expect(requiredSetupFreeBytes(1000, installedBytes: 1000), 300000000);
        expect(
          requiredSetupFreeBytes(500000000, installedBytes: 1),
          1000000000,
        );
      },
    );

    test('unknown and nonpositive extracted sizes retain legacy admission', () {
      for (final installed in <int?>[null, 0, -1]) {
        expect(
          requiredSetupFreeBytes(120000000, installedBytes: installed),
          300000000,
        );
        expect(
          requiredSetupFreeBytes(500000000, installedBytes: installed),
          1000000000,
        );
      }
      expect(
        requiredSetupFreeBytes(-1, installedBytes: 350000000),
        350000000 + 64 * 1024 * 1024,
      );
    });

    test(
      'known peak and doubled download saturate without integer overflow',
      () {
        const maximum = 0x7fffffffffffffff;
        const scratch = 64 * 1024 * 1024;
        expect(requiredSetupFreeBytes(1, installedBytes: maximum), maximum);
        expect(
          requiredSetupFreeBytes(1, installedBytes: maximum - scratch - 1),
          maximum,
        );
        expect(
          requiredSetupFreeBytes(1, installedBytes: maximum - scratch - 2),
          maximum - 1,
        );
        expect(requiredSetupFreeBytes(maximum, installedBytes: 1), maximum);
        expect(
          requiredSetupFreeBytes(maximum ~/ 2 + 1, installedBytes: 1),
          maximum,
        );
      },
    );
  });

  test('known extracted peak forwards through storage and full preflight', () {
    const download = 120000000;
    const installed = 350000000;
    const required = download + installed + 64 * 1024 * 1024;
    final low = checkSetupStoragePreflight(
      required - 1,
      downloadBytes: download,
      installedBytes: installed,
    );
    expect(low.issue, SetupPreflightIssue.lowSpace);
    expect(low.bytesToFree, 1);
    expect(
      checkSetupStoragePreflight(
        required,
        downloadBytes: download,
        installedBytes: installed,
      ).supported,
      isTrue,
    );
    expect(
      checkSetupStoragePreflight(
        null,
        downloadBytes: download,
        installedBytes: installed,
      ).supported,
      isTrue,
    );
    final full = checkSetupPreflight(
      _device(availableBytes: required - 1),
      downloadBytes: download,
      installedBytes: installed,
    );
    expect(full.issue, SetupPreflightIssue.lowSpace);
    expect(full.bytesToFree, 1);
    expect(
      checkSetupPreflight(
        _device(availableBytes: required),
        downloadBytes: download,
        installedBytes: installed,
      ).supported,
      isTrue,
    );
  });

  test(
    'component clones preserve known extracted payload and other metadata',
    () {
      const component = SetupComponent(
        id: 'fixture',
        title: 'Fixture',
        shortTitle: 'Fixture',
        checkScript: 'check',
        installScript: 'install',
        dependsOn: ['dependency'],
        required: true,
        defaultOn: true,
        estimatedSeconds: 13,
        downloadBytes: 120000000,
        installedBytes: 350000000,
        removeScript: 'remove',
        presenceScript: 'present',
        sizeScript: 'size',
        why: 'Required fixture',
        summary: 'Fixture summary',
        agentUser: true,
      );
      final copied = component.withDownloadBytes(42);
      expect(copied.downloadBytes, 42);
      expect(copied.installedBytes, 350000000);
      final offer = component.withAppOffer(
        const SetupAppOffer(
          downloadBytes: 77,
          downloadSize: DownloadSize.unknown(),
        ),
      );
      expect(offer.downloadBytes, 77);
      expect(offer.installedBytes, 350000000);
      final changed = copied.withInstalledBytes(123);
      expect(changed.installedBytes, 123);
      expect(changed.downloadBytes, 42);
      expect(changed.dependsOn, ['dependency']);
      expect(changed.required, isTrue);
      expect(changed.defaultOn, isTrue);
      expect(changed.estimatedSeconds, 13);
      expect(changed.checkScript, 'check');
      expect(changed.installScript, 'install');
      expect(changed.removeScript, 'remove');
      expect(changed.presenceScript, 'present');
      expect(changed.sizeScript, 'size');
      expect(changed.why, 'Required fixture');
      expect(changed.summary, 'Fixture summary');
      expect(changed.agentUser, isTrue);
      expect(changed.withInstalledBytes(null).installedBytes, isNull);
    },
  );

  test(
    'repeatable storage check respects fresh readings and exact threshold',
    () {
      expect(
        checkSetupStoragePreflight(299999999, downloadBytes: 0).issue,
        SetupPreflightIssue.lowSpace,
      );
      expect(
        checkSetupStoragePreflight(300000000, downloadBytes: 0).supported,
        isTrue,
      );
      expect(
        checkSetupStoragePreflight(1, downloadBytes: 0).supported,
        isFalse,
      );
      expect(
        checkSetupStoragePreflight(null, downloadBytes: 0).supported,
        isTrue,
      );
      expect(requiredSetupFreeBytes(-1), 300000000);
      expect(requiredSetupFreeBytes(0x7fffffffffffffff), 0x7fffffffffffffff);
    },
  );

  group('checkSetupPreflight', () {
    test('passes a supported, roomy, well-remembered phone', () {
      final result = checkSetupPreflight(_device(), downloadBytes: _download);
      expect(result.supported, isTrue);
      expect(result.issue, isNull);
    });

    test('blocks a 32-bit-only ABI, reporting the CPU it found', () {
      final result = checkSetupPreflight(
        _device(abis: ['armeabi-v7a']),
        downloadBytes: _download,
      );
      expect(result.issue, SetupPreflightIssue.unsupportedAbi);
      expect(result.reportedAbi, 'armeabi-v7a');
    });

    test('an x86_64 device passes: the rootfs ships for it too', () {
      final result = checkSetupPreflight(
        _device(abis: ['x86_64']),
        downloadBytes: _download,
      );
      expect(result.supported, isTrue);
    });

    test('an unknown (empty) ABI list never blocks', () {
      final result = checkSetupPreflight(
        _device(abis: const []),
        downloadBytes: _download,
      );
      expect(result.supported, isTrue);
    });

    test('blocks under the memory floor, naming both numbers', () {
      final result = checkSetupPreflight(
        _device(memoryMb: 1024),
        downloadBytes: _download,
      );
      expect(result.issue, SetupPreflightIssue.lowMemory);
      expect(result.totalMemoryMb, 1024);
    });

    // B2 (emulator QA 2026-09-28): the floor is 1,800 MB of total RAM, so a
    // nominal 2 GB phone (which reports ~1,972 MB) may set up; up to 3 GB
    // it is told plainly that it may be slow.
    test('1,700 MB is under the floor: refused', () {
      final result = checkSetupPreflight(
        _device(memoryMb: 1700),
        downloadBytes: _download,
      );
      expect(result.supported, isFalse);
      expect(result.issue, SetupPreflightIssue.lowMemory);
      expect(result.totalMemoryMb, 1700);
      expect(result.mayBeSlow, isFalse);
    });

    test('a nominal 2 GB phone (1,972 MB) may set up, with the slow note', () {
      final result = checkSetupPreflight(
        _device(memoryMb: 1972),
        downloadBytes: _download,
      );
      expect(result.supported, isTrue);
      expect(result.mayBeSlow, isTrue);
      expect(result.totalMemoryMb, 1972);
    });

    test('exactly at the memory floor passes, with the slow note', () {
      final result = checkSetupPreflight(
        _device(memoryMb: minimumSetupMemoryMb),
        downloadBytes: _download,
      );
      expect(minimumSetupMemoryMb, 1800);
      expect(result.supported, isTrue);
      expect(result.mayBeSlow, isTrue);
    });

    test('a nominal 3 GB phone (2,900 MB) still gets the slow note', () {
      final result = checkSetupPreflight(
        _device(memoryMb: 2900),
        downloadBytes: _download,
      );
      expect(result.supported, isTrue);
      expect(result.mayBeSlow, isTrue);
      expect(result.totalMemoryMb, 2900);
    });

    test('4,096 MB passes with no note', () {
      final result = checkSetupPreflight(
        _device(memoryMb: 4096),
        downloadBytes: _download,
      );
      expect(result.supported, isTrue);
      expect(result.mayBeSlow, isFalse);
      expect(result.totalMemoryMb, isNull);
    });

    test('3 GB (3,072 MB) and over carries no note', () {
      final result = checkSetupPreflight(
        _device(memoryMb: comfortableSetupMemoryMb),
        downloadBytes: _download,
      );
      expect(result.mayBeSlow, isFalse);
    });

    test('low space on a slow phone still blocks: space is checked first', () {
      final result = checkSetupPreflight(
        _device(memoryMb: 1972, availableBytes: 100000000),
        downloadBytes: _download,
      );
      expect(result.issue, SetupPreflightIssue.lowSpace);
      expect(result.mayBeSlow, isFalse);
    });

    test('an unknown (null) memory reading never blocks', () {
      final result = checkSetupPreflight(
        _device(memoryMb: null),
        downloadBytes: _download,
      );
      expect(result.supported, isTrue);
      expect(result.mayBeSlow, isFalse);
    });

    test('blocks low space, naming exactly how many bytes to free', () {
      final result = checkSetupPreflight(
        _device(availableBytes: 100000000),
        downloadBytes: _download,
      );
      expect(result.issue, SetupPreflightIssue.lowSpace);
      // Needs 330 MB (2x download), has 100 MB: 230 MB short.
      expect(result.bytesToFree, 230000000);
    });

    test('exactly the required space passes', () {
      final result = checkSetupPreflight(
        _device(availableBytes: requiredSetupFreeBytes(_download)),
        downloadBytes: _download,
      );
      expect(result.supported, isTrue);
    });

    test('an unknown (null) storage reading never blocks', () {
      final result = checkSetupPreflight(
        _device(availableBytes: null),
        downloadBytes: _download,
      );
      expect(result.supported, isTrue);
    });

    test('ABI is checked before memory or space: nothing else matters '
        'without a rootfs', () {
      final result = checkSetupPreflight(
        _device(abis: ['armeabi-v7a'], memoryMb: 512, availableBytes: 0),
        downloadBytes: _download,
      );
      expect(result.issue, SetupPreflightIssue.unsupportedAbi);
    });
  });
}
