//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'integration_env_method.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class IntegrationEnvMethod {
  /// Returns a new [IntegrationEnvMethod] instance.
  IntegrationEnvMethod({required this.type, required this.names});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: IntegrationEnvMethodTypeEnum.unknownDefaultOpenApi,
  )
  final IntegrationEnvMethodTypeEnum type;

  @JsonKey(name: r'names', required: true, includeIfNull: false)
  final List<String> names;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is IntegrationEnvMethod &&
            runtimeType == other.runtimeType &&
            equals([type, names], [other.type, other.names]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([type, names]);

  factory IntegrationEnvMethod.fromJson(Map<String, dynamic> json) =>
      _$IntegrationEnvMethodFromJson(json);

  Map<String, dynamic> toJson() => _$IntegrationEnvMethodToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum IntegrationEnvMethodTypeEnum {
  @JsonValue(r'env')
  env(r'env'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const IntegrationEnvMethodTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
