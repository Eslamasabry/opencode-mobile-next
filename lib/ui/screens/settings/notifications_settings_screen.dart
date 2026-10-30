part of '../settings_screen.dart';

/// The one "Notifications and background" screen (`notifications-settings`):
/// what notifies, quiet hours, saved-server monitoring, the background
/// connection, Keep running (what to allow on this phone) and What runs by
/// itself (the AI Team's level, Always allowed actions), in that order, one
/// list. Quiet hours and Wi-Fi only exist once, here, and both the
/// saved-server monitor and the quota monitor read them.
///
/// A row is absent when this device cannot do it, and a section with no rows
/// is absent. Kit only (screen-settings-1): each section is a [KitRowGroup]
/// of [KitSwitchRow]s and [KitRow]s whose one plain line says what the
/// setting does; how monitoring works is folded into Details at the end.
/// Which part of the old "Notifications and background" page a screen shows:
/// the Settings hub has Notifications and Background as two rows (Settings
/// Standard W1), and Permissions and automation moved to Chat. [all] is every
/// section on one page, kept for the entry points that predate the split.
enum NotificationsPage { all, notifications, background }

class NotificationsSettingsScreen extends StatefulWidget {
  final ConnectionController controller;

  /// The part of the page to show; the title is the label of the row that
  /// opened it (N2).
  final NotificationsPage page;

  /// A section slug (`what`, `quiet`, `servers`, `background`,
  /// `keep-running`, `automation`) a search result means: the screen opens
  /// scrolled to it.
  final String? initialSection;

  const NotificationsSettingsScreen({
    super.key,
    required this.controller,
    this.initialSection,
    this.page = NotificationsPage.all,
    this.onlyProfileId,
  });

  /// Opened from one server's page: only that server's choices, with a link
  /// to the general page.
  final String? onlyProfileId;

  @override
  State<NotificationsSettingsScreen> createState() =>
      _NotificationsSettingsScreenState();
}

