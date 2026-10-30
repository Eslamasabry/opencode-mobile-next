import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../../widgets_overlay/widget_overlay_api.dart';
import '../../../widgets_overlay/widget_overlay_launch.dart';
import '../../app_iconography.dart';
import '../../app_theme.dart' show AppStatusTone;
import '../../kit/kit.dart';

/// The Conversations widget's Edit: which project it lists. "All projects"
/// first, then each recent project. One tap saves and closes; the widget then
/// shows only that project's conversations.
class WidgetProjectScreen extends StatefulWidget {
  const WidgetProjectScreen({
    super.key,
    required this.api,
    required this.launch,
  });

  final WidgetOverlayApi api;
  final WidgetOverlayLaunch launch;

  @override
  State<WidgetProjectScreen> createState() => _WidgetProjectScreenState();
}

class _WidgetProjectScreenState extends State<WidgetProjectScreen> {
  late String _selected = widget.launch.project;
  bool _saving = false;
  bool _failed = false;

  Future<void> _choose(String project) async {
    if (_saving) return;
    setState(() {
      _selected = project;
      _saving = true;
      _failed = false;
    });
    try {
      // Native saves, redraws the widget and closes this window.
      await widget.api.saveProject(project);
    } on Object {
      if (mounted) setState(() => _failed = true);
    }
    if (mounted) setState(() => _saving = false);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final projects = [
      for (final p in widget.launch.projects)
        if (p != '') p,
    ];
    return KitFloatingPanel(
      onDismiss: () => widget.api.close(),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(
                child: KitText(
                  l10n.widgetProjectTitle,
                  role: KitTextRole.headline,
                  tone: KitTextTone.primary,
                ),
              ),
              KitIconButton(
                icon: AppIconography.close,
                tooltip: l10n.widgetV2Cancel,
                onPressed: _saving ? null : () => widget.api.close(),
                disabledReason: l10n.widgetV2Sending,
              ),
            ],
          ),
          SizedBox(height: tokens.space3),
          Flexible(
            child: ListView(
              shrinkWrap: true,
              children: [
                KitChoiceList<String>.single(
                  semanticsLabel: l10n.widgetProjectTitle,
                  choices: [
                    KitChoice(
                      value: '',
                      title: l10n.chatsHomeAllProjects,
                      key: const ValueKey('widget-project-all'),
                    ),
                    for (final p in projects)
                      KitChoice(
                        value: p,
                        title: p,
                        key: ValueKey('widget-project-$p'),
                      ),
                  ],
                  selected: _selected,
                  onSelected: _choose,
                ),
              ],
            ),
          ),
          if (_failed) ...[
            SizedBox(height: tokens.space3),
            KitNotice(
              icon: AppIconography.info,
              tone: AppStatusTone.neutral,
              message: l10n.widgetProjectUnavailable,
            ),
          ],
        ],
      ),
    );
  }
}
