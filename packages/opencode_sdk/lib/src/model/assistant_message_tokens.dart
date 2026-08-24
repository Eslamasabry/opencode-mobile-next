//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_tokens_cache.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'assistant_message_tokens.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class AssistantMessageTokens {
  /// Returns a new [AssistantMessageTokens] instance.
  AssistantMessageTokens({
    this.total,

    required this.input,

    required this.output,

    required this.reasoning,

    required this.cache,
  });

  @JsonKey(name: r'total', required: false, includeIfNull: false)
  final num? total;

  @JsonKey(name: r'input', required: true, includeIfNull: false)
  final num input;

  @JsonKey(name: r'output', required: true, includeIfNull: false)
  final num output;

  @JsonKey(name: r'reasoning', required: true, includeIfNull: false)
  final num reasoning;

  @JsonKey(name: r'cache', required: true, includeIfNull: false)
  final SessionTokensCache cache;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is AssistantMessageTokens &&
            runtimeType == other.runtimeType &&
            equals(
              [total, input, output, reasoning, cache],
              [
                other.total,
                other.input,
                other.output,
                other.reasoning,
                other.cache,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([total, input, output, reasoning, cache]);

  factory AssistantMessageTokens.fromJson(Map<String, dynamic> json) =>
      _$AssistantMessageTokensFromJson(json);

  Map<String, dynamic> toJson() => _$AssistantMessageTokensToJson(this);

  String toString() {
    return toJson().toString();
  }
}