class _NotificationsSettingsScreenState
    extends State<NotificationsSettingsScreen>
    with WidgetsBindingObserver {
  bool _saving = false;
  bool _sendingTest = false;

  /// A refused write, said on the page until the next one succeeds.
  bool _saveFailed = false;

  /// Why Android did not turn the background connection on.
  String? _backgroundError;

  /// Android's own text behind [_backgroundError], for Copy details only.
  String? _backgroundErrorDetails;
  final _sectionKeys = <String, GlobalKey>{};

  /// True once Android itself is refusing this app's notifications (denied
  /// permission or muted): the honest state P0.6 asks the page to show,
  /// re-checked on resume ([didChangeAppLifecycleState]) rather than only
  /// read once at open.
  bool get _notificationsBlocked =>
      platformCapabilities.supportsBackgroundService &&
      !widget.controller.backgroundLive.notificationGranted;

  @override
  void initState() {
    super.initState();
    if (widget.initialSection case final slug?) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        final target = _sectionKeys[slug]?.currentContext;
        if (mounted && target != null) Scrollable.ensureVisible(target);
      });
    }
    WidgetsBinding.instance.addObserver(this);
    widget.controller.addListener(_changed);
    widget.controller.backgroundLive.addListener(_changed);
    // After the frame, not during it: refreshStatus marks itself busy and
    // notifies synchronously, and a notification mid-build is a setState
    // during build for every listener above this screen.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && platformCapabilities.supportsBackgroundService) {
        widget.controller.backgroundLive.refreshStatus();
      }
    });
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  /// A row's icon tile, which steps aside from 2× text: on a narrow phone
  /// the words need the width more than the decoration (A11Y-8).
  Widget? _icon(IconData icon, {Color? color}) =>
      MediaQuery.textScalerOf(context).scale(1) >= 2.0
      ? null
      : KitRow.icon(context, icon, color: color);

  Future<void> _toggleBackground(bool value) async {
    final controller = widget.controller;
    if (controller.backgroundLive.busy) return;
    setState(() {
      _backgroundError = null;
      _backgroundErrorDetails = null;
    });
    final enabled = await controller.setKeepLiveInBackground(value);
    if (!mounted) return;
    final error = controller.backgroundLive.lastError;
    if (error != null || enabled != value) {
      // The app's own sentence from the Android side stays; exception
      // text is said in words and kept only under Copy details.
      final copy = _settingsCopy(context);
      final words = error == null
          ? (value ? copy.e7SettingsUi22 : copy.settingsBackgroundOffFailed)
          : productErrorText(error, l10n: copy);
      setState(() {
        _backgroundError = words;
        _backgroundErrorDetails = error == words ? null : error;
      });
    }
  }

  Future<void> _sendTestNotification() async {
    if (_sendingTest) return;
    setState(() => _sendingTest = true);
    try {
      await widget.controller.backgroundLive.sendTestNotification();
    } finally {
      if (mounted) setState(() => _sendingTest = false);
    }
  }

  /// One write at a time: two quick taps must not race two read-modify-write
  /// passes over the same stored rule.
  Future<void> _save(Future<void> Function() write) async {
    if (_saving) return;
    setState(() => _saving = true);
    try {
      await write();
      if (mounted) setState(() => _saveFailed = false);
    } catch (_) {
      if (mounted) setState(() => _saveFailed = true);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  /// Opens the same place search does for usage alerts: Usage, Alerts.
  Future<void> _openQuotaInUsage() async {
    final copy = _settingsCopy(context);
    final entry = allSearchEntries(
      copy,
    ).where((entry) => entry.id == 'inside-usage-alerts').firstOrNull;
    if (entry == null) return;
    await entry.open(context, SearchScope.of(context, widget.controller));
  }

  Future<void> _shared(SharedNotifyRules Function(SharedNotifyRules) change) =>
      _save(() => widget.controller.updateSharedNotifyRules(change));

  Future<void> _quietTime(bool start, SharedNotifyRules rules) async {
    final copy = _settingsCopy(context);
    final current = (start ? rules.quietStart : rules.quietEnd)!;
    // The stock picker, told which end it sets and with a verb on its
    // button (notifications-settings-quiet-time-dialog).
    final selected = await showTimePicker(
      context: context,
      initialTime: TimeOfDay(hour: current ~/ 60, minute: current % 60),
      helpText: start ? copy.notifyQuietStartPicker : copy.notifyQuietEndPicker,
      confirmText: copy.notifyQuietSet,
    );
    if (selected == null || !mounted) return;
    final minutes = selected.hour * 60 + selected.minute;
    await _shared(
      (rules) => start
          ? rules.copyWith(quietStart: minutes)
          : rules.copyWith(quietEnd: minutes),
    );
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed &&
        platformCapabilities.supportsBackgroundService) {
      widget.controller.backgroundLive.refreshStatus();
    }
  }

  /// Scrolls to a section of this page (an answer in What runs by itself is
  /// changed in the section above it).
  void _showSection(String slug) {
    final target = _sectionKeys[slug]?.currentContext;
    if (target != null) {
      Scrollable.ensureVisible(target, duration: KitMotion.standard);
    }
  }

  GlobalKey _sectionKey(String slug) => _sectionKeys.putIfAbsent(
    slug,
    () => GlobalKey(debugLabel: 'notifications-$slug'),
  );

  /// Keep running reads the app's providers; without any (a bare test of
  /// this page) its section is left out.
  bool get _hasProviders {
    try {
      ProviderScope.containerOf(context, listen: false);
      return true;
    } on StateError {
      return false;
    }
  }

  String _clock(int minutes) =>
      TimeOfDay(hour: minutes ~/ 60, minute: minutes % 60).format(context);

  List<Widget> _whatNotifies(SharedNotifyRules rules) {
    final copy = _settingsCopy(context);
    final controller = widget.controller;
    final notifications = platformCapabilities.supportsNotifications;
    final blocked = _notificationsBlocked;
    // Blocked by Android: the switches rest, dimmed, and keep saying what
    // each one is for; the notice above says why, once (R3).
    final checkIn = rules.checkInAfterMinutes;
    return [
      if (notifications)
        KitSwitchRow(
          key: const ValueKey('notify-finished-runs'),
          leading: _icon(AppIconography.checkCircle),
          title: copy.notifyFinishedRuns,
          supporting: copy.notifyFinishedRunsDetail,
          value: controller.notificationPreferences.finishedRuns,
          onChanged: blocked
              ? null
              : (value) => _save(() => controller.setNotifyFinishedRuns(value)),
        ),
      if (notifications)
        KitSwitchRow(
          key: const ValueKey('notify-requests'),
          leading: _icon(AppIconography.question),
          title: copy.notifyRequests,
          supporting: copy.notifyRequestsDetail,
          value: controller.notificationPreferences.requests,
          onChanged: blocked
              ? null
              : (value) => _save(() => controller.setNotifyRequests(value)),
        ),
      // A check-in is also a row in the attention list, so it is worth
      // setting on a device that cannot notify.
      KitSwitchRow(
        key: const ValueKey('notify-check-ins'),
        leading: _icon(AppIconography.clock),
        title: copy.monitorCheckIn,
        supporting: platformCapabilities.supportsBackgroundService
            ? copy.monitorCheckInDetail
            : copy.monitorCheckInDetailForeground,
        value: checkIn != null,
        onChanged: (value) => _shared(
          (rules) => value
              ? rules.copyWith(
                  checkInAfterMinutes: ProfileNotifyRules.defaultCheckInMinutes,
                )
              : rules.copyWith(clearCheckIn: true),
        ),
      ),
      if (checkIn != null)
        KitPickerRow<int>(
          rowKey: const ValueKey('notify-check-in-after'),
          leading: _icon(AppIconography.timer),
          title: copy.monitorCheckInAfter,
          valueLabel: copy.monitorMinutes(checkIn),
          choices: [
            for (final minutes in ProfileNotifyRules.checkInChoices)
              KitChoice(value: minutes, title: copy.monitorMinutes(minutes)),
          ],
          selected: ProfileNotifyRules.checkInChoices.contains(checkIn)
              ? checkIn
              : null,
          onSelected: (minutes) =>
              _shared((rules) => rules.copyWith(checkInAfterMinutes: minutes)),
        ),
      // Usage alerts are set on the Usage › Alerts page, beside the levels they follow;
      // this row only leads there (one place for the choice).
      if (notifications)
        KitRow(
          key: const ValueKey('notify-quota-alerts'),
          leading: _icon(AppIconography.usage),
          title: copy.notifyQuotaAlerts,
          supporting: TextSpan(text: copy.notifyQuotaAlertsDetail),
          supportingMaxLines: 2,
          trailing: const KitChevron(),
          onTap: () => unawaited(_openQuotaInUsage()),
        ),
      // Real proof, not just a read of the permission flag (P0.6): posts an
      // actual alert through the same channel a coding request would use.
      if (notifications)
        KitRow(
          key: const ValueKey('notify-send-test'),
          leading: _icon(AppIconography.notificationImportant),
          title: copy.notifySendTest,
          // It acts in place: the accent words of a text button.
          titleAccent: true,
          supporting: TextSpan(text: copy.notifySendTestDetail),
          supportingMaxLines: 2,
          enabled: !_sendingTest,
          disabledReason: _sendingTest ? copy.notifySendingTest : null,
          onTap: _sendTestNotification,
        ),
    ];
  }

  List<Widget> _quietHours(SharedNotifyRules rules) {
    // Quiet hours only silence notifications.
    if (!platformCapabilities.supportsNotifications) return const [];
    final copy = _settingsCopy(context);
    final allDay = rules.quietEnabled && rules.quietStart == rules.quietEnd;
    return [
      KitSwitchRow(
        key: const ValueKey('notify-quiet-hours'),
        leading: _icon(AppIconography.darkMode),
        title: copy.monitorQuiet,
        supporting: copy.notifyQuietDetail,
        value: rules.quietEnabled,
        onChanged: (value) => _shared(
          (rules) => value
              ? rules.copyWith(
                  quietStart: SharedNotifyRules.defaultQuietStart,
                  quietEnd: SharedNotifyRules.defaultQuietEnd,
                )
              : rules.copyWith(clearQuiet: true),
        ),
      ),
      if (rules.quietEnabled) ...[
        KitRow(
          key: const ValueKey('notify-quiet-start'),
          leading: _icon(AppIconography.clock),
          title: copy.monitorQuietStart,
          trailing: KitRowValue(_clock(rules.quietStart!)),
          onTap: () => _quietTime(true, rules),
        ),
        KitRow(
          key: const ValueKey('notify-quiet-end'),
          leading: _icon(AppIconography.clock),
          title: copy.monitorQuietEnd,
          // The same start and end mean quiet all day: said, not guessed.
          supporting: allDay ? TextSpan(text: copy.notifyQuietAllDay) : null,
          supportingMaxLines: 2,
          trailing: KitRowValue(_clock(rules.quietEnd!)),
          onTap: () => _quietTime(false, rules),
        ),
      ],
    ];
  }

  List<Widget> _background() {
    if (!platformCapabilities.supportsBackgroundService) return const [];
    final copy = _settingsCopy(context);
    final controller = widget.controller;
    final live = controller.backgroundLive;
    final error = _backgroundError;
    return [
      KitSwitchRow(
        key: const ValueKey('background-live-switch'),
        leading: _icon(AppIconography.sync),
        title: copy.e7SettingsUi25,
        supporting: copy.e7SettingsUi26,
        // The cost (battery, a permanent notification) is the end of the
        // sentence: it is never cut off.
        supportingMaxLines: 4,
        value: controller.keepLiveInBackground,
        onChanged: _toggleBackground,
        below: error == null
            ? null
            : KitNotice.error(
                key: const ValueKey('background-live-error'),
                message: error,
                details: _backgroundErrorDetails,
                reportSource: 'settings-background',
              ),
      ),
      if (controller.keepLiveInBackground)
        KitRow(
          key: const ValueKey('background-battery-row'),
          leading: _icon(
            live.batteryOptimizationIgnored
                ? AppIconography.batteryCharging
                : AppIconography.batteryWarning,
          ),
          title: live.batteryOptimizationIgnored
              ? copy.e7SettingsUi27
              : copy.e7SettingsUi28,
          supporting: TextSpan(
            text: live.batteryOptimizationIgnored
                ? copy.e7SettingsUi29
                : copy.e7SettingsUi30,
          ),
          supportingMaxLines: 2,
          trailing: live.batteryOptimizationIgnored ? null : const KitChevron(),
          onTap: live.batteryOptimizationIgnored
              ? null
              : () async {
                  await live.requestBatteryOptimizationExemption();
                },
        ),
      _BackgroundStatusRow(
        live: live,
        onRestart: live.busy ? null : () => _toggleBackground(true),
      ),
    ];
  }

  List<Widget> _savedServers(SharedNotifyRules rules) {
    final copy = _settingsCopy(context);
    final controller = widget.controller;
    final monitor = controller.profileMonitor;
    final servers = controller.isIsolated
        ? const <ServerProfile>[]
        : [
            for (final profile in controller.store.profiles)
              if (controller.isProfileReadable(profile.id)) profile,
          ];
    final wifi = platformCapabilities.supportsBackgroundService;
    if (servers.isEmpty && !wifi) return const [];
    return [
      for (final profile in servers)
        ..._monitoredServerRows(
          profile,
          onSave: (next) => _save(() => monitor.setRules(profile.id, next)),
        ),
      // Nothing to watch yet: said, rather than an empty section.
      if (servers.isEmpty)
        KitRow(
          key: const ValueKey('notify-no-servers'),
          leading: _icon(AppIconography.server),
          title: copy.notifyNoServersTitle,
          supporting: TextSpan(text: copy.notifyNoServersDetail),
          supportingMaxLines: 2,
        ),
      // One Wi-Fi rule for every background check: saved servers and quota
      // sources alike. Without a background service nothing checks in the
      // background, so there is nothing to restrict.
      if (wifi)
        KitSwitchRow(
          key: const ValueKey('notify-wifi-only'),
          leading: _icon(AppIconography.network),
          title: copy.notifyWifiOnly,
          supporting: copy.monitorWifiDetail,
          value: rules.wifiOnly,
          onChanged: (value) =>
              _shared((rules) => rules.copyWith(wifiOnly: value)),
        ),
    ];
  }

  /// One saved server: whether it is monitored at all, and whether what the
  /// monitor finds may notify. Everything else about notifying is shared.
  List<Widget> _monitoredServerRows(
    ServerProfile profile, {
    required ValueChanged<ProfileNotifyRules> onSave,
  }) {
    final copy = _settingsCopy(context);
    final controller = widget.controller;
    final monitor = controller.profileMonitor;
    final rules = monitor.rulesFor(profile.id);
    final supported = monitor.supportsProfile(profile);
    return [
      KitSwitchRow(
        key: ValueKey('monitor-enabled-${profile.id}'),
        leading: _icon(AppIconography.server),
        title: serverDisplayName(
          profile,
          lookupAppLocalizations(Localizations.localeOf(context)),
          among: controller.store.profiles,
        ),
        supporting: copy.monitorOptIn,
        value: rules.enabled,
        disabledReason: supported ? null : copy.e7ProjectMonitorUnsupported,
        onChanged: !supported
            ? null
            : (value) => onSave(rules.copyWith(enabled: value)),
      ),
      if (supported &&
          rules.enabled &&
          platformCapabilities.supportsNotifications)
        KitSwitchRow(
          key: ValueKey('monitor-notify-${profile.id}'),
          leading: _icon(AppIconography.notificationImportant),
          title: copy.monitorNotifications,
          value: rules.notifications,
          onChanged: (value) => onSave(rules.copyWith(notifications: value)),
        ),
    ];
  }

  @override
  Widget build(BuildContext context) {
    final copy = _settingsCopy(context);
    final controller = widget.controller;
    final tokens = KitTokens.of(context);
    final rules = controller.sharedNotifyRules;
    // Quiet hours is one switch that names itself: no header repeats it.
    final page = widget.page;
    final showNotifications = page != NotificationsPage.background;
    final showBackground = page != NotificationsPage.notifications;
    final only = widget.onlyProfileId == null
        ? null
        : controller.store.profiles
              .where((p) => p.id == widget.onlyProfileId)
              .firstOrNull;
    final sections = <(String, String?, List<Widget>)>[
      if (only != null)
        (
          'server',
          serverDisplayName(
            only,
            lookupAppLocalizations(Localizations.localeOf(context)),
            among: controller.store.profiles,
          ),
          _monitoredServerRows(
            only,
            onSave: (next) =>
                _save(() => controller.profileMonitor.setRules(only.id, next)),
          ),
        )
      else if (showNotifications) ...[
        ('what', copy.notifySectionWhat, _whatNotifies(rules)),
        ('quiet', null, _quietHours(rules)),
        ('servers', copy.notifySectionServers, _savedServers(rules)),
      ],
      if (showBackground && only == null)
        (
          'background',
          // The page is already named "Background": a header repeating it
          // is dropped.
          page == NotificationsPage.background
              ? null
              : copy.notifySectionBackground,
          _background(),
        ),
    ].where((section) => section.$3.isNotEmpty).toList();
    final hasServers =
        showNotifications &&
        only == null &&
        !controller.isIsolated &&
        controller.store.profiles.any(
          (profile) => controller.isProfileReadable(profile.id),
        );
    final notices = <Widget>[
      // The honest state (P0.6): Android is refusing this app's
      // notifications, so every switch below is decoration until this is
      // fixed. Comes before everything else.
      // The page's one main action while it holds: a solid button, not a
      // tertiary word under the notice.
      if (_notificationsBlocked)
        Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitNotice(
              key: const ValueKey('notifications-blocked-notice'),
              icon: AppIconography.notificationImportant,
              title: copy.notifyBlockedTitle,
              message: copy.notifyBlockedMessage,
            ),
            SizedBox(height: tokens.space3),
            KitButton.primary(
              key: const ValueKey('notifications-open-settings'),
              label: copy.notifyTurnOnInAndroid,
              onPressed: () =>
                  controller.backgroundLive.openNotificationSettings(),
            ),
          ],
        ),
      // Android 15 stops the service on its own once the daily budget is
      // spent, and the switch flips itself off when it does. It needs the
      // person, so it comes before every setting (plan 5.7).
      if (platformCapabilities.supportsBackgroundService &&
          controller.backgroundLive.stoppedByAndroidTimeout)
        KitNotice(
          key: const ValueKey('background-timeout-notice'),
          icon: AppIconography.timer,
          title: copy.e7SettingsUi23,
          message: copy.e7SettingsUi24,
        ),
      if (_saveFailed)
        KitNotice(
          key: const ValueKey('notifications-save-failed'),
          tone: AppStatusTone.failure,
          message: copy.monitorSaveFailed,
          onDismiss: () => setState(() => _saveFailed = false),
          dismissLabel: copy.notifyDismiss,
        ),
    ];
    return KitScreen(
      topBar: KitTopBar(
        title: switch (page) {
          NotificationsPage.all => copy.settingsHubGroupNotifications,
          NotificationsPage.notifications => copy.settingsV3Notifications,
          NotificationsPage.background => copy.settingsV3Background,
        },
      ),
      width: KitScreenWidth.reading,
      loading: _saving,
      loadingLabel: copy.notifySaving,
      // Not a lazy list: a few dozen rows in one Column, so a link that
      // means one section can find it laid out.
      body: ListView(
        key: const ValueKey('notifications-settings'),
        padding: EdgeInsetsDirectional.only(
          top: tokens.space2,
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (final notice in notices)
                Padding(
                  padding: EdgeInsetsDirectional.only(
                    start: tokens.gutter,
                    end: tokens.gutter,
                    bottom: tokens.sectionGap,
                  ),
                  child: notice,
                ),
              for (final (index, (slug, title, rows)) in sections.indexed) ...[
                // A section without a heading (Quiet hours names itself)
                // goes on from the list above it with the rows' own
                // hairline, not an empty section gap that names nothing
                // (impeccable audit 2026-10-28, #14). A style that frames
                // its panels keeps KitRowGroup's seam between them.
                if (index > 0 && title == null)
                  if (KitSurfaceLook.flatLists(context))
                    KitDivider(
                      inset: KitDividerInset.text,
                      endInset: tokens.gutter,
                    )
                  else
                    const SizedBox.shrink()
                else if (index > 0)
                  SizedBox(height: tokens.sectionGap),
                KeyedSubtree(
                  key: _sectionKey(slug),
                  child: KitRowGroup(
                    key: ValueKey('notifications-section-$slug'),
                    label: title,
                    children: rows,
                  ),
                ),
              ],
              // One server's view of the page: the way to the choices that
              // apply to every server.
              if (only != null) ...[
                SizedBox(height: tokens.sectionGap),
                KitRowGroup(
                  children: [
                    KitRow(
                      key: const ValueKey('notifications-all-servers-link'),
                      title: copy.settingsV3NotificationsAllServers,
                      trailing: const KitChevron(),
                      onTap: () => pushKitPage<void>(
                        context,
                        (_) => NotificationsSettingsScreen(
                          controller: controller,
                          page: NotificationsPage.notifications,
                        ),
                      ),
                    ),
                  ],
                ),
              ],
              // What to allow on this phone so Android keeps the app running.
              if (showBackground &&
                  only == null &&
                  platformCapabilities.supportsBackgroundService &&
                  _hasProviders) ...[
                SizedBox(height: tokens.sectionGap),
                KeyedSubtree(
                  key: _sectionKey('keep-running'),
                  child: const KeepRunningSection(),
                ),
              ],
              // What the app and the agent do without asking first: on the
              // old single page only; Chat › Permissions and automation
              // holds it now.
              if (page == NotificationsPage.all) ...[
                SizedBox(height: tokens.sectionGap),
                KeyedSubtree(
                  key: _sectionKey('automation'),
                  child: AutomationSettingsSection(
                    controller: controller,
                    onShowSection: _showSection,
                  ),
                ),
              ],
              // How monitoring works: read once, never first (KIT-33).
              if (hasServers) ...[
                SizedBox(height: tokens.sectionGap),
                Padding(
                  padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
                  child: KitDetailsFold(
                    key: const ValueKey('notifications-monitor-details'),
                    label: copy.notifyMonitorDetails,
                    notes: [
                      copy.monitorOptInDetail,
                      copy.monitorScope,
                      copy.monitorDisclosure,
                    ],
                  ),
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    widget.controller.removeListener(_changed);
    widget.controller.backgroundLive.removeListener(_changed);
    super.dispose();
  }
}

/// One line of truth under the switch: is the service actually running?
/// The preference and the service can disagree — Android 15+ stops the
/// service on its own after six hours per day — and this row is where the
/// disagreement shows, with the restart one tap away.
class _BackgroundStatusRow extends StatelessWidget {
  final BackgroundLiveController live;
  final VoidCallback? onRestart;

  const _BackgroundStatusRow({required this.live, required this.onRestart});

  @override
  Widget build(BuildContext context) {
    final copy = _settingsCopy(context);
    final stopped = live.stoppedByAndroidTimeout;
    final running = live.enabled && live.active;
    final starting = live.enabled && !live.active;
    final title = stopped
        ? copy.e7SettingsUi31
        : running
        ? copy.e7SettingsUi32
        : starting
        ? copy.commandRunning
        : copy.quotaBudgetOff;
    final icon = stopped
        ? AppIconography.timer
        : running
        ? AppIconography.sync
        : starting
        ? AppIconography.cloud
        : AppIconography.cloudOff;
    // The state is the tinted icon and the words (§6), not a coloured
    // title; a stop is said in words, never in the danger colour (LOOK-5).
    return KitRow(
      key: const ValueKey('background-status-row'),
      leading: KitRow.icon(
        context,
        icon,
        color: running ? ThemeRoles.of(context).success : null,
      ),
      title: title,
      supporting: TextSpan(text: copy.e7SettingsUi34),
      supportingMaxLines: 2,
      trailing: stopped
          ? KitIconButton(
              key: const ValueKey('background-restart'),
              icon: AppIconography.restart,
              tooltip: copy.notifyRestartBackground,
              onPressed: onRestart,
            )
          : null,
      onTap: stopped ? onRestart : null,
    );
  }
}
