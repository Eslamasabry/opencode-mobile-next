//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_config_models_value_modalities.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderConfigModelsValueModalities {
  /// Returns a new [ProviderConfigModelsValueModalities] instance.
  ProviderConfigModelsValueModalities({this.input, this.output});

  @JsonKey(name: r'input', required: false, includeIfNull: false)
  final List<ProviderConfigModelsValueModalitiesInputEnum>? input;

  @JsonKey(name: r'output', required: false, includeIfNull: false)
  final List<ProviderConfigModelsValueModalitiesOutputEnum>? output;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderConfigModelsValueModalities &&
            runtimeType == other.runtimeType &&
            equals([input, output], [other.input, other.output]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([input, output]);

  factory ProviderConfigModelsValueModalities.fromJson(
    Map<String, dynamic> json,
  ) => _$ProviderConfigModelsValueModalitiesFromJson(json);

  Map<String, dynamic> toJson() =>
      _$ProviderConfigModelsValueModalitiesToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ProviderConfigModelsValueModalitiesInputEnum {
  @JsonValue(r'text')
  text(r'text'),
  @JsonValue(r'audio')
  audio(r'audio'),
  @JsonValue(r'image')
  image(r'image'),
  @JsonValue(r'video')
  video(r'video'),
  @JsonValue(r'pdf')
  pdf(r'pdf'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ProviderConfigModelsValueModalitiesInputEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

enum ProviderConfigModelsValueModalitiesOutputEnum {
  @JsonValue(r'text')
  text(r'text'),
  @JsonValue(r'audio')
  audio(r'audio'),
  @JsonValue(r'image')
  image(r'image'),
  @JsonValue(r'video')
  video(r'video'),
  @JsonValue(r'pdf')
  pdf(r'pdf'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ProviderConfigModelsValueModalitiesOutputEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
