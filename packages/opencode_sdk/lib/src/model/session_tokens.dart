//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_tokens_cache.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_tokens.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionTokens {
  /// Returns a new [SessionTokens] instance.
  SessionTokens({
    required this.input,

    required this.output,

    required this.reasoning,

    required this.cache,
  });

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
        other is SessionTokens &&
            runtimeType == other.runtimeType &&
            equals(
              [input, output, reasoning, cache],
              [other.input, other.output, other.reasoning, other.cache],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([input, output, reasoning, cache]);

  factory SessionTokens.fromJson(Map<String, dynamic> json) =>
      _$SessionTokensFromJson(json);

  Map<String, dynamic> toJson() => _$SessionTokensToJson(this);

  String toString() {
    return toJson().toString();
  }
}
