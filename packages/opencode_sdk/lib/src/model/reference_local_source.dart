//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'reference_local_source.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ReferenceLocalSource {
  /// Returns a new [ReferenceLocalSource] instance.
  ReferenceLocalSource({
    required this.type,

    required this.path,

    this.description,

    this.hidden,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ReferenceLocalSourceTypeEnum.unknownDefaultOpenApi,
  )
  final ReferenceLocalSourceTypeEnum type;

  @JsonKey(name: r'path', required: true, includeIfNull: false)
  final String path;

  @JsonKey(name: r'description', required: false, includeIfNull: false)
  final String? description;

  @JsonKey(name: r'hidden', required: false, includeIfNull: false)
  final bool? hidden;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ReferenceLocalSource &&
            runtimeType == other.runtimeType &&
            equals(
              [type, path, description, hidden],
              [other.type, other.path, other.description, other.hidden],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([type, path, description, hidden]);

  factory ReferenceLocalSource.fromJson(Map<String, dynamic> json) =>
      _$ReferenceLocalSourceFromJson(json);

  Map<String, dynamic> toJson() => _$ReferenceLocalSourceToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ReferenceLocalSourceTypeEnum {
  @JsonValue(r'local')
  local(r'local'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ReferenceLocalSourceTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
