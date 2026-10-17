import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../session/session.dart';

/// User theme preference (light, dark, or follow the system), persisted on the
/// device.
class ThemeModeController extends Notifier<ThemeMode> {
  @override
  ThemeMode build() => _decode(ref.watch(tokenStoreProvider).themeModeCode);

  void set(ThemeMode mode) {
    state = mode;
    ref.read(tokenStoreProvider).themeModeCode = _encode(mode);
  }

  void toggle() => set(state == ThemeMode.dark ? ThemeMode.light : ThemeMode.dark);

  static ThemeMode _decode(String code) => switch (code) {
        'light' => ThemeMode.light,
        'dark' => ThemeMode.dark,
        _ => ThemeMode.system,
      };

  static String _encode(ThemeMode mode) => switch (mode) {
        ThemeMode.light => 'light',
        ThemeMode.dark => 'dark',
        ThemeMode.system => 'system',
      };
}

final themeModeProvider = NotifierProvider<ThemeModeController, ThemeMode>(ThemeModeController.new);

/// Low-bandwidth mode: defers images and media, compresses uploads, uses
/// smaller list pages, and disables non-essential animation
/// (specification section 8.6).
class LowBandwidthController extends Notifier<bool> {
  @override
  bool build() => ref.watch(tokenStoreProvider).lowBandwidth;

  void set(bool value) {
    state = value;
    ref.read(tokenStoreProvider).lowBandwidth = value;
  }

  void toggle() => set(!state);
}

final lowBandwidthProvider = NotifierProvider<LowBandwidthController, bool>(LowBandwidthController.new);
