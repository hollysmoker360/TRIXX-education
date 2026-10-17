import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/session/session.dart';
import '../../../core/widgets/common.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final session = ref.watch(sessionProvider);
    final user = session.user;
    final membership = session.membership;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        SectionCard(
          title: user?.displayName ?? '',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(user?.email ?? ''),
              if (membership != null) ...[
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  children: [
                    StatusPill(label: membership.roleLabel),
                    StatusPill(
                        label: membership.schoolName,
                        color: Colors.blueGrey),
                  ],
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 12),
        SectionCard(
          title: s.switchSchool,
          child: Column(
            children: [
              for (final m in session.memberships)
                ListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(m.schoolId == session.selectedSchoolId
                      ? Icons.radio_button_checked
                      : Icons.radio_button_unchecked),
                  title: Text(m.schoolName),
                  subtitle: Text(m.roleLabel),
                  onTap: () =>
                      ref.read(sessionProvider.notifier).selectSchool(m.schoolId),
                ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        SectionCard(
          title: s.language,
          child: Column(
            children: [
              for (final locale in const [Locale('en'), Locale('sw')])
                ListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(
                      ref.watch(localeProvider).languageCode ==
                              locale.languageCode
                          ? Icons.radio_button_checked
                          : Icons.radio_button_unchecked),
                  title: Text(
                      locale.languageCode == 'en' ? 'English' : 'Kiswahili'),
                  onTap: () =>
                      ref.read(localeProvider.notifier).set(locale),
                ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        SectionCard(
          title: _notificationTitle(s),
          child: Column(
            children: [
              SwitchListTile(
                value: ref.watch(tokenStoreProvider).notificationsInApp,
                onChanged: (value) =>
                    ref.read(tokenStoreProvider).notificationsInApp = value,
                title: Text(s.inAppNotifications),
              ),
              SwitchListTile(
                value: ref.watch(tokenStoreProvider).notificationsEmail,
                onChanged: (value) =>
                    ref.read(tokenStoreProvider).notificationsEmail = value,
                title: Text(s.emailNotifications),
              ),
              SwitchListTile(
                value: ref.watch(tokenStoreProvider).notificationsSms,
                onChanged: (value) =>
                    ref.read(tokenStoreProvider).notificationsSms = value,
                title: Text(s.smsNotifications),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        OutlinedButton.icon(
          onPressed: () => ref.read(sessionProvider.notifier).signOut(),
          icon: const Icon(Icons.logout),
          label: Text(s.signOut),
        ),
      ],
    );
  }

  String _notificationTitle(S s) => s.notificationSettings;
}
