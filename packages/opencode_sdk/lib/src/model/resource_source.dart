//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/file_part_source_text.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'resource_source.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ResourceSource {
  /// Returns a new [ResourceSource] instance.
  ResourceSource({
    required this.text,

    required this.type,

    required this.clientName,

    required this.uri,
  });

  @JsonKey(name: r'text', required: true, includeIfNull: false)
  final FilePartSourceText text;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ResourceSourceTypeEnum.unknownDefaultOpenApi,
  )
  final ResourceSourceTypeEnum type;

  @JsonKey(name: r'clientName', required: true, includeIfNull: false)
  final String clientName;

  @JsonKey(name: r'uri', required: true, includeIfNull: false)
  final String uri;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ResourceSource &&
            runtimeType == other.runtimeType &&
            equals(
              [text, type, clientName, uri],
              [other.text, other.type, other.clientName, other.uri],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([text, type, clientName, uri]);

  factory ResourceSource.fromJson(Map<String, dynamic> json) =>
      _$ResourceSourceFromJson(json);

  Map<String, dynamic> toJson() => _$ResourceSourceToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ResourceSourceTypeEnum {
  @JsonValue(r'resource')
  resource(r'resource'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ResourceSourceTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
