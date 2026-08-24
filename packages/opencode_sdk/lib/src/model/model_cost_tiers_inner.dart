//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_tokens_cache.dart';
import 'package:opencode_sdk/src/model/model_cost_tiers_inner_tier.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'model_cost_tiers_inner.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ModelCostTiersInner {
  /// Returns a new [ModelCostTiersInner] instance.
  ModelCostTiersInner({
    required this.input,

    required this.output,

    required this.cache,

    required this.tier,
  });

  @JsonKey(name: r'input', required: true, includeIfNull: false)
  final num input;

  @JsonKey(name: r'output', required: true, includeIfNull: false)
  final num output;

  @JsonKey(name: r'cache', required: true, includeIfNull: false)
  final SessionTokensCache cache;

  @JsonKey(name: r'tier', required: true, includeIfNull: false)
  final ModelCostTiersInnerTier tier;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ModelCostTiersInner &&
            runtimeType == other.runtimeType &&
            equals(
              [input, output, cache, tier],
              [other.input, other.output, other.cache, other.tier],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([input, output, cache, tier]);

  factory ModelCostTiersInner.fromJson(Map<String, dynamic> json) =>
      _$ModelCostTiersInnerFromJson(json);

  Map<String, dynamic> toJson() => _$ModelCostTiersInnerToJson(this);

  String toString() {
    return toJson().toString();
  }
}
