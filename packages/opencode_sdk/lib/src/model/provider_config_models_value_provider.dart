//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_config_models_value_provider.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderConfigModelsValueProvider {
  /// Returns a new [ProviderConfigModelsValueProvider] instance.
  ProviderConfigModelsValueProvider({this.npm, this.api});

  @JsonKey(name: r'npm', required: false, includeIfNull: false)
  final String? npm;

  @JsonKey(name: r'api', required: false, includeIfNull: false)
  final String? api;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderConfigModelsValueProvider &&
            runtimeType == other.runtimeType &&
            equals([npm, api], [other.npm, other.api]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([npm, api]);

  factory ProviderConfigModelsValueProvider.fromJson(
    Map<String, dynamic> json,
  ) => _$ProviderConfigModelsValueProviderFromJson(json);

  Map<String, dynamic> toJson() =>
      _$ProviderConfigModelsValueProviderToJson(this);

  String toString() {
    return toJson().toString();
  }
}
