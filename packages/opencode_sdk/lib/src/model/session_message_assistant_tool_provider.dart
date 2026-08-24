//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_message_assistant_tool_provider.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessageAssistantToolProvider {
  /// Returns a new [SessionMessageAssistantToolProvider] instance.
  SessionMessageAssistantToolProvider({
    required this.executed,

    this.metadata,

    this.resultMetadata,
  });

  @JsonKey(name: r'executed', required: true, includeIfNull: false)
  final bool executed;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Map<String, Object>? metadata;

  @JsonKey(name: r'resultMetadata', required: false, includeIfNull: false)
  final Map<String, Object>? resultMetadata;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessageAssistantToolProvider &&
            runtimeType == other.runtimeType &&
            equals(
              [executed, metadata, resultMetadata],
              [other.executed, other.metadata, other.resultMetadata],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([executed, metadata, resultMetadata]);

  factory SessionMessageAssistantToolProvider.fromJson(
    Map<String, dynamic> json,
  ) => _$SessionMessageAssistantToolProviderFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SessionMessageAssistantToolProviderToJson(this);

  String toString() {
    return toJson().toString();
  }
}
