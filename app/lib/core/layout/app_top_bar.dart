import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../brand/brand.dart';
import '../l10n/strings.dart';
import '../settings/preferences.dart';
import '../session/session.dart';
import '../theme/tokens.dart';
import '../widgets/brand.dart';
import '../widgets/sync_indicator.dart';
import 'window_class.dart';

/// The application top bar: school selector (only when the user belongs to
/// several schools), global search / command palette, notifications, sync
/// indicator, and the profile menu (specification section 5.2).
class AppTopBar extends ConsumerWidget implements PreferredSizeWidget {
  const AppTopBar({
    super.key,
    this.title,
    this.onCommandPalette,
    this.onNotifications,
    this.notificationCount = 0,
    this.onOpenSync,
    this.leading,
    this.showSearch = true,
  });

  final String? title;
  final VoidCallback? onCommandPalette;
  final VoidCallback? onNotifications;
  final int notificationCount;
  final VoidCallback? onOpenSync;
  final Widget? leading;
  final bool showSearch;

  @override
  Size get preferredSize => const Size.fromHeight(AppLayout.topBarHeight);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final session = ref.watch(sessionProvider);
    final window = windowClassOfContext(context);
    final showSearchField = showSearch && !window.isCompact && onCommandPalette != null;

    return Container(
      height: AppLayout.topBarHeight,
      decoration: BoxDecoration(
        color: context.scheme.surface,
        border: Border(bottom: BorderSide(color: context.tokens.border)),
      ),
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.s8),
      child: Row(
        children: [
          if (leading != null) leading!,
          if (!window.usesRail)
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: AppSpacing.s8),
              child: TrixxLogo(height: 26, markOnly: true),
            ),
          if (title != null && !window.isCompact)
            Flexible(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: AppSpacing.s8),
                child: Text(title!, style: context.texts.titleMedium, overflow: TextOverflow.ellipsis),
              ),
            ),
          const Spacer(),
          if (showSearchField)
            SizedBox(
              width: 260,
              child: InkWell(
                borderRadius: AppRadius.mdAll,
                onTap: onCommandPalette,
                child: InputDecorator(
                  decoration: InputDecoration(
                    isDense: true,
                    prefixIcon: const Icon(Icons.search, size: 18),
                    hintText: s.search,
                    suffixIcon: Padding(
                      padding: const EdgeInsets.only(right: AppSpacing.s8),
                      child: Text(
                        'Ctrl K',
                        style: context.texts.labelSmall,
                      ),
                    ),
                  ),
                  child: const SizedBox.shrink(),
                ),
              ),
            )
          else if (showSearch && onCommandPalette != null)
            IconButton(
              onPressed: onCommandPalette,
              icon: const Icon(Icons.search),
              tooltip: s.search,
            ),
          if (session.memberships.where((m) => m.status == 'active').length > 1)
            _SchoolSelector(session: session),
          IconButton(
            onPressed: onNotifications,
            tooltip: s.notifications,
            icon: Badge(
              isLabelVisible: notificationCount > 0,
              label: Text('$notificationCount'),
              child: const Icon(Icons.notifications_none),
            ),
          ),
          SyncIndicator(onOpenDetail: onOpenSync),
          const SizedBox(width: AppSpacing.s4),
          _ProfileMenu(session: session),
        ],
      ),
    );
  }
}

class _SchoolSelector extends ConsumerWidget {
  const _SchoolSelector({required this.session});

  final SessionState session;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final active = session.memberships.where((m) => m.status == 'active').toList();
    final current = session.membership;

    return PopupMenuButton<String>(
      tooltip: s.switchSchool,
      onSelected: (id) => ref.read(sessionProvider.notifier).selectSchool(id),
      itemBuilder: (context) => [
        for (final membership in active)
          PopupMenuItem(
            value: membership.schoolId,
            child: Row(
              children: [
                Avatar(name: membership.schoolName, size: 24, square: true),
                const SizedBox(width: AppSpacing.s8),
                Expanded(child: Text(membership.schoolName, overflow: TextOverflow.ellipsis)),
                if (membership.schoolId == current?.schoolId)
                  Icon(Icons.check, size: 18, color: context.scheme.primary),
              ],
            ),
          ),
      ],
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 220),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.s8, vertical: AppSpacing.s8),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Avatar(name: current?.schoolName ?? s.noSchool, size: 22, square: true),
              const SizedBox(width: AppSpacing.s8),
              Flexible(
                child: Text(
                  current?.schoolName ?? s.selectSchool,
                  style: context.texts.labelMedium,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const Icon(Icons.expand_more, size: 18),
            ],
          ),
        ),
      ),
    );
  }
}

class _ProfileMenu extends ConsumerWidget {
  const _ProfileMenu({required this.session});

  final SessionState session;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final locale = ref.watch(localeProvider);
    final themeMode = ref.watch(themeModeProvider);
    final lowBandwidth = ref.watch(lowBandwidthProvider);
    final name = session.user?.displayName ?? '';

    return PopupMenuButton<String>(
      tooltip: s.profile,
      onSelected: (value) async {
        switch (value) {
          case 'signout':
            await ref.read(sessionProvider.notifier).signOut();
          case 'theme':
            ref.read(themeModeProvider.notifier).toggle();
          case 'lowbandwidth':
            ref.read(lowBandwidthProvider.notifier).toggle();
          case 'en':
            ref.read(localeProvider.notifier).set(const Locale('en'));
          case 'sw':
            ref.read(localeProvider.notifier).set(const Locale('sw'));
        }
      },
      itemBuilder: (context) => [
        PopupMenuItem(
          enabled: false,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(name, style: context.texts.titleSmall),
              if (session.user?.email != null)
                Text(session.user!.email, style: context.texts.bodySmall),
              const SizedBox(height: AppSpacing.s4),
              Text(AppBrand.poweredBy, style: context.texts.labelSmall),
            ],
          ),
        ),
        const PopupMenuDivider(),
        CheckedPopupMenuItem(
          value: 'theme',
          checked: themeMode == ThemeMode.dark,
          child: Text(s.darkTheme),
        ),
        CheckedPopupMenuItem(
          value: 'lowbandwidth',
          checked: lowBandwidth,
          child: Text(s.lowBandwidthMode),
        ),
        PopupMenuDivider(),
        PopupMenuItem(value: 'en', child: Row(children: [
          if (locale.languageCode == 'en') Icon(Icons.check, size: 16, color: context.scheme.primary),
          const SizedBox(width: AppSpacing.s8),
          Text(s.english),
        ])),
        PopupMenuItem(value: 'sw', child: Row(children: [
          if (locale.languageCode == 'sw') Icon(Icons.check, size: 16, color: context.scheme.primary),
          const SizedBox(width: AppSpacing.s8),
          Text(s.kiswahili),
        ])),
        const PopupMenuDivider(),
        PopupMenuItem(
          value: 'signout',
          child: Row(children: [
            const Icon(Icons.logout, size: 18),
            const SizedBox(width: AppSpacing.s8),
            Text(s.signOut),
          ]),
        ),
      ],
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.s4),
        child: Avatar(name: name.isEmpty ? '?' : name, size: 32),
      ),
    );
  }
}
