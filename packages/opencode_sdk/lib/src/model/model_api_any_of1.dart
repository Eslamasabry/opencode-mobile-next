//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'model_api_any_of1.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ModelApiAnyOf1 {
  /// Returns a new [ModelApiAnyOf1] instance.
  ModelApiAnyOf1({
    required this.id,

    required this.type,

    this.url,

    required this.settings,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ModelApiAnyOf1TypeEnum.unknownDefaultOpenApi,
  )
  final ModelApiAnyOf1TypeEnum type;

  @JsonKey(name: r'url', required: false, includeIfNull: false)
  final String? url;

  @JsonKey(name: r'settings', required: true, includeIfNull: false)
  final Object settings;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ModelApiAnyOf1 &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, url, settings],
              [other.id, other.type, other.url, other.settings],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, url, settings]);

  factory ModelApiAnyOf1.fromJson(Map<String, dynamic> json) =>
      _$ModelApiAnyOf1FromJson(json);

  Map<String, dynamic> toJson() => _$ModelApiAnyOf1ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ModelApiAnyOf1TypeEnum {
  @JsonValue(r'native')
  native_(r'native'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ModelApiAnyOf1TypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
