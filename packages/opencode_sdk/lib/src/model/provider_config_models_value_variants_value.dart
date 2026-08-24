//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_config_models_value_variants_value.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderConfigModelsValueVariantsValue {
  /// Returns a new [ProviderConfigModelsValueVariantsValue] instance.
  ProviderConfigModelsValueVariantsValue({
    this.disabled,
    Map<String, Object?> additionalProperties = const {},
  }) : _additionalProperties = Map.unmodifiable(additionalProperties);

  @JsonKey(name: r'disabled', required: false, includeIfNull: false)
  final bool? disabled;

  Map<String, Object?> _additionalProperties;

  @JsonKey(includeFromJson: false, includeToJson: false)
  Map<String, Object?> get additionalProperties => _additionalProperties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderConfigModelsValueVariantsValue &&
            runtimeType == other.runtimeType &&
            equals([disabled], [other.disabled]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([disabled]);

  factory ProviderConfigModelsValueVariantsValue.fromJson(
    Map<String, dynamic> json,
  ) {
    final value = _$ProviderConfigModelsValueVariantsValueFromJson(json);
    const knownKeys = <String>{r'disabled'};
    value._additionalProperties = Map.unmodifiable({
      for (final entry in json.entries)
        if (!knownKeys.contains(entry.key)) entry.key: entry.value,
    });
    return value;
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
    for (final entry in additionalProperties.entries) entry.key: entry.value,
    ..._$ProviderConfigModelsValueVariantsValueToJson(this),
  };

  String toString() {
    return toJson().toString();
  }
}
