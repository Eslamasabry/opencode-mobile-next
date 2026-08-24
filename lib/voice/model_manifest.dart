enum VoiceLanguage {
  auto('', 'Auto detect'),
  english('en', 'English'),
  arabic('ar', 'Arabic');

  const VoiceLanguage(this.whisperCode, this.label);

  final String whisperCode;
  final String label;

  static VoiceLanguage fromID(String? id) => values.firstWhere(
    (value) => value.name == id,
    orElse: () => VoiceLanguage.auto,
  );
}

class VoiceModelFile {
  const VoiceModelFile({
    required this.name,
    required this.length,
    required this.sha256,
  });

  final String name;
  final int length;
  final String sha256;
}

class VoiceModelPack {
  const VoiceModelPack({
    required this.id,
    required this.label,
    required this.description,
    required this.repository,
    required this.revision,
    required this.files,
    required this.minimumMemoryMb,
  });

  final String id;
  final String label;
  final String description;
  final String repository;
  final String revision;
  final List<VoiceModelFile> files;
  final int minimumMemoryMb;

  int get downloadBytes => files.fold(0, (total, file) => total + file.length);

  Uri urlFor(VoiceModelFile file) => Uri.https(
    'huggingface.co',
    '/$repository/resolve/$revision/${file.name}',
  );

  VoiceModelFile get encoder => files[0];
  VoiceModelFile get decoder => files[1];
  VoiceModelFile get tokens => files[2];
}

const voiceModelPacks = <VoiceModelPack>[
  VoiceModelPack(
    id: 'base',
    label: 'Balanced',
    description: 'Recommended quality, storage, and speed tradeoff.',
    repository: 'csukuangfj/sherpa-onnx-whisper-base',
    revision: 'bb53ee204431c90d314c1cc08d28d23e5b7927cc',
    minimumMemoryMb: 256,
    files: [
      VoiceModelFile(
        name: 'base-encoder.int8.onnx',
        length: 29120534,
        sha256:
            '0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11',
      ),
      VoiceModelFile(
        name: 'base-decoder.int8.onnx',
        length: 130672026,
        sha256:
            '9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d',
      ),
      VoiceModelFile(
        name: 'base-tokens.txt',
        length: 816730,
        sha256:
            'b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126',
      ),
    ],
  ),
  VoiceModelPack(
    id: 'small',
    label: 'High accuracy',
    description: 'Optional best quality; requires substantially more memory.',
    repository: 'csukuangfj/sherpa-onnx-whisper-small',
    revision: '8f3c18b358db4d1f2fc1eae49d75cd20989e4309',
    minimumMemoryMb: 512,
    files: [
      VoiceModelFile(
        name: 'small-encoder.int8.onnx',
        length: 112442483,
        sha256:
            '4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9',
      ),
      VoiceModelFile(
        name: 'small-decoder.int8.onnx',
        length: 262226114,
        sha256:
            'acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee',
      ),
      VoiceModelFile(
        name: 'small-tokens.txt',
        length: 816730,
        sha256:
            'b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126',
      ),
    ],
  ),
  VoiceModelPack(
    id: 'tiny',
    label: 'Compact fallback',
    description: 'Fastest and smallest; reduced accuracy in difficult audio.',
    repository: 'csukuangfj/sherpa-onnx-whisper-tiny',
    revision: '65176e2deb88badc814a94058666cadccc29b61c',
    minimumMemoryMb: 192,
    files: [
      VoiceModelFile(
        name: 'tiny-encoder.int8.onnx',
        length: 12937772,
        sha256:
            'd24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434',
      ),
      VoiceModelFile(
        name: 'tiny-decoder.int8.onnx',
        length: 89855401,
        sha256:
            'd2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925',
      ),
      VoiceModelFile(
        name: 'tiny-tokens.txt',
        length: 816730,
        sha256:
            'b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126',
      ),
    ],
  ),
];

VoiceModelPack voiceModelPack(String id) => voiceModelPacks.firstWhere(
  (pack) => pack.id == id,
  orElse: () => voiceModelPacks.first,
);

String formatModelBytes(int bytes) =>
    '${(bytes / 1024 / 1024).toStringAsFixed(1)} MiB';
