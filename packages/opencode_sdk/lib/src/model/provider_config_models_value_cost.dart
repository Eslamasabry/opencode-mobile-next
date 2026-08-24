//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/provider_config_models_value_cost_context_over200k.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_config_models_value_cost.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderConfigModelsValueCost {
  /// Returns a new [ProviderConfigModelsValueCost] instance.
  ProviderConfigModelsValueCost({
    required this.input,

    required this.output,

    this.cacheRead,

    this.cacheWrite,

    this.contextOver200k,
  });

  @JsonKey(name: r'input', required: true, includeIfNull: false)
  final num input;

  @JsonKey(name: r'output', required: true, includeIfNull: false)
  final num output;

  @JsonKey(name: r'cache_read', required: false, includeIfNull: false)
  final num? cacheRead;

  @JsonKey(name: r'cache_write', required: false, includeIfNull: false)
  final num? cacheWrite;

  @JsonKey(name: r'context_over_200k', required: false, includeIfNull: false)
  final ProviderConfigModelsValueCostContextOver200k? contextOver200k;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderConfigModelsValueCost &&
            runtimeType == other.runtimeType &&
            equals(
              [input, output, cacheRead, cacheWrite, contextOver200k],
              [
                other.input,
                other.output,
                other.cacheRead,
                other.cacheWrite,
                other.contextOver200k,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        input,
        output,
        cacheRead,
        cacheWrite,
        contextOver200k,
      ]);

  factory ProviderConfigModelsValueCost.fromJson(Map<String, dynamic> json) =>
      _$ProviderConfigModelsValueCostFromJson(json);

  Map<String, dynamic> toJson() => _$ProviderConfigModelsValueCostToJson(this);

  String toString() {
    return toJson().toString();
  }
}
