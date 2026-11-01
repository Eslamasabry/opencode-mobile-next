import 'package:flutter/widgets.dart';

/// Whether the person is typing in a text field right now.
///
/// Keyboard shortcuts made of a bare key (N, P, [, ], PageUp, Home, End)
/// must call this and stand down when it is true, so a letter typed into the
/// message box or a comment field never moves the page. A shortcut that
/// needs Ctrl, Cmd or Alt may ignore it. Keys with no text meaning, such as
/// F-keys, are safe either way.
bool kitTypingInField() {
  final context = FocusManager.instance.primaryFocus?.context;
  if (context == null) return false;
  if (context.widget is EditableText) return true;
  return context.findAncestorStateOfType<EditableTextState>() != null;
}
