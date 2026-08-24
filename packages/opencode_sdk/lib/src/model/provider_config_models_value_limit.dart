//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_config_models_value_limit.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderConfigModelsValueLimit {
  /// Returns a new [ProviderConfigModelsValueLimit] instance.
  ProviderConfigModelsValueLimit({
    required this.context,

    this.input,

    required this.output,
  });

  @JsonKey(name: r'context', required: true, includeIfNull: false)
  final num context;

  @JsonKey(name: r'input', required: false, includeIfNull: false)
  final num? input;

  @JsonKey(name: r'output', required: true, includeIfNull: false)
  final num output;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderConfigModelsValueLimit &&
            runtimeType == other.runtimeType &&
            equals(
              [context, input, output],
              [other.context, other.input, other.output],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([context, input, output]);

  factory ProviderConfigModelsValueLimit.fromJson(Map<String, dynamic> json) =>
      _$ProviderConfigModelsValueLimitFromJson(json);

  Map<String, dynamic> toJson() => _$ProviderConfigModelsValueLimitToJson(this);

  String toString() {
    return toJson().toString();
  }
}
