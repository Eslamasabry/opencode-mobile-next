import 'package:flutter/material.dart';

import '../../platform/platform_capabilities.dart';
import '../../state/connection.dart';
import '../../state/profiles.dart';
import 'product_states.dart';

String appearanceLabel(AppAppearance appearance) => switch (appearance) {
  AppAppearance.system =>
    platformCapabilities.isAndroid ? 'Follow Android' : 'Follow system',
  AppAppearance.light => 'Light',
  AppAppearance.dark => 'Dark',
};

String _appearanceDescription(AppAppearance appearance) => switch (appearance) {
  AppAppearance.system =>
    platformCapabilities.isAndroid
        ? 'Match this phone’s current light or dark setting'
        : 'Match this device’s current light or dark setting',
  AppAppearance.light => 'Use the bright editorial workspace',
  AppAppearance.dark => 'Use the focused low-light workspace',
};

IconData _appearanceIcon(AppAppearance appearance) => switch (appearance) {
  AppAppearance.system => Icons.settings_brightness_outlined,
  AppAppearance.light => Icons.light_mode_outlined,
  AppAppearance.dark => Icons.dark_mode_outlined,
};

Future<void> showAppearancePicker(
  BuildContext context, {
  required ConnectionController controller,
}) async {
  final current = controller.appearance.value;
  final selected = await showModalBottomSheet<AppAppearance>(
    context: context,
    useSafeArea: true,
    isScrollControlled: true,
    constraints: const BoxConstraints(maxWidth: 620),
    builder: (context) => ListView(
      key: const Key('appearance-picker'),
      shrinkWrap: true,
      padding: const EdgeInsets.fromLTRB(12, 0, 12, 24),
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 4, 12, 12),
          child: Text(
            'Appearance',
            style: Theme.of(context).textTheme.titleLarge,
          ),
        ),
        for (final appearance in AppAppearance.values)
          Semantics(
            selected: current == appearance,
            child: ListTile(
              key: ValueKey('appearance-${appearance.name}'),
              leading: Icon(_appearanceIcon(appearance)),
              title: Text(appearanceLabel(appearance)),
              subtitle: Text(_appearanceDescription(appearance)),
              trailing: current == appearance
                  ? const Icon(Icons.check_rounded)
                  : null,
              onTap: () => Navigator.of(context).pop(appearance),
            ),
          ),
      ],
    ),
  );
  if (!context.mounted || selected == null || selected == current) return;
  try {
    await controller.setAppearance(selected);
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Appearance set to ${appearanceLabel(selected)}')),
    );
  } catch (error) {
    if (!context.mounted) return;
    showProductError(context, error);
  }
}
