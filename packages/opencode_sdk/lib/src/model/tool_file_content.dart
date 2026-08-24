//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'tool_file_content.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ToolFileContent {
  /// Returns a new [ToolFileContent] instance.
  ToolFileContent({
    required this.type,

    required this.uri,

    required this.mime,

    this.name,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ToolFileContentTypeEnum.unknownDefaultOpenApi,
  )
  final ToolFileContentTypeEnum type;

  @JsonKey(name: r'uri', required: true, includeIfNull: false)
  final String uri;

  @JsonKey(name: r'mime', required: true, includeIfNull: false)
  final String mime;

  @JsonKey(name: r'name', required: false, includeIfNull: false)
  final String? name;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ToolFileContent &&
            runtimeType == other.runtimeType &&
            equals(
              [type, uri, mime, name],
              [other.type, other.uri, other.mime, other.name],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, uri, mime, name]);

  factory ToolFileContent.fromJson(Map<String, dynamic> json) =>
      _$ToolFileContentFromJson(json);

  Map<String, dynamic> toJson() => _$ToolFileContentToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ToolFileContentTypeEnum {
  @JsonValue(r'file')
  file(r'file'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ToolFileContentTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
