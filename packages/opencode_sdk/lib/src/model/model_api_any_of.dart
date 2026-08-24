//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'model_api_any_of.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ModelApiAnyOf {
  /// Returns a new [ModelApiAnyOf] instance.
  ModelApiAnyOf({
    required this.id,

    required this.type,

    required this.package,

    this.url,

    this.settings,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ModelApiAnyOfTypeEnum.unknownDefaultOpenApi,
  )
  final ModelApiAnyOfTypeEnum type;

  @JsonKey(name: r'package', required: true, includeIfNull: false)
  final String package;

  @JsonKey(name: r'url', required: false, includeIfNull: false)
  final String? url;

  @JsonKey(name: r'settings', required: false, includeIfNull: false)
  final Object? settings;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ModelApiAnyOf &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, package, url, settings],
              [other.id, other.type, other.package, other.url, other.settings],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, type, package, url, settings]);

  factory ModelApiAnyOf.fromJson(Map<String, dynamic> json) =>
      _$ModelApiAnyOfFromJson(json);

  Map<String, dynamic> toJson() => _$ModelApiAnyOfToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ModelApiAnyOfTypeEnum {
  @JsonValue(r'aisdk')
  aisdk(r'aisdk'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ModelApiAnyOfTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
