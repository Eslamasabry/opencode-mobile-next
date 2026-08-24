//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_tokens_cache.dart';
import 'package:opencode_sdk/src/model/model_cost_tiers_inner.dart';
import 'package:opencode_sdk/src/model/model_cost_experimental_over200_k.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'model_cost.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ModelCost {
  /// Returns a new [ModelCost] instance.
  ModelCost({
    required this.input,

    required this.output,

    required this.cache,

    this.tiers,

    this.experimentalOver200K,
  });

  @JsonKey(name: r'input', required: true, includeIfNull: false)
  final num input;

  @JsonKey(name: r'output', required: true, includeIfNull: false)
  final num output;

  @JsonKey(name: r'cache', required: true, includeIfNull: false)
  final SessionTokensCache cache;

  @JsonKey(name: r'tiers', required: false, includeIfNull: false)
  final List<ModelCostTiersInner>? tiers;

  @JsonKey(name: r'experimentalOver200K', required: false, includeIfNull: false)
  final ModelCostExperimentalOver200K? experimentalOver200K;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ModelCost &&
            runtimeType == other.runtimeType &&
            equals(
              [input, output, cache, tiers, experimentalOver200K],
              [
                other.input,
                other.output,
                other.cache,
                other.tiers,
                other.experimentalOver200K,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([input, output, cache, tiers, experimentalOver200K]);

  factory ModelCost.fromJson(Map<String, dynamic> json) =>
      _$ModelCostFromJson(json);

  Map<String, dynamic> toJson() => _$ModelCostToJson(this);

  String toString() {
    return toJson().toString();
  }
}
