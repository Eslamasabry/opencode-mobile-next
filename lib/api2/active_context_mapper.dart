import 'dart:convert';

import '../domain/active_context.dart';
import 'models.dart';

/// Explicit content inspection. Metadata, binary bodies and attachment URLs
/// are not rendered. Unlike the ordinary transcript, this view can show the
/// text of system/synthetic/skill messages returned by the context endpoint.
ActiveContextMessage mapActiveContext(Api2Message message) {
  final parts = <ContextContent>[];
  void add(ContextContentKind kind, String? text, {String? name}) {
    if (text != null && text.isNotEmpty) {
      parts.add(ContextContent(kind, text, name: name));
    }
  }

  void file(String? name, String? mime) => parts.add(
    ContextContent(ContextContentKind.file, name ?? mime ?? '', name: mime),
  );
  switch (message) {
    case Api2UserMessage():
      add(ContextContentKind.text, message.text);
      for (final value in message.files) {
        file(value.name, value.mime);
      }
    case Api2AssistantMessage():
      for (final value in message.content) {
        switch (value) {
          case Api2TextContent():
            add(ContextContentKind.text, value.text);
          case Api2ReasoningContent():
            if (value.time?.pruned == null) {
              add(ContextContentKind.reasoning, value.text);
            } else {
              parts.add(const ContextContent(ContextContentKind.pruned, ''));
            }
          case Api2ToolCallContent():
            final state = value.state;
            final input = state is Api2ToolStreaming
                ? state.rawInput
                : state.input == null
                ? null
                : const JsonEncoder.withIndent('  ').convert(state.input);
            add(ContextContentKind.toolInput, input, name: value.name);
            if (value.time?.pruned == null) {
              for (final result in state.content) {
                switch (result) {
                  case Api2ToolResultText():
                    add(
                      ContextContentKind.toolOutput,
                      result.text,
                      name: value.name,
                    );
                  case Api2ToolResultFile():
                    file(result.name, result.mime);
                  case Api2ToolResultUnknown():
                    parts.add(
                      ContextContent(
                        ContextContentKind.notice,
                        '',
                        name: value.name,
                      ),
                    );
                }
              }
              if (state is Api2ToolError) {
                add(
                  ContextContentKind.toolOutput,
                  state.error?.message,
                  name: value.name,
                );
              }
            } else {
              parts.add(
                ContextContent(ContextContentKind.pruned, '', name: value.name),
              );
            }
          case Api2UnknownContent():
            parts.add(
              ContextContent(
                ContextContentKind.notice,
                '',
                name: value.raw['type']?.toString(),
              ),
            );
        }
      }
    case Api2SystemMessage():
      add(ContextContentKind.text, message.text, name: message.description);
    case Api2SyntheticMessage():
      add(ContextContentKind.text, message.text, name: message.description);
    case Api2SkillMessage():
      add(ContextContentKind.text, message.text, name: message.name);
    case Api2ShellMessage():
      add(ContextContentKind.toolInput, message.command);
      add(ContextContentKind.toolOutput, message.output, name: message.status);
      if (message.outputTruncated) {
        parts.add(const ContextContent(ContextContentKind.truncated, ''));
      }
    case Api2CompactionMessage():
      add(ContextContentKind.text, message.summary, name: message.status);
      add(ContextContentKind.notice, message.reason);
    case Api2AgentSwitchedMessage():
      add(ContextContentKind.notice, message.agent);
    case Api2ModelSwitchedMessage():
      add(ContextContentKind.notice, message.model?.id);
    case Api2LocationSwitchedMessage():
      add(ContextContentKind.notice, message.location?.directory);
    case Api2UnknownMessage():
      parts.add(
        ContextContent(ContextContentKind.notice, '', name: message.type),
      );
  }
  return ActiveContextMessage(
    id: message.id,
    type: message.type,
    content: parts,
  );
}
