//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/file_part_source_text.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'file_source.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class FileSource {
  /// Returns a new [FileSource] instance.
  FileSource({required this.text, required this.type, required this.path});

  @JsonKey(name: r'text', required: true, includeIfNull: false)
  final FilePartSourceText text;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: FileSourceTypeEnum.unknownDefaultOpenApi,
  )
  final FileSourceTypeEnum type;

  @JsonKey(name: r'path', required: true, includeIfNull: false)
  final String path;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is FileSource &&
            runtimeType == other.runtimeType &&
            equals([text, type, path], [other.text, other.type, other.path]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([text, type, path]);

  factory FileSource.fromJson(Map<String, dynamic> json) =>
      _$FileSourceFromJson(json);

  Map<String, dynamic> toJson() => _$FileSourceToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum FileSourceTypeEnum {
  @JsonValue(r'file')
  file(r'file'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const FileSourceTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
