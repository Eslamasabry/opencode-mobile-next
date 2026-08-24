//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'file_system_entry.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class FileSystemEntry {
  /// Returns a new [FileSystemEntry] instance.
  FileSystemEntry({required this.path, required this.type});

  @JsonKey(name: r'path', required: true, includeIfNull: false)
  final String path;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: FileSystemEntryTypeEnum.unknownDefaultOpenApi,
  )
  final FileSystemEntryTypeEnum type;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is FileSystemEntry &&
            runtimeType == other.runtimeType &&
            equals([path, type], [other.path, other.type]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([path, type]);

  factory FileSystemEntry.fromJson(Map<String, dynamic> json) =>
      _$FileSystemEntryFromJson(json);

  Map<String, dynamic> toJson() => _$FileSystemEntryToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum FileSystemEntryTypeEnum {
  @JsonValue(r'file')
  file(r'file'),
  @JsonValue(r'directory')
  directory(r'directory'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const FileSystemEntryTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
