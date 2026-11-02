// Issue #111 investigation: Android 15+ devices may run 16 KB memory pages,
// where a native library or executable whose LOAD segments are aligned for
// 4 KB pages fails to load or exec. Every ELF the app bundles is checked
// here, so a rebuilt library can never silently lose 16 KB alignment.
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';

const _page = 16384;

class _Elf {
  _Elf(this.machine, this.type, this.loads, this.interpreter);
  final int machine, type;
  final List<({int offset, int vaddr, int align})> loads;
  final String? interpreter;
}

_Elf _read(File file) {
  final bytes = file.readAsBytesSync();
  final data = ByteData.sublistView(bytes);
  expect(bytes.sublist(0, 4), [0x7f, 0x45, 0x4c, 0x46], reason: file.path);
  expect(bytes[4], 2, reason: '${file.path} must be 64-bit ELF');
  expect(bytes[5], 1, reason: '${file.path} must be little-endian');
  const e = Endian.little;
  final type = data.getUint16(16, e);
  final machine = data.getUint16(18, e);
  final phoff = data.getUint64(0x20, e);
  final phentsize = data.getUint16(0x36, e);
  final phnum = data.getUint16(0x38, e);
  final loads = <({int offset, int vaddr, int align})>[];
  String? interpreter;
  for (var i = 0; i < phnum; i++) {
    final at = phoff + i * phentsize;
    final kind = data.getUint32(at, e);
    final offset = data.getUint64(at + 8, e);
    final vaddr = data.getUint64(at + 16, e);
    final filesz = data.getUint64(at + 32, e);
    final align = data.getUint64(at + 48, e);
    if (kind == 1) loads.add((offset: offset, vaddr: vaddr, align: align));
    if (kind == 3) {
      interpreter = String.fromCharCodes(
        bytes.sublist(offset, offset + filesz).takeWhile((b) => b != 0),
      );
    }
  }
  return _Elf(machine, type, loads, interpreter);
}

void main() {
  final root = Directory('android/app/src/main/jniLibs');
  final libraries =
      root
          .listSync(recursive: true)
          .whereType<File>()
          .where((f) => f.path.endsWith('.so'))
          .toList()
        ..sort((a, b) => a.path.compareTo(b.path));

  test('the bundle has both ABIs and the three AI Team engine binaries', () {
    final names = libraries.map((f) => f.uri.pathSegments.skip(5).join('/'));
    for (final abi in ['arm64-v8a', 'x86_64']) {
      for (final name in [
        'libaiteam_engine.so',
        'libaiteam_sandbox.so',
        'libaiteam_boundary_probe.so',
        'libproot.so',
        'libproot-loader.so',
      ]) {
        expect(names, contains('$abi/$name'));
      }
    }
  });

  test('every bundled ELF loads on 16 KB-page Android devices', () {
    expect(libraries, isNotEmpty);
    for (final file in libraries) {
      final elf = _read(file);
      final abi = file.parent.uri.pathSegments.reversed.skip(1).first;
      expect(
        elf.machine,
        abi == 'arm64-v8a' ? 183 : 62,
        reason: '${file.path} is built for the wrong architecture',
      );
      expect(elf.loads, isNotEmpty, reason: file.path);
      for (final load in elf.loads) {
        expect(
          load.align >= _page && load.align % _page == 0,
          isTrue,
          reason: '${file.path}: LOAD p_align ${load.align} < 16 KB',
        );
        expect(
          (load.offset - load.vaddr) % _page,
          0,
          reason: '${file.path}: LOAD offset/vaddr not 16 KB congruent',
        );
      }
    }
  });

  test('the engine executables use the system linker', () {
    for (final file in libraries.where(
      (f) =>
          f.path.endsWith('libaiteam_engine.so') ||
          f.path.endsWith('libaiteam_sandbox.so') ||
          f.path.endsWith('libaiteam_boundary_probe.so'),
    )) {
      final elf = _read(file);
      expect(elf.type, 3, reason: '${file.path} must be position independent');
      expect(elf.interpreter, '/system/bin/linker64', reason: file.path);
    }
  });
}
