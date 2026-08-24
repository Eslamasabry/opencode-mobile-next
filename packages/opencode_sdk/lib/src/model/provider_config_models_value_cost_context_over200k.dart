//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_config_models_value_cost_context_over200k.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderConfigModelsValueCostContextOver200k {
  /// Returns a new [ProviderConfigModelsValueCostContextOver200k] instance.
  ProviderConfigModelsValueCostContextOver200k({
    required this.input,

    required this.output,

    this.cacheRead,

    this.cacheWrite,
  });

  @JsonKey(name: r'input', required: true, includeIfNull: false)
  final num input;

  @JsonKey(name: r'output', required: true, includeIfNull: false)
  final num output;

  @JsonKey(name: r'cache_read', required: false, includeIfNull: false)
  final num? cacheRead;

  @JsonKey(name: r'cache_write', required: false, includeIfNull: false)
  final num? cacheWrite;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderConfigModelsValueCostContextOver200k &&
            runtimeType == other.runtimeType &&
            equals(
              [input, output, cacheRead, cacheWrite],
              [other.input, other.output, other.cacheRead, other.cacheWrite],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([input, output, cacheRead, cacheWrite]);

  factory ProviderConfigModelsValueCostContextOver200k.fromJson(
    Map<String, dynamic> json,
  ) => _$ProviderConfigModelsValueCostContextOver200kFromJson(json);

  Map<String, dynamic> toJson() =>
      _$ProviderConfigModelsValueCostContextOver200kToJson(this);

  String toString() {
    return toJson().toString();
  }
}
