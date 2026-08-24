//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_tokens_cache.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionTokensCache {
  /// Returns a new [SessionTokensCache] instance.
  SessionTokensCache({required this.read, required this.write});

  @JsonKey(name: r'read', required: true, includeIfNull: false)
  final num read;

  @JsonKey(name: r'write', required: true, includeIfNull: false)
  final num write;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionTokensCache &&
            runtimeType == other.runtimeType &&
            equals([read, write], [other.read, other.write]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([read, write]);

  factory SessionTokensCache.fromJson(Map<String, dynamic> json) =>
      _$SessionTokensCacheFromJson(json);

  Map<String, dynamic> toJson() => _$SessionTokensCacheToJson(this);

  String toString() {
    return toJson().toString();
  }
}
